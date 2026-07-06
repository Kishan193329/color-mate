"""Stage 5a — Vector outline (real implementation).

Traces each detected region's contour from the connected-components label map
and emits **one closed SVG `<path>` per region** (every subpath ends in `Z`).
Regions with holes (e.g. a background that wraps the shapes) keep their holes as
additional `Z`-terminated subpaths so even-odd fill works for click-to-fill.

Side effects on `region_data`:
- attaches `region["path"]` (the `d` attribute) to every region, so
  `json_generator` can reuse the exact same geometry, and
- sets `region_data["_svg_path_count"]`.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from config import DEFAULT_DIFFICULTY, DIFFICULTY_PROFILES
from core.artistic_decisions import ArtisticDecisions
from services import contour_smoothing

STROKE = "#111111"
STROKE_WIDTH = 2

# Engineering Sprint 1 — QF13 authority seam. When a future sprint sets this True
# and passes the QF13 decisions, per-region simplification will honour
# `svg_precision` (HIGH -> crisper eps/more passes preserved; LOW -> stronger
# simplification). Sprint 1 keeps it False so the emitted geometry is byte-for-byte
# unchanged; the read path below is wired and inert.
OBEY_QF13 = False
# svg_precision -> (eps multiplier, passes delta). Applied only when OBEY_QF13.
_PRECISION_TUNE = {"HIGH": (0.6, +0), "MEDIUM": (1.0, 0), "LOW": (1.6, 0)}


def _contour_to_subpath(contour: np.ndarray) -> str:
    pts = contour.reshape(-1, 2)
    if len(pts) < 3:
        return ""
    head = f"M {int(pts[0][0])} {int(pts[0][1])}"
    body = " ".join(f"L {int(x)} {int(y)}" for x, y in pts[1:])
    return f"{head} {body} Z"


def build_region_path(labels: np.ndarray, label: int, eps_frac: float, passes: int) -> str:
    """Return the SVG `d` string (one or more `Z`-closed subpaths) for a region."""
    mask = np.uint8(labels == label) * 255
    # RETR_CCOMP: outer boundary + holes (holes become inner subpaths).
    contours, _ = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    subpaths: list[str] = []
    for contour in contours:
        smoothed = contour_smoothing.process(contour, eps_frac, passes)
        sub = _contour_to_subpath(smoothed)
        if sub:
            subpaths.append(sub)
    return " ".join(subpaths)


def run(region_data: dict, out_path: Path,
        decisions: ArtisticDecisions | None = None) -> Path:
    labels: np.ndarray = region_data["_label_map"]
    width = region_data["width"]
    height = region_data["height"]
    profile = region_data.get("_profile") or DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY]
    eps_frac = float(profile.get("smooth_eps_frac", 0.0015))
    passes = int(profile.get("smooth_passes", 1))

    parts: list[str] = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
        f'height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect width="{width}" height="{height}" fill="#ffffff"/>',
        f'<g fill="none" stroke="{STROKE}" stroke-width="{STROKE_WIDTH}" '
        f'stroke-linejoin="round" fill-rule="evenodd">',
    ]

    path_count = 0
    for region in region_data["regions"]:
        r_eps, r_passes = eps_frac, passes
        # QF13 authority seam (inert in Sprint 1): tune per-region simplification
        # to the brain's svg_precision. Read path wired via the artistic-decision
        # bridge; gated off so the geometry is unchanged.
        if OBEY_QF13 and decisions is not None:
            mult, dp = _PRECISION_TUNE.get(
                decisions.svg_precision(region["label"]), (1.0, 0))
            r_eps, r_passes = eps_frac * mult, max(0, passes + dp)
        d = build_region_path(labels, region["label"], r_eps, r_passes)
        region["path"] = d  # shared with json_generator
        if not d:
            continue
        parts.append(
            f'<path id="region-{region["number"]}" '
            f'data-region-id="{region["number"]}" d="{d}"/>'
        )
        path_count += 1

    parts.append("</g>")
    parts.append("</svg>")
    out_path.write_text("\n".join(parts), encoding="utf-8")

    region_data["_svg_path_count"] = path_count
    return out_path
