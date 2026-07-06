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
    # Quality Fix 5 — suspected-merged-region map.
    "merged_region_map": "merged_region_map.png",
    # Quality Fix 6 — advisory split preview.
    "split_preview_map": "split_preview_map.png",
    # Quality Fix 7 — semantic region analysis (analysis only).
    "semantic_region_report": "semantic_region_report.json",
    "semantic_overlay": "semantic_overlay.png",
    # Quality Fix 8 — material behavior layer (analysis only).
    "material_behavior_report": "material_behavior_report.json",
    "material_behavior_overlay": "material_behavior_overlay.png",
    # Quality Fix 9 — processing policy layer (policy only).
    "processing_policy_report": "processing_policy_report.json",
    "processing_policy_overlay": "processing_policy_overlay.png",
    # Quality Fix 10 — gradient preservation (pixel-changing).
    "gradient_preservation_report": "gradient_preservation_report.json",
    "gradient_preview_map": "gradient_preview_map.png",
    # Quality Fix 11 — flow-aware gradient (pixel-changing, supersedes QF10 engine).
    "flow_gradient_report": "flow_gradient_report.json",
    "flow_gradient_overlay": "flow_gradient_overlay.png",
    # Quality Fix 12 — intelligent region split (pixel-changing, supersedes QF11 engine).
    "intelligent_split_report": "intelligent_split_report.json",
    "intelligent_split_overlay": "intelligent_split_overlay.png",
    # Quality Fix 13 — artistic decision layer (decision only, no pixels).
    "artistic_decision_report": "artistic_decision_report.json",
    "artistic_decision_overlay": "artistic_decision_overlay.png",
    # Quality Fix 14 — artistic contour character (adaptive vector contour geometry).
    "contour_character_report": "contour_character_report.json",
    "contour_character_overlay": "contour_character_overlay.png",
    "quality_report": "quality_report.json",
    "result_zip": "result.zip",
}

# ── Output profiles (Product Sprint 1) ──────────────────────────────────────
# The engine is now a configurable product: a chosen profile drives every tunable
# in one place. Each profile declares eight product BUDGETS (human-facing levels)
# and the concrete engine parameters that realise them:
#
#   palette budget        -> palette_delta (fallback) + palette_budget_scale
#                            (scales core.palette_budget.MATERIAL_BUDGET; higher
#                             scale = looser merge = fewer colours)
#   region budget         -> min_area_frac / min_area_abs (region size floor)
#   simplification budget -> smooth_passes + dust/gap kernels (line/contour cleanup)
#   gradient preservation -> QF13 expansion gates (expansion_min_variance / _area,
#                            expansion_bg_*) — lower = more tonal splits kept
#   texture preservation  -> texture_kcap_delta (offsets QF12 MATERIAL_KCAP; more
#                            k = more material bands)
#   highlight preservation-> expansion_min_cluster / _min_luma (QF12) + FACIAL
#                            budget (kept strict via palette_budget_scale)
#   svg precision         -> smooth_eps_frac / smooth_passes (lower eps = crisper)
#   number density        -> label_min_area / label_scale (smaller floor = more #s)
#
# MEDIUM reproduces the current engine output EXACTLY (every value equals the
# pre-sprint constant / module default). EASY..MAXIMUM_FIDELITY span the range.
# No algorithm changes: profiles only select existing parameters.
OUTPUT_PROFILES = {
    "easy": {
        "label": "Easy",
        "description": "Fewest colours & regions, boldest simplification — quick, forgiving.",
        "budgets": {
            "palette": "minimal", "region": "minimal", "simplification": "aggressive",
            "gradient_preservation": "low", "texture_preservation": "low",
            "highlight_preservation": "balanced", "svg_precision": "low",
            "number_density": "low",
        },
        "lineart_block": 11, "lineart_c": 7, "dust_kernel": 3, "gap_kernel": 5, "gap_dilate": 1,
        "min_area_frac": 0.0020, "min_area_abs": 200,
        "smooth_eps_frac": 0.0040, "smooth_passes": 2,
        "label_min_area": 1200, "label_scale": 1.35,
        "palette_delta": 28, "palette_budget_scale": 1.45,
        "expansion_min_variance": 12.0, "expansion_min_area": 20000,
        "expansion_bg_min_variance": 16.0, "expansion_bg_min_area": 60000,
        "expansion_max_subregions": 2, "expansion_bg_max_subregions": 2,
        "expansion_min_cluster": 900, "expansion_min_luma": 8.0,
        "texture_kcap_delta": -2,
    },
    "medium": {
        "label": "Medium",
        "description": "Balanced default — the shipped engine output (unchanged).",
        "budgets": {
            "palette": "balanced", "region": "balanced", "simplification": "balanced",
            "gradient_preservation": "balanced", "texture_preservation": "balanced",
            "highlight_preservation": "balanced", "svg_precision": "balanced",
            "number_density": "balanced",
        },
        "lineart_block": 9, "lineart_c": 7, "dust_kernel": 2, "gap_kernel": 3, "gap_dilate": 1,
        "min_area_frac": 0.0008, "min_area_abs": 90,
        "smooth_eps_frac": 0.0020, "smooth_passes": 1,
        "label_min_area": 500, "label_scale": 1.0,
        # palette_delta is only the fallback: per-material budgets (Commercial Opt 1)
        # set the real per-region merge threshold; scale 1.0 = the current table.
        "palette_delta": 18, "palette_budget_scale": 1.0,
        "expansion_min_variance": 4.0, "expansion_min_area": 5000,
        "expansion_bg_min_variance": 8.0, "expansion_bg_min_area": 20000,
        "expansion_max_subregions": 3, "expansion_bg_max_subregions": 2,
        "expansion_min_cluster": 450, "expansion_min_luma": 5.0,
        "texture_kcap_delta": 0,
    },
    "hard": {
        "label": "Hard",
        "description": "More regions & colours, crisper contours — for detailed painters.",
        "budgets": {
            "palette": "high", "region": "high", "simplification": "low",
            "gradient_preservation": "high", "texture_preservation": "balanced",
            "highlight_preservation": "high", "svg_precision": "high",
            "number_density": "high",
        },
        "lineart_block": 7, "lineart_c": 5, "dust_kernel": 2, "gap_kernel": 3, "gap_dilate": 0,
        "min_area_frac": 0.0003, "min_area_abs": 50,
        "smooth_eps_frac": 0.0012, "smooth_passes": 0,
        "label_min_area": 250, "label_scale": 0.85,
        "palette_delta": 10, "palette_budget_scale": 0.78,
        "expansion_min_variance": 3.0, "expansion_min_area": 4000,
        "expansion_bg_min_variance": 6.0, "expansion_bg_min_area": 16000,
        "expansion_max_subregions": 3, "expansion_bg_max_subregions": 2,
        "expansion_min_cluster": 380, "expansion_min_luma": 4.5,
        "texture_kcap_delta": 0,
    },
    "professional": {
        "label": "Professional",
        "description": "Print-grade: fine regions, rich palette, high-precision vector.",
        "budgets": {
            "palette": "high", "region": "high", "simplification": "minimal",
            "gradient_preservation": "high", "texture_preservation": "high",
            "highlight_preservation": "high", "svg_precision": "maximum",
            "number_density": "high",
        },
        "lineart_block": 7, "lineart_c": 5, "dust_kernel": 2, "gap_kernel": 3, "gap_dilate": 0,
        "min_area_frac": 0.0002, "min_area_abs": 40,
        "smooth_eps_frac": 0.0009, "smooth_passes": 0,
        "label_min_area": 180, "label_scale": 0.80,
        "palette_delta": 8, "palette_budget_scale": 0.62,
        "expansion_min_variance": 2.5, "expansion_min_area": 3500,
        "expansion_bg_min_variance": 5.0, "expansion_bg_min_area": 13000,
        "expansion_max_subregions": 4, "expansion_bg_max_subregions": 3,
        "expansion_min_cluster": 320, "expansion_min_luma": 4.0,
        "texture_kcap_delta": 1,
    },
    "maximum_fidelity": {
        "label": "Maximum Fidelity",
        "description": "Everything preserved: most regions, colours, tonal bands & precision.",
        "budgets": {
            "palette": "maximum", "region": "maximum", "simplification": "minimal",
            "gradient_preservation": "maximum", "texture_preservation": "maximum",
            "highlight_preservation": "maximum", "svg_precision": "maximum",
            "number_density": "maximum",
        },
        "lineart_block": 7, "lineart_c": 5, "dust_kernel": 2, "gap_kernel": 3, "gap_dilate": 0,
        "min_area_frac": 0.00012, "min_area_abs": 30,
        "smooth_eps_frac": 0.0006, "smooth_passes": 0,
        "label_min_area": 120, "label_scale": 0.75,
        "palette_delta": 6, "palette_budget_scale": 0.5,
        "expansion_min_variance": 2.0, "expansion_min_area": 3000,
        "expansion_bg_min_variance": 4.0, "expansion_bg_min_area": 11000,
        "expansion_max_subregions": 4, "expansion_bg_max_subregions": 3,
        "expansion_min_cluster": 240, "expansion_min_luma": 3.5,
        "texture_kcap_delta": 2,
    },
}

# The 8 product budgets, in display order (for the UI / report).
PROFILE_BUDGET_KEYS = (
    "palette", "region", "simplification", "gradient_preservation",
    "texture_preservation", "highlight_preservation", "svg_precision", "number_density",
)

# Backward-compatible alias: existing code (region_detector, palette_grouper,
# svg_generator, label_optimizer, pipeline) reads DIFFICULTY_PROFILES; the two new
# profiles are simply extra keys. `reprocess`/APIs accept any profile name.
DIFFICULTY_PROFILES = OUTPUT_PROFILES

DEFAULT_DIFFICULTY = "medium"
DEFAULT_PROFILE = "medium"

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
