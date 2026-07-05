"""Quality Fix 11 — flow-aware gradient engine (pixel-changing, refines QF10).

QF10 banded selected regions by luminance quantiles, giving isotropic (blobby)
tonal steps. QF11 makes the bands **follow material flow** — fur/stripe, leaf
vein, cloth fold direction — by smoothing luminance *along* the region's dominant
flow before banding. Bands then elongate along the flow, so shading reads as fur
rather than patches.

Flow direction is taken from the reused QF7 `semantic_region_report.json`
(`dominant_gradient_direction` + `edge_flow_score` coherence); the flow/stroke
axis is perpendicular to the dominant gradient. Regions with weak coherence fall
back to a gentle isotropic blur (QF10-like). Selection + limits come from QF9's
`processing_policy_report.json`. No previous analysis is recomputed.

Reuses helpers from `gradient_preservation_engine` (QF10) to avoid duplication.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from services.gradient_preservation_engine import (
    LUMA_STEP,
    MAX_STEPS,
    MIN_LUMA_RANGE,
    SELECT_POLICY,
    SPLIT_MIN_STRENGTH,
    _boundaries,
    _delta_e,
    _mean_fill,
)

MIN_COHERENCE = 0.25   # edge_flow_score below this -> isotropic fallback
FLOW_MAX_STEPS = 4     # QF11 allows one more tonal step than QF10 (finer gradient)
BLUR_LEN = 15          # oriented band-boundary smoothing kernel length


def ssim(a_gray: np.ndarray, b_gray: np.ndarray, mask: np.ndarray | None = None) -> float:
    a = a_gray.astype(np.float64)
    b = b_gray.astype(np.float64)
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    mu_a = cv2.GaussianBlur(a, (11, 11), 1.5)
    mu_b = cv2.GaussianBlur(b, (11, 11), 1.5)
    sa = cv2.GaussianBlur(a * a, (11, 11), 1.5) - mu_a * mu_a
    sb = cv2.GaussianBlur(b * b, (11, 11), 1.5) - mu_b * mu_b
    sab = cv2.GaussianBlur(a * b, (11, 11), 1.5) - mu_a * mu_b
    m = ((2 * mu_a * mu_b + c1) * (2 * sab + c2)) / \
        ((mu_a * mu_a + mu_b * mu_b + c1) * (sa + sb + c2) + 1e-9)
    return float(m[mask].mean()) if mask is not None and mask.any() else float(m.mean())


def _oriented_blur(img: np.ndarray, angle_deg: float, length: int = BLUR_LEN, thick: int = 3) -> np.ndarray:
    k = np.zeros((length, length), np.float32)
    cv2.line(k, (0, length // 2), (length - 1, length // 2), 1.0, thick)
    rot = cv2.getRotationMatrix2D((length / 2 - 0.5, length / 2 - 0.5), -angle_deg, 1.0)
    k = cv2.warpAffine(k, rot, (length, length))
    s = k.sum()
    if s > 0:
        k /= s
    return cv2.filter2D(img, -1, k)


def _flow_step(new_labels, lbl, L, angle, coherence, next_label):
    mask = new_labels == lbl
    ys, xs = np.where(mask)
    if len(xs) < 200:
        return 1, next_label, False
    pad = 10
    x0, x1 = max(0, xs.min() - pad), min(L.shape[1], xs.max() + pad + 1)
    y0, y1 = max(0, ys.min() - pad), min(L.shape[0], ys.max() + pad + 1)
    Lc = L[y0:y1, x0:x1]
    mc = mask[y0:y1, x0:x1]

    # Band by RAW luminance (tone-accurate -> good colour fidelity), up to 4 steps.
    Lv = Lc[mc]
    lo, hi = np.percentile(Lv, [5, 95])
    if (hi - lo) < MIN_LUMA_RANGE:
        return 1, next_label, False
    k = int(np.clip(1 + (hi - lo) / LUMA_STEP, 2, FLOW_MAX_STEPS))
    qs = np.percentile(Lv, [100.0 * i / k for i in range(1, k)])
    band = np.digitize(Lc, qs).astype(np.float32)

    # Flow-align the band BOUNDARIES (not the tone): smoothing the band map along
    # the flow direction elongates bands along fur/vein/fold, with minimal colour
    # cost since only boundary pixels move.
    used_flow = coherence >= MIN_COHERENCE
    if used_flow:
        band = np.rint(_oriented_blur(band, angle, BLUR_LEN, 1))
    bu = np.where(mc, band, 255).astype(np.uint8)
    bu = cv2.medianBlur(bu, 3)

    made = 1
    for b in range(1, k):
        bm = mc & (bu == b)
        if bm.sum() < 200:
            continue
        full = np.zeros(new_labels.shape, bool)
        full[y0:y1, x0:x1] = bm
        new_labels[full] = next_label
        next_label += 1
        made += 1
    return made, next_label, used_flow


def run(semantic_report_path: Path, policy_report_path: Path, labels_path: Path,
        preprocessed_path: Path, overlay_path: Path | None = None,
        lineart_path: Path | None = None) -> tuple[np.ndarray, dict]:
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    policy = json.loads(Path(policy_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path).astype(np.int32)
    bgr = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    L = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)[:, :, 0].astype(np.float32)

    sem_by_id = {r["region_id"]: r for r in semantic.get("regions", [])}
    selected = [
        r for r in policy.get("regions", [])
        if r.get("policy_class") in SELECT_POLICY and r.get("preserve_gradient")
        and float(r.get("future_split_strength", 0.0)) >= SPLIT_MIN_STRENGTH
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
        flow_angle = dom + 90.0  # stroke/flow axis is perpendicular to the gradient
        region_mask = new_labels == lbl
        made, next_label, used_flow = _flow_step(new_labels, int(lbl), L, flow_angle, coh, next_label)
        if made > 1:
            split_mask |= region_mask
            details.append({
                "region_id": r["region_id"], "label": int(lbl),
                "semantic_class": r.get("semantic_class"), "bands": made,
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
    ss_after = ssim(cv2.cvtColor(after_fill, cv2.COLOR_BGR2GRAY), cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), non_line)

    if overlay_path is not None:
        _overlay(overlay_path, original_labels, new_labels, after_fill, split_mask, details, sem_by_id)

    info = {
        "version": 1,
        "note": "Flow-aware gradient bands; reuses QF7 orientation + QF9 policy (no recompute).",
        "sources": ["semantic_region_report.json", "material_behavior_report.json",
                    "processing_policy_report.json", "gradient_preservation_report.json"],
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
    prev[edge] = (0, 210, 255)
    # flow direction arrows per split region
    for d in details:
        s = sem_by_id.get(d["region_id"], {})
        pos = s.get("label_pos") or {}
        mask = new  # not needed; use region centroid from bbox in semantic report
        bbox = s.get("bbox")
        if not bbox:
            continue
        cx, cy = bbox[0] + bbox[2] // 2, bbox[1] + bbox[3] // 2
        ang = np.radians(d["flow_angle_deg"])
        dx, dy = int(35 * np.cos(ang)), int(35 * np.sin(ang))
        col = (0, 90, 255) if d["flow_aligned"] else (150, 150, 150)
        cv2.arrowedLine(prev, (cx - dx, cy - dy), (cx + dx, cy + dy), col, 2, tipLength=0.3)
    cv2.imwrite(str(path), prev)
