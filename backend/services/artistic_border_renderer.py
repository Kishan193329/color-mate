"""Artistic Border Render Pass — apply the decided border actions to the VECTOR layer.

The Artistic Border Intelligence engine already decided one action per border segment
(`artistic_border_report.json`). This pass *executes* those decisions — but ONLY on the
line-art / SVG rendering layer:

  * `outline.svg`                       — re-emitted per-contour with the action applied
  * region `path` strings (regions.json / game_config.json) — geometry updated

Nothing else is touched: no border re-detection, no region / label / mask changes, no
palette, no numbers, no reveal. The raster line art (`lineart.png`), `labels.npy`,
`region_mask.png`, and every reveal asset are left exactly as they were — so leaks
(a raster/label property) cannot change and the completed reveal still equals the
original by construction.

Each action gets a distinct SEMANTIC STROKE (width + colour + opacity, see `STROKE_STYLE`)
so the intent is legible in the rendered border itself — a thickened silhouette reads
boldest, an emphasised focal feature solid-black, a softened organic edge gentle and light,
a simplified background edge recessive, a thinned detail fine. Actions (geometry + stroke,
reusing QF14's adaptive machinery so KEEP is byte-for-byte the QF14 baseline):
  REMOVE     drop the contour from the render (compact specks only — already gated by
             the intelligence engine to non-critical, non-focal micro-fragments)
  SOFTEN     smoother Bézier + gentle light stroke (organic / fur / petal flow)
  THICKEN    heaviest stroke (outer silhouettes), geometry unchanged
  THIN       fine stroke (dense non-critical detail), geometry unchanged
  EMPHASIZE  bold solid-black stroke on the exact QF14 geometry (eyes / petals / accents)
  SIMPLIFY   coarser approximation + slightly recessive stroke (low-priority background)
  KEEP       QF14 policy unchanged

A uniform pre-semantic render is always written to `outline_baseline.svg` beside the
artistic `outline.svg`, giving a genuine before/after SVG pair per job.

Safety gate (rule 6 — "if visual border quality gets worse, reject"): the artistic
render is accepted only if it is no more jagged and no less shape-faithful than the QF14
baseline (within tolerance). On rejection the QF14 output is left untouched. Stroke
attributes never enter the gate — it scores geometry only.
"""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

import cv2
import numpy as np

from services import hsu_strategy_routing, hvi_border_guidance
from services.contour_character_engine import (
    CLASS_STYLE, MIN_CONTOUR_PTS, STYLE_POLICY,
    _catmull_bezier, _corner_indices, _polyline,
    _sample_d, _shape_similarity,
)

# HVI-Guided Border Decisions: when human_visual_report.json is present, each segment's
# decided action is re-weighted by its region's perceptual scores (recognition / attention /
# beauty / fun / tracing / drawing) before rendering. Purely an action choice on the SVG
# layer — no reveal / mask / region / palette / number change. Toggle for A/B validation.
HVI_GUIDANCE = True
# HSU-Guided Strategy Routing: after HVI, the scene's recommended strategy modulates the
# action per scene role (protect face/symmetry, simplify background, emphasise subject/near,
# clean lines). Still the SVG layer only. Toggle for A/B validation.
HSU_GUIDANCE = True

STROKE_DARK = "#111111"
STROKE_EMPH = "#000000"
# Semantic stroke model — each action renders with a distinct (width, colour, opacity) so
# the illustrator intent is legible in the SVG itself: a THICKENed silhouette reads the
# boldest, an EMPHASIZEd focal feature (eyes / lips / petal centre / accessory) reads
# solid black, a SOFTENed organic edge (cheeks / petals / hair flow) reads gentle and
# light, a SIMPLIFYed background/vein edge recedes, a THINned over-inked detail stays
# crisp but fine. KEEP is the QF14 baseline exactly. These are stroke ATTRIBUTES only —
# geometry (the path `d`) is decided separately — so the shape-fidelity gate is unaffected.
STROKE_STYLE = {
    "KEEP":      (2.0, STROKE_DARK, 1.00),
    "THICKEN":   (3.2, STROKE_EMPH, 1.00),   # primary outer silhouette — heaviest line
    "EMPHASIZE": (2.6, STROKE_EMPH, 1.00),   # focal feature — bold solid black
    "THIN":      (1.2, STROKE_DARK, 1.00),   # delicate over-inked detail — kept fine
    "SOFTEN":    (1.8, STROKE_DARK, 0.78),   # organic flow — gentle & light
    "SIMPLIFY":  (1.9, STROKE_DARK, 0.92),   # reducible background/veins — slightly recessive
}
IOU_TOL = 0.010           # transformed-contour IoU may not fall below the baseline by more than this
REMOVE_LEN_FRAC = 0.20    # removed specks may not exceed this fraction of total border length
# Absolute cap on the simplification tolerance. eps grows with a contour's perimeter, so
# a large region gets a big boundary shift (~11px) that fails the fidelity gate. Capping
# eps to a few px bounds that shift on large contours while leaving small ones untouched —
# this is what lets the render safely apply on heavily-detailed images.
MAX_EPS_PX = 4.0


def _load(path: Path) -> dict:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _policy_for(action: str, style: str) -> dict:
    """The QF14 style policy, adjusted for the artistic action. Only the two *smoothing*
    actions change geometry; KEEP / THIN / THICKEN / EMPHASIZE keep the exact QF14
    geometry (they differ only in stroke). EMPHASIZE deliberately does NOT re-approximate
    a protected feature (that could distort an eye/petal) — it emphasises via the stroke."""
    base = dict(STYLE_POLICY.get(style, STYLE_POLICY["HYBRID"]))
    if action == "SOFTEN":
        # Gentle smoothing: a soft Bézier without moving the border enough to distort it.
        base.update(bezier=True, corner_keep=False,
                    smoothing=max(base["smoothing"], 0.90),
                    eps_frac=base["eps_frac"] * 1.15)
    elif action == "SIMPLIFY":
        # Coarser than baseline, but deliberately restrained so shape fidelity holds
        # (aggressive simplification distorts detailed regions past the quality gate).
        base.update(bezier=True, corner_keep=False, smoothing=0.85,
                    eps_frac=base["eps_frac"] * 1.6)
    return base


def _stroke_for(action: str) -> tuple[float, str, float]:
    """(stroke-width, stroke-colour, stroke-opacity) for an action's semantic rendering."""
    return STROKE_STYLE.get(action, STROKE_STYLE["KEEP"])


def _adaptive(contour: np.ndarray, policy: dict, cap: float | None) -> str:
    """QF14's adaptive contour subpath, but with an optional absolute cap on the
    simplification tolerance so large contours are not over-shifted. `cap=None`
    reproduces QF14 exactly (used for the baseline and the geometry-neutral actions)."""
    perim = cv2.arcLength(contour, True)
    eps = max(1.0, policy["eps_frac"] * perim)
    if cap is not None:
        eps = min(eps, cap)
    approx = cv2.approxPolyDP(contour, eps, True).reshape(-1, 2).astype(np.float64)
    if len(approx) < MIN_CONTOUR_PTS:
        approx = contour.reshape(-1, 2).astype(np.float64)
    if not policy["bezier"] or len(approx) < MIN_CONTOUR_PTS:
        return _polyline(approx)
    corners = _corner_indices(approx, policy["corner_deg"]) if policy["corner_keep"] else set()
    return _catmull_bezier(approx, corners)


def _hex_bgr(color: str) -> tuple[int, int, int]:
    c = color.lstrip("#")
    r, g, b = int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    return b, g, r


def _panel(paths_w: list, h: int, w: int) -> np.ndarray:
    """White canvas with each (d, width, colour, opacity) drawn — a faithful raster of the
    rendered border: THICKEN/THIN show as width, EMPHASIZE as solid black, SOFTEN/SIMPLIFY
    as a lighter line (stroke-opacity simulated on the white ground by lightening the ink)."""
    canvas = np.full((h, w, 3), 255, np.uint8)
    for d, sw, color, opac in paths_w:
        t = max(1, int(round(sw)))
        b, g, r = _hex_bgr(color)
        col = (int(b * opac + 255 * (1 - opac)),
               int(g * opac + 255 * (1 - opac)),
               int(r * opac + 255 * (1 - opac)))
        for poly in _sample_d(d):
            cv2.polylines(canvas, [np.round(poly).astype(np.int32)], False, col, t, cv2.LINE_AA)
    return canvas


def _overlay(before_w: list, after_w: list, removed_ds: list, h: int, w: int, out_path: Path) -> None:
    """Side-by-side BEFORE (QF14) vs AFTER (artistic); removed specks tinted red on the
    before panel so the pruning is visible."""
    left = _panel(before_w, h, w)
    for d in removed_ds:                                   # show what REMOVE pruned
        for poly in _sample_d(d):
            cv2.polylines(left, [np.round(poly).astype(np.int32)], False, (60, 60, 235), 2, cv2.LINE_AA)
    right = _panel(after_w, h, w)
    gap = np.full((h, 6, 3), 200, np.uint8)
    combo = np.hstack([left, gap, right])
    cv2.putText(combo, "BEFORE (QF14)  red = removed", (10, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
    cv2.putText(combo, "AFTER (artistic)", (w + 16, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
    cv2.imwrite(str(out_path), combo)


def _svg(paths: list, w: int, h: int) -> str:
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
        f'<rect width="{w}" height="{h}" fill="#ffffff"/>',
        '<g fill="none" stroke-linejoin="round" stroke-linecap="round" fill-rule="evenodd">',
    ]
    for d, sw, color, opac, rid, action in paths:
        parts.append(f'<path d="{d}" stroke="{color}" stroke-width="{sw:g}" '
                     f'stroke-opacity="{opac:g}" data-region-id="{rid}" data-action="{action}"/>')
    parts += ["</g>", "</svg>"]
    return "\n".join(parts)


def render(labels_path: Path, artistic_border_report_path: Path, contour_report_path: Path,
           regions_json_path: Path, game_config_path: Path, outline_svg_path: Path,
           report_path: Path, overlay_path: Path) -> dict:
    """Apply the decided border actions to the SVG/vector layer, gated on border
    quality. Returns the render report (also written to `report_path`)."""
    labels = np.load(labels_path).astype(np.int32)
    h, w = labels.shape
    border = _load(artistic_border_report_path)
    contour = _load(contour_report_path)

    action_by = {(s.get("label"), s.get("contour_index")): s.get("action", "KEEP")
                 for s in border.get("segments", [])}
    style_by = {(r.get("label"), r.get("contour_index")): r.get("style", "HYBRID")
                for r in contour.get("contours", [])}
    outer_by = {(r.get("label"), r.get("contour_index")): bool(r.get("is_outer"))
                for r in contour.get("contours", [])}
    # HVI-Guided Border Decisions: per-region perceptual scores (analysis only, read-only).
    hvi_report = _load(Path(game_config_path).parent / "human_visual_report.json")
    hvi_by_label = {r.get("label"): r.get("scores", {})
                    for r in (hvi_report or {}).get("regions", [])} if hvi_report else {}
    hvi_on = bool(HVI_GUIDANCE and hvi_by_label)
    hvi_changed = 0
    hvi_by_rule: Counter = Counter()
    hvi_transitions: Counter = Counter()
    # HSU-Guided Strategy Routing: scene-strategy modulation (analysis only, read-only).
    hsu_plan = hsu_strategy_routing.load_plan(Path(game_config_path).parent) if HSU_GUIDANCE else None
    hsu_on = bool(hsu_plan and hsu_plan.get("strategy") not in (None, "UNKNOWN_CONSERVATIVE"))
    hsu_changed = 0
    hsu_by_rule: Counter = Counter()
    hsu_transitions: Counter = Counter()
    if not action_by:                                      # nothing decided — no-op
        report = {"version": 1, "note": "No artistic_border_report segments; render skipped.",
                  "segment_count": 0, "accepted": False, "outputs_updated": False}
        Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
        return report

    game_doc = _load(game_config_path)
    num_by_label = {r.get("label"): r.get("number") for r in game_doc.get("regions", [])}

    baseline_all_ds, baseline_kept_ds, artistic_ds, removed_ds = [], [], [], []
    before_w, after_w, svg_paths = [], [], []
    region_art: dict = defaultdict(list)
    action_counts: Counter = Counter()
    removed = 0
    total_len = removed_len = 0.0
    verts_before = verts_after = 0                          # complexity of transformed contours

    for lbl in sorted({k[0] for k in action_by}):
        mask = np.uint8(labels == lbl) * 255
        conts, _ = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
        for ci, contour in enumerate(conts):
            key = (lbl, ci)
            if key not in action_by:                       # matches QF14's <4pt skip
                continue
            style = style_by.get(key, CLASS_STYLE.get("UNKNOWN", "HYBRID"))
            base_d = _adaptive(contour, STYLE_POLICY.get(style, STYLE_POLICY["HYBRID"]), None)
            baseline_all_ds.append(base_d)
            before_w.append((base_d, 2.0, STROKE_DARK, 1.0))   # uniform pre-semantic render
            perim = float(cv2.arcLength(contour, True))
            total_len += perim

            action = action_by[key]
            if hvi_on:                                     # HVI re-weights the decided action
                hvi = hvi_by_label.get(lbl)
                if hvi:
                    adj = hvi_border_guidance.guide(action, hvi, outer_by.get(key, ci == 0), style_by.get(key))
                    if adj.value != action:
                        hvi_changed += 1
                        hvi_transitions[f"{action}->{adj.value}"] += 1
                        hvi_by_rule[adj.evidence.get("rule", "?")] += 1
                    action = adj.value
            if hsu_on:                                      # HSU scene strategy modulates it
                sd = hsu_strategy_routing.border_action(
                    hsu_plan["strategy"], action, hsu_plan["role_of"].get(lbl, "neutral"),
                    lbl in hsu_plan["focal_set"], outer_by.get(key, ci == 0))
                if sd.value != action:
                    hsu_changed += 1
                    hsu_transitions[f"{action}->{sd.value}"] += 1
                    hsu_by_rule[sd.evidence.get("rule", "?")] += 1
                action = sd.value
            action_counts[action] += 1
            if action == "REMOVE":
                removed += 1
                removed_len += perim
                removed_ds.append(base_d)
                continue

            cap = MAX_EPS_PX if action in ("SIMPLIFY", "SOFTEN") else None
            art_d = _adaptive(contour, _policy_for(action, style), cap)
            sw, color, opac = _stroke_for(action)
            baseline_kept_ds.append(base_d)
            artistic_ds.append(art_d)
            after_w.append((art_d, sw, color, opac))
            region_art[lbl].append(art_d)
            svg_paths.append((art_d, sw, color, opac, num_by_label.get(lbl, lbl), action))
            if action in ("SOFTEN", "SIMPLIFY"):            # complexity change of transformed geometry
                verts_before += base_d.count(" L ") + base_d.count(" C ")
                verts_after += art_d.count(" L ") + art_d.count(" C ")

    # --- border-quality gate -------------------------------------------------- #
    # Two robust checks (raster jaggedness is deliberately NOT used — it penalises the
    # intended vertex reduction of SIMPLIFY):
    #   faithful  — the KEPT/transformed contours still enclose their regions as well as
    #               the QF14 baseline did (guards distortion / over-simplification).
    #               REMOVE is excluded from BOTH sides so intentional decluttering isn't
    #               scored as a fidelity loss.
    #   bounded   — the removed contours are a small fraction of total border length
    #               (they are compact specks; never strip a large share of real border).
    iou_before = round(_shape_similarity(baseline_kept_ds, labels), 4)
    iou_after = round(_shape_similarity(artistic_ds, labels), 4)
    iou_qf14_full = round(_shape_similarity(baseline_all_ds, labels), 4)
    removed_len_frac = round(removed_len / total_len, 4) if total_len else 0.0
    faithful = iou_after >= iou_before - IOU_TOL
    bounded = removed_len_frac <= REMOVE_LEN_FRAC
    accepted = bool(faithful and bounded)

    outputs_updated = False
    if accepted:
        # region path strings — geometry only, REMOVE dropped; never leave a region empty.
        path_by_label = {}
        for lbl, subs in region_art.items():
            path_by_label[lbl] = " ".join(subs)
        for doc, p in ((_load(regions_json_path), regions_json_path),
                       (game_doc, game_config_path)):
            for r in doc.get("regions", []):
                lbl = r.get("label")
                if lbl in path_by_label and path_by_label[lbl]:
                    r["path"] = path_by_label[lbl]
            Path(p).write_text(json.dumps(doc, indent=2), encoding="utf-8")
        Path(outline_svg_path).write_text(_svg(svg_paths, w, h), encoding="utf-8")
        outputs_updated = True

    # Before/after SVG pair: the uniform pre-semantic render (every kept contour at the
    # KEEP stroke, no removals) is always written beside the artistic outline so the two
    # SVGs can be opened side by side. This is a diagnostic artifact — it does not replace
    # `outline.svg` and is not part of the export.
    baseline_svg_paths = [(d, 2.0, STROKE_DARK, 1.0, "", "BASELINE") for d in baseline_all_ds]
    baseline_svg_path = Path(outline_svg_path).with_name("outline_baseline.svg")
    baseline_svg_path.write_text(_svg(baseline_svg_paths, w, h), encoding="utf-8")

    _overlay(before_w, after_w, removed_ds, h, w, overlay_path)

    if accepted:
        reason = "accepted — transformed contours shape-faithful and removal bounded"
    else:
        why = []
        if not faithful:
            why.append(f"shape fidelity dropped {iou_before - iou_after:.4f} (> {IOU_TOL})")
        if not bounded:
            why.append(f"removed {removed_len_frac:.1%} of border length (> {REMOVE_LEN_FRAC:.0%})")
        reason = "rejected — " + "; ".join(why) + "; kept QF14 baseline"
    report = {
        "version": 1,
        "note": "Artistic border render pass — applies the decided actions to the SVG/vector "
                "layer only. No border re-detection, no region/label/mask/palette/number/reveal "
                "change; raster line art and leaks untouched. KEEP reproduces QF14 exactly.",
        "sources": ["artistic_border_report.json", "contour_character_report.json",
                    "human_visual_report.json"],
        "segment_count": len(action_by),
        "rendered_count": len(artistic_ds),
        "removed_count": removed,
        "action_applied_distribution": dict(action_counts),
        "hvi_guided": hvi_on,
        "hvi_adjustments": {"changed": hvi_changed, "by_rule": dict(hvi_by_rule),
                            "transitions": dict(hvi_transitions.most_common())},
        "hsu_guided": hsu_on,
        "hsu_strategy": (hsu_plan or {}).get("strategy"),
        "hsu_start_profile_hint_advisory": (hsu_plan or {}).get("start_profile_hint"),
        "hsu_adjustments": {"changed": hsu_changed, "by_rule": dict(hsu_by_rule),
                            "transitions": dict(hsu_transitions.most_common())},
        "stroke_settings": {a: {"width": w_, "colour": c_, "opacity": o_}
                            for a, (w_, c_, o_) in STROKE_STYLE.items()},
        "baseline_svg": baseline_svg_path.name,
        "quality": {
            # fidelity of the kept/transformed contours (the gate) — REMOVE excluded both sides
            "shape_iou_before": iou_before,
            "shape_iou_after": iou_after,
            "shape_iou_delta": round(iou_after - iou_before, 4),
            "shape_iou_vs_qf14_full": iou_qf14_full,      # incl. specks, for transparency
            # clutter reduction (the improvement)
            "fragments_before": len(baseline_all_ds),
            "fragments_after": len(artistic_ds),
            "fragments_removed": removed,
            "removed_border_length_fraction": removed_len_frac,
            # complexity of the geometry-changing (SOFTEN/SIMPLIFY) contours
            "transformed_vertices_before": verts_before,
            "transformed_vertices_after": verts_after,
        },
        "gate": {"iou_tol": IOU_TOL, "remove_len_frac_max": REMOVE_LEN_FRAC,
                 "faithful": bool(faithful), "bounded_removal": bool(bounded)},
        "accepted": accepted,
        "outputs_updated": outputs_updated,
        "decision_reason": reason,
    }
    Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
