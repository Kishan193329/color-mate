"""Phase 6 — palette grouper.

Groups regions whose sampled colors are perceptually similar using **LAB
distance** (CIE76 ΔE), then assigns a `color_id` / `number` per group. This is a
labelling step over already-detected regions — regions come from the line art,
NOT from color clustering, so it is not color-quantization-as-segmentation.

Grouping strength comes from the difficulty profile (`palette_delta`): Easy
groups aggressively (fewer numbers), Hard keeps colors distinct.

Sets `region_data["_palette_v2"]` = [{id, number, color}] and, per region,
`color_id` + `color_number`.
"""
from __future__ import annotations

import cv2
import numpy as np

from config import DEFAULT_DIFFICULTY, DIFFICULTY_PROFILES
from core.artistic_decisions import ArtisticDecisions

DEFAULT_DELTA = 18.0

# Engineering Sprint 1 — QF13 authority seam. When a future sprint sets this True
# and passes the QF13 decisions, the grouper will honour `palette_merge_permission`
# (LOCKED regions keep their own colour; WITHIN_MATERIAL only merges same-material
# regions). Sprint 1 keeps it False so behaviour is byte-for-byte unchanged; the
# read path below is wired and inert.
OBEY_QF13 = False


def _to_lab(rgb: tuple[int, int, int]) -> np.ndarray:
    arr = np.uint8([[[rgb[0], rgb[1], rgb[2]]]])  # RGB
    return cv2.cvtColor(arr, cv2.COLOR_RGB2LAB)[0, 0].astype(np.float64)


def _hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def group(region_data: dict, decisions: ArtisticDecisions | None = None) -> list[dict]:
    profile = region_data.get("_profile") or DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY]
    max_delta = float(profile.get("palette_delta", DEFAULT_DELTA))

    regions = region_data["regions"]
    # Seed groups with the largest regions first for stable numbering.
    order = sorted(range(len(regions)), key=lambda i: -regions[i]["area"])

    groups: list[dict] = []  # {"lab": running mean, "count": n, "rgbs": [...]}
    assign: dict[int, int] = {}

    for i in order:
        rgb = regions[i].get("_rgb") or _hex_to_rgb(regions[i].get("color", "#C8C8C8"))
        lab = _to_lab(rgb)
        best, best_d = -1, 1e9
        for gi, g in enumerate(groups):
            d = float(np.linalg.norm(lab - g["lab"]))
            if d < best_d:
                best_d, best = d, gi
        # QF13 authority seam (inert in Sprint 1): a LOCKED region keeps its own
        # colour and is never merged into a neighbour's group. Read path wired via
        # the artistic-decision bridge; gated off so behaviour is unchanged.
        if OBEY_QF13 and decisions is not None and \
                decisions.palette_merge_permission(regions[i].get("label")) == "LOCKED":
            best = -1
        if best >= 0 and best_d <= max_delta:
            g = groups[best]
            g["lab"] = (g["lab"] * g["count"] + lab) / (g["count"] + 1)
            g["count"] += 1
            g["rgbs"].append(rgb)
            assign[i] = best
        else:
            groups.append({"lab": lab, "count": 1, "rgbs": [rgb]})
            assign[i] = len(groups) - 1

    # Representative color = median of the group's member colors.
    palette: list[dict] = []
    for gi, g in enumerate(groups, start=1):
        med = np.median(np.array(g["rgbs"]), axis=0).astype(int)
        hex_color = "#{:02X}{:02X}{:02X}".format(int(med[0]), int(med[1]), int(med[2]))
        palette.append({"id": gi, "number": gi, "color": hex_color})

    for i, gi in assign.items():
        regions[i]["color_id"] = gi + 1
        regions[i]["color_number"] = gi + 1

    region_data["_palette_v2"] = palette
    return palette
