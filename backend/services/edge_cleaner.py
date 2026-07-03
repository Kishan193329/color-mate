"""Phase 9 — edge cleaner.

Removes weak texture noise and isolated dots from the raw edge layer while
preserving meaningful strokes. Works on a binary "lines" image (white strokes
on black). Component filtering is vectorized via an area lookup table.
"""
from __future__ import annotations

import cv2
import numpy as np


def remove_small_components(lines: np.ndarray, min_area: int) -> np.ndarray:
    n, labels, stats, _ = cv2.connectedComponentsWithStats(lines, connectivity=8)
    keep = stats[:, cv2.CC_STAT_AREA] >= min_area
    keep[0] = False  # background
    return np.where(keep[labels], np.uint8(255), np.uint8(0))


def remove_texture(lines: np.ndarray, open_k: int, min_area: int) -> np.ndarray:
    """Light morphological open + drop sub-threshold components (texture)."""
    if open_k > 1:
        lines = cv2.morphologyEx(lines, cv2.MORPH_OPEN, np.ones((open_k, open_k), np.uint8))
    return remove_small_components(lines, min_area)


def remove_dots(lines: np.ndarray, min_area: int) -> np.ndarray:
    """Drop isolated specks below `min_area`."""
    return remove_small_components(lines, min_area)
