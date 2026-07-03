"""Phase 5 — dust / noise removal.

Removes isolated speckle ("dust") from the line layer before region detection,
so stray dots don't become spurious regions or break flood fill. Reports how
many line fragments were eliminated.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np


def remove_noise(lineart_path: Path, out_path: Path, profile: dict) -> dict:
    gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    lines = cv2.bitwise_not(bw)  # strokes as white foreground

    before, _ = cv2.connectedComponents(lines, connectivity=8)

    k = max(1, int(profile.get("dust_kernel", 2)))
    kernel = np.ones((k, k), np.uint8)
    cleaned = cv2.morphologyEx(lines, cv2.MORPH_OPEN, kernel)

    after, _ = cv2.connectedComponents(cleaned, connectivity=8)
    dust_removed = max(0, before - after)

    cv2.imwrite(str(out_path), cv2.bitwise_not(cleaned))
    return {"dust_removed": int(dust_removed)}
