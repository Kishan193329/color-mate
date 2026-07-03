"""Phase 6 — texture fill preview.

Renders `texture_fill_preview.png`: the **original image texture revealed inside
each region**, clipped to the region masks, with clean black region outlines.
This keeps the original artwork look (no recoloring, no quantization). The
interactive editor reveals regions one at a time; this is the fully-revealed
reference image.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from services import texture_mapper

OUTLINE_THICKNESS = 2


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, dtype=bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def render(region_data: dict, out_path: Path) -> Path:
    base = texture_mapper.clipped_original(region_data)  # texture clipped to regions
    labels = region_data["_label_map"]

    outline = (_boundaries(labels).astype(np.uint8)) * 255
    if OUTLINE_THICKNESS > 1:
        outline = cv2.dilate(outline, np.ones((OUTLINE_THICKNESS, OUTLINE_THICKNESS), np.uint8))
    base[outline > 0] = (0, 0, 0)  # clean black borders

    cv2.imwrite(str(out_path), base)
    return out_path
