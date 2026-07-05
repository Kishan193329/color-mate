"""Quality Fix 9 — processing policy engine (policy layer only).

QF7 = WHAT a region is, QF8 = HOW its material behaves. This layer turns those
into a concrete, per-region **processing policy**: the exact preserve flags,
simplification limits, future split direction/strength, and palette/SVG/numbering
policies that later fixes must obey.

It **reuses** `semantic_region_report.json` (QF7) and `material_behavior_report.json`
(QF8) — it recomputes neither. It changes no regions, palette, numbers, SVG, or
export; it only emits `processing_policy_report.json` and
`processing_policy_overlay.png`.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

POLICY_COLOR = {
    "FLOW_PROTECT": (235, 150, 40), "GRADIENT_PRESERVE": (70, 160, 220),
    "DETAIL_PROTECT": (235, 90, 160), "SIMPLIFY": (205, 205, 205),
    "STANDARD": (120, 190, 120),
}

PALETTE_MERGE = {
    "DO_NOT_SIMPLIFY": "LOCKED", "PRESERVE_GRADIENT": "WITHIN_MATERIAL",
    "KEEP_TEXTURE": "WITHIN_MATERIAL", "SAFE_SIMPLIFY": "FREE",
    "PROTECT_DETAIL": "LOCKED",
}
SVG_SMOOTH = {
    "KEEP_TEXTURE": "KEEP_DETAIL", "PROTECT_DETAIL": "KEEP_DETAIL",
    "PRESERVE_GRADIENT": "LIGHT", "SAFE_SIMPLIFY": "STRONG", "DO_NOT_SIMPLIFY": "KEEP_DETAIL",
}
SPLIT_DIR = {  # by behavior class
    "FLOW": "ALONG_FLOW", "GRADIENT": "ACROSS_TONE",
    "DETAIL": "NONE", "SIMPLIFY": "FREE", "CONSERVATIVE": "NONE", "TEXTURE": "FREE",
}
# behavior_class -> (max_simplification, min_area_frac, base processing priority)
CLASS_LIMITS = {
    "FLOW": (0.20, 0.0008, 2), "GRADIENT": (0.35, 0.0008, 2),
    "DETAIL": (0.10, 0.00025, 1), "TEXTURE": (0.45, 0.0010, 3),
    "SIMPLIFY": (0.90, 0.0040, 4), "CONSERVATIVE": (0.30, 0.0010, 3),
}
GRADIENT_CLASSES = {"SKIN", "SKY", "LEAF", "CLOTH", "METAL", "GLASS", "WATER"}
FLOW_CLASSES = {"FUR", "HAIR", "WOOD", "WATER"}
TEXTURE_CLASSES = {"FUR", "LEAF", "WOOD", "STONE"}
STRIPE_CLASSES = {"FUR", "HAIR", "WOOD"}
SIMPLE_CLASSES = {"BACKGROUND", "PLASTIC"}
HIDE_SMALL_AREA = 900


def _clamp(v):
    return round(float(max(0.0, min(1.0, v))), 3)


def analyze(semantic_report_path: Path, material_report_path: Path, labels_path: Path,
            report_path: Path, overlay_path: Path) -> dict:
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    material = json.loads(Path(material_report_path).read_text(encoding="utf-8"))
    labels = np.load(labels_path)
    h, w = labels.shape
    total = h * w

    sem_by_id = {r["region_id"]: r for r in semantic.get("regions", [])}
    mat_by_id = {r["region_id"]: r for r in material.get("regions", [])}

    overlay = np.full((h, w, 3), 255, np.uint8)
    overlay[labels == 0] = (40, 40, 40)
    policy_counts: dict[str, int] = {}
    regions_out = []

    for rid, s in sem_by_id.items():
        m = mat_by_id.get(rid, {})
        sclass = s.get("semantic_class", "UNKNOWN")
        bclass = m.get("behavior_class", "CONSERVATIVE")
        cvar = float(s.get("color_variance", 0.0))
        flow_ev = float(s.get("edge_flow_score", 0.0))
        size = int(s.get("region_size", 0))
        size_frac = size / total
        flow_ps = float(m.get("flow_preservation_score", 0.0))
        grad_ps = float(m.get("gradient_preservation_score", 0.0))
        tex_ps = float(m.get("texture_preservation_score", 0.0))

        preserve_gradient = bool(sclass in GRADIENT_CLASSES or grad_ps >= 0.3)
        preserve_texture = bool(sclass in TEXTURE_CLASSES or tex_ps >= 0.4)
        preserve_flow = bool(bclass == "FLOW" and flow_ps >= 0.3)
        preserve_stripes = bool(sclass in STRIPE_CLASSES and flow_ev >= 0.45)
        allow_simplification = bool(bclass == "SIMPLIFY" or sclass in SIMPLE_CLASSES)

        max_simp, min_area_frac, base_prio = CLASS_LIMITS.get(bclass, (0.3, 0.001, 3))
        maximum_simplification = _clamp(max_simp * (0.6 + 0.4 * (1.0 - min(1.0, cvar / 25.0))))
        minimum_region_area = max(120, int(min_area_frac * total))

        # future split: only meaningful for large, high-variance, non-simplify regions
        if sclass in ("BACKGROUND", "SKY") or bclass == "SIMPLIFY":
            split_strength = 0.0
        else:
            split_strength = _clamp(min(1.0, cvar / 25.0) * (0.5 + 0.5 * max(flow_ps, grad_ps)))
            if bclass == "DETAIL":
                split_strength = min(split_strength, 0.2)  # protect flowers
        future_split_direction = SPLIT_DIR.get(bclass, "NONE") if split_strength > 0.15 else "NONE"

        palette_merge_policy = PALETTE_MERGE.get(m.get("future_palette_strategy"), "WITHIN_MATERIAL")
        svg_smoothing_policy = SVG_SMOOTH.get(m.get("future_svg_strategy"), "LIGHT")
        if size < HIDE_SMALL_AREA:
            numbering_policy = "HIDE_SMALL"
        elif m.get("future_number_strategy") == "PROTECT_DETAIL":
            numbering_policy = "PROTECT"
        else:
            numbering_policy = "STANDARD"

        if bclass == "DETAIL" or preserve_flow or (preserve_gradient and size_frac >= 0.02) or split_strength >= 0.6:
            validation_priority = "HIGH"
        elif allow_simplification:
            validation_priority = "LOW"
        else:
            validation_priority = "MEDIUM"

        processing_priority = base_prio

        if preserve_flow:
            pclass = "FLOW_PROTECT"
        elif preserve_gradient:
            pclass = "GRADIENT_PRESERVE"
        elif not allow_simplification:
            pclass = "DETAIL_PROTECT"
        elif allow_simplification:
            pclass = "SIMPLIFY"
        else:
            pclass = "STANDARD"
        policy_counts[pclass] = policy_counts.get(pclass, 0) + 1

        lbl = s.get("label")
        if lbl is not None:
            overlay[labels == lbl] = POLICY_COLOR[pclass][::-1]

        regions_out.append({
            "region_id": rid,
            "semantic_class": sclass,
            "material_behavior": bclass,
            "policy_class": pclass,
            "preserve_gradient": preserve_gradient,
            "preserve_texture": preserve_texture,
            "preserve_flow": preserve_flow,
            "preserve_stripes": preserve_stripes,
            "allow_simplification": allow_simplification,
            "maximum_simplification": maximum_simplification,
            "minimum_region_area": minimum_region_area,
            "future_split_direction": future_split_direction,
            "future_split_strength": split_strength,
            "palette_merge_policy": palette_merge_policy,
            "svg_smoothing_policy": svg_smoothing_policy,
            "numbering_policy": numbering_policy,
            "validation_priority": validation_priority,
            "processing_priority": processing_priority,
        })

    _legend(overlay, policy_counts)
    cv2.imwrite(str(overlay_path), overlay)

    report = {
        "version": 1,
        "note": "Processing policy only — reuses QF7 + QF8 reports; no regions, "
                "palette, numbers, SVG, or export changed.",
        "sources": ["semantic_region_report.json", "material_behavior_report.json"],
        "region_count": len(regions_out),
        "policy_distribution": policy_counts,
        "validation_priority_distribution": _count(regions_out, "validation_priority"),
        "palette_merge_distribution": _count(regions_out, "palette_merge_policy"),
        "svg_smoothing_distribution": _count(regions_out, "svg_smoothing_policy"),
        "regions": sorted(regions_out, key=lambda r: r["processing_priority"]),
    }
    Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def _count(rows, key):
    out: dict = {}
    for r in rows:
        out[r[key]] = out.get(r[key], 0) + 1
    return out


def _legend(overlay, counts):
    y = 12
    for pclass, col in POLICY_COLOR.items():
        if not counts.get(pclass):
            continue
        c = col[::-1]
        cv2.rectangle(overlay, (10, y), (34, y + 18), c, -1)
        cv2.rectangle(overlay, (10, y), (34, y + 18), (40, 40, 40), 1)
        cv2.putText(overlay, f"{pclass} ({counts[pclass]})", (40, y + 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.48, (30, 30, 30), 1, cv2.LINE_AA)
        y += 24
