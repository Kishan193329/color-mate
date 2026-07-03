"""Stage 4b — Region mask rendering (real OpenCV implementation).

Renders `region_mask.png` directly from the connected-components label map:
every kept region is filled with its sampled representative color, and the
line/background skeleton renders dark. This is a visualization of the detected
regions, derived entirely from the line art — it does not recolor the original.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np


def run(region_data: dict, out_path: Path) -> Path:
    labels: np.ndarray = region_data["_label_map"]
    lut: np.ndarray = region_data["_color_lut"]  # RGB

    # Map every pixel's label to its color in one vectorized lookup.
    rgb = lut[labels]  # H x W x 3, RGB
    bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    cv2.imwrite(str(out_path), bgr)
    return out_path
