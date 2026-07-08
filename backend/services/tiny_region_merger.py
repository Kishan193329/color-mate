"""Phase 5 — tiny region merger.

After connected-components labelling, regions below the area floor are either
**merged into the adjacent large region** they share the most border with, or
dropped if they have no large neighbour (pure speckle). Returns a label remap
plus counts, leaving the heavy geometry recompute to `region_detector`.

`plan_insignificant_merge` (Upstream Tiny-Region Merging) is an additional,
colour-aware pass: it dissolves *visually insignificant* tiny regions — ones that
are nearly the same colour as an adjacent region — into that neighbour, so the
coloring page carries fewer needless micro-regions. It only ever reassigns pixels
to a NON-ZERO neighbour (the `labels > 0` union is preserved, so the completed
reveal still equals the original), only merges when the colour difference is small
(no strong edge is crossed → high-contrast features like eyes / lips / accessories
are inherently spared), and always merges the smaller region into the larger one
(so the dominant region keeps its identity).
"""
from __future__ import annotations

import cv2
import numpy as np

# OpenCV 8-bit Lab: L in [0,255] (=L*·255/100), a/b in [0,255] (=ab*+128). Rescale L
# so the Euclidean distance approximates CIE76 ΔE.
_L_SCALE = 100.0 / 255.0


def _mean_lab(labels: np.ndarray, lab: np.ndarray, num_labels: int) -> tuple[np.ndarray, np.ndarray]:
    flat = labels.ravel()
    cnt = np.bincount(flat, minlength=num_labels).astype(np.float64)
    L = np.bincount(flat, weights=lab[:, :, 0].astype(np.float64).ravel(), minlength=num_labels)
    a = np.bincount(flat, weights=lab[:, :, 1].astype(np.float64).ravel(), minlength=num_labels)
    b = np.bincount(flat, weights=lab[:, :, 2].astype(np.float64).ravel(), minlength=num_labels)
    safe = np.maximum(cnt, 1.0)
    mean = np.stack([L / safe, a / safe, b / safe], axis=1)
    return mean, cnt


def _delta_e(m1: np.ndarray, m2: np.ndarray) -> float:
    dL = (m1[0] - m2[0]) * _L_SCALE
    da = m1[1] - m2[1]
    db = m1[2] - m2[2]
    return float(np.sqrt(dL * dL + da * da + db * db))


def plan_insignificant_merge(
    labels: np.ndarray,
    lab_img: np.ndarray,
    num_labels: int,
    stats: np.ndarray,
    kept: list[int],
    area_max: int,
    delta_e_max: float,
    dilate_px: int = 4,
) -> tuple[np.ndarray, int]:
    """Merge visually-insignificant tiny regions into their most colour-similar
    neighbour. Returns (remap2, merged_count). `remap2` is identity except for the
    dissolved labels, which point at the surviving (larger) neighbour. Never maps to
    0, so the union is preserved."""
    mean_lab, areas = _mean_lab(labels, lab_img, num_labels)
    h, w = labels.shape
    kern = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * dilate_px + 1, 2 * dilate_px + 1))

    parent = {int(i): int(i) for i in kept}

    def find(x: int) -> int:
        r = x
        while parent[r] != r:
            r = parent[r]
        while parent[x] != r:
            parent[x], x = r, parent[x]
        return r

    # Smallest regions first: a speck should dissolve into whatever it sits in.
    cands = sorted((int(i) for i in kept if 0 < areas[i] < area_max), key=lambda i: areas[i])
    merged = 0
    for i in cands:
        if find(i) != i:                              # already absorbed into a group
            continue
        x = int(stats[i, cv2.CC_STAT_LEFT]); y = int(stats[i, cv2.CC_STAT_TOP])
        ww = int(stats[i, cv2.CC_STAT_WIDTH]); hh = int(stats[i, cv2.CC_STAT_HEIGHT])
        pad = dilate_px + 2
        x0, y0 = max(0, x - pad), max(0, y - pad)
        x1, y1 = min(w, x + ww + pad), min(h, y + hh + pad)
        sub = labels[y0:y1, x0:x1]
        mask = (sub == i).astype(np.uint8)
        if not mask.any():
            continue
        ring = (cv2.dilate(mask, kern) > 0) & (mask == 0)
        nb = sub[ring]
        nb = nb[nb != 0]
        if nb.size == 0:
            continue
        # colour-closest neighbour group (weight ties toward the longer shared border)
        vals, counts = np.unique(nb, return_counts=True)
        best, best_de = None, 1e9
        ci = mean_lab[i]
        for v, c in zip(vals.tolist(), counts.tolist()):
            r = find(int(v))
            if r == i:
                continue
            de = _delta_e(ci, mean_lab[r])
            if de < best_de:
                best_de, best = de, r
        if best is None or best_de > delta_e_max:
            continue                                  # a real edge / distinct feature -> keep
        # merge the smaller group into the larger, so the dominant region survives
        a, b = find(i), best
        if a == b:
            continue
        if areas[a] <= areas[b]:
            parent[a] = b; areas[b] += areas[a]
        else:
            parent[b] = a; areas[a] += areas[b]
        merged += 1

    remap2 = np.arange(num_labels, dtype=np.int32)
    for i in kept:
        remap2[i] = find(int(i))
    return remap2, merged


def plan_merge(
    labels: np.ndarray,
    num_labels: int,
    stats: np.ndarray,
    keep_area: int,
    width: int,
    height: int,
) -> tuple[np.ndarray, list[int], int, int]:
    """Return (remap, kept_labels, merged_count, removed_count).

    `remap[i]` is where label i's pixels should go: itself (kept), a large
    neighbour (merged), or 0 = line/background (removed).
    """
    areas = stats[:, cv2.CC_STAT_AREA]
    remap = np.arange(num_labels, dtype=np.int32)
    kept = [i for i in range(1, num_labels) if areas[i] >= keep_area]
    kept_set = set(kept)

    merged = 0
    removed = 0
    nbr_kernel = np.ones((3, 3), np.uint8)

    for i in range(1, num_labels):
        if areas[i] >= keep_area:
            continue
        # Crop a 1px-padded window around the small component.
        x = int(stats[i, cv2.CC_STAT_LEFT])
        y = int(stats[i, cv2.CC_STAT_TOP])
        w = int(stats[i, cv2.CC_STAT_WIDTH])
        h = int(stats[i, cv2.CC_STAT_HEIGHT])
        x0, y0 = max(0, x - 1), max(0, y - 1)
        x1, y1 = min(width, x + w + 1), min(height, y + h + 1)
        sub = labels[y0:y1, x0:x1]

        mask = (sub == i).astype(np.uint8)
        border = (cv2.dilate(mask, nbr_kernel) > 0) & (mask == 0)
        neighbours = sub[border]
        neighbours = neighbours[(neighbours != 0) & (neighbours != i)]

        best = None
        if neighbours.size:
            vals, counts = np.unique(neighbours, return_counts=True)
            for idx in np.argsort(-counts):
                v = int(vals[idx])
                if v in kept_set:  # only merge into a large neighbour
                    best = v
                    break

        if best is not None:
            remap[i] = best
            merged += 1
        else:
            remap[i] = 0
            removed += 1

    return remap, sorted(kept), merged, removed
