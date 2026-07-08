"""Artistic Border Intelligence Engine — border *judgement* (intelligence only).

Edge detection and segmentation are already done and are NOT touched here. This
engine asks a different question: for every border segment that already exists,
what would an illustrator do with it? It scores each segment on six artistic
dimensions and decides one action —

    KEEP · SIMPLIFY · EMPHASIZE · REMOVE · THICKEN · THIN · SOFTEN

— reusing the brain reports that upstream engines already wrote (no recompute):

  * QF14 `contour_character_report.json` — the border SEGMENTS themselves (one
    row per region contour) with class / style / geometry (length, curvature,
    local complexity, organic-ness, corner count, area) and protection flags.
  * QF13 `artistic_decision_report.json` — per-region artistic_role,
    visual_importance, protect_* flags, validation_priority.
  * QF8  `material_behavior_report.json`  — behavior_class + noise_removal_score.
  * QF7  `semantic_region_report.json`    — semantic_class + texture / flow scores.

It writes ONE artifact, `artistic_border_report.json`, and changes nothing else:
no line art, borders, geometry, labels, palette, numbers, colours, SVG, or export.
There is deliberately **no rendering and no border modification** — this is the
intelligence layer a future rendering pass will obey.

Illustrator intent the actions encode:
  Hair   — preserve flow (SOFTEN), remove noise (REMOVE), keep elegant strands (KEEP/EMPHASIZE)
  Face   — protect eyes/lips (EMPHASIZE), simplify cheeks (SIMPLIFY)
  Flower — protect petals (SOFTEN), enhance centre (EMPHASIZE)
  Leaves — simplify veins (SIMPLIFY)
  Clothes— keep major folds (KEEP), remove micro-wrinkles (REMOVE)
  Accessories — protect decorative details (KEEP/EMPHASIZE)
"""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

MICRO_AREA = 400          # contour area below this reads as a micro-fragment (matches QF14)
# A removable noise speck is small in BOTH area and length. The length floor is what
# saves an elegant strand or a whisker (thin but long: tiny area, large length) from
# being mistaken for a speck. Region-level `protected` guards the region's SHAPE — not
# a sub-pixel interior fragment inside it — so removal is gated on these, not `protected`.
NOISE_FLOOR_AREA = 30
NOISE_FLOOR_LEN = 40
FOCAL_CLASSES = {"FACIAL_EDGE", "PETAL_EDGE", "DECORATIVE"}
# Classes whose small contours are signature features, never noise to remove.
NEVER_REMOVE = {"FACIAL_EDGE", "OUTER_SILHOUETTE", "PETAL_EDGE", "WHISKER"}
FLOW_CLASSES = {"FLOW_EDGE", "FUR_EDGE", "PETAL_EDGE", "OUTER_SILHOUETTE"}
ORGANIC_STYLES = {"ORGANIC", "SOFT", "TAPERED"}


def _clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else float(x)


def _load(path: Path) -> dict:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


# --------------------------------------------------------------------------- #
# per-segment scoring — every score normalised to [0, 1]
# --------------------------------------------------------------------------- #
def _visual_importance(s: dict) -> float:
    """How much the segment matters to the picture as a whole."""
    v = float(s["importance"])                       # QF13 visual_importance for the region
    if s["contour_class"] == "OUTER_SILHOUETTE":
        v = max(v, 0.85)
    if s["contour_class"] == "FACIAL_EDGE":
        v = max(v, 0.90)
    v += 0.15 * s["len_norm"]                         # longer lines carry more of the image
    if s["area"] < MICRO_AREA:
        v -= 0.12                                     # a speck rarely matters on its own
    return round(_clamp01(v), 3)


def _colouring_enjoyment(s: dict) -> float:
    """How satisfying this region is to actually paint. A pleasant mid-sized shape
    scores high; a tiny speck or an overwhelming jagged mess scores low."""
    area = s["area"]
    if area < MICRO_AREA:
        a_term = 0.15
    elif area < 1500:
        a_term = 0.60
    elif area < 20000:
        a_term = 0.90
    elif area < 80000:
        a_term = 0.70
    else:
        a_term = 0.50                                 # a huge flat expanse is easy but dull
    comp_pen = _clamp01((s["local_complexity"] - 0.35) / 0.20)   # jagged borders are tedious
    e = a_term - 0.35 * comp_pen
    if s["contour_class"] == "OUTER_SILHOUETTE":
        e += 0.10                                     # a clean silhouette is fun to fill
    if area < MICRO_AREA and s["contour_class"] in ("FUR_EDGE", "INNER_STRUCTURE"):
        e -= 0.10
    return round(_clamp01(e), 3)


def _structural_importance(s: dict) -> float:
    """How much the segment defines the drawing's structure (silhouette / major shape)."""
    v = 0.0
    if s["is_outer"]:
        v += 0.35
    cls = s["contour_class"]
    if cls == "OUTER_SILHOUETTE":
        v += 0.45
    elif cls == "FACIAL_EDGE":
        v += 0.25                                     # eyes/nose/mouth structure a face
    if s["protect_shape"]:
        v += 0.20
    if s["validation_priority"] == "CRITICAL":
        v += 0.20
    elif s["validation_priority"] == "HIGH":
        v += 0.10
    v += 0.20 * s["len_norm"]
    if s["area"] < MICRO_AREA:
        v -= 0.25
    if cls in ("FUR_EDGE", "INNER_STRUCTURE", "DECORATIVE") and not s["is_outer"]:
        v -= 0.10                                     # inner micro-detail isn't structural
    return round(_clamp01(v), 3)


def _artistic_importance(s: dict) -> float:
    """The 'an illustrator would guard this' value — focal features and elegant edges."""
    cls = s["contour_class"]
    base = {
        "FACIAL_EDGE": 0.55, "PETAL_EDGE": 0.50, "DECORATIVE": 0.40,
        "OUTER_SILHOUETTE": 0.35, "WHISKER": 0.35, "FLOW_EDGE": 0.30,
        "LEAF_EDGE": 0.25, "FUR_EDGE": 0.20, "INNER_STRUCTURE": 0.10, "UNKNOWN": 0.05,
    }.get(cls, 0.10)
    v = base + 0.15 * float(s["importance"])
    if s["protected"]:
        v += 0.20
    # long, flowing, organic edges read as elegant strands / graceful contours
    if cls in FLOW_CLASSES and s["organic_score"] >= 0.18 and s["len_norm"] >= 0.5:
        v += 0.10
    return round(_clamp01(v), 3)


def _simplification(s: dict) -> float:
    """How much the segment SHOULD be simplified (higher = simplify more)."""
    v = 0.0
    if s["behavior_class"] == "SIMPLIFY":
        v += 0.40
    v += 0.40 * s["noise_removal"]
    v += 0.30 * _clamp01((s["local_complexity"] - 0.30) / 0.20) * (1.0 - float(s["importance"]))
    if s["semantic_class"] == "LEAF" and not s["is_outer"]:
        v += 0.20                                     # inner leaf veins
    if s["semantic_class"] == "SKIN" and s["area"] >= 4000 \
            and s["behavior_class"] in ("SIMPLIFY", "GRADIENT"):
        v += 0.20                                     # broad cheeks
    if s["artistic_role"] == "BACKGROUND_SUPPORT":
        v += 0.20
    # protection strongly suppresses simplification
    if s["protected"]:
        v -= 0.35
    if s["validation_priority"] == "CRITICAL":
        v -= 0.40
    if s["contour_class"] == "FACIAL_EDGE":
        v -= 0.40
    elif s["contour_class"] == "OUTER_SILHOUETTE":
        v -= 0.30
    return round(_clamp01(v), 3)


def _playability(s: dict) -> float:
    """Contribution to a good color-by-number to actually play: clean, colourable
    regions raise it; tiny clutter and over-jagged borders lower it."""
    v = 0.60
    area = s["area"]
    if area < MICRO_AREA:
        v -= 0.40                                     # specks clutter the sheet
    elif area < 1200:
        v -= 0.10
    if 1500 <= area <= 40000:
        v += 0.15                                     # an ideal colourable size
    if s["local_complexity"] > 0.40:
        v -= 0.20 * _clamp01((s["local_complexity"] - 0.40) / 0.20)   # hard to stay inside
    if s["contour_class"] == "OUTER_SILHOUETTE":
        v += 0.20                                     # a clear outline reads well
    return round(_clamp01(v), 3)


# --------------------------------------------------------------------------- #
# decision — priority-ordered; the first matching rule wins
# --------------------------------------------------------------------------- #
def _decide(s: dict, sc: dict) -> tuple[str, str]:
    cls = s["contour_class"]
    critical = s["validation_priority"] == "CRITICAL"
    focal = cls in FOCAL_CLASSES

    # 1. REMOVE — a compact micro-fragment (small in area AND length): a sub-pixel
    #    interior speck / hair noise / micro-wrinkle. Guarded so it never touches a
    #    focal feature, a silhouette, a whisker, or a CRITICAL region. Region-level
    #    protection guards the SHAPE, not this clutter inside it.
    if (s["area"] <= NOISE_FLOOR_AREA and s["length"] <= NOISE_FLOOR_LEN
            and cls not in NEVER_REMOVE and not critical):
        noise = {"CLOTH": "micro-wrinkle", "FUR": "fur", "LEAF": "vein fleck",
                 "SKIN": "skin fleck"}.get(s["semantic_class"], "border")
        return "REMOVE", f"compact micro-fragment {noise} noise ({s['area']}px) — declutters, shape unaffected"

    # 2. EMPHASIZE — a focal feature that should read strongly (eyes, lips, petal
    #    centre, decorative accessory, the subject silhouette). Small high-importance
    #    petal edge = flower centre.
    centre = cls == "PETAL_EDGE" and float(s["importance"]) >= 0.70 and s["area"] < 6000
    if (sc["artistic_importance"] >= 0.60 and sc["visual_importance"] >= 0.60
            and (focal or sc["structural_importance"] >= 0.65)
            and (s["protected"] or focal or centre)):
        what = {"FACIAL_EDGE": "facial feature (protect eyes/lips)",
                "PETAL_EDGE": "flower centre" if centre else "petal focal",
                "DECORATIVE": "decorative detail (protect accessory)"}.get(cls, "subject silhouette")
        return "EMPHASIZE", f"high-value {what} — make it stand out"

    # 3. SOFTEN — organic flowing edges: preserve elegant flow (hair strands, petals,
    #    an organic silhouette). Not when it is really a simplification target.
    if (cls in FLOW_CLASSES and s["style"] in ORGANIC_STYLES
            and s["organic_score"] >= 0.15 and sc["simplification"] < 0.50):
        flow = "fur/hair flow" if s["semantic_class"] == "FUR" else \
               "petal edge" if cls == "PETAL_EDGE" else "flowing contour"
        return "SOFTEN", f"organic {flow} — preserve smooth flow"

    # 4. THICKEN — a primary structural boundary that should read boldly.
    if (sc["structural_importance"] >= 0.65 and s["is_outer"]
            and cls == "OUTER_SILHOUETTE" and sc["playability"] < 0.85):
        return "THICKEN", "primary silhouette — thicken for legibility"

    # 5. THIN — a delicate, precise feature carried by a heavy, over-dense line;
    #    thinning keeps it crisp (fine decorative filigree, dense micro-detail). Small,
    #    high-value, high-complexity, worth keeping but currently over-inked.
    if (not focal and not critical and sc["artistic_importance"] >= 0.40
            and NOISE_FLOOR_AREA < s["area"] < 2000 and s["local_complexity"] >= 0.35
            and s["style"] in ("SHARP", "MECHANICAL", "MICRO_DETAIL")):
        return "THIN", "delicate over-inked detail — thin to keep it crisp"

    # 6. SIMPLIFY — reducible complexity worth keeping but cleaning (cheeks, veins,
    #    minor folds, busy background). Allowed on protected-but-non-critical, non-focal
    #    shapes (the score already discounts protection heavily).
    if sc["simplification"] >= 0.50 and not critical \
            and cls not in ("FACIAL_EDGE", "OUTER_SILHOUETTE", "PETAL_EDGE"):
        target = "leaf veins" if s["semantic_class"] == "LEAF" else \
                 "cheek shading" if s["semantic_class"] == "SKIN" else \
                 "background" if s["artistic_role"] == "BACKGROUND_SUPPORT" else "border detail"
        return "SIMPLIFY", f"reducible {target} — simplify while keeping the shape"

    # 7. KEEP — balanced / already good (major folds, elegant strands, decent regions).
    return "KEEP", "balanced value — retain as drawn"


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def analyze(contour_report_path: Path, artistic_report_path: Path,
            material_report_path: Path, semantic_report_path: Path,
            out_report_path: Path) -> dict:
    """Score every border segment and decide its artistic action. Reads the four
    brain reports, writes `artistic_border_report.json`, returns the report dict.
    Analysis only — no rendering, no border/geometry/palette/export change."""
    contour = _load(contour_report_path)
    artistic = _load(artistic_report_path)
    material = _load(material_report_path)
    semantic = _load(semantic_report_path)

    rows = contour.get("contours", [])

    # region context keyed by label (contours are keyed by label).
    art_by_label, rid_to_label = {}, {}
    for r in artistic.get("regions", []):
        lbl = r.get("label")
        if lbl is not None:
            art_by_label[lbl] = r
            rid_to_label[r.get("region_id")] = lbl
    for r in semantic.get("regions", []):
        rid_to_label.setdefault(r.get("region_id"), r.get("label"))
    sem_by_label = {r.get("label"): r for r in semantic.get("regions", [])}
    mat_by_label = {}
    for r in material.get("regions", []):
        lbl = rid_to_label.get(r.get("region_id"))
        if lbl is not None:
            mat_by_label[lbl] = r

    lengths = sorted(float(r.get("length", 0.0)) for r in rows)
    len_ref = lengths[int(0.9 * (len(lengths) - 1))] if lengths else 1.0
    len_ref = len_ref or 1.0

    segments = []
    action_counts: Counter = Counter()
    class_action: dict = defaultdict(Counter)
    score_sums: dict = defaultdict(float)

    for r in rows:
        lbl = r.get("label")
        art = art_by_label.get(lbl, {})
        mat = mat_by_label.get(lbl, {})
        sem = sem_by_label.get(lbl, {})

        s = {
            "contour_class": r.get("contour_class", "UNKNOWN"),
            "style": r.get("style", "HYBRID"),
            "is_outer": bool(r.get("is_outer", False)),
            "protected": bool(r.get("protected", False)),
            "importance": float(r.get("importance", art.get("visual_importance", 0.5) or 0.5)),
            "length": float(r.get("length", 0.0)),
            "area": int(r.get("area", 0)),
            "curvature": float(r.get("curvature", 0.0)),
            "local_complexity": float(r.get("local_complexity", 0.0)),
            "organic_score": float(r.get("organic_score", 0.0)),
            "corner_count": int(r.get("corner_count", 0)),
            "len_norm": _clamp01(float(r.get("length", 0.0)) / len_ref),
            "semantic_class": sem.get("semantic_class") or art.get("semantic_class") or "UNKNOWN",
            "artistic_role": art.get("artistic_role", "UNKNOWN_SUPPORT"),
            "protect_shape": bool(art.get("protect_shape", False)),
            "protect_flow": bool(art.get("protect_flow", False)),
            "validation_priority": art.get("validation_priority"),
            "behavior_class": mat.get("behavior_class") or art.get("material_behavior"),
            "noise_removal": float(mat.get("noise_removal_score", 0.0) or 0.0),
        }

        sc = {
            "visual_importance": _visual_importance(s),
            "colouring_enjoyment": _colouring_enjoyment(s),
            "structural_importance": _structural_importance(s),
            "artistic_importance": _artistic_importance(s),
            "simplification": _simplification(s),
            "playability": _playability(s),
        }
        action, reason = _decide(s, sc)
        action_counts[action] += 1
        class_action[s["contour_class"]][action] += 1
        for k, v in sc.items():
            score_sums[k] += v

        segments.append({
            "label": lbl,
            "contour_index": r.get("contour_index"),
            "contour_class": s["contour_class"],
            "style": s["style"],
            "semantic_class": s["semantic_class"],
            "artistic_role": s["artistic_role"],
            "is_outer": s["is_outer"],
            "protected": s["protected"],
            "material_behavior": s["behavior_class"],
            "validation_priority": s["validation_priority"],
            "scores": sc,
            "action": action,
            "reason": reason,
            # geometry echoed for traceability (recomputed nothing)
            "length": round(s["length"], 1),
            "area": s["area"],
            "curvature": round(s["curvature"], 4),
            "local_complexity": round(s["local_complexity"], 4),
            "organic_score": round(s["organic_score"], 4),
        })

    n = len(segments)
    mean_scores = {k: round(v / n, 3) for k, v in score_sums.items()} if n else {}
    report = {
        "version": 1,
        "note": "Artistic border intelligence — analysis only. Scores every existing "
                "border segment on six artistic dimensions and decides one action "
                "(KEEP/SIMPLIFY/EMPHASIZE/REMOVE/THICKEN/THIN/SOFTEN). Reuses the QF14 "
                "contours + QF13/QF8/QF7 reports; recomputes no edge detection or "
                "segmentation and modifies no border, geometry, palette, numbers, or "
                "export. No rendering — this is the intelligence a future render pass obeys.",
        "sources": ["contour_character_report.json", "artistic_decision_report.json",
                    "material_behavior_report.json", "semantic_region_report.json"],
        "actions": ["KEEP", "SIMPLIFY", "EMPHASIZE", "REMOVE", "THICKEN", "THIN", "SOFTEN"],
        "score_dimensions": ["visual_importance", "colouring_enjoyment",
                             "structural_importance", "artistic_importance",
                             "simplification", "playability"],
        "segment_count": n,
        "action_distribution": dict(action_counts),
        "class_action_matrix": {k: dict(v) for k, v in class_action.items()},
        "protected_segment_count": sum(1 for s in segments if s["protected"]),
        "removable_noise_count": action_counts.get("REMOVE", 0),
        "mean_scores": mean_scores,
        "segments": segments,
    }
    Path(out_report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
