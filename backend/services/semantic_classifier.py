"""Phase 7 — semantic classifier.

Assigns each region a `semantic_label` and a `region_type`. Uses the local
rules engine by default. `SEMANTIC_MODE=ai` is honoured *if* an AI backend is
available; otherwise it transparently falls back to rules so local processing
never breaks and RunPod is not required.
"""
from __future__ import annotations

from collections import defaultdict

from services import semantic_rules as R


def _ai_available() -> bool:
    """No model is bundled yet — the AI path always falls back to rules."""
    return False


def resolve_mode(mode: str) -> str:
    if mode == "ai" and _ai_available():
        return "ai"
    return "rules"


def classify(regions: list[dict], width: int, height: int, mode: str = "rules") -> str:
    """Annotate each region in place. Returns the effective mode used."""
    effective = resolve_mode(mode)

    # Pass 1 — coarse label from color + geometry.
    for region in regions:
        rgb = R.get_rgb(region)
        bbox = R.get_bbox(region)
        area = int(region.get("area", bbox[2] * bbox[3]))
        region["semantic_label"] = R.classify_label(rgb, area, bbox, width, height)

    # Pass 2 — region role (main/shadow/highlight/detail) relative to its label
    # group's median luminance and area.
    groups: dict[str, list[dict]] = defaultdict(list)
    for region in regions:
        groups[region["semantic_label"]].append(region)

    for label, members in groups.items():
        lums = sorted(R.luminance(R.get_rgb(r)) for r in members)
        med_lum = lums[len(lums) // 2]
        areas = sorted(int(r.get("area", 1)) for r in members)
        med_area = areas[len(areas) // 2]
        for region in members:
            region["region_type"] = R.region_type(
                label, R.get_rgb(region), med_lum, int(region.get("area", 1)), med_area
            )

    return effective
