"""Phase 6 — texture mapper.

Helpers that clip the original working image to the detected region areas. The
texture is masked by the label map so it can never bleed past a region boundary
(the line/background label stays white). Used by `texture_preview_renderer` and
available for future per-region texture export.
"""
from __future__ import annotations

import numpy as np


def region_alpha(region_data: dict) -> np.ndarray:
    """uint8 alpha: 255 inside any kept region, 0 on lines/background/dropped."""
    labels = region_data["_label_map"]
    return np.where(labels > 0, 255, 0).astype(np.uint8)


def clipped_original(region_data: dict) -> np.ndarray:
    """BGR image with original texture inside regions and white elsewhere."""
    img = region_data["_preprocessed_bgr"]
    labels = region_data["_label_map"]
    out = np.full_like(img, 255)
    mask = labels > 0
    out[mask] = img[mask]
    return out


def clip_region(region_data: dict, label: int) -> np.ndarray:
    """BGR image showing only a single region's texture (white elsewhere)."""
    img = region_data["_preprocessed_bgr"]
    labels = region_data["_label_map"]
    out = np.full_like(img, 255)
    mask = labels == label
    out[mask] = img[mask]
    return out
