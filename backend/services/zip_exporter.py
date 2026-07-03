"""Stage 6b — Export bundle (real implementation).

Bundles the deliverable artifacts into `result.zip`. Files that are missing on
disk are skipped (and reported back) rather than aborting the export, so a
partial job still yields a usable download.

Returns the list of arcnames actually written.
"""
from __future__ import annotations

import zipfile
from pathlib import Path

# Canonical export set (Phase 3).
DEFAULT_INCLUDE = [
    "original.png",
    "lineart.png",
    "region_mask.png",
    "outline.svg",
    "regions.json",
    "game_config.json",
    "palette.json",
    "preview.png",
    "texture_fill_preview.png",
    "game_preview.png",
    "quality_report.json",
    "border_quality_report.json",
]


def run(job_folder: Path, out_path: Path, include: list[str] | None = None) -> list[str]:
    names = include if include is not None else DEFAULT_INCLUDE
    written: list[str] = []
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for filename in names:
            file_path = job_folder / filename
            if file_path.exists():
                zf.write(file_path, arcname=filename)
                written.append(filename)
    return written
