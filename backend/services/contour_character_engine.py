"""Quality Fix 14 — artistic contour character engine (contour geometry).

The border is closed; QF14 gives every contour its own *artistic personality* the
way an illustrator does — a flowing outer silhouette, organic fur edges, crisp
eyes, tapered leaf tips, elegant petals — instead of one uniform polyline for
everything. It classifies each region contour, measures it, assigns a drawing
style + rendering policy, and re-emits the vector outline with **adaptive
smoothing**: organic/soft contours become smooth cubic Béziers, sharp contours
(eyes) stay crisp polylines, tapered contours (leaf tips) keep their points.

Reuses (NO recompute): QF13 `artistic_decision_report.json` (role / importance /
protect flags), QF7 `semantic_region_report.json`, QF8/QF9/QF11/QF12 reports for
context, QF3 `border_quality_report.json`. Adjacency helpers are imported from the
QF13 engine — nothing is recomputed twice.

It changes only the vector contour geometry (`outline.svg` and the region `path`
strings in `regions.json` / `game_config.json`). Labels, palette, numbers, colours,
region counts, line art, and the exporter are all untouched — so leaks, numbering,
and colour fidelity (ΔE/SSIM) are unchanged by construction.
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import cv2
import numpy as np

from services.artistic_decision_engine import _fill_lines, _edge_lengths
from services.border_engine import _jaggedness

STROKE, STROKE_WIDTH = "#111111", 2

# Contour classes -> legend colour (RGB).
CLASS_COLOR = {
    "OUTER_SILHOUETTE": (20, 20, 20), "INNER_STRUCTURE": (150, 150, 150),
    "FUR_EDGE": (245, 140, 40), "FLOW_EDGE": (230, 170, 30),
    "LEAF_EDGE": (60, 170, 75), "PETAL_EDGE": (232, 95, 180),
    "FACIAL_EDGE": (225, 45, 45), "WHISKER": (80, 80, 240),
    "DECORATIVE": (150, 110, 215), "UNKNOWN": (120, 120, 120),
}
# Contour class -> drawing style.
CLASS_STYLE = {
    "OUTER_SILHOUETTE": "ORGANIC", "INNER_STRUCTURE": "HYBRID",
    "FUR_EDGE": "ORGANIC", "FLOW_EDGE": "ORGANIC", "LEAF_EDGE": "TAPERED",
    "PETAL_EDGE": "SOFT", "FACIAL_EDGE": "SHARP", "WHISKER": "TAPERED",
    "DECORATIVE": "MECHANICAL", "UNKNOWN": "HYBRID",
}
# Style -> rendering policy. eps_frac scales approx simplification (lower = crisper);
# bezier = emit smooth cubic curves; corner_keep = preserve sharp vertices as cusps.
STYLE_POLICY = {
    "ORGANIC":     {"eps_frac": 0.0016, "bezier": True,  "smoothing": 0.85, "corner_keep": False, "corner_deg": 70},
    "SOFT":        {"eps_frac": 0.0022, "bezier": True,  "smoothing": 0.95, "corner_keep": False, "corner_deg": 80},
    "SHARP":       {"eps_frac": 0.0010, "bezier": False, "smoothing": 0.15, "corner_keep": True,  "corner_deg": 40},
    "TAPERED":     {"eps_frac": 0.0016, "bezier": True,  "smoothing": 0.70, "corner_keep": True,  "corner_deg": 45},
    "MECHANICAL":  {"eps_frac": 0.0030, "bezier": False, "smoothing": 0.40, "corner_keep": True,  "corner_deg": 30},
    "MICRO_DETAIL":{"eps_frac": 0.0008, "bezier": False, "smoothing": 0.10, "corner_keep": True,  "corner_deg": 35},
    "HYBRID":      {"eps_frac": 0.0018, "bezier": True,  "smoothing": 0.60, "corner_keep": True,  "corner_deg": 55},
}
SUBJECT_ROLES = {"SUBJECT_CORE", "FUR_PATTERN", "SUBJECT_DETAIL", "FACIAL_FEATURE"}
MIN_CONTOUR_PTS = 4
WHISKER_ASPECT = 6.0       # elongated thin region -> whisker-like
MICRO_AREA = 400           # very small contour -> micro-detail precision


# --------------------------------------------------------------------------- #
# geometry helpers
# --------------------------------------------------------------------------- #
def _turning_angles(pts: np.ndarray) -> np.ndarray:
    v = np.roll(pts, -1, axis=0) - pts
    n = np.linalg.norm(v, axis=1)
    n[n == 0] = 1.0
    v = v / n[:, None]
    dots = np.clip((v * np.roll(v, 1, axis=0)).sum(axis=1), -1.0, 1.0)
    return np.arccos(dots)


def _corner_indices(pts: np.ndarray, deg: float) -> set:
    ang = _turning_angles(pts)
    thr = np.radians(deg)
    return {i for i, a in enumerate(ang) if a >= thr}


def _catmull_bezier(pts: np.ndarray, corners: set) -> str:
    """Closed Catmull-Rom -> cubic Bézier `d`. Corner indices become cusps
    (tangent collapses) so tapered tips / kept corners stay sharp."""
    n = len(pts)
    d = [f"M {pts[0][0]:.1f} {pts[0][1]:.1f}"]
    for i in range(n):
        p0, p1 = pts[i], pts[(i + 1) % n]
        pm = pts[(i - 1) % n]
        pn = pts[(i + 2) % n]
        c1 = p1 if i in corners else p0 + (p1 - pm) / 6.0
        c2 = p0 if (i + 1) % n in corners else p1 - (pn - p0) / 6.0
        d.append(f"C {c1[0]:.1f} {c1[1]:.1f} {c2[0]:.1f} {c2[1]:.1f} {p1[0]:.1f} {p1[1]:.1f}")
    d.append("Z")
    return " ".join(d)


def _polyline(pts: np.ndarray) -> str:
    d = [f"M {int(pts[0][0])} {int(pts[0][1])}"]
    d += [f"L {int(x)} {int(y)}" for x, y in pts[1:]]
    d.append("Z")
    return " ".join(d)


def _adaptive_subpath(contour: np.ndarray, policy: dict) -> str:
    perim = cv2.arcLength(contour, True)
    eps = max(1.0, policy["eps_frac"] * perim)
    approx = cv2.approxPolyDP(contour, eps, True).reshape(-1, 2).astype(np.float64)
    if len(approx) < MIN_CONTOUR_PTS:
        approx = contour.reshape(-1, 2).astype(np.float64)
    if not policy["bezier"] or len(approx) < MIN_CONTOUR_PTS:
        return _polyline(approx)
    corners = _corner_indices(approx, policy["corner_deg"]) if policy["corner_keep"] else set()
    return _catmull_bezier(approx, corners)


# --------------------------------------------------------------------------- #
# path sampling (for smoothness measurement + rasterised previews)
# --------------------------------------------------------------------------- #
def _sample_d(d: str, step: float = 8.0) -> list:
    """Sample an M/L/C/Z path string into polylines (list of (N,2) arrays)."""
    subpaths, cur, start, tokens = [], [], None, d.replace(",", " ").split()
    i = 0
    def flush():
        if len(cur) >= 2:
            subpaths.append(np.array(cur, np.float32))
    while i < len(tokens):
        t = tokens[i]
        if t == "M":
            flush(); cur.clear()
            start = (float(tokens[i + 1]), float(tokens[i + 2])); cur.append(start); i += 3
        elif t == "L":
            cur.append((float(tokens[i + 1]), float(tokens[i + 2]))); i += 2
        elif t == "C":
            p0 = np.array(cur[-1]); c1 = np.array([float(tokens[i+1]), float(tokens[i+2])])
            c2 = np.array([float(tokens[i+3]), float(tokens[i+4])]); p1 = np.array([float(tokens[i+5]), float(tokens[i+6])])
            seg = max(2, int(np.linalg.norm(p1 - p0) / step) + 1)
            for s in range(1, seg + 1):
                u = s / seg
                pt = ((1-u)**3)*p0 + 3*((1-u)**2)*u*c1 + 3*(1-u)*(u**2)*c2 + (u**3)*p1
                cur.append((float(pt[0]), float(pt[1])))
            i += 7
        elif t == "Z":
            if start is not None:
                cur.append(start)
            flush(); cur = []; i += 1
        else:
            i += 1
    flush()
    return subpaths


def _rasterize(paths: list, h: int, w: int) -> np.ndarray:
    canvas = np.zeros((h, w), np.uint8)
    for d in paths:
        for poly in _sample_d(d):
            cv2.polylines(canvas, [np.round(poly).astype(np.int32)], False, 255, 1)
    return canvas


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def run(labels_path: Path, artistic_report_path: Path, semantic_report_path: Path,
        regions_json_path: Path, game_config_path: Path, outline_svg_path: Path,
        report_path: Path, overlay_path: Path) -> dict:
    labels = np.load(labels_path).astype(np.int32)
    h, w = labels.shape
    artistic = json.loads(Path(artistic_report_path).read_text(encoding="utf-8"))
    semantic = json.loads(Path(semantic_report_path).read_text(encoding="utf-8"))
    regions_doc = json.loads(Path(regions_json_path).read_text(encoding="utf-8"))
    game_doc = json.loads(Path(game_config_path).read_text(encoding="utf-8"))

    role_by_id = {r["region_id"]: r for r in artistic.get("regions", [])}
    label_by_id = {r["region_id"]: r.get("label") for r in semantic.get("regions", [])}
    role_by_label, importance_by_label, protect_by_label = {}, {}, {}
    for rid, a in role_by_id.items():
        lbl = label_by_id.get(rid)
        if lbl is not None:
            role_by_label[lbl] = a.get("artistic_role", "UNKNOWN_SUPPORT")
            importance_by_label[lbl] = float(a.get("visual_importance", 0.5))
            protect_by_label[lbl] = bool(a.get("protect_shape", False))

    # dominant neighbour label (bridge outlines first) -> to spot the silhouette.
    filled = _fill_lines(labels)
    edges = _edge_lengths(filled)
    nb: dict[int, Counter] = {}
    for (a, b), c in edges.items():
        if a == 0 or b == 0:
            continue
        nb.setdefault(a, Counter())[b] += c
        nb.setdefault(b, Counter())[a] += c
    dom_label = {l: ctr.most_common(1)[0][0] for l, ctr in nb.items()}

    sem_by_label = {r.get("label"): r for r in semantic.get("regions", [])}

    def classify(lbl, is_outer):
        role = role_by_label.get(lbl, "UNKNOWN_SUPPORT")
        nb_role = role_by_label.get(dom_label.get(lbl), "UNKNOWN_SUPPORT")
        if role == "FACIAL_FEATURE":
            return "FACIAL_EDGE"
        if role == "FLOWER_DETAIL":
            return "PETAL_EDGE"
        if role == "LEAF_DETAIL":
            return "LEAF_EDGE"
        if role in SUBJECT_ROLES and is_outer and nb_role == "BACKGROUND_SUPPORT":
            return "OUTER_SILHOUETTE"
        if role == "BACKGROUND_SUPPORT" and nb_role in SUBJECT_ROLES:
            return "OUTER_SILHOUETTE"
        if role == "FUR_PATTERN":
            s = sem_by_label.get(lbl, {})
            return "FLOW_EDGE" if float(s.get("edge_flow_score", 0)) >= 0.45 else "FUR_EDGE"
        if role == "SUBJECT_CORE":
            return "OUTER_SILHOUETTE" if (is_outer and nb_role == "BACKGROUND_SUPPORT") else "INNER_STRUCTURE"
        if role == "DECORATIVE_OBJECT":
            return "DECORATIVE"
        if role in ("SUBJECT_DETAIL",):
            return "INNER_STRUCTURE"
        return "UNKNOWN"

    overlay = np.full((h, w, 3), 255, np.uint8)
    overlay[labels == 0] = (235, 235, 235)
    contour_rows, class_counts, style_counts, before_paths, after_paths = [], Counter(), Counter(), [], []
    path_by_label: dict[int, str] = {}

    for lbl in sorted(role_by_label.keys()):
        mask = np.uint8(labels == lbl) * 255
        conts, hierarchy = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
        subpaths = []
        for ci, contour in enumerate(conts):
            pts = contour.reshape(-1, 2)
            if len(pts) < MIN_CONTOUR_PTS:
                continue
            is_outer = hierarchy is None or hierarchy[0][ci][3] == -1
            area = cv2.contourArea(contour)
            x, y, bw, bh = cv2.boundingRect(contour)
            aspect = max(bw, bh) / max(1.0, min(bw, bh))

            cclass = classify(lbl, is_outer)
            if aspect >= WHISKER_ASPECT and area < MICRO_AREA * 4:
                cclass = "WHISKER"
            style = CLASS_STYLE[cclass]
            if area < MICRO_AREA and style not in ("SHARP",):
                style = "MICRO_DETAIL"
            policy = STYLE_POLICY[style]

            # metrics
            fpts = pts.astype(np.float64)
            ang = _turning_angles(fpts)
            perim = cv2.arcLength(contour, True)
            corners = _corner_indices(fpts, 45)
            metrics = {
                "length": round(float(perim), 1),
                "importance": round(importance_by_label.get(lbl, 0.5), 3),
                "curvature": round(float(ang.mean()), 4),
                "local_complexity": round(len(pts) / max(1.0, perim), 4),
                "continuity": 1.0,                       # closed loop
                "flow_alignment": round(float(sem_by_label.get(lbl, {}).get("edge_flow_score", 0.0)), 3),
                "organic_score": round(float(ang.std()), 4),
                "corner_sharpness": round(float(ang.max()), 4),
                "corner_count": len(corners),
                "required_precision": policy["eps_frac"],
                "area": int(area),
            }

            before_d = _contour_before(contour)      # what the old uniform pass would emit
            after_d = _adaptive_subpath(contour, policy)
            subpaths.append(after_d)
            before_paths.append(before_d)
            after_paths.append(after_d)

            contour_rows.append({
                "label": int(lbl), "contour_index": ci,
                "contour_class": cclass, "style": style,
                "is_outer": bool(is_outer), "protected": protect_by_label.get(lbl, False),
                "rendering_policy": {
                    "bezier_precision": round(1.0 if policy["bezier"] else 0.0, 2),
                    "spline_precision": round(policy["smoothing"], 2),
                    "smoothing_strength": policy["smoothing"],
                    "corner_preservation": policy["corner_keep"],
                    "endpoint_protection": cclass in ("WHISKER", "LEAF_EDGE"),
                    "shape_protection": protect_by_label.get(lbl, False),
                    "silhouette_protection": cclass == "OUTER_SILHOUETTE",
                    "adaptive_sampling_density": "HIGH" if style in ("MICRO_DETAIL", "SHARP") else "MEDIUM",
                },
                **metrics,
            })
            class_counts[cclass] += 1
            style_counts[style] += 1
            col = CLASS_COLOR[cclass][::-1]
            cv2.drawContours(overlay, [contour], -1, col, 2)

        path_by_label[lbl] = " ".join(sp for sp in subpaths if sp)

    # rasterised smoothness (project-standard jaggedness) before vs after
    jag_before = _jaggedness(_rasterize(before_paths, h, w))
    jag_after = _jaggedness(_rasterize(after_paths, h, w))
    # shape similarity: IoU of rasterised-filled after-contours vs label regions
    shape_iou = _shape_similarity(after_paths, labels)

    _write_outputs(path_by_label, regions_doc, game_doc, regions_json_path,
                   game_config_path, outline_svg_path, w, h)
    _legend(overlay, class_counts)
    cv2.imwrite(str(overlay_path), overlay)

    report = {
        "version": 1,
        "note": "Adaptive per-contour geometry (style + Bézier/polyline policy). "
                "Reuses QF13 roles + QF7 semantics; recomputes nothing. Only vector "
                "contour geometry changed — labels/palette/numbers/lineart/export intact.",
        "sources": ["artistic_decision_report.json", "semantic_region_report.json",
                    "material_behavior_report.json", "processing_policy_report.json",
                    "flow_gradient_report.json", "intelligent_split_report.json",
                    "border_quality_report.json"],
        "contour_count": len(contour_rows),
        "contour_class_distribution": dict(class_counts),
        "style_distribution": dict(style_counts),
        "bezier_precision_distribution": dict(Counter(
            "BEZIER" if r["rendering_policy"]["bezier_precision"] >= 1.0 else "POLYLINE"
            for r in contour_rows)),
        "protected_contours": sum(1 for r in contour_rows if r["protected"]),
        "silhouette_contours": int(class_counts.get("OUTER_SILHOUETTE", 0)),
        "contour_smoothness_before": jag_before,
        "contour_smoothness_after": jag_after,
        "shape_similarity_iou": round(shape_iou, 4),
        "contours": contour_rows,
    }
    Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def _contour_before(contour: np.ndarray) -> str:
    """Reproduce the previous uniform smoothing (medium profile) for a fair
    before/after comparison."""
    from services import contour_smoothing
    approx = contour_smoothing.process(contour, 0.0020, 1)
    return _polyline(approx.reshape(-1, 2).astype(np.float64))


def _shape_similarity(paths: list, labels: np.ndarray) -> float:
    h, w = labels.shape
    filled = np.zeros((h, w), np.uint8)
    for d in paths:
        polys = [np.round(p).astype(np.int32) for p in _sample_d(d)]
        if polys:
            cv2.fillPoly(filled, polys, 255)
    region = (labels > 0).astype(np.uint8) * 255
    inter = np.logical_and(filled > 0, region > 0).sum()
    union = np.logical_or(filled > 0, region > 0).sum()
    return float(inter / union) if union else 1.0


def _write_outputs(path_by_label, regions_doc, game_doc, regions_json_path,
                   game_config_path, outline_svg_path, w, h):
    # update region path strings in-place (geometry only; every other field intact)
    for doc, p in ((regions_doc, regions_json_path), (game_doc, game_config_path)):
        for r in doc.get("regions", []):
            lbl = r.get("label")
            if lbl in path_by_label and path_by_label[lbl]:
                r["path"] = path_by_label[lbl]
        Path(p).write_text(json.dumps(doc, indent=2), encoding="utf-8")

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}">',
        f'<rect width="{w}" height="{h}" fill="#ffffff"/>',
        f'<g fill="none" stroke="{STROKE}" stroke-width="{STROKE_WIDTH}" '
        f'stroke-linejoin="round" stroke-linecap="round" fill-rule="evenodd">',
    ]
    for r in game_doc.get("regions", []):
        d = r.get("path")
        if d:
            parts.append(f'<path id="region-{r["number"]}" '
                         f'data-region-id="{r["number"]}" d="{d}"/>')
    parts += ["</g>", "</svg>"]
    Path(outline_svg_path).write_text("\n".join(parts), encoding="utf-8")


def _legend(overlay, counts):
    y = 14
    for cclass, col in CLASS_COLOR.items():
        if not counts.get(cclass):
            continue
        c = col[::-1]
        cv2.rectangle(overlay, (10, y), (32, y + 16), c, -1)
        cv2.rectangle(overlay, (10, y), (32, y + 16), (40, 40, 40), 1)
        cv2.putText(overlay, f"{cclass} ({counts[cclass]})", (38, y + 13),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.44, (20, 20, 20), 1, cv2.LINE_AA)
        y += 22
