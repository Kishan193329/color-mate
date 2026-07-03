"""Phase 7 — object detector.

Infers the broad object each region belongs to (the `parent_object`) from the
mix of semantic labels present. In rules mode this is derived from the labels
the classifier produced; an AI detector could later replace `detect` with real
bounding-box detection without changing the rest of the pipeline.

Returns a small summary of detected objects.
"""
from __future__ import annotations

from collections import defaultdict

from services import semantic_rules as R


def detect(regions: list[dict], mode: str = "rules") -> list[dict]:
    labels = {r.get("semantic_label") for r in regions}

    # Primary subject inferred from the labels present.
    if R.SKIN in labels:
        primary = "person"
    elif R.FUR in labels or R.HAIR in labels:
        primary = "animal"
    elif R.FLOWER in labels:
        primary = "flower"
    else:
        primary = "scene"

    counts: dict[str, int] = defaultdict(int)
    for region in regions:
        label = region.get("semantic_label")
        if label == R.BACKGROUND:
            parent = "background"
        elif label == R.FLOWER and primary != "flower":
            parent = "decoration"
        else:
            parent = primary
        region["parent_object"] = parent
        counts[parent] += 1

    return [{"name": name, "region_count": n} for name, n in counts.items()]
