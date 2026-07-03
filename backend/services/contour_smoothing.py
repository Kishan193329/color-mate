"""Phase 5 — contour smoothing & simplification.

Two steps that make borders look hand-drawn rather than pixelated, without
distorting the shape:
1. `approxPolyDP` simplification (epsilon scales with perimeter), and
2. an optional Laplacian-style point average to soften jagged edges.

Used by `svg_generator`; the strength is profile-driven (more for Easy, none
for Hard).
"""
from __future__ import annotations

import cv2
import numpy as np

EPS_MIN = 1.0


def _average_points(points: np.ndarray, passes: int) -> np.ndarray:
    pts = points.reshape(-1, 2).astype(np.float64)
    if len(pts) < 5:
        return points
    for _ in range(passes):
        prev = np.roll(pts, 1, axis=0)
        nxt = np.roll(pts, -1, axis=0)
        pts = (prev + 2.0 * pts + nxt) / 4.0  # closed-curve smoothing
    return pts.reshape(-1, 1, 2).astype(np.int32)


def process(contour: np.ndarray, eps_frac: float, passes: int) -> np.ndarray:
    """Simplify then (optionally) smooth a single contour."""
    perimeter = cv2.arcLength(contour, True)
    eps = max(EPS_MIN, eps_frac * perimeter)
    approx = cv2.approxPolyDP(contour, eps, True)
    if passes > 0:
        approx = _average_points(approx, passes)
    return approx
