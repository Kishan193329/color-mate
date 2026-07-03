"""Phase 6 — palette (flat) game preview.

Renders `game_preview.png`: every region filled with its **grouped palette
color** (the simplified color-by-number look), black outlines, and the color
number drawn inside each visible region. Colors come from the LAB-grouped
palette built by `palette_grouper`.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

FONT = cv2.FONT_HERSHEY_SIMPLEX
OUTLINE_THICKNESS = 2
LINE_COLOR_RGB = (40, 40, 40)


def _hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, dtype=bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def render(region_data: dict, out_path: Path) -> Path:
    labels = region_data["_label_map"]
    palette = region_data.get("_palette_v2", [])
    cid_to_rgb = {p["id"]: _hex_to_rgb(p["color"]) for p in palette}

    num = region_data["_color_lut"].shape[0]
    lut = np.zeros((num, 3), dtype=np.uint8)  # RGB
    lut[0] = LINE_COLOR_RGB
    for region in region_data["regions"]:
        lut[region["label"]] = cid_to_rgb.get(region.get("color_id"), (200, 200, 200))

    base = cv2.cvtColor(lut[labels], cv2.COLOR_RGB2BGR)

    outline = (_boundaries(labels).astype(np.uint8)) * 255
    if OUTLINE_THICKNESS > 1:
        outline = cv2.dilate(outline, np.ones((OUTLINE_THICKNESS, OUTLINE_THICKNESS), np.uint8))
    base[outline > 0] = (0, 0, 0)

    label_mult = float(region_data.get("_label_scale", 1.0))
    for region in region_data["regions"]:
        if not region.get("visible"):
            continue
        text = str(region.get("color_number", region.get("color_id", "")))
        pos = region.get("label_pos", {"x": region["cx"], "y": region["cy"]})
        scale = float(np.clip(region.get("_label_r", 12.0) / 22.0 * label_mult, 0.4, 1.8))
        thickness = max(1, int(round(scale * 2)))
        (tw, th), _ = cv2.getTextSize(text, FONT, scale, thickness)
        org = (int(pos["x"] - tw / 2), int(pos["y"] + th / 2))
        cv2.putText(base, text, org, FONT, scale, (255, 255, 255), thickness + 2, cv2.LINE_AA)
        cv2.putText(base, text, org, FONT, scale, (20, 20, 20), thickness, cv2.LINE_AA)

    cv2.imwrite(str(out_path), base)
    return out_path
