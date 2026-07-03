"""Stage 5b — Number label placement (real implementation).

Finds the best point to drop each region's number using a **distance
transform**: the pixel furthest from any boundary is the center of the largest
inscribed circle, i.e. the roomiest spot — robust for concave and holed shapes.

Attaches to every region:
- `label_pos` = {"x", "y"}  — where the number should be drawn
- `visible`   = bool        — False when the region is too small to label
- `_label_r`  = float       — inscribed radius (used to size preview text)
"""
from __future__ import annotations

import cv2
import numpy as np

# A region is labelled only if it's both big enough and has enough open space
# for a number to sit legibly.
MIN_LABEL_AREA = 400
MIN_LABEL_RADIUS = 6.0


def run(region_data: dict) -> dict:
    labels: np.ndarray = region_data["_label_map"]

    for region in region_data["regions"]:
        mask = np.uint8(labels == region["label"])
        if mask.any():
            dist = cv2.distanceTransform(mask, cv2.DIST_L2, 3)
            _, max_val, _, max_loc = cv2.minMaxLoc(dist)
            region["label_pos"] = {"x": int(max_loc[0]), "y": int(max_loc[1])}
            region["_label_r"] = float(max_val)
            region["visible"] = bool(
                region["area"] >= MIN_LABEL_AREA and max_val >= MIN_LABEL_RADIUS
            )
        else:
            # Should not happen for a kept region, but stay safe.
            region["label_pos"] = {"x": region["cx"], "y": region["cy"]}
            region["_label_r"] = 0.0
            region["visible"] = False

    return region_data
