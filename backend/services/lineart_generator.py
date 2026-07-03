"""Stage 2 — Creating clean line art (real OpenCV implementation).

    preprocessed → grayscale → median blur → adaptive threshold → despeckle

Produces clean black line art (black strokes on a white page). The line art is
the single source of truth for region boundaries downstream — regions are the
white areas enclosed by these black lines.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

# Adaptive-threshold window. Larger block = thicker, more forgiving strokes.
BLOCK_SIZE = 9  # must be odd
C = 7  # constant subtracted from the local mean
DESPECKLE_KERNEL = 2  # removes isolated 1-2px noise dots


def run(preprocessed_path: Path, out_path: Path, profile: dict | None = None) -> Path:
    block = int(profile.get("lineart_block", BLOCK_SIZE)) if profile else BLOCK_SIZE
    if block % 2 == 0:
        block += 1  # adaptiveThreshold requires an odd block size
    c = int(profile.get("lineart_c", C)) if profile else C

    img = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Median blur tames sensor/JPEG noise so the threshold doesn't pick it up.
    gray = cv2.medianBlur(gray, 5)

    # Adaptive threshold: white background (255), black lines (0). Robust to
    # uneven lighting because the threshold is local. Block/C come from the
    # difficulty profile (Hard = smaller block/C = more detail).
    lineart = cv2.adaptiveThreshold(
        gray,
        255,
        cv2.ADAPTIVE_THRESH_MEAN_C,
        cv2.THRESH_BINARY,
        block,
        c,
    )

    # Despeckle: open the line layer (lines as foreground) to drop tiny dots.
    lines = cv2.bitwise_not(lineart)  # strokes -> 255
    kernel = np.ones((DESPECKLE_KERNEL, DESPECKLE_KERNEL), np.uint8)
    lines = cv2.morphologyEx(lines, cv2.MORPH_OPEN, kernel)
    clean = cv2.bitwise_not(lines)  # back to black-on-white

    cv2.imwrite(str(out_path), clean)
    return out_path
