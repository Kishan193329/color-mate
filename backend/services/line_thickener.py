"""Phase 9 — line thickener.

Slightly thickens thin strokes so the coloring page reads cleanly and regions
stay well separated. `thickness` ~1.0 leaves 1px edges untouched; higher values
dilate with a larger elliptical kernel.
"""
from __future__ import annotations

import cv2
import numpy as np


def thicken(lines: np.ndarray, thickness: float) -> np.ndarray:
    extra = int(round(thickness - 1.0))
    if extra <= 0:
        return lines
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * extra + 1, 2 * extra + 1))
    return cv2.dilate(lines, kernel)
