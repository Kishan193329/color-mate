"""Phase 9 — line-art quality score.

Rates the generated line art (0-100). A good coloring-page line art is well
connected (few fragments), not too dense (not noisy) nor too sparse, and free of
isolated dots.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np


def score(lineart_path: Path) -> dict:
    gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
    height, width = gray.shape[:2]
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    lines = cv2.bitwise_not(bw)  # strokes white

    total = float(lines.size)
    density = float((lines > 0).sum()) / total

    n, _, stats, _ = cv2.connectedComponentsWithStats(lines, connectivity=8)
    fragments = max(0, n - 1)
    dots = int(np.sum(stats[1:, cv2.CC_STAT_AREA] <= 3)) if n > 1 else 0
    megapixels = max(0.1, total / 1_000_000.0)
    frags_per_mp = fragments / megapixels

    s = 100.0
    if density > 0.12:                       # too busy / noisy
        s -= min(35.0, (density - 0.12) * 300.0)
    if density < 0.008:                      # nearly empty
        s -= 25.0
    s -= min(25.0, frags_per_mp * 0.015)     # disconnected strokes
    s -= min(15.0, dots * (1.0 / megapixels) * 0.01)
    final = int(round(max(30.0, min(100.0, s))))

    return {
        "version": 1,
        "score": final,
        "line_density": round(density, 4),
        "fragments": int(fragments),
        "isolated_dots": dots,
        "width": int(width),
        "height": int(height),
    }


def write(report: dict, out_path: Path) -> Path:
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return out_path
