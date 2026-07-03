"""Stage 3 — Closing gaps (real OpenCV implementation).

Line art often has small breaks where strokes don't quite meet. Those breaks
let the region flood-fill leak between cells. We morphologically close (and
lightly dilate) the *line* layer so strokes become continuous, then write the
result back as clean black-on-white line art.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

CLOSE_KERNEL = 3  # structuring element size for the close op
DILATE_ITERS = 1  # extra bridging for stubborn 1px gaps


def run(lineart_path: Path, out_path: Path) -> Path:
    gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)

    # Operate on the line layer (strokes as white foreground).
    lines = cv2.bitwise_not(bw)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (CLOSE_KERNEL, CLOSE_KERNEL))

    # Close bridges gaps without eating the strokes; the small dilation joins
    # any remaining hairline breaks so every region is fully enclosed.
    lines = cv2.morphologyEx(lines, cv2.MORPH_CLOSE, kernel, iterations=1)
    if DILATE_ITERS:
        lines = cv2.dilate(lines, kernel, iterations=DILATE_ITERS)

    result = cv2.bitwise_not(lines)
    cv2.imwrite(str(out_path), result)
    return out_path
