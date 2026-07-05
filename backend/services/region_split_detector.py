"""Quality Fix 5 — missing split detection (detection only, no auto-split).

Quality Fix 4's flood-fill test cannot see a *fully-merged* leak: when a missing
border makes two intended areas a single connected component, a fill stays inside
it (nothing to escape). This detector finds those merged regions from evidence in
the ORIGINAL image that the line art failed to capture.

Signals per region (all measured on the untouched working image):
  - area outlier          — region far larger than its peers
  - internal color variance — one region spanning clearly different colors (Lab)
  - internal edge evidence  — a coherent source-image edge crossing the region
                              interior that was never drawn as a border
  - shape complexity        — dumbbell / concave shape (compactness)
  - local contrast          — tonal spread inside the region

A region is flagged only when multiple signals agree (a lone soft edge or a bit
of texture is not enough). Nothing is split — the detector emits IDs, evidence,
and split recommendations for later review. It changes no palette, numbers,
regions, or colors.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

MIN_REGION_FRAC = 0.0008
# Evidence thresholds (Lab std 0-255; ratios in [0,1]).
EDGE_SPAN = 0.38          # internal edge spans >=38% of the region bbox diagonal
COLOR_STD = 11.0          # Lab std indicating >1 colour in the region
BIG_COLOR_STD = 15.0
AREA_OUTLIER = 6.0        # >=6x the median interior-region area
EDGE_DENSITY = 0.03       # fraction of interior pixels that are source edges
COMPACT = 4.0             # perimeter^2 / (4*pi*area); 1 = circle


def _region_signals(mask, interior, stats, lbl, lab, gray, edges, median_area):
    x = int(stats[lbl, cv2.CC_STAT_LEFT]); y = int(stats[lbl, cv2.CC_STAT_TOP])
    w = int(stats[lbl, cv2.CC_STAT_WIDTH]); h = int(stats[lbl, cv2.CC_STAT_HEIGHT])
    area = int(stats[lbl, cv2.CC_STAT_AREA])
    bbox_diag = float(np.hypot(w, h)) or 1.0

    px = lab[mask].astype(np.float32)
    color_std = float(px.std(axis=0).mean())
    contrast = float(gray[mask].std() / 255.0)

    ie = (edges > 0) & interior
    interior_n = int(interior.sum())
    edge_density = float(ie.sum() / max(1, interior_n))

    # longest coherent internal edge (a candidate missing border)
    span = 0.0
    best_bbox = None
    n2, _, st2, _ = cv2.connectedComponentsWithStats(ie.astype(np.uint8), 8)
    for i in range(1, n2):
        if st2[i, cv2.CC_STAT_AREA] < 15:
            continue
        d = float(np.hypot(st2[i, cv2.CC_STAT_WIDTH], st2[i, cv2.CC_STAT_HEIGHT]))
        if d > span * bbox_diag:
            span = d / bbox_diag
            best_bbox = [int(st2[i, cv2.CC_STAT_LEFT]), int(st2[i, cv2.CC_STAT_TOP]),
                         int(st2[i, cv2.CC_STAT_WIDTH]), int(st2[i, cv2.CC_STAT_HEIGHT])]

    cnts, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    perim = sum(cv2.arcLength(c, True) for c in cnts)
    compactness = (perim * perim) / (4.0 * np.pi * area) if area else 1.0

    return {
        "area": area,
        "bbox": [x, y, w, h],
        "area_ratio": round(area / median_area, 2),
        "color_std": round(color_std, 2),
        "internal_edge_span": round(span, 3),
        "internal_edge_density": round(edge_density, 4),
        "compactness": round(float(compactness), 2),
        "contrast": round(contrast, 3),
        "_edge_bbox": best_bbox,
        "_ie": ie,
    }


def _split_line(mask, interior, edges):
    """Suggest one split line for a merged region (advisory guide, not a cut).

    If a coherent source edge crosses the interior (a missing border), fit a line
    to it. Otherwise fall back to a geometric bisector perpendicular to the
    region's major axis. Returns (p1, p2, source) in image coordinates.
    """
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return None, None, None
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    diag = float(np.hypot(x1 - x0, y1 - y0)) or 1.0

    ie = ((edges > 0) & interior).astype(np.uint8)
    n2, lab2, st2, _ = cv2.connectedComponentsWithStats(ie, 8)
    best, best_len = 0, 0.0
    for i in range(1, n2):
        if st2[i, cv2.CC_STAT_AREA] < 15:
            continue
        d = float(np.hypot(st2[i, cv2.CC_STAT_WIDTH], st2[i, cv2.CC_STAT_HEIGHT]))
        if d > best_len:
            best_len, best = d, i

    if best > 0 and best_len >= 0.30 * diag:
        eys, exs = np.where(lab2 == best)
        pts = np.column_stack([exs, eys]).astype(np.float32)
        vx, vy, px, py = cv2.fitLine(pts, cv2.DIST_L2, 0, 0.01, 0.01).ravel()
        source = "internal-edge"
    else:
        pts = np.column_stack([xs, ys]).astype(np.float32)
        mean = pts.mean(axis=0)
        cov = np.cov((pts - mean).T)
        evals, evecs = np.linalg.eigh(cov)
        major = evecs[:, int(np.argmax(evals))]
        perp = np.array([-major[1], major[0]], np.float32)  # cut across the long axis
        vx, vy, px, py = perp[0], perp[1], mean[0], mean[1]
        source = "geometry-bisector"

    p1 = (int(px - vx * diag), int(py - vy * diag))
    p2 = (int(px + vx * diag), int(py + vy * diag))
    return p1, p2, source


def detect(lineart_gray, preprocessed_path: Path, merged_map_path: Path | None = None,
           split_preview_path: Path | None = None) -> dict:
    _, bw = cv2.threshold(lineart_gray, 127, 255, cv2.THRESH_BINARY)
    num, labels, stats, _ = cv2.connectedComponentsWithStats((bw > 0).astype(np.uint8), 4)
    h, w = lineart_gray.shape
    total = h * w
    frame = set(np.unique(np.concatenate([
        labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]
    ]).tolist()))
    frame.discard(0)

    img = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    if img is None or img.shape[:2] != (h, w):
        img = cv2.resize(img, (w, h)) if img is not None else np.full((h, w, 3), 255, np.uint8)
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 60, 150)

    min_region = max(400, int(MIN_REGION_FRAC * total))
    interior = [L for L in range(1, num)
                if stats[L, cv2.CC_STAT_AREA] >= min_region and L not in frame]
    if not interior:
        if merged_map_path is not None:
            cv2.imwrite(str(merged_map_path), np.full((h, w, 3), 255, np.uint8))
        return {"suspected_merged_region_count": 0,
                "suspected_merged_region_ids": [], "split_recommendations": []}

    median_area = float(np.median([stats[L, cv2.CC_STAT_AREA] for L in interior]))

    suspects: list[int] = []
    recs: list[dict] = []
    edge_layer = np.zeros((h, w), np.uint8)

    for lbl in interior:
        mask = labels == lbl
        inter = cv2.erode(mask.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
        if inter.sum() < min_region * 0.3:
            continue
        s = _region_signals(mask, inter, stats, lbl, lab, gray, edges, median_area)

        reasons = []
        if s["internal_edge_span"] >= EDGE_SPAN and s["color_std"] >= COLOR_STD:
            reasons.append("a coherent source edge crosses the region interior with a colour change (missing border)")
        if s["area_ratio"] >= AREA_OUTLIER and s["color_std"] >= BIG_COLOR_STD:
            reasons.append("over-large region with high internal colour variance")
        if (s["internal_edge_density"] >= EDGE_DENSITY and s["color_std"] >= BIG_COLOR_STD
                and s["compactness"] >= COMPACT and s["area_ratio"] >= 0.8):
            reasons.append("dense internal edges, high colour variance, and complex shape")

        if reasons:
            suspects.append(int(lbl))
            edge_layer[s["_ie"]] = 255
            p1, p2, line_source = _split_line(mask, inter, edges)
            recs.append({
                "region_id": int(lbl),
                "area": s["area"],
                "bbox": s["bbox"],
                "reasons": reasons,
                "evidence": {
                    "area_ratio": s["area_ratio"],
                    "color_std": s["color_std"],
                    "internal_edge_span": s["internal_edge_span"],
                    "internal_edge_density": s["internal_edge_density"],
                    "compactness": s["compactness"],
                    "contrast": s["contrast"],
                },
                "suggested_split_near_bbox": s["_edge_bbox"],
                "split_line": [list(p1), list(p2)] if p1 else None,
                "split_line_source": line_source,
            })

    if merged_map_path is not None:
        canvas = np.full((h, w, 3), 255, np.uint8)
        canvas[labels == 0] = (40, 40, 40)              # line art
        for lbl in suspects:
            canvas[labels == lbl] = (90, 90, 235)        # suspected merged region (red)
        canvas[edge_layer > 0] = (0, 170, 255)           # missing-border edge evidence (orange)
        cv2.imwrite(str(merged_map_path), canvas)

    # Quality Fix 6 — advisory split preview: original background + highlighted
    # suspects + a clearly drawn suggested split line per region.
    split_preview_count = sum(1 for r in recs if r.get("split_line"))
    if split_preview_path is not None:
        prev = cv2.addWeighted(img, 0.7, np.full_like(img, 255), 0.3, 0)  # lighten
        prev[labels == 0] = (40, 40, 40)                 # line art for context
        overlay = prev.copy()
        for lbl in suspects:
            overlay[labels == lbl] = (90, 90, 235)        # suspect tint (red)
        prev = cv2.addWeighted(overlay, 0.30, prev, 0.70, 0)
        for r in recs:
            sl = r.get("split_line")
            if not sl:
                continue
            line = np.zeros((h, w), np.uint8)
            cv2.line(line, tuple(sl[0]), tuple(sl[1]), 255, 6)
            line = cv2.bitwise_and(line, (labels == r["region_id"]).astype(np.uint8) * 255)
            prev[line > 0] = (0, 0, 255)                  # bold red split guide
        cv2.imwrite(str(split_preview_path), prev)

    return {
        "suspected_merged_region_count": len(suspects),
        "suspected_merged_region_ids": suspects,
        "split_recommendations": recs,
        "split_preview_available": split_preview_count > 0,
        "split_preview_count": split_preview_count,
    }
