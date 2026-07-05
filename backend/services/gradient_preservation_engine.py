"""Quality Fix 10 — gradient preservation engine (first pixel-changing fix).

Makes the flat fill closer to the original by preserving important shading as
*stepped paint regions*. It splits ONLY the regions QF9's policy selected
(preserve_gradient + GRADIENT_PRESERVE/FLOW_PROTECT + high future_split_strength)
into a few luminance bands, sampling colours from the original. Everything else
is untouched.

Reuses `semantic_region_report.json` (QF7, for region_id -> label) and
`processing_policy_report.json` (QF9, for the selection + per-region limits). It
recomputes no analysis. It returns an edited label map; the caller rebuilds
regions/palette/numbers/SVG consistently from it (via the existing
`pipeline.rebuild_from_labels`).

Guards against fragments/over-splitting: max 3 bands, balanced quantile bands,
median-smoothed, and a minimum luminance range before any split happens.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

SPLIT_MIN_STRENGTH = 0.5      # policy future_split_strength gate
MIN_LUMA_RANGE = 16.0         # Lab-L span (p5..p95) needed before splitting
LUMA_STEP = 20.0              # ~1 band per this much L range
MAX_STEPS = 3                 # never more than 3 tonal steps (avoid over-split)
SELECT_POLICY = {"GRADIENT_PRESERVE", "FLOW_PROTECT"}


def _mean_fill(labels: np.ndarray, bgr: np.ndarray) -> np.ndarray:
    """Flat fill: every region painted with the mean original colour (label 0 = line)."""
    num = int(labels.max()) + 1
    flat = labels.ravel()
    counts = np.bincount(flat, minlength=num).astype(np.float64)
    counts[counts == 0] = 1
    out = np.zeros_like(bgr)
    for ch in range(3):
        s = np.bincount(flat, weights=bgr[:, :, ch].astype(np.float64).ravel(), minlength=num)
        m = (s / counts).astype(np.uint8)
        out[:, :, ch] = m[labels]
    out[labels == 0] = (35, 35, 35)
    return out


def _delta_e(fill_bgr: np.ndarray, orig_bgr: np.ndarray, mask: np.ndarray) -> float:
    """Mean CIE76 Lab distance between a flat fill and the original, over `mask`."""
    a = cv2.cvtColor(fill_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    b = cv2.cvtColor(orig_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    d = np.sqrt(((a - b) ** 2).sum(axis=2))
    return float(d[mask].mean()) if mask.any() else 0.0


def _step_region(new_labels: np.ndarray, lbl: int, L: np.ndarray, next_label: int) -> tuple[int, int]:
    """Split one region into balanced luminance bands. Returns (n_bands, next_label)."""
    mask = new_labels == lbl
    Lv = L[mask].astype(np.float32)
    if Lv.size < 200:
        return 1, next_label
    lo, hi = np.percentile(Lv, [5, 95])
    if (hi - lo) < MIN_LUMA_RANGE:
        return 1, next_label
    k = int(np.clip(1 + (hi - lo) / LUMA_STEP, 2, MAX_STEPS))
    qs = np.percentile(Lv, [100.0 * i / k for i in range(1, k)])

    band = np.digitize(L, qs)                      # 0..k-1 everywhere
    bu = np.where(mask, band, 255).astype(np.uint8)
    bu = cv2.medianBlur(bu, 5)                      # de-speckle bands

    made = 1  # band 0 keeps lbl
    for b in range(1, k):
        bmask = mask & (bu == b)
        if bmask.sum() < 200:
            continue
        new_labels[bmask] = next_label
        next_label += 1
        made += 1
    return made, next_label


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def run(semantic_report_path: Path, policy_report_path: Path, labels_path: Path,
        preprocessed_path: Path, preview_path: Path | None = None,
        lineart_path: Path | None = None) -> tuple[np.ndarray, dict]:
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    policy = json.loads(Path(policy_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path).astype(np.int32)
    bgr = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
    L = lab[:, :, 0].astype(np.float32)

    id_to_label = {r["region_id"]: r.get("label") for r in semantic.get("regions", [])}
    selected = [
        r for r in policy.get("regions", [])
        if r.get("policy_class") in SELECT_POLICY
        and r.get("preserve_gradient")
        and float(r.get("future_split_strength", 0.0)) >= SPLIT_MIN_STRENGTH
    ]

    original_labels = labels.copy()
    new_labels = labels.copy()
    next_label = int(labels.max()) + 1
    split_mask = np.zeros(labels.shape, bool)
    details = []
    for r in selected:
        lbl = id_to_label.get(r["region_id"])
        if lbl is None:
            continue
        region_mask = new_labels == lbl
        made, next_label = _step_region(new_labels, int(lbl), L, next_label)
        if made > 1:
            split_mask |= region_mask
            details.append({
                "region_id": r["region_id"], "label": int(lbl),
                "semantic_class": r.get("semantic_class"), "bands": made,
                "future_split_strength": r.get("future_split_strength"),
            })

    # Draw the new tonal-band boundaries into the line art so the Line Art view,
    # regions, and SVG stay consistent (thin lines separating shading steps).
    if lineart_path is not None and details:
        art = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
        band_edges = _boundaries(new_labels) & split_mask & (original_labels != 0)
        band_edges = cv2.dilate(band_edges.astype(np.uint8), np.ones((2, 2), np.uint8)) > 0
        art[band_edges] = 0
        cv2.imwrite(str(lineart_path), art)

    # Fidelity: mean Lab distance of the flat fill to the original (lower = closer).
    non_line = original_labels > 0
    before_fill = _mean_fill(original_labels, bgr)
    after_fill = _mean_fill(new_labels, bgr)
    de_before = _delta_e(before_fill, bgr, non_line)
    de_after = _delta_e(after_fill, bgr, non_line)
    de_before_sel = _delta_e(before_fill, bgr, split_mask) if split_mask.any() else 0.0
    de_after_sel = _delta_e(after_fill, bgr, split_mask) if split_mask.any() else 0.0
    improvement = round((de_before - de_after) / de_before * 100.0, 2) if de_before else 0.0
    sel_improvement = round((de_before_sel - de_after_sel) / de_before_sel * 100.0, 2) if de_before_sel else 0.0

    if preview_path is not None:
        _render_preview(preview_path, original_labels, new_labels, after_fill, split_mask)

    info = {
        "version": 1,
        "note": "Gradient preservation via stepped sub-regions on policy-selected "
                "regions only; reuses QF7 + QF9 (no recompute).",
        "sources": ["semantic_region_report.json", "processing_policy_report.json"],
        "regions_selected": len(selected),
        "regions_split": len(details),
        "subregions_created": int(new_labels.max()) - int(original_labels.max()),
        "region_labels_before": int((np.unique(original_labels) > 0).sum()),
        "region_labels_after": int((np.unique(new_labels) > 0).sum()),
        "flat_fill_deltaE_before": round(de_before, 3),
        "flat_fill_deltaE_after": round(de_after, 3),
        "deltaE_improvement_percent": improvement,
        "selected_region_deltaE_before": round(de_before_sel, 3),
        "selected_region_deltaE_after": round(de_after_sel, 3),
        "selected_region_improvement_percent": sel_improvement,
        "gradient_fidelity_score": improvement,
        "split_details": details,
        "changed": len(details) > 0,
    }
    return new_labels, info


def _render_preview(path: Path, old: np.ndarray, new: np.ndarray, after_fill: np.ndarray,
                    split_mask: np.ndarray) -> None:
    prev = after_fill.copy()
    prev[old == 0] = (35, 35, 35)                       # line art
    # New band boundaries inside split regions, drawn bright.
    edge = np.zeros(new.shape, bool)
    edge[:, :-1] |= new[:, :-1] != new[:, 1:]
    edge[:-1, :] |= new[:-1, :] != new[1:, :]
    new_edges = edge & split_mask & (old != 0)
    new_edges = cv2.dilate(new_edges.astype(np.uint8), np.ones((2, 2), np.uint8)) > 0
    prev[new_edges] = (0, 210, 255)                      # stepped-gradient boundaries (orange)
    cv2.imwrite(str(path), prev)
