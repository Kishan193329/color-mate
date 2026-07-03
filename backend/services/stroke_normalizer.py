"""Phase 9 — stroke normalizer.

Turns ragged edges into clean, uniform black strokes: optional smoothing of the
stroke boundary (mode-dependent), then a hard binarization so every stroke is
pure black/white with no antialiased grey.
"""
from __future__ import annotations

import cv2
import numpy as np


def normalize(lines: np.ndarray, smoothing: float) -> np.ndarray:
    if smoothing > 0:
        k = int(round(smoothing * 2)) * 2 + 1  # odd kernel
        blurred = cv2.GaussianBlur(lines, (k, k), smoothing)
        _, lines = cv2.threshold(blurred, 90, 255, cv2.THRESH_BINARY)
    else:
        _, lines = cv2.threshold(lines, 90, 255, cv2.THRESH_BINARY)
    return lines
