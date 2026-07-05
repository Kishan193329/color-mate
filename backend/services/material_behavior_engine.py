"""Quality Fix 8 — material behavior engine (understanding layer only).

QF7 established WHAT each region is (`semantic_region_report.json`). This layer
establishes HOW that material should behave in future processing: per class it
defines behavior tokens, per-region preservation scores, and the future
strategies for splitting, palette grouping, SVG, and numbering.

It **reuses** the QF7 report and the persisted `labels.npy` — it does NOT recompute
semantic analysis, and it changes no regions, palette, numbers, SVG, or export.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

# Behavior class -> overlay colour (RGB).
BEHAVIOR_COLOR = {
    "FLOW": (235, 150, 40), "GRADIENT": (70, 160, 220), "DETAIL": (235, 90, 160),
    "TEXTURE": (150, 110, 60), "SIMPLIFY": (205, 205, 205), "CONSERVATIVE": (130, 130, 130),
}

# Per semantic-class behavior profile. Base scores in [0,1] are modulated per
# region by the QF7 measurements. Strategy tokens come from the recommended set:
# FOLLOW_FLOW, PRESERVE_GRADIENT, SAFE_SIMPLIFY, PROTECT_DETAIL, MERGE_NOISE_ONLY,
# DO_NOT_SIMPLIFY, KEEP_TEXTURE.
PROFILES: dict[str, dict] = {
    "FUR": dict(behavior_class="FLOW",
                behaviors=["preserve_flow", "preserve_stripes", "preserve_soft_gradient",
                           "avoid_micro_noise", "split_along_flow", "never_flatten"],
                flow=0.9, gradient=0.5, texture=0.7, noise=0.4,
                split="FOLLOW_FLOW", palette="PRESERVE_GRADIENT", svg="KEEP_TEXTURE", number="PROTECT_DETAIL"),
    "HAIR": dict(behavior_class="FLOW",
                 behaviors=["preserve_direction", "preserve_highlights", "split_along_strands"],
                 flow=0.9, gradient=0.5, texture=0.6, noise=0.4,
                 split="FOLLOW_FLOW", palette="PRESERVE_GRADIENT", svg="KEEP_TEXTURE", number="PROTECT_DETAIL"),
    "SKIN": dict(behavior_class="GRADIENT",
                 behaviors=["preserve_soft_gradient", "remove_skin_noise", "never_split_pores", "preserve_face_volume"],
                 flow=0.2, gradient=0.9, texture=0.2, noise=0.8,
                 split="PRESERVE_GRADIENT", palette="PRESERVE_GRADIENT", svg="SAFE_SIMPLIFY", number="PROTECT_DETAIL"),
    "FLOWER": dict(behavior_class="DETAIL",
                   behaviors=["preserve_petal_shape", "preserve_center", "keep_petal_boundaries"],
                   flow=0.3, gradient=0.5, texture=0.4, noise=0.4,
                   split="PROTECT_DETAIL", palette="DO_NOT_SIMPLIFY", svg="PROTECT_DETAIL", number="PROTECT_DETAIL"),
    "LEAF": dict(behavior_class="GRADIENT",
                 behaviors=["preserve_veins", "preserve_gradient", "remove_small_noise"],
                 flow=0.5, gradient=0.6, texture=0.5, noise=0.6,
                 split="PRESERVE_GRADIENT", palette="PRESERVE_GRADIENT", svg="KEEP_TEXTURE", number="PROTECT_DETAIL"),
    "WOOD": dict(behavior_class="FLOW",
                 behaviors=["preserve_grain_direction", "merge_random_noise"],
                 flow=0.8, gradient=0.4, texture=0.6, noise=0.6,
                 split="FOLLOW_FLOW", palette="PRESERVE_GRADIENT", svg="KEEP_TEXTURE", number="SAFE_SIMPLIFY"),
    "METAL": dict(behavior_class="GRADIENT",
                  behaviors=["preserve_specular_gradient", "remove_noise"],
                  flow=0.3, gradient=0.8, texture=0.3, noise=0.7,
                  split="PRESERVE_GRADIENT", palette="PRESERVE_GRADIENT", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "GLASS": dict(behavior_class="GRADIENT",
                  behaviors=["preserve_transparency_gradient", "safe_simplify"],
                  flow=0.2, gradient=0.7, texture=0.2, noise=0.7,
                  split="PRESERVE_GRADIENT", palette="SAFE_SIMPLIFY", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "STONE": dict(behavior_class="TEXTURE",
                  behaviors=["preserve_texture", "merge_random_noise"],
                  flow=0.3, gradient=0.4, texture=0.6, noise=0.5,
                  split="MERGE_NOISE_ONLY", palette="SAFE_SIMPLIFY", svg="KEEP_TEXTURE", number="SAFE_SIMPLIFY"),
    "CLOTH": dict(behavior_class="GRADIENT",
                  behaviors=["preserve_fold_direction", "preserve_large_shadows"],
                  flow=0.6, gradient=0.7, texture=0.4, noise=0.5,
                  split="PRESERVE_GRADIENT", palette="PRESERVE_GRADIENT", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "BACKGROUND": dict(behavior_class="SIMPLIFY",
                       behaviors=["simplify_when_safe"],
                       flow=0.1, gradient=0.4, texture=0.2, noise=0.8,
                       split="SAFE_SIMPLIFY", palette="SAFE_SIMPLIFY", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "SKY": dict(behavior_class="GRADIENT",
                behaviors=["preserve_large_gradient", "ignore_cloud_texture_noise", "avoid_small_regions"],
                flow=0.2, gradient=0.9, texture=0.2, noise=0.8,
                split="PRESERVE_GRADIENT", palette="PRESERVE_GRADIENT", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "WATER": dict(behavior_class="FLOW",
                  behaviors=["preserve_wave_direction", "preserve_reflection_gradient"],
                  flow=0.7, gradient=0.8, texture=0.3, noise=0.6,
                  split="FOLLOW_FLOW", palette="PRESERVE_GRADIENT", svg="KEEP_TEXTURE", number="SAFE_SIMPLIFY"),
    "PLASTIC": dict(behavior_class="SIMPLIFY",
                    behaviors=["flat_shading", "safe_simplify"],
                    flow=0.2, gradient=0.4, texture=0.2, noise=0.7,
                    split="SAFE_SIMPLIFY", palette="SAFE_SIMPLIFY", svg="SAFE_SIMPLIFY", number="SAFE_SIMPLIFY"),
    "UNKNOWN": dict(behavior_class="CONSERVATIVE",
                    behaviors=["conservative_mode"],
                    flow=0.3, gradient=0.4, texture=0.4, noise=0.4,
                    split="DO_NOT_SIMPLIFY", palette="DO_NOT_SIMPLIFY", svg="DO_NOT_SIMPLIFY", number="PROTECT_DETAIL"),
}


def _clamp(v: float) -> float:
    return round(float(max(0.0, min(1.0, v))), 3)


def analyze(semantic_report_path: Path, labels_path: Path,
            report_path: Path, overlay_path: Path) -> dict:
    """Read QF7 semantic report + labels.npy; emit behavior report + overlay."""
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path)
    h, w = labels.shape

    overlay = np.full((h, w, 3), 255, np.uint8)
    overlay[labels == 0] = (40, 40, 40)

    regions_out = []
    behavior_counts: dict[str, int] = {}
    flow_scores, grad_scores = [], []

    for r in semantic.get("regions", []):
        sclass = r.get("semantic_class", "UNKNOWN")
        p = PROFILES.get(sclass, PROFILES["UNKNOWN"])

        # Reuse QF7 measurements (no recomputation).
        flow = float(r.get("edge_flow_score", 0.0))
        cvar = float(r.get("color_variance", 0.0))
        tex = float(r.get("texture_score", 0.0))

        flow_ps = _clamp(p["flow"] * (0.4 + 0.6 * flow))
        grad_ps = _clamp(p["gradient"] * (0.4 + 0.6 * min(1.0, cvar / 25.0)))
        tex_ps = _clamp(p["texture"] * (0.4 + 0.6 * tex))
        noise_rs = _clamp(p["noise"] * (0.4 + 0.6 * (1.0 - min(1.0, flow))))

        bclass = p["behavior_class"]
        behavior_counts[bclass] = behavior_counts.get(bclass, 0) + 1
        flow_scores.append(flow_ps)
        grad_scores.append(grad_ps)

        lbl = r.get("label")
        if lbl is not None:
            overlay[labels == lbl] = BEHAVIOR_COLOR[bclass][::-1]  # RGB->BGR

        regions_out.append({
            "region_id": r.get("region_id"),
            "semantic_class": sclass,
            "behavior_class": bclass,
            "material_behavior": p["behaviors"],
            "flow_preservation_score": flow_ps,
            "gradient_preservation_score": grad_ps,
            "texture_preservation_score": tex_ps,
            "noise_removal_score": noise_rs,
            "future_split_strategy": p["split"],
            "future_palette_strategy": p["palette"],
            "future_svg_strategy": p["svg"],
            "future_number_strategy": p["number"],
        })

    _draw_legend(overlay, behavior_counts)
    cv2.imwrite(str(overlay_path), overlay)

    def summ(vals):
        return {"mean": round(float(np.mean(vals)), 3) if vals else 0.0,
                "min": round(float(np.min(vals)), 3) if vals else 0.0,
                "max": round(float(np.max(vals)), 3) if vals else 0.0}

    report = {
        "version": 1,
        "note": "Behavior understanding only — reuses QF7 semantic_region_report.json; "
                "no regions, palette, numbers, SVG, or export changed.",
        "source": "semantic_region_report.json",
        "region_count": len(regions_out),
        "behavior_distribution": behavior_counts,
        "flow_preservation_summary": summ(flow_scores),
        "gradient_preservation_summary": summ(grad_scores),
        "regions": regions_out,
    }
    Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def _draw_legend(overlay: np.ndarray, counts: dict) -> None:
    y = 12
    for bclass, col in BEHAVIOR_COLOR.items():
        if not counts.get(bclass):
            continue
        c = col[::-1]
        cv2.rectangle(overlay, (10, y), (34, y + 18), c, -1)
        cv2.rectangle(overlay, (10, y), (34, y + 18), (40, 40, 40), 1)
        cv2.putText(overlay, f"{bclass} ({counts[bclass]})", (40, y + 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (30, 30, 30), 1, cv2.LINE_AA)
        y += 24
