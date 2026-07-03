"""Phase 7 — region hierarchy & semantic grouping.

Builds the scene → object → part → region tree and assigns a `group_id` to each
region. Regions sharing a (parent_object, semantic_label) "part" are grouped —
which is exactly the semantic merge behaviour the rules call for:

    tiny skin shadow  -> same group as the skin part
    eye / small detail -> folds into its part's group
    hair / fur strokes -> share the hair part group
    background pieces  -> one background group
    decorations        -> their own decoration group (kept off skin)

`apply_all` is the single entry point used by both the pipeline and the
/smart-group endpoint.
"""
from __future__ import annotations

from services import object_detector, semantic_classifier


def build(regions: list[dict], objects: list[dict], width: int, height: int) -> dict:
    """Assign group_id per (parent_object, semantic_label) and return the tree."""
    groups: dict[tuple[str, str], int] = {}
    next_id = 0
    # Largest regions seed groups first for stable numbering.
    for region in sorted(regions, key=lambda r: -int(r.get("area", 0))):
        key = (
            region.get("parent_object", "scene"),
            region.get("semantic_label", "detail"),
        )
        if key not in groups:
            next_id += 1
            groups[key] = next_id
        region["group_id"] = groups[key]

    members: dict[int, list[int]] = {}
    for region in regions:
        members.setdefault(region["group_id"], []).append(region.get("id", region.get("number")))

    obj_parts: dict[str, list[dict]] = {}
    for (parent, label), gid in groups.items():
        obj_parts.setdefault(parent, []).append(
            {"part": label, "group_id": gid, "region_count": len(members.get(gid, []))}
        )

    return {
        "scene": {
            "objects": [
                {"name": name, "parts": parts} for name, parts in obj_parts.items()
            ]
        }
    }


def apply_all(regions: list[dict], width: int, height: int, mode: str = "rules") -> dict:
    """Classify → detect objects → build hierarchy. Annotates regions in place."""
    effective = semantic_classifier.classify(regions, width, height, mode)
    objects = object_detector.detect(regions, mode)
    hierarchy = build(regions, objects, width, height)
    group_count = len({r.get("group_id") for r in regions})
    return {
        "mode": effective,
        "objects": objects,
        "hierarchy": hierarchy,
        "group_count": group_count,
    }
