"""Quality Fix 7 — semantic region analysis foundation (analysis ONLY).

Classifies every region into a semantic material/object class (FUR, HAIR, SKIN,
FLOWER, LEAF, WOOD, METAL, GLASS, STONE, CLOTH, BACKGROUND, SKY, WATER, PLASTIC,
UNKNOWN) and measures the visual properties every future split decision will
depend on — texture, gradient, edge-flow, colour variance, orientation, and
structure-tensor strength.

This is the understanding layer. It **does not** split, merge, recolour, renumber,
or change SVG/export/regions. It only emits `semantic_region_report.json` and
`semantic_overlay.png`.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

CLASSES = [
    "FUR", "HAIR", "SKIN", "FLOWER", "LEAF", "WOOD", "METAL", "GLASS",
    "STONE", "CLOTH", "BACKGROUND", "SKY", "WATER", "PLASTIC", "UNKNOWN",
]

# Overlay colour per class (RGB).
CLASS_COLOR = {
    "FUR": (230, 140, 60), "HAIR": (95, 65, 45), "SKIN": (240, 200, 170),
    "FLOWER": (235, 90, 160), "LEAF": (80, 170, 80), "WOOD": (150, 100, 50),
    "METAL": (170, 175, 185), "GLASS": (180, 220, 230), "STONE": (150, 150, 150),
    "CLOTH": (120, 140, 210), "BACKGROUND": (215, 215, 215), "SKY": (135, 190, 235),
    "WATER": (70, 150, 210), "PLASTIC": (200, 120, 220), "UNKNOWN": (120, 120, 120),
}

ACTION_BY_CLASS = {
    "FUR": "PRESERVE_FUR_FLOW", "HAIR": "PRESERVE_FUR_FLOW",
    "SKIN": "PRESERVE_GRADIENT", "FLOWER": "DO_NOT_GROUP",
    "LEAF": "ALLOW_COLOR_GROUPING", "WOOD": "PRESERVE_STRIPES",
    "METAL": "PRESERVE_GRADIENT", "GLASS": "SAFE_TO_SIMPLIFY",
    "STONE": "ALLOW_COLOR_GROUPING", "CLOTH": "ALLOW_COLOR_GROUPING",
    "BACKGROUND": "SAFE_TO_SIMPLIFY", "SKY": "PRESERVE_GRADIENT",
    "WATER": "PRESERVE_GRADIENT", "PLASTIC": "ALLOW_COLOR_GROUPING",
    "UNKNOWN": "KEEP_AS_SINGLE_REGION",
}

MIN_LARGE_FRAC = 0.0015  # "large region" threshold for the split-candidate flag


def _is_skin(r, g, b) -> bool:
    y = 0.299 * r + 0.587 * g + 0.114 * b
    cr = (r - y) * 0.713 + 128
    cb = (b - y) * 0.564 + 128
    return 135 <= cr <= 180 and 85 <= cb <= 135 and y >= 80 and r >= g >= b - 10


def _classify(m: dict, touches_border: bool, size_frac: float):
    """Return (semantic_class, confidence) from measurements `m` + colour."""
    r, g, b = m["_rgb"]
    hue, sat, val = m["_hsv"]          # hue 0-360, sat/val 0-255
    tex, flow = m["texture_score"], m["edge_flow_score"]

    warm = 8 <= hue <= 50
    # Background / sky first (large, border-touching, smooth).
    if touches_border and size_frac >= 0.03 and tex < 0.35:
        if 190 <= hue <= 250 and sat < 120 and val > 150:
            return "SKY", 0.6
        return "BACKGROUND", 0.7
    # Dark, high-flow, elongated -> hair (before fur so black hair isn't "fur").
    if val < 75 and flow >= 0.3:
        return "HAIR", 0.58
    # Warm orange/tan region -> fur (animal body); cartoon fur is often smooth,
    # so a large warm area counts even without strong texture.
    if warm and sat >= 30 and (tex >= 0.2 or flow >= 0.3 or size_frac >= 0.02):
        if flow >= 0.5 and sat < 140 and tex >= 0.3:
            return "WOOD", 0.5          # strong directional grain -> wood
        return "FUR", 0.68
    # Restricted human-skin: smooth, pinkish, not huge.
    if _is_skin(r, g, b) and tex < 0.22 and size_frac < 0.03 and 5 <= hue <= 30:
        return "SKIN", 0.6
    # Green -> foliage
    if 70 <= hue <= 165 and sat > 45:
        return "LEAF", 0.64
    # Vivid warm/pink -> flower
    if sat >= 120 and (hue <= 25 or hue >= 300):
        return "FLOWER", 0.6
    # Low-saturation, high-gradient grey -> metal; textured grey -> stone
    if sat < 40:
        if m["gradient_score"] >= 0.12 and tex >= 0.4:
            return "STONE", 0.42
        if m["gradient_score"] >= 0.15:
            return "METAL", 0.4
        if val > 180:
            return "GLASS", 0.4
    # Blue, smooth -> water
    if 190 <= hue <= 250 and tex < 0.35:
        return "WATER", 0.42
    # Soft saturated, low texture -> cloth; flat saturated -> plastic
    if sat >= 60 and tex < 0.4:
        return "PLASTIC" if (tex < 0.15 and flow < 0.2) else "CLOTH", 0.5
    return "UNKNOWN", 0.3


def _recommended_action(sclass: str, size_frac: float, color_variance: float) -> str:
    # Large + high internal colour variance -> future split candidate (links QF5).
    if size_frac >= MIN_LARGE_FRAC and color_variance >= 18.0 and sclass not in ("BACKGROUND", "SKY"):
        return "REQUIRES_FUTURE_SPLIT"
    return ACTION_BY_CLASS.get(sclass, "KEEP_AS_SINGLE_REGION")


def analyze(region_data: dict, report_path: Path, overlay_path: Path) -> dict:
    labels: np.ndarray = region_data["_label_map"]
    bgr: np.ndarray = region_data["_preprocessed_bgr"]
    h, w = labels.shape
    total = h * w

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    gmag = np.sqrt(gx * gx + gy * gy)
    lap = cv2.Laplacian(gray, cv2.CV_32F)
    edges = cv2.Canny((gray * 255).astype(np.uint8), 60, 150)
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)

    frame = set(np.unique(np.concatenate([
        labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]
    ]).tolist()))

    regions_out = []
    class_counts = {c: 0 for c in CLASSES}
    overlay = np.full((h, w, 3), 255, np.uint8)
    overlay[labels == 0] = (40, 40, 40)

    for region in region_data["regions"]:
        lbl = region["label"]
        x, y, bw, bh = region["x"], region["y"], region["w"], region["h"]
        sm = labels[y:y + bh, x:x + bw] == lbl
        n = int(sm.sum())
        if n < 20:
            continue

        def crop(a):
            return a[y:y + bh, x:x + bw]

        gmr = crop(gmag)[sm]
        gxr, gyr = crop(gx)[sm], crop(gy)[sm]
        labpix = crop(lab)[sm].astype(np.float32)
        hsvpix = crop(hsv)[sm].astype(np.float32)

        color_variance = float(labpix.std(axis=0).mean())
        gradient_score = float(gmr.mean())
        texture_score = float(min(1.0, crop(lap)[sm].std() * 3.5))
        local_edge_density = float((crop(edges)[sm] > 0).mean())

        jxx, jyy, jxy = float((gxr * gxr).mean()), float((gyr * gyr).mean()), float((gxr * gyr).mean())
        tr, det = jxx + jyy, jxx * jyy - jxy * jxy
        disc = max(0.0, (tr / 2) ** 2 - det) ** 0.5
        l1, l2 = tr / 2 + disc, tr / 2 - disc
        structure_tensor_strength = float(min(1.0, l1 * 12.0))
        edge_flow_score = float((l1 - l2) / (l1 + l2 + 1e-6))
        dom_dir = float(np.degrees(0.5 * np.arctan2(2 * jxy, jxx - jyy)))

        ys, xs = np.where(sm)
        pts = np.column_stack([xs, ys]).astype(np.float32)
        cov = np.cov((pts - pts.mean(axis=0)).T) if len(pts) > 2 else np.eye(2)
        evals, evecs = np.linalg.eigh(cov)
        vmaj = evecs[:, int(np.argmax(evals))]
        major_orientation = float(np.degrees(np.arctan2(vmaj[1], vmaj[0])))
        minor_orientation = float((major_orientation + 90.0 + 180.0) % 180.0 - 90.0)

        rgb = (int(region["color"][1:3], 16), int(region["color"][3:5], 16), int(region["color"][5:7], 16))
        meas = {
            "_rgb": rgb,
            "_hsv": (float(hsvpix[:, 0].mean() * 2.0), float(hsvpix[:, 1].mean()), float(hsvpix[:, 2].mean())),
            "texture_score": round(texture_score, 3),
            "gradient_score": round(gradient_score, 4),
            "edge_flow_score": round(edge_flow_score, 3),
            "color_variance": round(color_variance, 2),
            "region_size": n,
            "major_orientation": round(major_orientation, 1),
            "minor_orientation": round(minor_orientation, 1),
            "structure_tensor_strength": round(structure_tensor_strength, 4),
            "dominant_gradient_direction": round(dom_dir, 1),
            "local_edge_density": round(local_edge_density, 4),
        }

        size_frac = n / total
        sclass, conf = _classify(meas, lbl in frame, size_frac)
        # nudge confidence by texture/flow agreement for textured classes
        if sclass in ("FUR", "HAIR", "WOOD") and edge_flow_score >= 0.4:
            conf = min(0.9, conf + 0.1)
        action = _recommended_action(sclass, size_frac, color_variance)
        class_counts[sclass] += 1

        overlay[y:y + bh, x:x + bw][sm] = CLASS_COLOR[sclass][::-1]  # RGB->BGR

        meas.pop("_rgb"); meas.pop("_hsv")
        regions_out.append({
            "region_id": region["number"],
            "label": int(lbl),
            "semantic_class": sclass,
            "confidence": round(float(conf), 2),
            **meas,
            "recommended_future_action": action,
        })

    _draw_legend(overlay, class_counts)
    cv2.imwrite(str(overlay_path), overlay)

    confs = [r["confidence"] for r in regions_out]
    report = {
        "version": 1,
        "note": "Semantic analysis only — no regions, palette, numbers, SVG, or export were changed.",
        "region_count": len(regions_out),
        "class_distribution": {c: n for c, n in class_counts.items() if n},
        "confidence_summary": {
            "mean": round(float(np.mean(confs)), 3) if confs else 0.0,
            "min": round(float(np.min(confs)), 3) if confs else 0.0,
            "max": round(float(np.max(confs)), 3) if confs else 0.0,
        },
        "regions": regions_out,
    }
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def _draw_legend(overlay: np.ndarray, class_counts: dict) -> None:
    present = [c for c in CLASSES if class_counts.get(c)]
    y = 12
    for c in present:
        col = CLASS_COLOR[c][::-1]
        cv2.rectangle(overlay, (10, y), (34, y + 18), col, -1)
        cv2.rectangle(overlay, (10, y), (34, y + 18), (40, 40, 40), 1)
        cv2.putText(overlay, f"{c} ({class_counts[c]})", (40, y + 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (30, 30, 30), 1, cv2.LINE_AA)
        y += 24
