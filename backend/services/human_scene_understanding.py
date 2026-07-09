"""Human Scene Understanding (HSU) Foundation — whole-image reasoning.

Before border decisions, understand the picture the way a human does: what the main subject
is, what is background / foreground, where the focal areas are, the object relationships,
depth, lighting and composition. Analysis ONLY — it reads existing reports and writes exactly
one artifact, `human_scene_report.json`. It changes no border rendering, no regions, no reveal,
no SVG, no palette, no numbering.

Inputs: image_type_report, semantic_region_report, material_behavior_report,
artistic_decision_report, human_visual_report, review.json.

Every field is a `core.decision.Decision` (value / reason / confidence / evidence /
override_point / source); `hsu.*` override points are seam-ready for a future policy.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from core.decision import Decision, decide

FIELDS = ["main_subject_regions", "secondary_subject_regions", "background_regions",
          "foreground_regions", "focal_regions", "low_importance_regions",
          "subject_background_separation_score", "composition_score", "scene_complexity",
          "depth_layers", "lighting_direction_estimate", "eye_flow_direction",
          "scene_type", "recommended_scene_strategy"]

STRATEGIES = ["SUBJECT_FOCUS", "BACKGROUND_SIMPLIFY", "PORTRAIT_PROTECT_FACE",
              "LANDSCAPE_DEPTH_LAYERS", "MANDALA_KEEP_SYMMETRY", "SKETCH_CLEAN_LINES",
              "OBJECT_CENTERED", "UNKNOWN_CONSERVATIVE"]

IMAGE_TYPE_SCENE = {"Portrait": "portrait", "Landscape": "landscape", "Object": "object",
                    "Mandala": "mandala", "Sketch": "sketch", "Logo": "logo",
                    "Anime": "illustration", "Illustration": "illustration"}

# region-classification thresholds (on HVI scores)
MAIN_REC = 0.66
SUBJ_REC = 0.42
BG_REC = 0.30
FOCAL_ATT = 0.60


def _clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else float(x)


def _load(p: Path):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _val(hexstr: str) -> float:
    try:
        c = hexstr.lstrip("#")
        return max(int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)) / 255.0
    except (ValueError, AttributeError, IndexError):
        return 0.5


def _mean(xs, default=0.0):
    xs = [x for x in xs if isinstance(x, (int, float))]
    return sum(xs) / len(xs) if xs else default


def _dir_label(dx: float, dy: float) -> str:
    """8-way compass label for a vector in image coords with +y already flipped to point up."""
    a = (math.degrees(math.atan2(dy, dx)) + 360) % 360
    if a < 22.5 or a >= 337.5:
        return "right"
    for lo, name in [(67.5, "top_right"), (112.5, "top"), (157.5, "top_left"), (202.5, "left"),
                     (247.5, "bottom_left"), (292.5, "bottom"), (337.5, "bottom_right")]:
        if a < lo:
            return name
    return "right"


def analyze(folder: Path, out_path: Path | None = None) -> dict:
    folder = Path(folder)
    gc = _load(folder / "game_config.json") or {}
    w, h = float(gc.get("width") or 1), float(gc.get("height") or 1)
    gc_by_label = {r.get("label"): r for r in gc.get("regions", [])}

    itr = _load(folder / "image_type_report.json") or {}
    feats = itr.get("features", {}) or {}
    hvi = _load(folder / "human_visual_report.json") or {}
    review = _load(folder / "review.json") or {}
    material = _load(folder / "material_behavior_report.json") or {}
    semantic = _load(folder / "semantic_region_report.json") or {}

    # material behaviour per label (via region_id -> label from semantic/artistic-style ids)
    rid_to_label = {}
    for r in semantic.get("regions", []):
        if r.get("label") is not None:
            rid_to_label[r.get("region_id")] = r["label"]
    mat_by_label = {rid_to_label.get(r.get("region_id")): r for r in material.get("regions", [])
                    if rid_to_label.get(r.get("region_id")) is not None}

    # per-region joined features
    regs = []
    for r in hvi.get("regions", []):
        lbl = r.get("label")
        s = r.get("scores", {})
        gcr = gc_by_label.get(lbl, {})
        bx, by, bw, bh = (list(gcr.get("bbox") or [0, 0, 0, 0]) + [0, 0, 0, 0])[:4]
        regs.append({
            "label": lbl, "area_frac": (r.get("area_pct", 0.0) or 0.0) / 100.0,
            "cx": (bx + bw / 2.0) / w, "cy": (by + bh / 2.0) / h,
            "value": _val(gcr.get("sampled_color") or "#808080"),
            "rec": s.get("recognition_weight", 0.0), "att": s.get("viewer_attention", 0.0),
            "beauty": s.get("beauty_score", 0.0), "fun": s.get("fun_to_color_score", 0.0),
            "tracing": s.get("tracing_priority", 0.0), "drawing": s.get("drawing_priority", 0.0),
            "artistic": s.get("artistic_importance", 0.0), "memory": s.get("memory_importance", 0.0),
            "vweight": s.get("visual_weight", 0.0), "vcomplex": s.get("visual_complexity", 0.0),
            "role": r.get("artistic_role", "UNKNOWN"), "semantic": r.get("semantic_class", "UNKNOWN"),
            "is_focal": bool(r.get("is_focal")), "is_subject": bool(r.get("is_subject")),
            "behavior": (mat_by_label.get(lbl, {}) or {}).get("behavior_class"),
        })
    n = len(regs)

    # ---- region classification (raw) ---------------------------------------
    main = [r["label"] for r in regs if r["rec"] >= MAIN_REC or (r["role"] == "SUBJECT_CORE" and r["rec"] >= 0.5)]
    mset = set(main)
    secondary = [r["label"] for r in regs if r["label"] not in mset and r["is_subject"]
                 and SUBJ_REC <= r["rec"] < MAIN_REC]
    sset = set(secondary)
    background = [r["label"] for r in regs if r["label"] not in mset and r["label"] not in sset
                  and (r["rec"] <= BG_REC or r["semantic"] == "BACKGROUND" or r["role"] == "BACKGROUND_SUPPORT")]
    bset = set(background)
    foreground = [r["label"] for r in regs if r["label"] not in bset and r["cy"] >= 0.55 and r["area_frac"] >= 0.015]
    focal = [r["label"] for r in regs if r["is_focal"] or r["att"] >= FOCAL_ATT or r["memory"] >= 0.55]
    low_imp = [r["label"] for r in regs if r["rec"] <= 0.30 and r["att"] <= 0.30
               and r["artistic"] <= 0.35 and r["fun"] <= 0.35]

    subj_regs = [r for r in regs if r["label"] in mset or r["label"] in sset]
    bg_regs = [r for r in regs if r["label"] in bset]

    # ---- scores ------------------------------------------------------------
    rec_gap = _mean([r["rec"] for r in subj_regs]) - _mean([r["rec"] for r in bg_regs])
    att_gap = _mean([r["att"] for r in subj_regs]) - _mean([r["att"] for r in bg_regs])
    separation = _clamp01(1.3 * (0.6 * rec_gap + 0.4 * att_gap)) if (subj_regs and bg_regs) else 0.35

    main_regs = [r for r in regs if r["label"] in mset]
    dominant_area = max((r["area_frac"] for r in main_regs), default=0.0)
    if main_regs:
        mcx = _mean([r["cx"] for r in main_regs], 0.5)
        mcy = _mean([r["cy"] for r in main_regs], 0.5)
        placement = _clamp01(1.0 - 2.0 * math.hypot(mcx - 0.5, mcy - 0.5))
        central_main = math.hypot(mcx - 0.5, mcy - 0.5) < 0.28
    else:
        placement, central_main = 0.3, False
    has_clear_focal = 1.0 if focal and max((r["att"] for r in regs if r["label"] in set(focal)), default=0) >= 0.6 else 0.4

    review_cx = (review.get("statistics", {}) or {}).get("complexity_score")
    hvi_vc = (hvi.get("means", {}) or {}).get("visual_complexity", 0.0)
    parts = [(0.45, hvi_vc), (0.25, min(1.0, n / 200.0))]      # review is optional
    if isinstance(review_cx, (int, float)):
        parts.append((0.30, review_cx / 100.0))
    wsum = sum(wt for wt, _ in parts) or 1.0
    scene_complexity = _clamp01(sum(wt * val for wt, val in parts) / wsum)
    composition = _clamp01(0.35 * has_clear_focal + 0.25 * placement + 0.25 * separation
                           + 0.15 * (1.0 - scene_complexity))

    # ---- depth layers ------------------------------------------------------
    layers = []
    if background:
        layers.append("background")
    if secondary:
        layers.append("midground")
    if main:
        layers.append("subject")
    if foreground:
        layers.append("foreground")
    depth = max(1, len(layers))

    # ---- lighting ----------------------------------------------------------
    mv = _mean([r["value"] for r in regs], 0.5)
    bright = [(r["cx"], r["cy"], r["area_frac"]) for r in regs if r["value"] >= mv + 0.04]
    dark = [(r["cx"], r["cy"], r["area_frac"]) for r in regs if r["value"] <= mv - 0.04]

    def _wc(pts):
        tw = sum(a for _, _, a in pts) or 1.0
        return sum(x * a for x, _, a in pts) / tw, sum(y * a for _, y, a in pts) / tw

    if bright and dark:
        bx_, by_ = _wc(bright)
        dx_, dy_ = _wc(dark)
        vx, vy = bx_ - dx_, -(by_ - dy_)          # flip y so +y = up
        lighting = _dir_label(vx, vy) if math.hypot(vx, vy) >= 0.06 else "diffuse"
    else:
        lighting = "diffuse"

    # ---- scene type --------------------------------------------------------
    itype = itr.get("image_type", "Illustration")
    it_conf = float(itr.get("confidence", 0.5) or 0.5)
    skin, cskin = feats.get("skin_frac", 0.0), feats.get("center_skin", 0.0)
    base_scene = IMAGE_TYPE_SCENE.get(itype, "illustration")
    has_face = skin >= 0.12 and cskin >= 0.08
    if base_scene in ("illustration", "object", "landscape") and has_face and main:
        scene_type = "portrait"
    elif base_scene == "illustration" and background and len(background) >= len(main) + 3 \
            and dominant_area < 0.15:
        scene_type = "scene"
    else:
        scene_type = base_scene

    # ---- eye flow ----------------------------------------------------------
    fregs = [r for r in regs if r["label"] in set(focal)]
    if scene_type == "mandala":
        eye_flow = "radial"
    elif not fregs:
        eye_flow = "diffuse"
    else:
        xs, ys = [r["cx"] for r in fregs], [r["cy"] for r in fregs]
        xr, yr = (max(xs) - min(xs)), (max(ys) - min(ys))
        if len(fregs) <= 2 and abs(_mean(xs, 0.5) - 0.5) < 0.22 and abs(_mean(ys, 0.5) - 0.5) < 0.25:
            eye_flow = "center_focus"
        elif xr >= yr * 1.3:
            eye_flow = "left_to_right"
        elif yr >= xr * 1.3:
            eye_flow = "top_to_bottom"
        else:
            eye_flow = "z_pattern"

    # ---- recommended strategy ---------------------------------------------
    bg_share = (len(background) + len(low_imp)) / n if n else 0.0
    if it_conf < 0.35 and separation < 0.35 and not main:
        strategy = "UNKNOWN_CONSERVATIVE"
    elif scene_type == "mandala":
        strategy = "MANDALA_KEEP_SYMMETRY"
    elif scene_type in ("sketch", "logo"):
        strategy = "SKETCH_CLEAN_LINES"
    elif scene_type == "portrait" or (has_face and focal and main):
        strategy = "PORTRAIT_PROTECT_FACE"
    elif scene_type in ("landscape", "scene") and depth >= 3 and dominant_area < 0.20:
        strategy = "LANDSCAPE_DEPTH_LAYERS"
    elif scene_type == "object" or (main and dominant_area >= 0.25 and len(main) <= 3 and central_main):
        strategy = "OBJECT_CENTERED"
    elif main and separation >= 0.45:
        strategy = "SUBJECT_FOCUS"
    elif bg_share >= 0.40 and separation < 0.45:
        strategy = "BACKGROUND_SIMPLIFY"
    else:
        strategy = "UNKNOWN_CONSERVATIVE"

    # ---- build the fourteen Decisions -------------------------------------
    def D(field, value, reason, confidence, evidence):
        return decide(f"hsu.{field}", value, reason=reason, confidence=confidence,
                      evidence=evidence, override_point=f"hsu.{field}")

    decisions = {
        "main_subject_regions": D("main_subject_regions", main,
            "high recognition or SUBJECT_CORE regions", 0.55,
            {"count": len(main), "rec_threshold": MAIN_REC, "dominant_area_frac": round(dominant_area, 3)}),
        "secondary_subject_regions": D("secondary_subject_regions", secondary,
            "subject regions with moderate recognition", 0.5,
            {"count": len(secondary), "rec_band": [SUBJ_REC, MAIN_REC]}),
        "background_regions": D("background_regions", background,
            "low recognition or BACKGROUND semantic / BACKGROUND_SUPPORT role", 0.55,
            {"count": len(background), "rec_threshold": BG_REC}),
        "foreground_regions": D("foreground_regions", foreground,
            "non-background, sizeable, low in frame (near-camera heuristic)", 0.4,
            {"count": len(foreground), "cy_min": 0.55, "area_frac_min": 0.015}),
        "focal_regions": D("focal_regions", focal,
            "focal features or high viewer attention / memory importance", 0.55,
            {"count": len(focal), "attention_threshold": FOCAL_ATT}),
        "low_importance_regions": D("low_importance_regions", low_imp,
            "low recognition + attention + artistic importance + fun (declutter candidates)", 0.55,
            {"count": len(low_imp)}),
        "subject_background_separation_score": D("subject_background_separation_score", round(separation, 3),
            "recognition + attention gap between subject and background", 0.55,
            {"rec_gap": round(rec_gap, 3), "att_gap": round(att_gap, 3),
             "subject_regions": len(subj_regs), "background_regions": len(bg_regs)}),
        "composition_score": D("composition_score", round(composition, 3),
            "clear focal + subject placement + subject/background separation + low clutter", 0.45,
            {"has_clear_focal": has_clear_focal, "placement": round(placement, 3),
             "separation": round(separation, 3), "scene_complexity": round(scene_complexity, 3)}),
        "scene_complexity": D("scene_complexity", round(scene_complexity, 3),
            "review complexity + HVI mean visual complexity + region count", 0.55,
            {"review_complexity": review_cx, "region_count": n}),
        "depth_layers": D("depth_layers", depth,
            "distinct depth bands present (background/midground/subject/foreground)", 0.4,
            {"layers": layers}),
        "lighting_direction_estimate": D("lighting_direction_estimate", lighting,
            "direction from the dark-region centroid toward the bright-region centroid", 0.3,
            {"mean_value": round(mv, 3), "bright_regions": len(bright), "dark_regions": len(dark)}),
        "eye_flow_direction": D("eye_flow_direction", eye_flow,
            "spatial arrangement of the focal/high-attention regions", 0.35,
            {"focal_regions": len(fregs), "scene_type": scene_type}),
        "scene_type": D("scene_type", scene_type,
            f"image-type router ('{itype}', conf {it_conf:.2f}) refined by skin / subject signals", it_conf,
            {"image_type": itype, "runner_up": itr.get("runner_up"), "skin_frac": skin,
             "center_skin": cskin, "has_face": has_face}),
        "recommended_scene_strategy": D("recommended_scene_strategy", strategy,
            f"derived from scene_type='{scene_type}', separation {separation:.2f}, depth {depth}",
            round(_clamp01(0.35 + 0.4 * it_conf + 0.25 * separation), 3),
            {"scene_type": scene_type, "separation": round(separation, 3), "depth_layers": depth,
             "dominant_area_frac": round(dominant_area, 3), "background_share": round(bg_share, 3),
             "has_face": has_face}),
    }

    report = {
        "version": 1,
        "note": "Human Scene Understanding — whole-image reasoning ONLY. Reads the image-type, "
                "semantic, material, artistic, HVI and review reports and writes this one report. "
                "Changes no border rendering, regions, reveal, SVG, palette or numbering. Every "
                "field is a core.decision Decision; hsu.* override points are seam-ready.",
        "sources": ["image_type_report.json", "semantic_region_report.json",
                    "material_behavior_report.json", "artistic_decision_report.json",
                    "human_visual_report.json", "review.json"],
        "image_type": {"type": itype, "confidence": it_conf, "runner_up": itr.get("runner_up")},
        "region_count": n,
        "fields": FIELDS,
        "strategies": STRATEGIES,
        "scene": {k: v.value for k, v in decisions.items()},
        "decisions": {k: v.to_dict() for k, v in decisions.items()},
    }
    out = Path(out_path) if out_path else (folder / "human_scene_report.json")
    out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
