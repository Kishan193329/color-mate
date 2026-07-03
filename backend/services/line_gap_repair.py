"""Phase 9 — line gap repair.

Closes small gaps so strokes become continuous (and regions fully enclosed).
`strength` (0..1) scales the closing kernel; stronger settings also add a light
dilation to bridge stubborn hairline breaks. Reports how many fragments merged.
"""
from __future__ import annotations

import cv2
import numpy as np


def _fragments(lines: np.ndarray) -> int:
    n, _ = cv2.connectedComponents(lines, connectivity=8)
    return max(0, n - 1)


def repair(lines: np.ndarray, strength: float) -> tuple[np.ndarray, dict]:
    before = _fragments(lines)

    k = 1 + int(round(strength * 6))  # 1..7
    if k % 2 == 0:
        k += 1
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
    closed = cv2.morphologyEx(lines, cv2.MORPH_CLOSE, kernel, iterations=1)
    if strength > 0.6:
        closed = cv2.dilate(closed, np.ones((2, 2), np.uint8))

    after = _fragments(closed)
    return closed, {"gaps_repaired": max(0, before - after), "fragments": after}
