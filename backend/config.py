"""Central configuration for ColorForge AI backend.

All paths are resolved relative to the repository root so the app can be run
from anywhere (e.g. `uvicorn backend.main:app` or from inside `backend/`).
"""
from __future__ import annotations

import os
from pathlib import Path

# Phase 7 — semantic layer mode. "rules" uses the local heuristic classifier
# (always available); "ai" tries an external model and falls back to rules if
# unavailable. No RunPod / external dependency is required.
SEMANTIC_MODE = os.getenv("SEMANTIC_MODE", "rules").lower()
if SEMANTIC_MODE not in ("rules", "ai"):
    SEMANTIC_MODE = "rules"

# backend/config.py -> backend/ -> colorforge-ai/
ROOT_DIR = Path(__file__).resolve().parent.parent
STORAGE_DIR = ROOT_DIR / "storage"
JOBS_DIR = STORAGE_DIR / "jobs"

# Make sure storage exists on startup.
JOBS_DIR.mkdir(parents=True, exist_ok=True)

# Phase 8 — optional RunPod AI assist. "local" uses the on-box engine only;
# "runpod" sends the image to a RunPod worker for enhancement / line art /
# semantic helper data, then ALWAYS detects regions locally. RunPod is never the
# region source, and any failure falls back to the local engine.
AI_MODE = os.getenv("AI_MODE", "local").lower()
if AI_MODE not in ("local", "runpod"):
    AI_MODE = "local"
RUNPOD_API_URL = os.getenv("RUNPOD_API_URL", "").rstrip("/")
RUNPOD_API_KEY = os.getenv("RUNPOD_API_KEY", "")
RUNPOD_TIMEOUT_SECONDS = int(os.getenv("RUNPOD_TIMEOUT_SECONDS", "600"))
# Runtime AI-mode override (set from the UI) persists here, overriding AI_MODE.
AI_SETTINGS_FILE = STORAGE_DIR / "ai_settings.json"

# Output artifact names produced by the processing pipeline. Centralised so the
# frontend, zip exporter, and download endpoint all agree on file names.
ARTIFACTS = {
    "original": "original.png",
    "preprocessed": "preprocessed.png",
    "lineart": "lineart.png",
    "region_mask": "region_mask.png",
    "outline": "outline.svg",
    "regions": "regions.json",
    "game_config": "game_config.json",
    "palette": "palette.json",
    "preview": "preview.png",
    "texture_fill_preview": "texture_fill_preview.png",
    "game_preview": "game_preview.png",
    # Phase 8 — optional AI helper outputs (saved when AI_MODE=runpod succeeds).
    "enhanced": "enhanced.png",
    "ai_lineart": "ai_lineart.png",
    "semantic_map": "semantic_map.json",
    "confidence": "confidence.json",
    # Phase 9 — advanced line art.
    "lineart_quality": "lineart_quality.json",
    "lineart_preview": "lineart_preview.png",
    # Quality Fix 3 — Perfect Border & Shape Engine report.
    "border_quality_report": "border_quality_report.json",
    # Quality Fix 4 — region-aware leak map.
    "leak_map": "leak_map.png",
    "quality_report": "quality_report.json",
    "result_zip": "result.zip",
}

# Difficulty profiles (Phase 5). Each tunes the whole pipeline: line-art
# sensitivity, dust/gap handling, region size floor, tiny-region merging,
# contour smoothing, and label sizing.
#
# Easy   -> larger regions, fewer details, bigger labels.
# Medium -> balanced (default).
# Hard   -> more details, smaller regions allowed.
DIFFICULTY_PROFILES = {
    "easy": {
        "lineart_block": 11, "lineart_c": 7,
        "dust_kernel": 3,
        "gap_kernel": 5, "gap_dilate": 1,
        "min_area_frac": 0.0020, "min_area_abs": 200,
        "smooth_eps_frac": 0.0040, "smooth_passes": 2,
        "label_min_area": 1200, "label_scale": 1.35,
        "palette_delta": 28,
    },
    "medium": {
        "lineart_block": 9, "lineart_c": 7,
        "dust_kernel": 2,
        "gap_kernel": 3, "gap_dilate": 1,
        "min_area_frac": 0.0008, "min_area_abs": 90,
        "smooth_eps_frac": 0.0020, "smooth_passes": 1,
        "label_min_area": 500, "label_scale": 1.0,
        "palette_delta": 18,
    },
    "hard": {
        "lineart_block": 7, "lineart_c": 5,
        "dust_kernel": 2,
        "gap_kernel": 3, "gap_dilate": 0,
        "min_area_frac": 0.0003, "min_area_abs": 50,
        "smooth_eps_frac": 0.0012, "smooth_passes": 0,
        "label_min_area": 250, "label_scale": 0.85,
        "palette_delta": 10,
    },
}

DEFAULT_DIFFICULTY = "medium"

# Phase 9 — line-art style modes. Each tunes the advanced line-art engine:
# edge sensitivity (canny + optional adaptive), stroke thickness, gap-closing
# strength, tiny-detail removal, and smoothing. `thickness/detail/gap/
# remove_noise` are also the default slider values shown in the UI.
LINEART_MODES = {
    "simple_cartoon": {
        "label": "Simple Cartoon", "canny_lo": 55, "canny_hi": 160,
        "block": 11, "c": 9, "use_adaptive": False, "smoothing": 1.2,
        "thickness": 2.2, "detail": 0.2, "gap": 0.9, "remove_noise": True,
    },
    "detailed_artwork": {
        "label": "Detailed Artwork", "canny_lo": 50, "canny_hi": 150,
        "block": 9, "c": 7, "use_adaptive": True, "smoothing": 0.6,
        "thickness": 1.3, "detail": 0.7, "gap": 0.6, "remove_noise": True,
    },
    "mandala_pattern": {
        "label": "Mandala / Pattern", "canny_lo": 40, "canny_hi": 120,
        "block": 7, "c": 5, "use_adaptive": True, "smoothing": 0.3,
        "thickness": 1.0, "detail": 0.9, "gap": 0.4, "remove_noise": False,
    },
    "animal_fur": {
        "label": "Animal Fur", "canny_lo": 40, "canny_hi": 130,
        "block": 9, "c": 6, "use_adaptive": True, "smoothing": 0.4,
        "thickness": 1.0, "detail": 0.85, "gap": 0.35, "remove_noise": False,
    },
    "portrait": {
        "label": "Portrait", "canny_lo": 60, "canny_hi": 160,
        "block": 9, "c": 8, "use_adaptive": True, "smoothing": 0.9,
        "thickness": 1.5, "detail": 0.55, "gap": 0.7, "remove_noise": True,
    },
    "object_product": {
        "label": "Object / Product", "canny_lo": 55, "canny_hi": 170,
        "block": 11, "c": 9, "use_adaptive": False, "smoothing": 1.0,
        "thickness": 1.9, "detail": 0.35, "gap": 0.9, "remove_noise": True,
    },
}
DEFAULT_LINEART_MODE = "detailed_artwork"
LINEART_SLIDER_KEYS = ("mode", "thickness", "detail", "gap", "remove_noise")

# Human-readable processing steps shown in the Processing UI. The pipeline
# reports progress against these.
PROCESSING_STEPS = [
    "Preparing image",
    "Creating clean line art",
    "Closing gaps",
    "Detecting regions",
    "Placing numbers",
    "Preparing export",
]

# CORS origins allowed during local development.
ALLOWED_ORIGINS = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:4173",
]
