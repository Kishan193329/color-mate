"""Human Visual Intelligence (HVI) Foundation — the first visual-reasoning layer.

Analysis ONLY. For every region this scores how a professional coloring-book artist perceives
it — ten perceptual scores — by reusing the brain reports (semantic / material / artistic /
contour), the review statistics, and the experience patterns. It generates **no borders**,
changes **no rendering / reveal / SVG**, and writes exactly one artifact:
`human_visual_report.json`.

The ten scores:
    visual_weight · recognition_weight · drawing_priority · tracing_priority · beauty_score ·
    fun_to_color_score · visual_complexity · viewer_attention · memory_importance ·
    artistic_importance

Every score is a future-learning-compatible Decision (core.decision): reason, confidence,
evidence, override_point, source. The `hvi.*` override points are seam-ready — a future
experience policy can override them with no change here (the v1 policy whitelists `review.*`
only, so today HVI scores are the static rules).
"""
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from core.decision import Decision, decide
from services import experience_policy

SCORES = ["visual_weight", "recognition_weight", "drawing_priority", "tracing_priority",
          "beauty_score", "fun_to_color_score", "visual_complexity", "viewer_attention",
          "memory_importance", "artistic_importance"]

FOCAL_SEMANTIC = {"FACE", "EYE", "EYES", "EYE_HIGHLIGHT", "PUPIL", "MOUTH", "LIPS", "NOSE",
                  "PETAL", "FLOWER_CENTER", "DECORATIVE", "WHISKER"}
BACKGROUND_SEMANTIC = {"BACKGROUND", "SKY", "BACKDROP", "WALL"}
ROLE_BASE = {"SUBJECT_FOCUS": 0.95, "SUBJECT_CORE": 0.90, "SUBJECT_DETAIL": 0.72,
             "SUBJECT_SUPPORT": 0.55, "FOREGROUND_SUPPORT": 0.45, "MIDGROUND_SUPPORT": 0.40,
             "BACKGROUND_SUPPORT": 0.15, "UNKNOWN_SUPPORT": 0.30}

# Heuristic perceptual layer -> moderate confidences (higher when grounded in a strong upstream
# signal, e.g. the artistic engine's visual_importance).
CONF = {"visual_weight": 0.50, "recognition_weight": 0.55, "drawing_priority": 0.50,
        "tracing_priority": 0.50, "beauty_score": 0.40, "fun_to_color_score": 0.45,
        "visual_complexity": 0.55, "viewer_attention": 0.45, "memory_importance": 0.45,
        "artistic_importance": 0.60}


def _clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else float(x)


def _load(p: Path):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _hsv(hexstr: str) -> tuple[float, float]:
    try:
        c = hexstr.lstrip("#")
        r, g, b = int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    except (ValueError, AttributeError, IndexError):
        return 0.0, 0.5
    mx, mn = max(r, g, b), min(r, g, b)
    val = mx / 255.0
    sat = 0.0 if mx == 0 else (mx - mn) / mx
    return sat, val


def _mid_term(af: float) -> float:
    """A pleasant-to-colour size band (peaks at a comfortable mid size)."""
    if af < 0.0008:
        return 0.15
    if af < 0.01:
        return 0.60
    if af < 0.10:
        return 0.90
    if af < 0.30:
        return 0.70
    return 0.50


def _big_term(af: float) -> float:
    return _clamp01(af / 0.15)


# --------------------------------------------------------------------------- #
# raw composites reused by several scores (kept consistent, no seam)
# --------------------------------------------------------------------------- #
def _recognition(x: dict) -> float:
    foc = 1.0 if x["vp"] == "CRITICAL" else (0.6 if x["vp"] == "HIGH" else (0.5 if x["is_focal"] else 0.0))
    return _clamp01(0.55 * x["role_base"] + 0.25 * x["visual_importance"] + 0.20 * foc)


def _attention(x: dict, rec: float) -> float:
    return _clamp01(0.35 * x["centrality"] + 0.30 * x["pop"] + 0.20 * rec + 0.15 * _big_term(x["area_frac"]))


def _complexity(x: dict) -> float:
    detail = 1.0 if (x["area_frac"] < 0.005 and x["local_complexity"] > 0.2) else 0.0
    return _clamp01(0.60 * min(1.0, x["local_complexity"] / 0.4)
                    + 0.25 * min(1.0, x["corner_count"] / 300.0) + 0.15 * detail)


# --------------------------------------------------------------------------- #
# the ten scores (each a Decision)
# --------------------------------------------------------------------------- #
def _score_decisions(x: dict) -> list[Decision]:
    rec = _recognition(x)
    att = _attention(x, rec)
    vc = _complexity(x)
    clutter = min(1.0, x["local_complexity"] / 0.4)
    big = _big_term(x["area_frac"])
    mid = _mid_term(x["area_frac"])
    structural = _clamp01(0.5 * x["is_outer"] + 0.3 * x["protect_shape"]
                          + 0.2 * (1.0 if x["vp"] in ("CRITICAL", "HIGH") else 0.0))
    color_harmony = _clamp01(0.5 + 0.5 * x["sat"]) * (1.0 - 0.3 * min(1.0, x["color_variance"] / 20.0))
    distinct = _clamp01(0.5 * x["pop"] + 0.5 * x["is_focal"])

    def D(name, value, reason, evidence):
        return decide(f"hvi.{name}", round(_clamp01(value), 3), reason=reason,
                      confidence=CONF[name], evidence=evidence, override_point=f"hvi.{name}")

    return [
        D("visual_weight", 0.55 * big + 0.25 * x["pop"] + 0.20 * x["centrality"],
          "dominates when large, saturated and central",
          {"area_frac": round(x["area_frac"], 4), "pop": round(x["pop"], 3),
           "centrality": round(x["centrality"], 3)}),
        D("recognition_weight", rec,
          "identifies the subject: driven by artistic role, visual importance and focal/validation priority",
          {"role_base": x["role_base"], "visual_importance": x["visual_importance"],
           "validation_priority": x["vp"], "is_focal": x["is_focal"]}),
        D("drawing_priority", 0.35 * structural + 0.35 * rec + 0.20 * big + 0.10 * x["contour_importance"],
          "what an artist lays down first: structure (silhouette/protected) + recognition + size",
          {"structural": round(structural, 3), "recognition": round(rec, 3), "big": round(big, 3),
           "is_outer": x["is_outer"]}),
        D("tracing_priority", 0.5 * rec + 0.25 * x["protect_shape"] + 0.25 * min(1.0, x["local_complexity"] / 0.4),
          "how carefully the outline must be followed (recognition + protected shape + boundary detail) "
          "— a perceptual priority, not any tracing/rendering",
          {"recognition": round(rec, 3), "protect_shape": x["protect_shape"],
           "local_complexity": round(x["local_complexity"], 3)}),
        D("beauty_score", 0.35 * color_harmony + 0.30 * min(1.0, x["organic"] / 0.3) + 0.20 * mid + 0.15 * (1.0 - clutter),
          "aesthetic appeal: colour harmony, organic elegance, pleasant size, low clutter",
          {"color_harmony": round(color_harmony, 3), "organic": round(x["organic"], 3),
           "mid_size": round(mid, 3), "clutter": round(clutter, 3)}),
        D("fun_to_color_score", mid - 0.35 * clutter + 0.10 * x["is_subject"] - (0.15 if x["area_frac"] < 0.0008 else 0.0),
          "satisfying to fill: a comfortable size, clean boundary, part of the subject; specks are no fun",
          {"mid_size": round(mid, 3), "clutter": round(clutter, 3), "is_subject": x["is_subject"],
           "area_frac": round(x["area_frac"], 4)}),
        D("visual_complexity", vc,
          "busy-ness of the region: local boundary complexity + corner density + small-but-detailed",
          {"local_complexity": round(x["local_complexity"], 3), "corner_count": x["corner_count"],
           "area_frac": round(x["area_frac"], 4)}),
        D("viewer_attention", att,
          "where the eye lands: centrality + colour pop + recognition + size (saliency)",
          {"centrality": round(x["centrality"], 3), "pop": round(x["pop"], 3),
           "recognition": round(rec, 3)}),
        D("memory_importance", 0.5 * rec + 0.3 * distinct + 0.2 * att,
          "how iconic/memorable: recognition + distinctiveness (unusual colour / focal) + attention",
          {"recognition": round(rec, 3), "distinctiveness": round(distinct, 3),
           "attention": round(att, 3)}),
        D("artistic_importance", 0.5 * x["visual_importance"] + 0.3 * (x["protect"] / 5.0) + 0.2 * x["is_focal"],
          "'an illustrator would guard this': artistic visual importance + protection flags + focal",
          {"visual_importance": x["visual_importance"], "protect_flags": x["protect"],
           "is_focal": x["is_focal"]}),
    ]


# --------------------------------------------------------------------------- #
# feature extraction + main
# --------------------------------------------------------------------------- #
def _features(gc_r: dict, sem: dict, mat: dict, art: dict, cont: dict, w: int, h: int) -> dict:
    area = int(gc_r.get("area", 0))
    bbox = (gc_r.get("bbox") or [0, 0, 0, 0])[:4]
    bx, by, bw, bh = (list(bbox) + [0, 0, 0, 0])[:4]
    cx, cy = bx + bw / 2.0, by + bh / 2.0
    diag = ((w ** 2 + h ** 2) ** 0.5) / 2.0 or 1.0
    centrality = _clamp01(1.0 - (((cx - w / 2.0) ** 2 + (cy - h / 2.0) ** 2) ** 0.5) / diag)
    sat, val = _hsv(gc_r.get("sampled_color") or "#808080")
    role = art.get("artistic_role", "UNKNOWN_SUPPORT")
    sem_class = sem.get("semantic_class") or art.get("semantic_class") or gc_r.get("semantic_label") or "UNKNOWN"
    vp = art.get("validation_priority")
    is_bg = (str(sem_class).upper() in BACKGROUND_SEMANTIC) or role == "BACKGROUND_SUPPORT"
    is_focal = (str(sem_class).upper() in FOCAL_SEMANTIC) or vp == "CRITICAL"
    return {
        "area": area, "area_frac": area / float(w * h or 1), "centrality": centrality,
        "sat": sat, "val": val, "pop": _clamp01(0.7 * sat + 0.3 * min(1.0, abs(val - 0.5) * 2)),
        "semantic_class": sem_class, "role": role, "role_base": ROLE_BASE.get(role, 0.4),
        "visual_importance": float(art.get("visual_importance", 0.5) or 0.5),
        "vp": vp, "protect": sum(1 for k in ("protect_shape", "protect_highlight", "protect_shadow",
                                             "protect_texture", "protect_flow") if art.get(k)),
        "protect_shape": 1.0 if art.get("protect_shape") else 0.0,
        "is_bg": is_bg, "is_focal": is_focal,
        "is_subject": 1.0 if (str(role).startswith("SUBJECT") or art.get("embedded_in_subject") or not is_bg) else 0.0,
        "local_complexity": float(cont.get("local_complexity", 0.0) or 0.0),
        "organic": float(cont.get("organic_score", 0.0) or 0.0),
        "corner_count": int(cont.get("corner_count", 0) or 0),
        "is_outer": 1.0 if cont.get("is_outer") else 0.0,
        "contour_importance": float(cont.get("importance", 0.0) or 0.0),
        "noise_removal": float(mat.get("noise_removal_score", 0.0) or 0.0),
        "texture_score": float(sem.get("texture_score", 0.0) or 0.0),
        "color_variance": float(sem.get("color_variance", 0.0) or 0.0),
    }


def _aggregate_contours(contour_report: dict) -> dict:
    """One aggregate contour-geometry record per region label (max complexity / organic,
    summed corners, any-outer)."""
    by_label: dict = defaultdict(lambda: {"local_complexity": 0.0, "organic_score": 0.0,
                                          "corner_count": 0, "is_outer": False, "importance": 0.0})
    for c in (contour_report or {}).get("contours", []):
        lbl = c.get("label")
        if lbl is None:
            continue
        a = by_label[lbl]
        a["local_complexity"] = max(a["local_complexity"], float(c.get("local_complexity", 0.0) or 0.0))
        a["organic_score"] = max(a["organic_score"], float(c.get("organic_score", 0.0) or 0.0))
        a["corner_count"] += int(c.get("corner_count", 0) or 0)
        a["is_outer"] = a["is_outer"] or bool(c.get("is_outer"))
        a["importance"] = max(a["importance"], float(c.get("importance", 0.0) or 0.0))
    return by_label


def _experience_context() -> dict:
    pat = experience_policy.patterns()
    by_cat = pat.get("by_reject_category") or {}
    dominant = max(by_cat, key=by_cat.get) if by_cat else None
    return {"available": bool(pat), "totals": pat.get("totals", {}),
            "by_reject_category": by_cat, "dominant_reject_category": dominant}


def analyze(folder: Path, out_path: Path | None = None) -> dict:
    """Score every region on the ten perceptual dimensions. Reads the brain reports + review +
    experience patterns; writes human_visual_report.json. Analysis only — no rendering, border,
    reveal, SVG, or region change."""
    folder = Path(folder)
    gc = _load(folder / "game_config.json") or {}
    regions = gc.get("regions", [])
    w, h = int(gc.get("width") or 0), int(gc.get("height") or 0)

    semantic = _load(folder / "semantic_region_report.json") or {}
    material = _load(folder / "material_behavior_report.json") or {}
    artistic = _load(folder / "artistic_decision_report.json") or {}
    contour = _aggregate_contours(_load(folder / "contour_character_report.json") or {})
    review = _load(folder / "review.json") or {}

    sem_by_label, art_by_label, rid_to_label = {}, {}, {}
    for r in semantic.get("regions", []):
        if r.get("label") is not None:
            sem_by_label[r["label"]] = r
            rid_to_label[r.get("region_id")] = r["label"]
    for r in artistic.get("regions", []):
        if r.get("label") is not None:
            art_by_label[r["label"]] = r
            rid_to_label.setdefault(r.get("region_id"), r["label"])
    mat_by_label = {}
    for r in material.get("regions", []):
        lbl = rid_to_label.get(r.get("region_id"))
        if lbl is not None:
            mat_by_label[lbl] = r

    exp_ctx = _experience_context()
    review_ctx = {k: (review.get("statistics", {}) or {}).get(k)
                  for k in ("complexity_score", "artistic_score", "border_quality_score",
                            "region_count", "estimated_difficulty")}

    out_regions = []
    sums = defaultdict(float)
    for gc_r in regions:
        lbl = gc_r.get("label")
        x = _features(gc_r, sem_by_label.get(lbl, {}), mat_by_label.get(lbl, {}),
                      art_by_label.get(lbl, {}), contour.get(lbl, {}), w, h)
        decisions = _score_decisions(x)
        scores = {d.key.split(".", 1)[1]: d.value for d in decisions}
        for k, v in scores.items():
            sums[k] += v
        out_regions.append({
            "label": lbl, "id": gc_r.get("id"), "number": gc_r.get("number"),
            "semantic_class": x["semantic_class"], "artistic_role": x["role"],
            "area": x["area"], "area_pct": round(100.0 * x["area_frac"], 3),
            "is_focal": bool(x["is_focal"]), "is_subject": bool(x["is_subject"]),
            "experience_dominant_reject": exp_ctx["dominant_reject_category"],
            "scores": scores,
            "decisions": [d.to_dict() for d in decisions],
        })

    n = len(out_regions)
    means = {k: round(sums[k] / n, 3) for k in SCORES} if n else {}
    report = {
        "version": 1,
        "note": "Human Visual Intelligence — visual reasoning ONLY. Scores every region on ten "
                "perceptual dimensions as a coloring-book artist would. Reuses the semantic / "
                "material / artistic / contour reports + review statistics + experience patterns. "
                "Generates no borders and changes no rendering, reveal, SVG, palette, numbers or "
                "regions. Every score is a core.decision Decision (reason/confidence/evidence/"
                "override_point/source); hvi.* override points are seam-ready for a future "
                "experience policy.",
        "sources": ["semantic_region_report.json", "material_behavior_report.json",
                    "artistic_decision_report.json", "contour_character_report.json",
                    "review.json", "experience/patterns/patterns.json"],
        "score_names": SCORES,
        "region_count": n,
        "means": means,
        "experience_context": exp_ctx,
        "review_context": review_ctx,
        "regions": out_regions,
    }
    out = Path(out_path) if out_path else (folder / "human_visual_report.json")
    out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
