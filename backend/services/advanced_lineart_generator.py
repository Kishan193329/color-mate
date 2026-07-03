"""Phase 9 — advanced line-art generator.

Turns a photo into a clean coloring-page line art via the full pipeline:

    grayscale → denoise → strong structural edges (Canny [+ adaptive]) →
    remove weak texture noise → normalize strokes → thicken → close gaps →
    remove isolated dots → export black-on-white

Behaviour is controlled by a line-art *mode* (see config.LINEART_MODES) plus
optional slider overrides (thickness / detail / gap / remove_noise). Line art
remains the source of truth — this only produces a cleaner line art; regions are
still detected locally from it downstream.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from config import DEFAULT_LINEART_MODE, LINEART_MODES
from services import (
    edge_cleaner,
    line_gap_detector,
    line_gap_repair,
    line_thickener,
    stroke_normalizer,
)


def resolve_params(settings: dict | None) -> dict:
    """Merge a job's saved settings (mode + sliders) onto the mode defaults."""
    settings = settings or {}
    mode = settings.get("mode") or DEFAULT_LINEART_MODE
    if mode not in LINEART_MODES:
        mode = DEFAULT_LINEART_MODE
    params = dict(LINEART_MODES[mode])
    params["mode"] = mode
    for key in ("thickness", "detail", "gap", "remove_noise"):
        if settings.get(key) is not None:
            params[key] = settings[key]
    return params


def _derived_areas(detail: float, width: int, height: int) -> tuple[int, int]:
    """Min component area for texture removal and dot removal from `detail`."""
    area = width * height
    min_frac = 0.0015 * (1.0 - float(detail)) + 0.00004  # more detail -> smaller
    min_area = max(6, int(min_frac * area))
    dot_area = max(3, int(min_area * 0.4))
    return min_area, dot_area


def run(preprocessed_path: Path, out_path: Path, settings: dict | None) -> dict:
    p = resolve_params(settings)
    img = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    height, width = img.shape[:2]

    # grayscale + edge-preserving denoise
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, 7, 50, 50)

    # strong structural edges (Canny), optionally unioned with adaptive detail
    lines = cv2.Canny(gray, int(p["canny_lo"]), int(p["canny_hi"]))
    if p["use_adaptive"]:
        adaptive = cv2.adaptiveThreshold(
            gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV,
            int(p["block"]), int(p["c"]),
        )
        lines = cv2.bitwise_or(lines, adaptive)

    min_area, dot_area = _derived_areas(p["detail"], width, height)

    # remove weak texture noise (keeps meaningful internal detail via min_area)
    if p["remove_noise"]:
        lines = edge_cleaner.remove_texture(lines, open_k=1, min_area=min_area)

    # normalize strokes to clean black, thicken, close gaps, drop dots
    lines = stroke_normalizer.normalize(lines, float(p["smoothing"]))
    lines = line_thickener.thicken(lines, float(p["thickness"]))
    detect = line_gap_detector.detect(lines)
    lines, repair = line_gap_repair.repair(lines, float(p["gap"]))
    if p["remove_noise"]:
        lines = edge_cleaner.remove_dots(lines, dot_area)

    cv2.imwrite(str(out_path), cv2.bitwise_not(lines))  # black strokes on white

    return {
        "mode": p["mode"],
        "thickness": p["thickness"],
        "detail": p["detail"],
        "gap": p["gap"],
        "remove_noise": p["remove_noise"],
        "endpoints": detect.get("endpoints"),
        "gaps_repaired": repair.get("gaps_repaired"),
    }
