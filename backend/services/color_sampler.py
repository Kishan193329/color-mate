"""Phase 6 — color sampler.

Samples each region's **median color** from the original working image inside
its detected mask. Median (not mean) is robust to outliers like the dark line
pixels at a region's edge. This is sampling, not quantization — the original
image is never recolored.

Attaches to every region:
- `sampled_color` = "#RRGGBB"
- `_rgb`          = (r, g, b)  (used by the palette grouper)
"""
from __future__ import annotations

import numpy as np


def sample(region_data: dict) -> dict:
    img = region_data["_preprocessed_bgr"]   # BGR
    labels = region_data["_label_map"]

    for region in region_data["regions"]:
        x, y, w, h = region["x"], region["y"], region["w"], region["h"]
        sub = img[y : y + h, x : x + w]
        sub_labels = labels[y : y + h, x : x + w]
        pixels = sub[sub_labels == region["label"]]
        if pixels.size:
            med = np.median(pixels, axis=0)  # BGR
            b, g, r = int(med[0]), int(med[1]), int(med[2])
        else:
            b = g = r = 200
        region["_rgb"] = (r, g, b)
        region["sampled_color"] = "#{:02X}{:02X}{:02X}".format(r, g, b)

    return region_data
