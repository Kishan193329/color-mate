"""Phase 9 — line gap detector.

Estimates how broken the line layer is: the number of disconnected fragments and
the number of stroke endpoints (pixels with a single 8-neighbour). More
fragments / endpoints means more gaps to repair.
"""
from __future__ import annotations

import cv2
import numpy as np

_NEIGHBORS = np.array([[1, 1, 1], [1, 0, 1], [1, 1, 1]], np.uint8)


def detect(lines: np.ndarray) -> dict:
    n, _ = cv2.connectedComponents(lines, connectivity=8)
    fragments = max(0, n - 1)

    binary = (lines > 0).astype(np.uint8)
    neigh = cv2.filter2D(binary, -1, _NEIGHBORS, borderType=cv2.BORDER_CONSTANT)
    endpoints = int(((binary == 1) & (neigh == 1)).sum())

    return {"fragments": int(fragments), "endpoints": endpoints}
