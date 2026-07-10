"""Number-by-Color regions built FROM the clean AI line-art (not colour polygons, not Voronoi).

Pipeline: binarize the line-art -> close only small gaps -> flood-fill the CLOSED white cells
between the lines -> merge cells until every remaining region is large enough to hold a legible
number (inscribed-radius merge, which also absorbs thin folds) -> absorb the line pixels into the
nearest region so masks tile the image 100% -> sample the original colour per region -> k-means
group matching colours into palette numbers.

Hard rules honoured: regions are the line-art's own closed shapes (no polygon/Voronoi fallback);
masks tile the image; the reveal source stays the original master so reveal delta-E remains 0;
number placement uses each region's inscribed radius so numbers fit inside their cell (no overlap).
"""
from __future__ import annotations

import numpy as np
import cv2

NUMBER_MIN_RADIUS = 13.0     # a region smaller than this (inscribed radius, px) can't hold a number
PALETTE_NUMBERS = 24


def _region_bboxes(fl: np.ndarray) -> dict:
    out = {}
    ids = np.unique(fl)
    for rid in ids:
        if rid == 0:
            continue
        ys, xs = np.where(fl == rid)
        out[int(rid)] = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))
    return out


def _inscribed_radius(fl: np.ndarray, rid: int, bbox) -> tuple[float, tuple[int, int]]:
    x0, y0, x1, y1 = bbox
    crop = (fl[y0:y1 + 1, x0:x1 + 1] == rid).astype(np.uint8)
    dist = cv2.distanceTransform(crop, cv2.DIST_L2, 3)
    _, r, _, loc = cv2.minMaxLoc(dist)
    return float(r), (int(loc[0]) + x0, int(loc[1]) + y0)


def _dominant_neighbour(fl: np.ndarray, rid: int, bbox) -> int | None:
    x0, y0, x1, y1 = bbox
    pad = 2
    ys0, ys1 = max(0, y0 - pad), min(fl.shape[0], y1 + pad + 1)
    xs0, xs1 = max(0, x0 - pad), min(fl.shape[1], x1 + pad + 1)
    sub = fl[ys0:ys1, xs0:xs1]
    m = (sub == rid).astype(np.uint8)
    edge = cv2.dilate(m, np.ones((3, 3), np.uint8)).astype(bool) & ~m.astype(bool)
    nb = sub[edge]
    nb = nb[(nb != 0) & (nb != rid)]
    if nb.size == 0:
        return None
    return int(np.bincount(nb).argmax())


def _merge_by_radius(fl: np.ndarray, r_min: float, rounds: int = 12) -> np.ndarray:
    """Merge every region whose inscribed radius < r_min into its dominant neighbour, so all
    remaining regions can hold a number. Repeats until stable."""
    fl = fl.copy()
    for _ in range(rounds):
        bboxes = _region_bboxes(fl)
        # smallest-first so specks fold into growing neighbours
        radii = []
        for rid, bb in bboxes.items():
            r, _ = _inscribed_radius(fl, rid, bb)
            if r < r_min:
                radii.append((r, rid, bb))
        if not radii:
            break
        radii.sort()
        changed = False
        for _, rid, bb in radii:
            if not (fl == rid).any():
                continue
            nb = _dominant_neighbour(fl, rid, bb)
            if nb is not None:
                fl[fl == rid] = nb
                changed = True
        if not changed:
            break
    return fl


def _relabel(fl: np.ndarray) -> np.ndarray:
    uniq = [u for u in np.unique(fl) if u != 0]
    out = np.zeros_like(fl)
    for i, u in enumerate(uniq, start=1):
        out[fl == u] = i
    return out


def build_regions(line_gray: np.ndarray, r_min: float = NUMBER_MIN_RADIUS,
                  line_thresh: int = 165, close: int = 5, min_speck_frac: float = 8e-5) -> np.ndarray:
    """Closed, numberable regions from the line-art, tiling the image. Returns int labels 1..K.

    `line_thresh` captures the soft grey lines too (so the flood-fill does not leak between
    neighbouring shapes); `close` bridges only genuine small gaps."""
    from services.reveal_game_builder import fill_labels   # lazy: avoids an import cycle
    n_px = line_gray.size
    lines = (line_gray < line_thresh).astype(np.uint8)
    lines = cv2.morphologyEx(lines, cv2.MORPH_CLOSE, np.ones((close, close), np.uint8))  # close SMALL gaps only
    white = (lines == 0).astype(np.uint8)
    _, lab = cv2.connectedComponents(white, connectivity=4)          # 0 = lines, 1..n = closed cells
    # pre-merge pin-prick specks by area (fast) before the radius merge
    areas = np.bincount(lab.ravel())
    speck = max(40, int(n_px * min_speck_frac))
    for rid in np.where(areas < speck)[0]:
        if rid == 0:
            continue
        m = lab == rid
        if not m.any():
            continue
        edge = cv2.dilate(m.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~m
        nb = lab[edge]
        nb = nb[(nb != 0) & (nb != rid)]
        if nb.size:
            lab[m] = int(np.bincount(nb).argmax())
    fl = fill_labels(lab.astype(np.int32))                            # absorb line pixels -> tiles 100%
    fl = _merge_by_radius(fl, r_min)                                  # ensure every region is numberable
    return _relabel(fl)


def palette_numbers(original: np.ndarray, fl: np.ndarray, n_colors: int = PALETTE_NUMBERS) -> dict:
    """Group regions' sampled original colours into palette numbers (matching colours share one)."""
    ids = [int(i) for i in np.unique(fl) if i != 0]
    cols = np.array([np.median(original[fl == i].reshape(-1, 3), axis=0) for i in ids], np.float32)
    k = min(n_colors, len(ids))
    if k < 1:
        return {}
    _, klab, _ = cv2.kmeans(cols, k, None,
                            (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5),
                            4, cv2.KMEANS_PP_CENTERS)
    return {ids[j]: int(klab[j][0]) + 1 for j in range(len(ids))}


def region_meta(fl: np.ndarray, numbers: dict) -> list:
    """Per-region {id, number, x, y, r (inscribed radius), bbox} — number goes at the most-interior
    point, sized to the inscribed radius so it stays inside its cell."""
    meta = []
    for rid, bb in _region_bboxes(fl).items():
        r, (px, py) = _inscribed_radius(fl, rid, bb)
        meta.append({"id": rid, "n": numbers.get(rid, rid), "x": px, "y": py,
                     "r": round(r, 1), "bb": [bb[0], bb[1], bb[2] - bb[0] + 1, bb[3] - bb[1] + 1]})
    return meta
