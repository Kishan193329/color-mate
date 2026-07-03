"""Stage 6 — Preview render (real implementation).

Composites `preview.png`:
- the **original working image** underneath (never recolored),
- a **black region-boundary outline** overlay (from the label map), and
- **optional number labels** drawn at each visible region's `label_pos`.

Only thin black outlines and small numbers are painted over the photo — no fill,
no recoloring.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

OUTLINE_THICKNESS = 2
FONT = cv2.FONT_HERSHEY_SIMPLEX


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, dtype=bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def _font_scale(radius: float, mult: float) -> float:
    return float(np.clip(radius / 22.0 * mult, 0.4, 1.8))


def run(mask_path: Path, lineart_path: Path, region_data: dict, out_path: Path) -> Path:
    base = region_data.get("_preprocessed_bgr")
    if base is None:
        base = cv2.imread(str(mask_path), cv2.IMREAD_COLOR)
    base = base.copy()

    labels = region_data.get("_label_map")
    if labels is not None:
        edge = _boundaries(labels)
    else:
        gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)
        edge = gray < 100

    outline = (edge.astype(np.uint8)) * 255
    if OUTLINE_THICKNESS > 1:
        outline = cv2.dilate(outline, np.ones((OUTLINE_THICKNESS, OUTLINE_THICKNESS), np.uint8))
    base[outline > 0] = (0, 0, 0)

    # Optional number labels: the color number a player should paint.
    label_mult = float(region_data.get("_label_scale", 1.0))
    for region in region_data["regions"]:
        if not region.get("visible"):
            continue
        text = str(region.get("color_id", region.get("number", "")))
        pos = region.get("label_pos", {"x": region["cx"], "y": region["cy"]})
        scale = _font_scale(region.get("_label_r", 12.0), label_mult)
        thickness = max(1, int(round(scale * 2)))
        (tw, th), _ = cv2.getTextSize(text, FONT, scale, thickness)
        org = (int(pos["x"] - tw / 2), int(pos["y"] + th / 2))
        # White halo for legibility, then dark number on top.
        cv2.putText(base, text, org, FONT, scale, (255, 255, 255), thickness + 2, cv2.LINE_AA)
        cv2.putText(base, text, org, FONT, scale, (20, 20, 20), thickness, cv2.LINE_AA)

    cv2.imwrite(str(out_path), base)
    return out_path
