"""Processing pipeline orchestrator (Phase 5).

`_process` runs the full line-art → regions → numbers → export flow against a
**difficulty profile**. `run` is the initial pass (from the upload, medium
profile); `reprocess` re-runs against a different profile for the cleanup APIs,
snapshotting the previous result first so it can be undone.

Each stage is a thin call into `services/*`. The order is:

    preprocess → line art → dust removal → gap repair → region detect (+ merge)
    → mask → SVG (smoothed) → label optimize → JSON → preview → quality report
    → game config → zip
"""
from __future__ import annotations

import json
import shutil
import time
from pathlib import Path

import cv2
import numpy as np

from config import (
    ARTIFACTS,
    DEFAULT_DIFFICULTY,
    DEFAULT_LINEART_MODE,
    DEFAULT_PROFILE,
    DIFFICULTY_PROFILES,
    OUTPUT_PROFILES,
    PROCESSING_STEPS,
    SEMANTIC_MODE,
)
from core import palette_budget
from job_store import (
    fail_job,
    finish_job,
    job_dir,
    make_backup,
    read_lineart_settings,
    set_step,
    write_lineart_settings,
    write_progress,
)
from services import (
    advanced_lineart_generator,
    ai_mode_router,
    artistic_border_engine,
    artistic_border_renderer,
    artistic_decision_engine,
    border_engine,
    contour_character_engine,
    fragment_cleanup_engine,
    color_sampler,
    game_preview_renderer,
    gap_repair,
    gradient_preservation_engine,
    flow_gradient_engine,
    intelligent_split_engine,
    image_preprocess,
    image_type_router,
    json_generator,
    label_optimizer,
    lineart_quality_score,
    mask_generator,
    material_behavior_engine,
    original_pixel_reveal_renderer,
    processing_policy_engine,
    palette_grouper,
    preview_renderer,
    quality_analyzer,
    region_cleanup,
    region_detector,
    region_hierarchy,
    review_engine,
    human_visual_intelligence,
    human_scene_understanding,
    publishing,
    semantic_region_analyzer,
    svg_generator,
    texture_preview_renderer,
    zip_exporter,
)

# A small delay so the UI's step animation stays readable. Excluded from the
# reported processing time, which only counts real compute.
STEP_DELAY_SECONDS = 0.6


def _p(folder: Path, logical: str) -> Path:
    return folder / ARTIFACTS[logical]


def _profile_material_budget(profile: dict) -> dict:
    """The profile's per-material palette budget: the base CO1 table scaled by the
    profile's `palette_budget_scale` (1.0 = current medium table, exact)."""
    scale = float((profile or {}).get("palette_budget_scale", 1.0))
    return {k: v * scale for k, v in palette_budget.MATERIAL_BUDGET.items()}


def _profile_material_kcap(profile: dict) -> dict:
    """The profile's per-material split k-cap: the base QF12 table offset by
    `texture_kcap_delta` (0 = current medium table, exact), floored at 2."""
    delta = int((profile or {}).get("texture_kcap_delta", 0))
    return {k: max(2, v + delta) for k, v in intelligent_split_engine.MATERIAL_KCAP.items()}


def _palette_budget(folder: Path, profile: dict | None = None) -> tuple[dict | None, dict | None]:
    """Per-region LAB merge thresholds + material categories from the brain reports
    (Commercial Opt 1), using the chosen profile's per-material budget.

    Reads QF13 `artistic_decision_report` + QF12 `intelligent_split_report` from the
    job folder and returns (label -> threshold, label -> category) for
    `palette_grouper.group`. Returns (None, None) when the reports are absent (e.g.
    the first `_process` pass, before the brain has run) so grouping transparently
    falls back to the flat delta."""
    art = _p(folder, "artistic_decision_report")
    if not art.exists():
        return None, None
    try:
        artistic = json.loads(art.read_text(encoding="utf-8"))
        spl = _p(folder, "intelligent_split_report")
        split = json.loads(spl.read_text(encoding="utf-8")) if spl.exists() else {}
        delta, cats = palette_budget.build_label_budget(
            artistic, split, budget=_profile_material_budget(profile or {})
        )
        return (delta or None), (cats or None)
    except (ValueError, OSError):
        return None, None


def _process(
    job_id: str, profile: dict, profile_name: str, source: str,
    use_ai: bool = True, skip_lineart: bool = False,
) -> None:
    """Run the full pipeline. `source='original'` preprocesses the upload;
    anything else reuses the existing preprocessed.png (for reprocessing).
    `use_ai` lets the initial run try the RunPod helper; reprocessing forces the
    local engine. `skip_lineart` reuses the existing lineart.png (after a
    line-art regeneration) and only re-runs region detection onward."""
    folder = job_dir(job_id)
    compute_seconds = 0.0

    # Reset OpenCV's global RNG to its default state so a run is deterministic
    # regardless of any prior run in the same process (the quality-retry engine
    # runs several profiles back to back). setRNGSeed(0) == the fresh-process
    # default state, so this is a no-op for the first/only run (output unchanged).
    cv2.setRNGSeed(0)

    def timed(fn, *args, **kwargs):
        nonlocal compute_seconds
        start = time.monotonic()
        result = fn(*args, **kwargs)
        compute_seconds += time.monotonic() - start
        return result

    # Step 1 — Preparing image
    set_step(job_id, 0, 10)
    if source == "original":
        preprocessed = timed(image_preprocess.run, _p(folder, "original"), _p(folder, "preprocessed"))
    else:
        preprocessed = _p(folder, "preprocessed")

    # Router — classify the input type (Input -> Router -> Processing -> Reveal).
    # Reporting only; never raises, changes no downstream processing.
    try:
        image_type_router.route(_p(folder, "original"), _p(folder, "image_type_report"))
    except Exception:  # noqa: BLE001 - routing must never break processing
        pass
    time.sleep(STEP_DELAY_SECONDS)

    # Optional AI helper (enhance + AI line art + semantics). Never the region
    # source: on success we only use the AI line art as input to the LOCAL
    # region pipeline; any failure falls back to the local line-art engine.
    ai_plan = {"source": "local"}
    if use_ai and not skip_lineart:
        try:
            ai_plan = ai_mode_router.maybe_enhance(job_id, folder)
        except Exception:  # noqa: BLE001 - AI is best-effort
            ai_plan = {"source": "fallback"}

    # Step 2 — Creating clean line art (existing | AI | advanced local engine)
    set_step(job_id, 1, 26)
    ai_lineart = ai_plan.get("ai_lineart")
    if skip_lineart:
        lineart = _p(folder, "lineart")  # already regenerated by caller
    elif ai_lineart and Path(ai_lineart).exists():
        shutil.copyfile(ai_lineart, _p(folder, "lineart"))
        lineart = _p(folder, "lineart")
    else:
        timed(
            advanced_lineart_generator.run,
            preprocessed, _p(folder, "lineart"), read_lineart_settings(job_id),
        )
        lineart = _p(folder, "lineart")
    # Line-art quality is reported for every job.
    lineart_quality_score.write(
        lineart_quality_score.score(lineart), _p(folder, "lineart_quality")
    )
    # Ensure a settings file always exists so it is captured by backups and can
    # be reverted on undo.
    if not (folder / "lineart_settings.json").exists():
        write_lineart_settings(job_id, {"mode": DEFAULT_LINEART_MODE})
    time.sleep(STEP_DELAY_SECONDS)

    # Step 3 — Closing gaps: dust removal + Perfect Border & Shape Engine
    # (PBSE v1: safe 1-2px gap repair + shape refinement + border validation).
    set_step(job_id, 2, 42)
    noise_metrics = timed(region_cleanup.remove_noise, lineart, lineart, profile)
    border_report = timed(
        border_engine.run, lineart, lineart, preprocessed, profile,
        _p(folder, "leak_map"), _p(folder, "merged_region_map"),
        _p(folder, "split_preview_map"),
    )
    (folder / ARTIFACTS["border_quality_report"]).write_text(
        json.dumps(border_report, indent=2), encoding="utf-8"
    )
    repair_metrics = {
        "gaps_repaired": border_report.get("repaired_gaps", 0),
        "line_fragments": border_report.get("fragments_after", 0),
    }
    time.sleep(STEP_DELAY_SECONDS)

    # Step 4 — Detecting regions (+ tiny merge, mask, smoothed SVG,
    #          median color sampling, LAB palette grouping)
    set_step(job_id, 3, 60)
    region_data = timed(region_detector.run, lineart, preprocessed, profile)
    np.save(folder / "labels.npy", region_data["_label_map"])  # for region tools
    timed(mask_generator.run, region_data, _p(folder, "region_mask"))
    timed(svg_generator.run, region_data, _p(folder, "outline"))
    timed(color_sampler.sample, region_data)
    _pb_delta, _pb_cat = _palette_budget(folder, profile)
    timed(palette_grouper.group, region_data, label_budget=_pb_delta, label_category=_pb_cat)
    # Phase 7 — optional semantic layer (helper only; never breaks processing).
    semantic_info: dict = {"mode": "rules", "hierarchy": None}
    try:
        semantic_info = timed(
            region_hierarchy.apply_all,
            region_data["regions"], region_data["width"], region_data["height"],
            SEMANTIC_MODE,
        )
    except Exception:  # noqa: BLE001 - semantics are best-effort
        semantic_info = {"mode": "rules", "hierarchy": None}
    time.sleep(STEP_DELAY_SECONDS)

    # Step 5 — Placing numbers (labels + JSON + previews: original, texture, flat)
    set_step(job_id, 4, 80)
    timed(label_optimizer.run, region_data)
    timed(json_generator.write_regions, region_data, _p(folder, "regions"))
    timed(json_generator.write_palette, region_data, _p(folder, "palette"))
    timed(
        preview_renderer.run,
        _p(folder, "region_mask"), _p(folder, "lineart"), region_data, _p(folder, "preview"),
    )
    timed(texture_preview_renderer.render, region_data, _p(folder, "texture_fill_preview"))
    timed(game_preview_renderer.render, region_data, _p(folder, "game_preview"))
    time.sleep(STEP_DELAY_SECONDS)

    # Step 6 — Preparing export (quality report + game config + zip)
    set_step(job_id, 5, 94)
    report = quality_analyzer.build_report(
        region_data, repair_metrics, noise_metrics, profile_name
    )
    quality_analyzer.write_report(report, _p(folder, "quality_report"))
    stats = {
        "regions_count": len(region_data["regions"]),
        "color_count": len(region_data.get("_palette_v2", [])),
        "small_regions_removed": region_data.get("small_regions_removed", 0),
        "tiny_regions_merged": region_data.get("tiny_regions_merged", 0),
        "gaps_repaired": repair_metrics.get("gaps_repaired", 0),
        "dust_removed": noise_metrics.get("dust_removed", 0),
        "svg_path_count": region_data.get("_svg_path_count", 0),
        "quality_score": report["score"],
        "difficulty": profile_name,
        "ai_source": ai_plan.get("source", "local"),  # ai | fallback | local
        "processing_time_seconds": round(compute_seconds, 3),
    }
    json_generator.write_game_config(
        job_id, region_data, _p(folder, "game_config"), stats,
        semantic={"mode": semantic_info.get("mode", "rules"),
                  "hierarchy": semantic_info.get("hierarchy")},
    )
    zip_exporter.run(folder, _p(folder, "result_zip"))
    # Quality Fix 7 — semantic region analysis (additive; reads region_data only,
    # never modifies regions/palette/numbers/SVG/export).
    try:
        semantic_region_analyzer.analyze(
            region_data, _p(folder, "semantic_region_report"),
            _p(folder, "semantic_overlay"),
        )
    except Exception:  # noqa: BLE001 - analysis is best-effort, never blocks output
        pass
    # Quality Fix 8 — material behavior layer (reuses the QF7 report + labels.npy).
    try:
        material_behavior_engine.analyze(
            _p(folder, "semantic_region_report"), folder / "labels.npy",
            _p(folder, "material_behavior_report"), _p(folder, "material_behavior_overlay"),
        )
    except Exception:  # noqa: BLE001
        pass
    # Quality Fix 9 — processing policy layer (reuses the QF7 + QF8 reports).
    try:
        processing_policy_engine.analyze(
            _p(folder, "semantic_region_report"), _p(folder, "material_behavior_report"),
            folder / "labels.npy", _p(folder, "processing_policy_report"),
            _p(folder, "processing_policy_overlay"),
        )
    except Exception:  # noqa: BLE001
        pass
    _apply_artistic_decisions(folder, profile)  # QF13 — decision brain (reads reports only)
    _apply_reveal(folder, region_data)           # Product Core — original-pixel reveal mode
    time.sleep(STEP_DELAY_SECONDS)


def _apply_reveal(folder: Path, region_data: dict) -> None:
    """Product Core — original-pixel reveal mode. Renders the reveal master + the 4
    preview states + validation from the (immutable) original and the region masks,
    then advertises reveal as the primary mode in game_config. Purely additive: the
    flat-fill (game_preview) and palette/region/number data are untouched. Best-effort
    — never blocks export."""
    try:
        out = {name: _p(folder, key) for name, key in (
            ("master", "reveal_master"), ("blank", "reveal_blank"),
            ("highlight", "reveal_highlight"), ("partial", "reveal_partial"),
            ("completed", "reveal_completed"), ("validation", "reveal_validation"),
        )}
        manifest = original_pixel_reveal_renderer.render_all(
            region_data, _p(folder, "original"), out
        )
        gc_path = _p(folder, "game_config")
        if gc_path.exists():
            gc = json.loads(gc_path.read_text(encoding="utf-8"))
            gc["default_mode"] = "reveal"                    # reveal is the main mode
            gc["modes"] = {"primary": "reveal", "available": ["reveal", "palette", "texture"]}
            gc["reveal"] = manifest
            gc.setdefault("assets", {}).update({
                "reveal_master": manifest["master_asset"],
                "reveal_blank": manifest["previews"]["blank"],
                "reveal_partial": manifest["previews"]["partial"],
                "reveal_completed": manifest["previews"]["completed"],
            })
            gc_path.write_text(json.dumps(gc, indent=2), encoding="utf-8")
    except Exception:  # noqa: BLE001 - reveal is a best-effort additive layer
        pass


def _render_labels(folder: Path) -> Path:
    """The label map the BORDER/render chain draws from: the fragmentation-reduced
    `labels_render.npy` when present, else the original `labels.npy`. The original is
    never modified, so masks / palette / numbers / reveal always use it."""
    cleaned = _p(folder, "labels_render")
    return cleaned if cleaned.exists() else (folder / "labels.npy")


def _apply_fragment_cleanup(job_id: str) -> None:
    """Line-Art Fragmentation Reduction — dissolve non-primary compact contour fragments
    into their dominant neighbour, writing a cleaned label map (`labels_render.npy`) the
    border chain renders from. Preserves the union (reveal==original, closed masks), every
    numbered region (palette/numbers), and all facial/flower/leaf/fur/decorative labels.
    `labels.npy` itself is untouched. Runs before QF14. Best-effort; never blocks output."""
    folder = job_dir(job_id)
    art = _p(folder, "artistic_decision_report")
    if not ((folder / "labels.npy").exists() and art.exists()):
        return
    try:
        fragment_cleanup_engine.clean(
            folder / "labels.npy", art,
            _p(folder, "labels_render"), _p(folder, "fragment_cleanup_report"),
        )
    except Exception:  # noqa: BLE001 - cleanup is best-effort, never blocks output
        pass


def _apply_contour_character(job_id: str) -> None:
    """Quality Fix 14 — give every contour its own artistic style and re-emit the
    vector outline with adaptive Bézier/polyline geometry (smooth silhouette, organic
    fur, crisp eyes, tapered leaf tips). Reads QF13 roles + labels; rewrites only the
    contour geometry in outline.svg / regions.json / game_config.json. No labels,
    palette, numbers, colours, line art, or export touched. Runs LAST so nothing
    reverts it. Idempotent (regenerates from labels each time). Draws from the
    fragmentation-reduced label map when present."""
    folder = job_dir(job_id)
    art = _p(folder, "artistic_decision_report")
    sem = _p(folder, "semantic_region_report")
    if not (art.exists() and sem.exists() and (folder / "labels.npy").exists()
            and _p(folder, "game_config").exists()):
        return
    try:
        contour_character_engine.run(
            _render_labels(folder), art, sem, _p(folder, "regions"),
            _p(folder, "game_config"), _p(folder, "outline"),
            _p(folder, "contour_character_report"), _p(folder, "contour_character_overlay"),
        )
    except Exception:  # noqa: BLE001 - contour styling is best-effort, never blocks output
        pass


def _apply_artistic_border(job_id: str) -> None:
    """Artistic Border Intelligence — score every existing border segment and decide
    its artistic action (KEEP/SIMPLIFY/EMPHASIZE/REMOVE/THICKEN/THIN/SOFTEN) from the
    QF14 contours + QF13/QF8/QF7 reports. Writes only `artistic_border_report.json`.
    Analysis ONLY — no rendering, no border/geometry/palette/number/export change.
    Runs after QF14 so the contour segments exist. Best-effort; never blocks output."""
    folder = job_dir(job_id)
    contour = _p(folder, "contour_character_report")
    art = _p(folder, "artistic_decision_report")
    if not (contour.exists() and art.exists()):
        return
    try:
        artistic_border_engine.analyze(
            contour, art, _p(folder, "material_behavior_report"),
            _p(folder, "semantic_region_report"), _p(folder, "artistic_border_report"),
        )
    except Exception:  # noqa: BLE001 - intelligence layer is best-effort, never blocks output
        pass


def _apply_artistic_border_render(job_id: str) -> None:
    """Artistic Border Render Pass — execute the decided border actions on the SVG/vector
    layer only (outline.svg + region path strings). Reads artistic_border_report +
    contour_character_report; gated on border quality (rejects if worse, keeping the QF14
    baseline). Touches no raster line art, labels, masks, palette, numbers, or reveal —
    so leaks and reveal==original are preserved. Runs after the intelligence pass.
    Best-effort; never blocks output."""
    folder = job_dir(job_id)
    abr = _p(folder, "artistic_border_report")
    cc = _p(folder, "contour_character_report")
    if not (abr.exists() and cc.exists() and (folder / "labels.npy").exists()
            and _p(folder, "game_config").exists()):
        return
    try:
        artistic_border_renderer.render(
            _render_labels(folder), abr, cc, _p(folder, "regions"),
            _p(folder, "game_config"), _p(folder, "outline"),
            _p(folder, "artistic_border_render_report"), _p(folder, "artistic_border_overlay"),
        )
    except Exception:  # noqa: BLE001 - render pass is best-effort, never blocks output
        pass


def _apply_review(job_id: str) -> None:
    """Admin Review System — build the read-only review layer (eight previews, ten
    statistics, four actions -> review.json) from the finished outputs. Reads only;
    writes review_* assets + review.json. Never modifies rendering/regions/palette/
    numbers/reveal. Best-effort; never blocks output."""
    folder = job_dir(job_id)
    if not (_p(folder, "game_config").exists() and (folder / "labels.npy").exists()):
        return
    try:
        review_engine.build_review(folder, job_id)
    except Exception:  # noqa: BLE001 - review is best-effort, never blocks output
        pass


def _set_publish(job_id: str, status: str) -> None:
    """Advance the Admin Publishing Flow lifecycle (metadata only — no algorithm change).
    Force-set so a regenerate cleanly resets an already-approved image. Best-effort."""
    try:
        publishing.set_status(job_dir(job_id), status, validate=False, actor="pipeline")
    except Exception:  # noqa: BLE001
        pass


def _apply_human_visual(job_id: str) -> None:
    """Human Visual Intelligence — score every region on ten perceptual dimensions into
    human_visual_report.json. Analysis only: reads the brain reports + review + experience
    patterns; writes one report; no borders/rendering/reveal/SVG/regions. Best-effort."""
    folder = job_dir(job_id)
    if not (_p(folder, "game_config").exists() and _p(folder, "semantic_region_report").exists()):
        return
    try:
        human_visual_intelligence.analyze(folder)
    except Exception:  # noqa: BLE001 - analysis pass is best-effort, never blocks output
        pass


def _apply_human_scene(job_id: str) -> None:
    """Human Scene Understanding — whole-image reasoning into human_scene_report.json.
    Analysis only: reads the image-type / semantic / material / artistic / HVI / review
    reports; writes one report; no border/rendering/regions/reveal/SVG/palette/number
    change. Best-effort."""
    folder = job_dir(job_id)
    if not (_p(folder, "game_config").exists() and (folder / "human_visual_report.json").exists()):
        return
    try:
        human_scene_understanding.analyze(folder)
    except Exception:  # noqa: BLE001 - analysis pass is best-effort, never blocks output
        pass


def _apply_artistic_decisions(folder: Path, profile: dict | None = None) -> None:
    """Quality Fix 13 — fuse the QF7/QF8/QF9 (+QF11/QF12/QF3 when present) reports
    into the per-region artistic decision record future engines obey. Reads reports
    only; writes just its own two new artifacts — no regions/palette/SVG/numbers/
    export are touched. The `profile` supplies the gradient-preservation gates
    (expansion thresholds); medium reproduces the module defaults exactly."""
    sem = _p(folder, "semantic_region_report")
    pol = _p(folder, "processing_policy_report")
    if not (sem.exists() and pol.exists() and (folder / "labels.npy").exists()):
        return
    try:
        artistic_decision_engine.analyze(
            sem, _p(folder, "material_behavior_report"), pol, folder / "labels.npy",
            _p(folder, "artistic_decision_report"), _p(folder, "artistic_decision_overlay"),
            _p(folder, "flow_gradient_report"), _p(folder, "intelligent_split_report"),
            _p(folder, "border_quality_report"), profile=profile,
        )
    except Exception:  # noqa: BLE001 - decision layer is best-effort, never blocks output
        pass


def _apply_gradient_preservation(job_id: str, profile: dict | None = None) -> None:
    """Quality Fix 12 — split the regions the QF13 brain selected (via its
    authoritative split_plan) by INTELLIGENT Lab k-means clustering
    (stripe/shadow/highlight/base separated by colour), keep coherent sub-regions
    with flow-aligned boundaries, then rebuild everything consistently. Supersedes
    the QF10/QF11 luminance engines while reusing their helpers (now in
    core.gradient_common). Decision-gated: on flat content QF13 selects nothing and
    the output is unchanged (backward compatible)."""
    folder = job_dir(job_id)
    sem = _p(folder, "semantic_region_report")
    pol = _p(folder, "processing_policy_report")
    if not (sem.exists() and pol.exists() and (folder / "labels.npy").exists()):
        return
    try:
        new_labels, info = intelligent_split_engine.run(
            sem, pol, folder / "labels.npy", _p(folder, "preprocessed"),
            _p(folder, "intelligent_split_overlay"), _p(folder, "lineart"),
            _p(folder, "material_behavior_report"),
            artistic_report_path=_p(folder, "artistic_decision_report"),  # QF13 authority
            material_kcap=_profile_material_kcap(profile or {}),
            min_luma=(profile or {}).get("expansion_min_luma"),
            min_cluster=(profile or {}).get("expansion_min_cluster"),
        )
        (folder / ARTIFACTS["intelligent_split_report"]).write_text(
            json.dumps(info, indent=2), encoding="utf-8"
        )
        if info.get("changed"):
            np.save(folder / "labels.npy", new_labels)
            rebuild_from_labels(job_id)  # regenerate regions/palette/numbers/SVG/previews
    except Exception:  # noqa: BLE001 - fidelity pass is best-effort, never blocks output
        pass


def run(job_id: str, profile_name: str = DEFAULT_PROFILE) -> None:
    """Initial processing of an upload with the chosen output profile (default
    medium, AI helper if enabled). The profile drives every tunable end to end."""
    if profile_name not in OUTPUT_PROFILES:
        profile_name = DEFAULT_PROFILE
    profile = OUTPUT_PROFILES[profile_name]
    try:
        _set_publish(job_id, "processing")             # Admin Publishing Flow: draft -> processing
        _process(job_id, profile, profile_name, "original", use_ai=True)
        _apply_gradient_preservation(job_id, profile)  # QF12 intelligent-split fidelity pass
        _apply_fragment_cleanup(job_id)                # Fragmentation reduction (cleaned label map)
        _apply_contour_character(job_id)               # QF14 adaptive contour styling (runs last)
        _apply_human_visual(job_id)                    # Human Visual Intelligence (analysis only) — before borders
        _apply_artistic_border(job_id)                 # Artistic Border Intelligence (analysis only)
        _apply_artistic_border_render(job_id)          # Artistic Border Render Pass (SVG layer only, HVI-guided, gated)
        finish_job(job_id)
        _apply_review(job_id)                          # Admin Review System (read-only assets + review.json)
        _apply_human_scene(job_id)                     # Human Scene Understanding (analysis only)
        _set_publish(job_id, "review")                 # Admin Publishing Flow: processing -> review
    except Exception as exc:  # noqa: BLE001
        fail_job(job_id, f"Processing failed: {exc}")


def reprocess(job_id: str, difficulty: str) -> None:
    """Re-run cleanup at a given difficulty. Backs up first; resets fills since
    region ids change."""
    if difficulty not in DIFFICULTY_PROFILES:
        difficulty = DEFAULT_DIFFICULTY
    try:
        make_backup(job_id)  # always back up before cleanup
        _set_publish(job_id, "processing")             # Admin Publishing Flow: -> processing
        # Reprocessing forces the LOCAL engine (deterministic, no AI dependency).
        _process(job_id, DIFFICULTY_PROFILES[difficulty], difficulty, "preprocessed", use_ai=False)
        write_progress(job_id, [])  # regions changed -> clear coloring
        finish_job(job_id)
        _apply_review(job_id)                          # Admin Review System refresh (read-only)
        _apply_human_visual(job_id)                    # Human Visual Intelligence (analysis only)
        _apply_human_scene(job_id)                     # Human Scene Understanding (analysis only)
        _set_publish(job_id, "review")                 # Admin Publishing Flow: processing -> review
    except Exception as exc:  # noqa: BLE001
        fail_job(job_id, f"Reprocessing failed: {exc}")


def preview_lineart(job_id: str, overrides: dict) -> dict:
    """Render a candidate line art (lineart_preview.png) WITHOUT touching the
    job's real outputs or regions. Returns its quality score."""
    folder = job_dir(job_id)
    settings = {**read_lineart_settings(job_id), **{k: v for k, v in overrides.items() if v is not None}}
    advanced_lineart_generator.run(
        _p(folder, "preprocessed"), _p(folder, "lineart_preview"), settings
    )
    return lineart_quality_score.score(_p(folder, "lineart_preview"))


def regenerate_lineart(job_id: str, overrides: dict, difficulty: str) -> None:
    """Persist new line-art settings, regenerate lineart.png, then re-run the
    full local region pipeline (gap close → regions → SVG/JSON → preview).
    Backs up first so it can be undone; local engine only."""
    if difficulty not in DIFFICULTY_PROFILES:
        difficulty = DEFAULT_DIFFICULTY
    folder = job_dir(job_id)
    try:
        make_backup(job_id)  # always back up before regeneration
        settings = write_lineart_settings(job_id, overrides)
        advanced_lineart_generator.run(
            _p(folder, "preprocessed"), _p(folder, "lineart"), settings
        )
        # Re-run region detection onward from the NEW line art (local, no AI).
        _process(
            job_id, DIFFICULTY_PROFILES[difficulty], difficulty, "preprocessed",
            use_ai=False, skip_lineart=True,
        )
        write_progress(job_id, [])  # regions changed -> clear coloring
        finish_job(job_id)
    except Exception as exc:  # noqa: BLE001
        fail_job(job_id, f"Line-art regeneration failed: {exc}")


def _job_difficulty(job_id: str) -> str:
    path = job_dir(job_id) / "game_config.json"
    try:
        return json.loads(path.read_text(encoding="utf-8")).get("stats", {}).get(
            "difficulty", DEFAULT_DIFFICULTY
        )
    except (ValueError, OSError):
        return DEFAULT_DIFFICULTY


def _emit_from_region_data(job_id: str, folder: Path, region_data: dict, profile_name: str) -> None:
    """Regenerate every region-derived artifact from an (edited) region_data.
    Shared by the region tools. Lines/line art are untouched here."""
    profile = region_data.get("_profile")
    mask_generator.run(region_data, _p(folder, "region_mask"))
    svg_generator.run(region_data, _p(folder, "outline"))
    color_sampler.sample(region_data)
    _pb_delta, _pb_cat = _palette_budget(folder, profile)
    palette_grouper.group(region_data, label_budget=_pb_delta, label_category=_pb_cat)
    semantic_info = {"mode": "rules", "hierarchy": None}
    try:
        semantic_info = region_hierarchy.apply_all(
            region_data["regions"], region_data["width"], region_data["height"], SEMANTIC_MODE
        )
    except Exception:  # noqa: BLE001
        pass
    label_optimizer.run(region_data)
    json_generator.write_regions(region_data, _p(folder, "regions"))
    json_generator.write_palette(region_data, _p(folder, "palette"))
    preview_renderer.run(
        _p(folder, "region_mask"), _p(folder, "lineart"), region_data, _p(folder, "preview")
    )
    texture_preview_renderer.render(region_data, _p(folder, "texture_fill_preview"))
    game_preview_renderer.render(region_data, _p(folder, "game_preview"))
    report = quality_analyzer.build_report(region_data, {}, {}, profile_name)
    quality_analyzer.write_report(report, _p(folder, "quality_report"))
    stats = {
        "regions_count": len(region_data["regions"]),
        "color_count": len(region_data.get("_palette_v2", [])),
        "small_regions_removed": 0, "tiny_regions_merged": 0,
        "gaps_repaired": 0, "dust_removed": 0,
        "svg_path_count": region_data.get("_svg_path_count", 0),
        "quality_score": report["score"], "difficulty": profile_name,
        "ai_source": "local", "processing_time_seconds": 0.0, "edited": True,
    }
    json_generator.write_game_config(
        job_id, region_data, _p(folder, "game_config"), stats,
        semantic={"mode": semantic_info.get("mode", "rules"),
                  "hierarchy": semantic_info.get("hierarchy")},
    )
    zip_exporter.run(folder, _p(folder, "result_zip"))
    try:  # QF7 semantic analysis (additive) — keep region-edit rebuilds in sync
        semantic_region_analyzer.analyze(
            region_data, _p(folder, "semantic_region_report"),
            _p(folder, "semantic_overlay"),
        )
        material_behavior_engine.analyze(  # QF8 reuses the QF7 report
            _p(folder, "semantic_region_report"), folder / "labels.npy",
            _p(folder, "material_behavior_report"), _p(folder, "material_behavior_overlay"),
        )
        processing_policy_engine.analyze(  # QF9 reuses QF7 + QF8 reports
            _p(folder, "semantic_region_report"), _p(folder, "material_behavior_report"),
            folder / "labels.npy", _p(folder, "processing_policy_report"),
            _p(folder, "processing_policy_overlay"),
        )
        _apply_artistic_decisions(folder, profile)  # QF13 refreshes decisions post-edit
    except Exception:  # noqa: BLE001
        pass
    _apply_reveal(folder, region_data)  # Product Core — refresh reveal mode after edits


def rebuild_from_labels(job_id: str) -> None:
    """Rebuild all region artifacts from the (edited) labels.npy. Used after a
    manual merge / split / delete. Line art stays the source of truth."""
    folder = job_dir(job_id)
    labels = np.load(folder / "labels.npy")
    color = cv2.imread(str(_p(folder, "preprocessed")), cv2.IMREAD_COLOR)
    profile_name = _job_difficulty(job_id)
    profile = DIFFICULTY_PROFILES.get(profile_name, DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY])
    region_data = region_detector.build_from_labels(labels, color, profile)
    _emit_from_region_data(job_id, folder, region_data, profile_name)
    np.save(folder / "labels.npy", region_data["_label_map"])
    _apply_fragment_cleanup(job_id)   # Fragmentation reduction (cleaned label map)
    _apply_contour_character(job_id)  # QF14 re-styles contours after any region edit
    _apply_artistic_border(job_id)    # Artistic Border Intelligence refresh (analysis only)
    _apply_artistic_border_render(job_id)  # Artistic Border Render Pass (SVG layer only, gated)
    write_progress(job_id, [])
    finish_job(job_id)


def recalculate_regions(job_id: str) -> None:
    """Re-detect regions from the CURRENT (edited) line art, then rebuild
    everything. Local engine only."""
    profile_name = _job_difficulty(job_id)
    profile = DIFFICULTY_PROFILES.get(profile_name, DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY])
    try:
        _process(job_id, profile, profile_name, "preprocessed", use_ai=False, skip_lineart=True)
        write_progress(job_id, [])
        finish_job(job_id)
    except Exception as exc:  # noqa: BLE001
        fail_job(job_id, f"Recalculate failed: {exc}")


# Sanity check that the orchestrator stays in sync with the declared steps.
assert len(PROCESSING_STEPS) == 6
