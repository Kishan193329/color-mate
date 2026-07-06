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
from core.artistic_decisions import ArtisticDecisions

MIN_LABEL_RADIUS = 6.0

# Engineering Sprint 1 — QF13 authority seam. When a future sprint sets this True
# and passes the QF13 decisions, per-region number visibility will honour
# `numbering_sensitivity` (HIGH -> keep small-region numbers that the size floor
# would hide; LOW -> hide more). Sprint 1 keeps it False so visibility is
# byte-for-byte unchanged; the read path below is wired and inert.
OBEY_QF13 = False


def run(region_data: dict, decisions: ArtisticDecisions | None = None) -> dict:
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
            visible = bool(region["area"] >= min_area and max_val >= MIN_LABEL_RADIUS)
            # QF13 authority seam (inert in Sprint 1): a HIGH-sensitivity region
            # (e.g. a facial feature) keeps its number even below the size floor,
            # provided a label actually fits. Read path wired; gated off so the
            # visible flags are unchanged.
            if OBEY_QF13 and decisions is not None \
                    and decisions.numbering_sensitivity(region["label"]) == "HIGH" \
                    and max_val >= MIN_LABEL_RADIUS:
                visible = True
            region["visible"] = visible
        else:
            region["label_pos"] = {"x": region["cx"], "y": region["cy"]}
            region["_label_r"] = 0.0
            region["visible"] = False

    return region_data
