"""Phase 5 — gap repair (upgrade of gap_closer).

Detects broken/open lines and closes small gaps so every region is fully
enclosed before detection. We measure the number of disconnected line fragments
before and after closing; the drop is reported as `gaps_repaired`.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np


def _fragments(lines: np.ndarray) -> int:
    n, _ = cv2.connectedComponents(lines, connectivity=8)
    return max(0, n - 1)  # minus background


def repair(lineart_path: Path, out_path: Path, profile: dict) -> dict:
    gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    lines = cv2.bitwise_not(bw)  # strokes as white foreground

    before = _fragments(lines)

    k = max(1, int(profile.get("gap_kernel", 3)))
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
    closed = cv2.morphologyEx(lines, cv2.MORPH_CLOSE, kernel, iterations=1)
    dilate_iters = int(profile.get("gap_dilate", 1))
    if dilate_iters:
        closed = cv2.dilate(closed, kernel, iterations=dilate_iters)

    after = _fragments(closed)
    gaps_repaired = max(0, before - after)

    cv2.imwrite(str(out_path), cv2.bitwise_not(closed))
    return {
        "gaps_repaired": int(gaps_repaired),
        "line_fragments_before": int(before),
        "line_fragments": int(after),
    }
