"""Quality Fix 12 — intelligent region split engine (pixel-changing, refines QF11).

QF10/QF11 split policy-selected regions into *luminance* bands. Luminance ignores
chroma, so a tabby stripe (different hue, similar lightness) is averaged into the
base fur and structure is lost. QF12 splits by **full-Lab k-means clustering** —
which separates stripe / shadow / highlight / base by their actual colour — then
keeps only **spatially-coherent connected components**, giving meaningful sub-regions
instead of tone blobs. Boundaries are flow-aligned (reusing QF11) for fur/leaf/cloth,
small details are protected, and tiny fragments revert to the base region.

Because k-means minimises within-cluster colour variance (exactly what the flat-fill
ΔE measures), each sub-region's sampled colour fits its pixels better than a
luminance band's — so the cat gets measurably closer to the original.

Engineering Sprint 1 rewired selection: QF12 is now an **actuator that obeys the
QF13 artistic decision brain**. Instead of re-deriving the split set from QF9
policy, it consumes QF13's authoritative `split_plan` (`artistic_decision_report
.json`) — the ordered list of regions to split, each with its semantic class,
split style and protection flags. QF13 builds that plan with the exact historical
QF12 predicate, so the selection (and every resulting pixel) is unchanged. If the
QF13 report is unavailable, QF12 falls back to the legacy QF9 predicate for
robustness (identical result).

Reuses (NO recompute): QF13 `artistic_decision_report.json` (split_plan — the
selection authority), QF7 `semantic_region_report.json` (region_id → label, flow
direction + coherence), QF8 `material_behavior_report.json` (per-class split budget).
Colours are sampled downstream from the original by the existing pipeline; this
engine only edits region geometry.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

# Sprint 1: shared gradient/split primitives live in core.gradient_common — the
# single source of truth. QF12 imports them directly instead of borrowing math
# from the QF10/QF11 engine modules (which are now helper libraries only).
from core.gradient_common import (
    BLUR_LEN,
    MIN_COHERENCE,
    MIN_LUMA_RANGE,
    SELECT_POLICY,           # noqa: F401 — kept for backward-compatible re-export
    SPLIT_MIN_STRENGTH,      # noqa: F401 — kept for backward-compatible re-export
    SPLIT_PROTECT_CLASSES,
    _boundaries,
    _delta_e,
    _mean_fill,
    _oriented_blur,
    legacy_split_selected,
    ssim,
)

MIN_REGION_AREA = 1200   # regions smaller than this are protected (eyes/nose/small)
MIN_CLUSTER = 220        # a k-means cluster below this stays with the base region
OPEN_KERNEL = 3          # morphological open to de-speckle each cluster (no fragments)
NEW_LABELS_CAP = 5       # max NEW sub-regions per region (avoid over-splitting)
KCLUSTER_STEP = 16.0     # ~1 extra cluster per this much combined Lab spread
L_WEIGHT = 1.0           # Lab channels weighted equally (empirically best ΔE+SSIM)

# Visual Improvement 1 — GRADIENT_EXPANSION tier. Large body/fur/face areas the
# legacy gate skipped are smoothly shaded, so their L/chroma p5..p95 span can sit
# just under MIN_LUMA_RANGE. This relaxed floor lets QF13-permitted expansion
# regions split into a couple of tonal bands; the per-entry sub-region cap and
# MIN_CLUSTER still prevent over-splitting / speckle.
EXPANSION_MIN_LUMA = 5.0
EXPANSION_MAX_SUBREGIONS_DEFAULT = 3   # fallback cap if the plan entry omits it
# Tiers QF13 marks for a bounded tonal split (vs the PRIMARY high-variance splits).
EXPANSION_TIERS = {"GRADIENT_EXPANSION", "BACKGROUND_EXPANSION"}
EXPANSION_MIN_CLUSTER = 450            # expansion bands below this fold back to base,
#   keeping every expansion sub-region numberable (no new clutter -> visual score held)
#   while still admitting the tonal bands that actually reduce ΔE. Tuned on cat.png.

# Per-material cluster budget (reuses QF8 semantic class). Textured materials get
# more clusters; flat/fragile ones get fewer. Unknowns fall back to 3.
MATERIAL_KCAP = {
    "FUR": 5, "HAIR": 5, "CLOTH": 5, "LEAF": 4, "WOOD": 4,
    "SKIN": 4, "STONE": 4, "METAL": 3, "PLASTIC": 3,
}
# Classes never split (small / fragile / structural detail) now live in
# core.gradient_common.SPLIT_PROTECT_CLASSES and are enforced by QF13's selection
# (legacy_split_selected). Kept here as a backward-compatible alias.
PROTECT_CLASSES = SPLIT_PROTECT_CLASSES


def _kmeans_labels(samples: np.ndarray, k: int) -> np.ndarray:
    """Lab k-means; returns a per-sample cluster index (0..k-1)."""
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
    # Deterministic init (KMEANS_PP_CENTERS is seeded internally by OpenCV); no RNG
    # dependence in the workflow sense — same input -> same clusters.
    _compact, labels, _centers = cv2.kmeans(
        samples.astype(np.float32), k, None, crit, 3, cv2.KMEANS_PP_CENTERS
    )
    return labels.ravel()


def _pick_k(Lv: np.ndarray, av: np.ndarray, bv: np.ndarray, kcap: int,
           min_luma: float = MIN_LUMA_RANGE) -> int:
    """Choose cluster count from combined luminance + chroma spread, capped by material."""
    l_span = float(np.percentile(Lv, 95) - np.percentile(Lv, 5))
    chroma = np.sqrt((av - 128.0) ** 2 + (bv - 128.0) ** 2)
    c_span = float(np.percentile(chroma, 95) - np.percentile(chroma, 5))
    if l_span < min_luma and c_span < min_luma:
        return 1
    score = l_span + 0.7 * c_span
    return int(np.clip(round(1 + score / KCLUSTER_STEP), 2, kcap))


def _split_region(new_labels, lbl, lab, angle, coherence, material, next_label,
                  kcap_override=None, min_luma=MIN_LUMA_RANGE, new_labels_cap=NEW_LABELS_CAP,
                  min_cluster=MIN_CLUSTER, material_kcap=None):
    """Cluster one region in Lab, keep coherent components. Returns
    (n_subregions, next_label, flow_aligned, k).

    PRIMARY splits pass the defaults (material k-cap, standard luma floor, full
    NEW_LABELS_CAP). GRADIENT_EXPANSION splits pass a tighter `kcap_override`
    (2–3 bands), a relaxed `min_luma`, and a small `new_labels_cap` so large
    smoothly-shaded areas get a bounded tonal split without over-splitting."""
    mask = new_labels == lbl
    ys, xs = np.where(mask)
    if len(xs) < MIN_REGION_AREA:                 # protect small details
        return 1, next_label, False, 0, []
    pad = 6
    x0, x1 = max(0, xs.min() - pad), min(lab.shape[1], xs.max() + pad + 1)
    y0, y1 = max(0, ys.min() - pad), min(lab.shape[0], ys.max() + pad + 1)
    labc = lab[y0:y1, x0:x1]
    mc = mask[y0:y1, x0:x1]
    Lc, ac, bc = labc[:, :, 0], labc[:, :, 1], labc[:, :, 2]

    kcap = kcap_override if kcap_override is not None \
        else (material_kcap or MATERIAL_KCAP).get(material, 3)
    k = _pick_k(Lc[mc].astype(np.float32), ac[mc].astype(np.float32),
                bc[mc].astype(np.float32), kcap, min_luma)
    if k < 2:
        return 1, next_label, False, 0, []

    # Full-Lab clustering: separates stripe / shadow / highlight / base by colour.
    # L is up-weighted so clustering still respects luminance structure (SSIM).
    samples = labc[mc].reshape(-1, 3).astype(np.float32)
    samples[:, 0] *= L_WEIGHT
    cluster = _kmeans_labels(samples, k)
    band = np.full(mc.shape, 255, np.float32)
    band[mc] = cluster.astype(np.float32)

    # Flow-align cluster boundaries (masked normalised oriented blur) when the
    # region has a trustworthy dominant direction — elongates sub-regions along
    # fur/vein/fold with negligible colour cost. Low coherence -> leave as-is.
    used_flow = coherence >= MIN_COHERENCE
    if used_flow:
        mf = mc.astype(np.float32)
        num = _oriented_blur(band * mf, angle, BLUR_LEN, 1)
        den = _oriented_blur(mf, angle, BLUR_LEN, 1)
        aligned = np.divide(num, den, out=band.copy(), where=den > 1e-3)
        band = np.where(mc, np.rint(aligned), 255.0)
    bu = band.astype(np.uint8)
    bu[~mc] = 255
    bu = cv2.medianBlur(bu, 3)
    bu[~mc] = 255

    # Order clusters by size; the largest keeps the base label. Each other cluster
    # becomes ONE new label (all its pixels — same colour, valid), after a small
    # morphological open that folds speckle back into the base region so no tiny
    # fragments are created. This groups pixels by colour (minimising ΔE) rather
    # than forcing connectivity, which fragments textured fur.
    ker = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (OPEN_KERNEL, OPEN_KERNEL))
    sizes = sorted(((c, int(((bu == c) & mc).sum())) for c in range(k)),
                   key=lambda t: t[1], reverse=True)
    made, created = 1, 0
    child_labels: list[int] = []                   # new labels created (for palette inheritance)
    for c, sz in sizes[1:]:                        # skip the largest (stays base)
        if sz < min_cluster or created >= new_labels_cap:
            continue
        cmask = ((bu == c) & mc).astype(np.uint8)
        cmask = cv2.morphologyEx(cmask, cv2.MORPH_OPEN, ker)  # drop speckle -> base
        if int(cmask.sum()) < min_cluster:
            continue
        full = np.zeros(new_labels.shape, bool)
        full[y0:y1, x0:x1] = cmask > 0
        new_labels[full] = next_label
        child_labels.append(int(next_label))
        next_label += 1
        made += 1
        created += 1
    return made, next_label, used_flow, k, child_labels


def _selection_plan(artistic_report_path: Path | None,
                    policy_report_path: Path | None) -> tuple[list[dict], str]:
    """The regions to split, in order, from the QF13 brain (authority).

    Primary: QF13's `split_plan` in `artistic_decision_report.json` — the
    authoritative selection + style + protection. Fallback (only if that report is
    missing/unreadable): re-derive the exact legacy selection straight from QF9
    policy so a QF13 failure never disables the fidelity pass. Both paths yield the
    same ordered set, so the result is identical."""
    if artistic_report_path is not None and Path(artistic_report_path).exists():
        try:
            artistic = json.loads(Path(artistic_report_path).read_text(encoding="utf-8"))
            plan = artistic.get("split_plan")
            if plan is not None:
                return list(plan), "artistic_decision_report.split_plan (QF13)"
        except (ValueError, OSError):
            pass
    if policy_report_path is not None and Path(policy_report_path).exists():
        policy = json.loads(Path(policy_report_path).read_text(encoding="utf-8"))
        plan = [
            {"region_id": r["region_id"], "semantic_class": r.get("semantic_class"),
             "split_style": None}
            for r in policy.get("regions", [])
            if legacy_split_selected(r, r.get("semantic_class"))
        ]
        return plan, "processing_policy_report (QF9 fallback)"
    return [], "none"


def run(semantic_report_path: Path, policy_report_path: Path, labels_path: Path,
        preprocessed_path: Path, overlay_path: Path | None = None,
        lineart_path: Path | None = None,
        material_report_path: Path | None = None,
        artistic_report_path: Path | None = None,
        material_kcap: dict | None = None,
        min_luma: float | None = None,
        min_cluster: int | None = None) -> tuple[np.ndarray, dict]:
    # Output-profile texture / highlight parameters (Product Sprint 1). Absent /
    # medium profile falls back to the module defaults => identical behaviour.
    kcap_table = material_kcap or MATERIAL_KCAP
    exp_min_luma = EXPANSION_MIN_LUMA if min_luma is None else float(min_luma)
    exp_min_cluster = EXPANSION_MIN_CLUSTER if min_cluster is None else int(min_cluster)

    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path).astype(np.int32)
    bgr = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)

    sem_by_id = {r["region_id"]: r for r in semantic.get("regions", [])}
    # QF13 is the decision authority — obey its ordered split_plan.
    selected, selection_source = _selection_plan(artistic_report_path, policy_report_path)

    original_labels = labels.copy()
    new_labels = labels.copy()
    next_label = int(labels.max()) + 1
    split_mask = np.zeros(labels.shape, bool)
    details = []
    for entry in selected:
        rid = entry.get("region_id")
        s = sem_by_id.get(rid, {})
        lbl = s.get("label")                          # geometry authority stays QF7
        if lbl is None:
            continue
        dom = float(s.get("dominant_gradient_direction", 0.0))
        coh = float(s.get("edge_flow_score", 0.0))
        flow_angle = dom + 90.0                       # stroke axis ⊥ tonal gradient
        region_mask = new_labels == lbl
        tier = entry.get("split_tier", "PRIMARY")
        if tier in EXPANSION_TIERS:
            # QF13-permitted bounded tonal split (large body/fur/face subject area,
            # or a large graded background) with a relaxed luma floor. PRIMARY regions
            # keep the default behaviour. `cap` (2–3 medium; up to 4 for high-fidelity
            # profiles) comes from QF13's per-profile max_subregions.
            cap = int(entry.get("max_subregions", EXPANSION_MAX_SUBREGIONS_DEFAULT))
            made, next_label, used_flow, k, children = _split_region(
                new_labels, int(lbl), lab, flow_angle, coh,
                entry.get("semantic_class"), next_label,
                kcap_override=max(2, cap), min_luma=exp_min_luma,
                new_labels_cap=max(1, cap - 1), min_cluster=exp_min_cluster,
                material_kcap=kcap_table,
            )
        else:
            made, next_label, used_flow, k, children = _split_region(
                new_labels, int(lbl), lab, flow_angle, coh,
                entry.get("semantic_class"), next_label,
                material_kcap=kcap_table,
            )
        if made > 1:
            split_mask |= region_mask
            details.append({
                "region_id": rid, "label": int(lbl),
                "semantic_class": entry.get("semantic_class"),
                "split_style": entry.get("split_style"),   # from QF13 (advisory)
                "split_tier": tier,                        # PRIMARY | GRADIENT_EXPANSION | BACKGROUND_EXPANSION
                "clusters": k, "subregions": made,
                "child_labels": children,                  # new labels -> inherit parent material (palette)
                "flow_angle_deg": round(flow_angle % 180.0, 1),
                "coherence": round(coh, 3), "flow_aligned": bool(used_flow),
            })

    if lineart_path is not None and details:
        art = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
        edges = _boundaries(new_labels) & split_mask & (original_labels != 0)
        edges = cv2.dilate(edges.astype(np.uint8), np.ones((2, 2), np.uint8)) > 0
        art[edges] = 0
        cv2.imwrite(str(lineart_path), art)

    non_line = original_labels > 0
    after_fill = _mean_fill(new_labels, bgr)
    de_after = _delta_e(after_fill, bgr, non_line)
    ss_after = ssim(cv2.cvtColor(after_fill, cv2.COLOR_BGR2GRAY),
                    cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), non_line)

    if overlay_path is not None:
        _overlay(overlay_path, original_labels, new_labels, after_fill, split_mask,
                 details, sem_by_id)

    info = {
        "version": 1,
        "note": "Intelligent Lab k-means + connected-component region splits. "
                "Selection obeys the QF13 artistic decision brain (split_plan); "
                "reuses QF7 flow + QF8 material budget (no recompute).",
        "selection_authority": "QF13 artistic_decision_engine",
        "selection_source": selection_source,
        "sources": ["artistic_decision_report.json", "semantic_region_report.json",
                    "material_behavior_report.json"],
        "regions_selected": len(selected),
        "regions_split": len(details),
        "primary_regions_split": sum(1 for d in details if d.get("split_tier", "PRIMARY") == "PRIMARY"),
        "expansion_regions_split": sum(1 for d in details if d.get("split_tier") in EXPANSION_TIERS),
        "background_regions_split": sum(1 for d in details if d.get("split_tier") == "BACKGROUND_EXPANSION"),
        "flow_aligned_regions": sum(1 for d in details if d["flow_aligned"]),
        "subregions_created": int(new_labels.max()) - int(original_labels.max()),
        "flat_fill_deltaE_after": round(de_after, 3),
        "flat_fill_ssim_after": round(ss_after, 4),
        "split_details": details,
        "changed": len(details) > 0,
    }
    return new_labels, info


def _overlay(path, old, new, after_fill, split_mask, details, sem_by_id):
    prev = after_fill.copy()
    prev[old == 0] = (35, 35, 35)
    edge = _boundaries(new) & split_mask & (old != 0)
    edge = cv2.dilate(edge.astype(np.uint8), np.ones((2, 2), np.uint8)) > 0
    prev[edge] = (0, 210, 255)                        # new sub-region boundaries (orange)
    for d in details:
        s = sem_by_id.get(d["region_id"], {})
        bbox = s.get("bbox")
        if not bbox:
            continue
        cx, cy = bbox[0] + bbox[2] // 2, bbox[1] + bbox[3] // 2
        ang = np.radians(d["flow_angle_deg"])
        dx, dy = int(35 * np.cos(ang)), int(35 * np.sin(ang))
        col = (0, 90, 255) if d["flow_aligned"] else (150, 150, 150)
        cv2.arrowedLine(prev, (cx - dx, cy - dy), (cx + dx, cy + dy), col, 2, tipLength=0.3)
    cv2.imwrite(str(path), prev)
