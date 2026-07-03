"""Phase 5 — label optimizer (upgrade of label_placement).

Same distance-transform "largest inscribed circle" placement, but driven by the
difficulty profile: Easy hides more small regions and renders bigger numbers;
Hard labels smaller regions with smaller numbers.

Attaches to each region: `label_pos`, `visible`, `_label_r`. Stores the
profile's label scale on `region_data["_label_scale"]` for the preview.
"""
from __future__ import annotations

import cv2
import numpy as np

from config import DEFAULT_DIFFICULTY, DIFFICULTY_PROFILES

MIN_LABEL_RADIUS = 6.0


def run(region_data: dict) -> dict:
    profile = region_data.get("_profile") or DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY]
    labels: np.ndarray = region_data["_label_map"]
    min_area = int(profile.get("label_min_area", 500))
    region_data["_label_scale"] = float(profile.get("label_scale", 1.0))

    for region in region_data["regions"]:
        mask = np.uint8(labels == region["label"])
        if mask.any():
            dist = cv2.distanceTransform(mask, cv2.DIST_L2, 3)
            _, max_val, _, max_loc = cv2.minMaxLoc(dist)
            region["label_pos"] = {"x": int(max_loc[0]), "y": int(max_loc[1])}
            region["_label_r"] = float(max_val)
            region["visible"] = bool(
                region["area"] >= min_area and max_val >= MIN_LABEL_RADIUS
            )
        else:
            region["label_pos"] = {"x": region["cx"], "y": region["cy"]}
            region["_label_r"] = 0.0
            region["visible"] = False

    return region_data
