"""Phase 5 — quality analyzer.

Scores how "clean" the generated color-by-number is and produces a human
readable report. The score (0-100) rewards few leftover tiny regions and a
well-connected line layer, and surfaces what cleanup did (gaps repaired, tiny
regions merged/removed, dust removed).
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np


def build_report(
    region_data: dict,
    repair_metrics: dict,
    noise_metrics: dict,
    profile_name: str,
) -> dict:
    regions = region_data.get("regions", [])
    areas = [r["area"] for r in regions]
    avg_area = int(np.mean(areas)) if areas else 0

    removed = int(region_data.get("small_regions_removed", 0))
    merged = int(region_data.get("tiny_regions_merged", 0))
    gaps = int(repair_metrics.get("gaps_repaired", 0))
    dust = int(noise_metrics.get("dust_removed", 0))
    fragments = int(repair_metrics.get("line_fragments", 0))

    # Regions still smaller than the label floor read as clutter.
    floor = region_data.get("_profile", {}).get("label_min_area", 500)
    clutter = sum(1 for a in areas if a < floor)
    count = max(1, len(regions))
    clutter_ratio = clutter / count
    detected = len(regions) + removed + merged
    tiny_ratio = (removed + merged) / detected if detected else 0.0

    score = 100.0
    score -= clutter_ratio * 40.0        # leftover small regions
    score -= tiny_ratio * 15.0           # how noisy the raw detection was
    score -= min(20.0, fragments / count * 12.0)  # broken / disconnected lines
    score = int(round(max(30.0, min(100.0, score))))

    notes: list[str] = []
    if gaps:
        notes.append(f"Closed {gaps} line gap(s).")
    if merged:
        notes.append(f"Merged {merged} tiny region(s) into neighbours.")
    if removed:
        notes.append(f"Removed {removed} dust region(s).")
    if dust:
        notes.append(f"Cleared {dust} noise speck(s).")
    if not notes:
        notes.append("Line art was already clean.")

    return {
        "version": 1,
        "profile": profile_name,
        "score": score,
        "regions_count": len(regions),
        "tiny_regions_removed": removed,
        "tiny_regions_merged": merged,
        "gaps_repaired": gaps,
        "dust_removed": dust,
        "line_fragments": fragments,
        "avg_region_area": avg_area,
        "small_regions_remaining": int(clutter),
        "notes": notes,
    }


def write_report(report: dict, out_path: Path) -> Path:
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return out_path


def report_from_files(job_folder: Path) -> dict | None:
    """Best-effort report for a job processed before Phase 5 (legacy)."""
    regions_path = job_folder / "regions.json"
    if not regions_path.exists():
        return None
    data = json.loads(regions_path.read_text(encoding="utf-8"))
    regions = data.get("regions", [])
    areas = [r.get("area", 0) for r in regions]
    avg_area = int(np.mean(areas)) if areas else 0
    return {
        "version": 1,
        "profile": "legacy",
        "score": 70,
        "regions_count": len(regions),
        "tiny_regions_removed": 0,
        "tiny_regions_merged": 0,
        "gaps_repaired": 0,
        "dust_removed": 0,
        "line_fragments": 0,
        "avg_region_area": avg_area,
        "small_regions_remaining": 0,
        "notes": ["Generated before Phase 5 — run Auto Clean for a full report."],
    }
