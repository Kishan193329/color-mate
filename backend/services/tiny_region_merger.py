"""Phase 5 — tiny region merger.

After connected-components labelling, regions below the area floor are either
**merged into the adjacent large region** they share the most border with, or
dropped if they have no large neighbour (pure speckle). Returns a label remap
plus counts, leaving the heavy geometry recompute to `region_detector`.
"""
from __future__ import annotations

import cv2
import numpy as np


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
