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


def _premerge_specks(lab: np.ndarray, min_area: int) -> np.ndarray:
    areas = np.bincount(lab.ravel())
    for rid in np.where(areas < min_area)[0]:
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
    return lab


def _compat_matrix(centres, merge_de: float):
    """k x k 'these two colours read as the same' matrix (CIE-Lab distance under merge_de).
    Lets five near-identical pinks merge into one region while yellow can never join green."""
    if centres is None:
        return None
    c = np.asarray(centres, np.uint8).reshape(1, -1, 3)
    lab = cv2.cvtColor(c, cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    d = np.linalg.norm(lab[:, None, :] - lab[None, :, :], axis=2)
    return d < merge_de


def intersect_color(lineart_fl: np.ndarray, color_fl: np.ndarray, r_min: float = 11.0,
                    min_speck_frac: float = 8e-5, centres=None, merge_de: float = 16.0,
                    target: int | None = None) -> np.ndarray:
    """Split each line-art cell by the original image's colour regions so a single tap never
    fills a big multi-colour area — every playable region is one colour inside one line-art shape
    (harder, more distinct). Both inputs follow real structure (line-art + colour), so this is not
    a polygon/Voronoi fallback. Re-merges tiny slivers and keeps masks tiling."""
    color_fl = color_fl.astype(np.int64)
    combo = lineart_fl.astype(np.int64) * (int(color_fl.max()) + 2) + color_fl
    _, inv = np.unique(combo, return_inverse=True)
    fl = (inv.reshape(lineart_fl.shape) + 1).astype(np.int32)

    # The combo split makes every region one colour. The clean-up merges must KEEP it that way:
    # a small piece may only fold into a neighbour of the SAME colour. Merging colour-blind was
    # what let one tap fill across colour boundaries on rich artwork (yellow taking its neighbours).
    rcol = _region_colours(fl, color_fl)
    compat = _compat_matrix(centres, merge_de)
    fl = _merge_specks_same_colour(fl, rcol, max(40, int(fl.size * min_speck_frac)), compat)
    fl = _merge_by_radius_same_colour(fl, rcol, r_min, compat=compat)
    if target:
        fl = _merge_to_target(fl, rcol, target, compat)
    return _relabel(fl)


def _region_colours(fl: np.ndarray, color_fl: np.ndarray) -> np.ndarray:
    """Colour index per region id. Regions are colour-pure right after the combo split, so any
    pixel of a region reports its colour."""
    out = np.zeros(int(fl.max()) + 1, np.int64)
    out[fl.ravel()] = color_fl.ravel()
    return out


def _same_colour_neighbour(fl: np.ndarray, rcol: np.ndarray, rid: int,
                           box: tuple | None = None, compat=None) -> int | None:
    """Dominant touching region that shares this region's colour — or None, in which case the
    region is left alone (a slightly small region beats a colour leak)."""
    if box is None:
        ys, xs = np.where(fl == rid)
        if ys.size == 0:
            return None
        y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    else:
        x0, y0, w, h = box
        x1, y1 = x0 + w - 1, y0 + h - 1
    y0, y1 = max(0, y0 - 2), min(fl.shape[0], y1 + 3)
    x0, x1 = max(0, x0 - 2), min(fl.shape[1], x1 + 3)
    sub = fl[y0:y1, x0:x1]
    m = sub == rid
    if not m.any():
        return None
    edge = cv2.dilate(m.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~m
    nb = sub[edge]
    nb = nb[(nb != 0) & (nb != rid)]
    if nb.size == 0:
        return None
    same = nb[compat[rcol[rid], rcol[nb]]] if compat is not None else nb[rcol[nb] == rcol[rid]]
    if same.size == 0:
        return None
    return int(np.bincount(same).argmax())


def _merge_specks_same_colour(fl: np.ndarray, rcol: np.ndarray, min_area: int, compat=None) -> np.ndarray:
    areas = np.bincount(fl.ravel())
    boxes = _region_bboxes(fl)
    for rid in np.where(areas < min_area)[0]:
        if rid == 0:
            continue
        nb = _same_colour_neighbour(fl, rcol, int(rid), boxes.get(int(rid)), compat)
        if nb is not None:
            fl[fl == rid] = nb
    return fl


def _merge_to_target(fl: np.ndarray, rcol: np.ndarray, target: int, compat=None) -> np.ndarray:
    """Keep folding the SMALLEST regions into a same-colour neighbour until the board is down to
    ~`target` regions. Detailed artwork honestly contains thousands of colour areas; without this
    the player faces an endless board. Merging stays colour-safe, so nothing leaks."""
    for _ in range(400):
        ids, areas = np.unique(fl, return_counts=True)
        keep = ids != 0
        ids, areas = ids[keep], areas[keep]
        if len(ids) <= target:
            break
        boxes = _region_bboxes(fl)
        order = np.argsort(areas)
        batch = max(1, min(len(ids) - target, len(ids) // 8))
        merged = False
        for j in order[:batch]:
            rid = int(ids[j])
            nb = _same_colour_neighbour(fl, rcol, rid, boxes.get(rid), compat)
            if nb is not None:
                fl[fl == rid] = nb
                merged = True
        if not merged:
            break                      # everything left is colour-isolated; stop rather than leak
    return fl


def _merge_by_radius_same_colour(fl: np.ndarray, rcol: np.ndarray, r_min: float,
                                 rounds: int = 12, compat=None) -> np.ndarray:
    """Same intent as _merge_by_radius (every region must be big enough to hold a number) but a
    region only ever folds into a same-colour neighbour."""
    fl = fl.copy()
    for _ in range(rounds):
        small = []
        for rid, bb in _region_bboxes(fl).items():
            r, _c = _inscribed_radius(fl, rid, bb)
            if r < r_min:
                small.append((r, rid, bb))
        if not small:
            break
        small.sort()
        changed = False
        for _r, rid, bb in small:
            nb = _same_colour_neighbour(fl, rcol, int(rid), bb, compat)
            if nb is not None:
                fl[fl == rid] = nb
                changed = True
        if not changed:
            break
    return fl


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
