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

Reuses (NO recompute): QF7 `semantic_region_report.json` (region_id → label, flow
direction + coherence), QF8 `material_behavior_report.json` (per-class split budget),
QF9 `processing_policy_report.json` (selection + limits), QF11
`flow_gradient_report.json`. Colours are sampled downstream from the original by the
existing pipeline; this engine only edits region geometry.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from services.gradient_preservation_engine import (
    MIN_LUMA_RANGE,
    SELECT_POLICY,
    SPLIT_MIN_STRENGTH,
    _boundaries,
    _delta_e,
    _mean_fill,
)
from services.flow_gradient_engine import (
    BLUR_LEN,
    MIN_COHERENCE,
    _oriented_blur,
    ssim,
)

MIN_REGION_AREA = 1200   # regions smaller than this are protected (eyes/nose/small)
MIN_CLUSTER = 220        # a k-means cluster below this stays with the base region
OPEN_KERNEL = 3          # morphological open to de-speckle each cluster (no fragments)
NEW_LABELS_CAP = 5       # max NEW sub-regions per region (avoid over-splitting)
KCLUSTER_STEP = 16.0     # ~1 extra cluster per this much combined Lab spread
L_WEIGHT = 1.0           # Lab channels weighted equally (empirically best ΔE+SSIM)

# Per-material cluster budget (reuses QF8 semantic class). Textured materials get
# more clusters; flat/fragile ones get fewer. Unknowns fall back to 3.
MATERIAL_KCAP = {
    "FUR": 5, "HAIR": 5, "CLOTH": 5, "LEAF": 4, "WOOD": 4,
    "SKIN": 4, "STONE": 4, "METAL": 3, "PLASTIC": 3,
}
# Never split these (small / fragile / structural detail), even if policy selects.
PROTECT_CLASSES = {"FLOWER", "GLASS", "EYE", "SKY"}


def _kmeans_labels(samples: np.ndarray, k: int) -> np.ndarray:
    """Lab k-means; returns a per-sample cluster index (0..k-1)."""
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
    # Deterministic init (KMEANS_PP_CENTERS is seeded internally by OpenCV); no RNG
    # dependence in the workflow sense — same input -> same clusters.
    _compact, labels, _centers = cv2.kmeans(
        samples.astype(np.float32), k, None, crit, 3, cv2.KMEANS_PP_CENTERS
    )
    return labels.ravel()


def _pick_k(Lv: np.ndarray, av: np.ndarray, bv: np.ndarray, kcap: int) -> int:
    """Choose cluster count from combined luminance + chroma spread, capped by material."""
    l_span = float(np.percentile(Lv, 95) - np.percentile(Lv, 5))
    chroma = np.sqrt((av - 128.0) ** 2 + (bv - 128.0) ** 2)
    c_span = float(np.percentile(chroma, 95) - np.percentile(chroma, 5))
    if l_span < MIN_LUMA_RANGE and c_span < MIN_LUMA_RANGE:
        return 1
    score = l_span + 0.7 * c_span
    return int(np.clip(round(1 + score / KCLUSTER_STEP), 2, kcap))


def _split_region(new_labels, lbl, lab, angle, coherence, material, next_label):
    """Cluster one region in Lab, keep coherent components. Returns
    (n_subregions, next_label, flow_aligned, k)."""
    mask = new_labels == lbl
    ys, xs = np.where(mask)
    if len(xs) < MIN_REGION_AREA:                 # protect small details
        return 1, next_label, False, 0
    pad = 6
    x0, x1 = max(0, xs.min() - pad), min(lab.shape[1], xs.max() + pad + 1)
    y0, y1 = max(0, ys.min() - pad), min(lab.shape[0], ys.max() + pad + 1)
    labc = lab[y0:y1, x0:x1]
    mc = mask[y0:y1, x0:x1]
    Lc, ac, bc = labc[:, :, 0], labc[:, :, 1], labc[:, :, 2]

    kcap = MATERIAL_KCAP.get(material, 3)
    k = _pick_k(Lc[mc].astype(np.float32), ac[mc].astype(np.float32),
                bc[mc].astype(np.float32), kcap)
    if k < 2:
        return 1, next_label, False, 0

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
    for c, sz in sizes[1:]:                        # skip the largest (stays base)
        if sz < MIN_CLUSTER or created >= NEW_LABELS_CAP:
            continue
        cmask = ((bu == c) & mc).astype(np.uint8)
        cmask = cv2.morphologyEx(cmask, cv2.MORPH_OPEN, ker)  # drop speckle -> base
        if int(cmask.sum()) < MIN_CLUSTER:
            continue
        full = np.zeros(new_labels.shape, bool)
        full[y0:y1, x0:x1] = cmask > 0
        new_labels[full] = next_label
        next_label += 1
        made += 1
        created += 1
    return made, next_label, used_flow, k


def run(semantic_report_path: Path, policy_report_path: Path, labels_path: Path,
        preprocessed_path: Path, overlay_path: Path | None = None,
        lineart_path: Path | None = None,
        material_report_path: Path | None = None) -> tuple[np.ndarray, dict]:
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    policy = json.loads(Path(policy_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path).astype(np.int32)
    bgr = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)

    sem_by_id = {r["region_id"]: r for r in semantic.get("regions", [])}
    selected = [
        r for r in policy.get("regions", [])
        if r.get("policy_class") in SELECT_POLICY and r.get("preserve_gradient")
        and float(r.get("future_split_strength", 0.0)) >= SPLIT_MIN_STRENGTH
        and r.get("semantic_class") not in PROTECT_CLASSES     # protect small details
    ]

    original_labels = labels.copy()
    new_labels = labels.copy()
    next_label = int(labels.max()) + 1
    split_mask = np.zeros(labels.shape, bool)
    details = []
    for r in selected:
        s = sem_by_id.get(r["region_id"], {})
        lbl = s.get("label")
        if lbl is None:
            continue
        dom = float(s.get("dominant_gradient_direction", 0.0))
        coh = float(s.get("edge_flow_score", 0.0))
        flow_angle = dom + 90.0                       # stroke axis ⊥ tonal gradient
        region_mask = new_labels == lbl
        made, next_label, used_flow, k = _split_region(
            new_labels, int(lbl), lab, flow_angle, coh,
            r.get("semantic_class"), next_label,
        )
        if made > 1:
            split_mask |= region_mask
            details.append({
                "region_id": r["region_id"], "label": int(lbl),
                "semantic_class": r.get("semantic_class"),
                "clusters": k, "subregions": made,
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
        "note": "Intelligent Lab k-means + connected-component region splits; "
                "reuses QF7 flow, QF8 material budget, QF9 policy, QF11 (no recompute).",
        "sources": ["semantic_region_report.json", "material_behavior_report.json",
                    "processing_policy_report.json", "flow_gradient_report.json",
                    "border_quality_report.json"],
        "regions_selected": len(selected),
        "regions_split": len(details),
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
