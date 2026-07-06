"""Quality Fix 13 — artistic decision engine (decision layer only, no pixels).

QF7 = WHAT a region is, QF8 = HOW its material behaves, QF9 = the processing
policy, QF10-12 = the pixel changes those enabled. QF13 is the **central artist
brain and the single decision authority**: for every region it fuses all prior
reports into one authoritative artistic decision record — role, importance, what
must be protected, and exactly what future engines are *permitted* to do (split /
merge palette / smooth SVG / renumber). Future engines read THIS report instead
of re-deriving policy from QF9.

Engineering Sprint 1 wired QF13 to the actuators. It now publishes, in addition
to the per-region decision, an authoritative **`split_plan`**: the ordered list of
regions the split engine (QF12) must process, each carrying its label, semantic
class, split style, protection flags and validation priority. QF12 obeys this plan
verbatim; it no longer re-derives selection from QF9. The plan reproduces the
historical QF12 selection exactly (see `core.gradient_common.legacy_split_selected`
and the `split_selected` per-region flag), so wiring the brain changes no pixels.

It **reuses** (recomputes nothing): QF7 `semantic_region_report.json`, QF8
`material_behavior_report.json`, QF9 `processing_policy_report.json`, and — when
present — QF11 `flow_gradient_report.json`, QF12 `intelligent_split_report.json`,
QF3 `border_quality_report.json`. The only new computation is cheap boundary
*adjacency* (who each region touches), used to spot subject/facial details that
upstream semantics mislabel (e.g. green cat eyes classed as LEAF).

It changes NO regions, palette, numbers, SVG, or export — it emits only
`artistic_decision_report.json` and `artistic_decision_overlay.png`.
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import cv2
import numpy as np

from core.gradient_common import legacy_split_selected

# Artistic roles (RGB legend colours).
ROLE_COLOR = {
    "SUBJECT_CORE": (245, 130, 40), "SUBJECT_DETAIL": (250, 205, 60),
    "FACIAL_FEATURE": (225, 45, 45), "FUR_PATTERN": (240, 150, 25),
    "FLOWER_DETAIL": (232, 95, 180), "LEAF_DETAIL": (60, 170, 75),
    "BACKGROUND_SUPPORT": (200, 200, 200), "DECORATIVE_OBJECT": (150, 110, 215),
    "UNKNOWN_SUPPORT": (120, 120, 120),
}
SUBJECT_MATERIALS = {"FUR", "HAIR", "SKIN"}
FACIAL_MAX_AREA = 2600      # embedded subject region below this reads as a facial feature
SUBJECT_DETAIL_MAX = 9000   # embedded subject region below this is a subject detail
DECOR_CLASSES = {"WOOD", "METAL", "GLASS", "PLASTIC", "STONE", "CLOTH"}
IMPORTANCE = {
    "FACIAL_FEATURE": 1.00, "SUBJECT_CORE": 0.90, "FUR_PATTERN": 0.82,
    "SUBJECT_DETAIL": 0.70, "FLOWER_DETAIL": 0.66, "LEAF_DETAIL": 0.52,
    "DECORATIVE_OBJECT": 0.46, "UNKNOWN_SUPPORT": 0.32, "BACKGROUND_SUPPORT": 0.20,
}
SPLIT_STYLE = {  # how this role is permitted to be split (obeyed by QF12+)
    "FUR_PATTERN": "LAB_CLUSTER_ALONG_FLOW", "SUBJECT_CORE": "LAB_CLUSTER_TONAL",
    "LEAF_DETAIL": "ALONG_VEIN", "DECORATIVE_OBJECT": "ALONG_STRUCTURE",
}
WOOD_STYLE, CLOTH_STYLE = "ALONG_GRAIN", "ALONG_FOLD"

# --- Visual Improvement 1 — Brain-Guided Gradient Expansion ----------------- #
# The legacy split gate (legacy_split_selected) only fires on high-variance
# regions (future_split_strength >= 0.5), so the *large, smoothly-shaded* body /
# fur / face masses stay flat — the dominant source of rendered ΔE. This tier lets
# QF13 (the authority) ALSO select those large gradient areas for a *bounded*
# 2–3 band tonal split. It is deliberately conservative:
#   • only large subject body/fur areas (SUBJECT_CORE / FUR_PATTERN),
#   • never facial features (eyes/nose/mouth are FACIAL_FEATURE, not in the set),
#   • never tiny regions (area floor well above QF12's MIN_REGION_AREA),
#   • capped at EXPANSION_MAX_SUBREGIONS to avoid over-splitting.
# Protected-shape subject cores are allowed *when safe* — a tonal split adds only
# INTERNAL band boundaries and never moves the outer silhouette.
EXPANSION_ROLES = {"SUBJECT_CORE", "FUR_PATTERN"}
EXPANSION_MIN_AREA = 5000       # "large area" — ~4x QF12's MIN_REGION_AREA(1200)
EXPANSION_MIN_VARIANCE = 4.0    # some tonal range to benefit (Lab std). Thresholds
#   tuned on cat.png (process-isolated sweep): this pair maximised rendered-ΔE gain
#   while holding the cleanliness score and never splitting facial features.
EXPANSION_MAX_SUBREGIONS = 3    # 2–3 bands per large area; never more

# --- VI2 — Background tonal tier -------------------------------------------- #
# The largest remaining ΔE lives in the big smoothly-graded BACKGROUND masses
# (~4.5 ΔE on cat.png). QF13 permits a *bounded* tonal split there too — but a
# background must never compete with the subject, so it is capped at 2 tones and
# gated to only genuinely large, genuinely graded backgrounds. Small/flat
# backgrounds and any non-background role are untouched.
EXPANSION_BG_ROLES = {"BACKGROUND_SUPPORT"}
EXPANSION_BG_MIN_AREA = 20000     # only the big backdrop masses
EXPANSION_BG_MIN_VARIANCE = 8.0   # only clearly-graded backgrounds (Lab std)
EXPANSION_BG_MAX_SUBREGIONS = 2   # ≤2 tones — QF13 rejects busier backgrounds
REJECTION = {
    "FACIAL_FEATURE": "Reject any split/merge/smoothing that alters the eye, nose or "
        "mouth silhouette or removes the catch-light highlight.",
    "SUBJECT_CORE": "Reject simplification that flattens fur volume or dissolves the "
        "cream/orange boundary.",
    "FUR_PATTERN": "Reject if stripe direction is lost or stripes merge back into the "
        "base fur (ΔE regression).",
    "SUBJECT_DETAIL": "Reject if the detail is merged away or its shape is distorted.",
    "FLOWER_DETAIL": "Reject any split or palette merge that dulls petal colour or loses "
        "petal separation.",
    "LEAF_DETAIL": "Reject if the vein gradient is banded across the wrong axis or the "
        "leaf edge becomes jagged.",
    "DECORATIVE_OBJECT": "Reject if the object reads flat where the original shows form "
        "(grain / fold / shading lost).",
    "BACKGROUND_SUPPORT": "Reject over-splitting into more than two tones or any change "
        "that competes with the subject.",
    "UNKNOWN_SUPPORT": "Reject aggressive edits; treat conservatively until reclassified.",
}
COMMERCIAL = {  # "Would a professional illustrator treat it this way?"
    "FACIAL_FEATURE": "Yes — an illustrator guards eyes/nose/mouth first and never simplifies them.",
    "SUBJECT_CORE": "Yes — the subject's main body is rendered with the most care and volume.",
    "FUR_PATTERN": "Yes — tabby markings are painted as directional strokes, not flat fills.",
    "SUBJECT_DETAIL": "Yes — small subject details are kept crisp, not merged into the body.",
    "FLOWER_DETAIL": "Yes — flowers keep saturated, separated petals.",
    "LEAF_DETAIL": "Yes — leaves are shaded along their veins, edges kept clean.",
    "DECORATIVE_OBJECT": "Yes — props get form (grain/fold) but less detail than the subject.",
    "BACKGROUND_SUPPORT": "Yes — backgrounds are kept simple so the subject reads.",
    "UNKNOWN_SUPPORT": "Partly — an illustrator would first identify it, then treat conservatively.",
}


def _load(path: Path | None) -> dict:
    if path is not None and Path(path).exists():
        try:
            return json.loads(Path(path).read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return {}
    return {}


def _fill_lines(labels: np.ndarray, iters: int = 8) -> np.ndarray:
    """Grow region labels into the black line-art (label 0) so neighbours separated
    only by a thin outline become truly adjacent. Without this, every cell is an
    island (all borders are line) and no region-to-region adjacency exists."""
    filled = labels.copy()
    for _ in range(iters):
        z = filled == 0
        if not z.any():
            break
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh = np.roll(filled, (dy, dx), axis=(0, 1))
            take = z & (sh != 0)
            filled[take] = sh[take]
            z = filled == 0
    return filled


def _edge_lengths(labels: np.ndarray) -> Counter:
    """Border length between every pair of adjacent labels (no wrap, line 0 kept).
    Vectorised — the only new computation this engine adds."""
    stride = int(labels.max()) + 1
    edges: Counter = Counter()
    for a, b in ((labels[:, :-1], labels[:, 1:]), (labels[:-1, :], labels[1:, :])):
        d = a != b
        u, v = a[d].astype(np.int64), b[d].astype(np.int64)
        lo, hi = np.minimum(u, v), np.maximum(u, v)
        key = lo * stride + hi
        uk, ck = np.unique(key, return_counts=True)
        for k, c in zip(uk.tolist(), ck.tolist()):
            edges[(k // stride, k % stride)] += int(c)
    return edges


def _adjacency_dominant(edges: Counter, label_to_class: dict) -> dict:
    """For each label, the neighbour semantic class it shares the most border with."""
    nb: dict[int, Counter] = {}
    for (a, b), c in edges.items():
        if a == 0 or b == 0:
            continue
        nb.setdefault(a, Counter())[label_to_class.get(b, "UNKNOWN")] += c
        nb.setdefault(b, Counter())[label_to_class.get(a, "UNKNOWN")] += c
    return {lbl: ctr.most_common(1)[0][0] for lbl, ctr in nb.items() if ctr}


def _subject_embedded(labels: np.ndarray, edges: Counter, label_to_class: dict) -> set:
    """Labels that belong to the SUBJECT by enclosure: a connected group of
    non-subject / non-background regions whose outer border is dominated by the
    subject's fur/skin and which never touches the image edge. This overrides
    upstream misclassification (cream chest read as CLOTH, green eyes as LEAF)."""
    subject = {l for l, c in label_to_class.items() if c in SUBJECT_MATERIALS}
    background = {l for l, c in label_to_class.items()
                 if c in ("BACKGROUND", "SKY", "WATER")}
    # Anchor to the SUBJECT'S main mass: the largest connected fur/skin blob is the
    # actual subject body. Fur-coloured props elsewhere (e.g. a warm window frame
    # mislabelled FUR) form separate blobs and must NOT count as the subject, or
    # they would wrongly pull nearby props into it.
    subject_mask = np.isin(labels, list(subject)) if subject else np.zeros(labels.shape, bool)
    if subject_mask.any():
        ncc, cc = cv2.connectedComponents(subject_mask.astype(np.uint8))
        areas = np.bincount(cc.ravel())
        areas[0] = 0
        main_cc = cc == int(areas.argmax())
        main_subject = set()
        for l in subject:
            lm = labels == l
            a = lm.sum()
            if a and (main_cc & lm).sum() >= 0.5 * a:
                main_subject.add(l)
    else:
        main_subject = set()
    nodes = {l for l in label_to_class
             if l != 0 and l not in subject and l not in background}
    border = (set(labels[0, :].tolist()) | set(labels[-1, :].tolist())
              | set(labels[:, 0].tolist()) | set(labels[:, -1].tolist()))

    parent = {n: n for n in nodes}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for (a, b), _c in edges.items():
        if a in nodes and b in nodes:
            parent[find(a)] = find(b)

    groups: dict[int, list] = {}
    for n in nodes:
        groups.setdefault(find(n), []).append(n)

    # External border per group, split into subject / background / other.
    ext = {g: {"subj": 0, "bg": 0, "other": 0} for g in groups}
    root = {n: find(n) for n in nodes}
    for (a, b), c in edges.items():
        if a == 0 or b == 0:
            continue
        for x, y in ((a, b), (b, a)):
            if x in nodes and root.get(x) != root.get(y):  # y outside x's group
                if y in main_subject:                       # only the cat body counts
                    ext[root[x]]["subj"] += c
                elif y in background:
                    ext[root[x]]["bg"] += c
                else:
                    ext[root[x]]["other"] += c

    embedded: set = set()
    for g, members in groups.items():
        if any(m in border for m in members):
            continue                                   # touches image edge -> not subject
        e = ext[g]
        tot = e["subj"] + e["bg"] + e["other"]
        if tot > 0 and e["bg"] == 0 and e["subj"] / tot >= 0.5:
            embedded.update(members)
    return embedded


def _role(sclass, size, embedded_in_subject, preserve_stripes, split_strength):
    if embedded_in_subject and sclass not in SUBJECT_MATERIALS \
            and sclass not in ("BACKGROUND", "SKY", "WATER"):
        # An island fully inside the subject's fur/skin IS part of the subject,
        # whatever material it was (mis)labelled as upstream — the brain overrides
        # the raw class. Small -> facial feature (nose/mouth/eye), medium -> subject
        # detail (eye/collar/spot), large -> subject core (cream chest / belly).
        if size <= FACIAL_MAX_AREA:
            return "FACIAL_FEATURE"
        if size <= SUBJECT_DETAIL_MAX:
            return "SUBJECT_DETAIL"
        return "SUBJECT_CORE"
    if sclass in ("FUR", "HAIR"):
        return "FUR_PATTERN" if (preserve_stripes or split_strength >= 0.4) else "SUBJECT_CORE"
    if sclass == "SKIN":
        return "SUBJECT_CORE" if size >= SUBJECT_DETAIL_MAX else "SUBJECT_DETAIL"
    if sclass == "FLOWER":
        return "FLOWER_DETAIL"
    if sclass == "LEAF":
        return "LEAF_DETAIL"
    if sclass in DECOR_CLASSES:
        return "DECORATIVE_OBJECT"
    if sclass in ("BACKGROUND", "SKY", "WATER"):
        return "BACKGROUND_SUPPORT"
    return "UNKNOWN_SUPPORT"


def _plan_split_style(role, sclass):
    """The authoritative split style for a region QF12 will actually split.

    Unlike the per-region `split_style` field (which QF13 zeroes to "NONE" when
    its own stricter `split_permission` gate denies), this always names a concrete
    style so every entry in `split_plan` tells the splitter *how* to split. It is
    advisory for Sprint 1 (QF12 records but does not yet branch on it), so it can
    never change pixels."""
    if role == "DECORATIVE_OBJECT" and sclass == "WOOD":
        return WOOD_STYLE
    if role == "DECORATIVE_OBJECT" and sclass == "CLOTH":
        return CLOTH_STYLE
    return SPLIT_STYLE.get(role, "LAB_CLUSTER_TONAL")


def _prec(svg_policy):
    return {"KEEP_DETAIL": "HIGH", "LIGHT": "MEDIUM", "STRONG": "LOW"}.get(svg_policy, "MEDIUM")


def _num_sens(numbering_policy):
    return {"PROTECT": "HIGH", "STANDARD": "MEDIUM", "HIDE_SMALL": "LOW"}.get(numbering_policy, "MEDIUM")


def analyze(semantic_report_path: Path, material_report_path: Path,
            policy_report_path: Path, labels_path: Path,
            report_path: Path, overlay_path: Path,
            flow_report_path: Path | None = None,
            split_report_path: Path | None = None,
            border_report_path: Path | None = None,
            profile: dict | None = None) -> dict:
    semantic = _load(semantic_report_path)
    material = _load(material_report_path)
    policy = _load(policy_report_path)
    flow = _load(flow_report_path)
    split = _load(split_report_path)
    border = _load(border_report_path)
    labels = np.load(labels_path)
    h, w = labels.shape
    total = h * w

    # Output-profile gradient-preservation gates (Product Sprint 1). Absent /
    # medium profile falls back to the module defaults => identical behaviour.
    profile = profile or {}
    exp_min_area = profile.get("expansion_min_area", EXPANSION_MIN_AREA)
    exp_min_var = profile.get("expansion_min_variance", EXPANSION_MIN_VARIANCE)
    exp_max_sub = profile.get("expansion_max_subregions", EXPANSION_MAX_SUBREGIONS)
    bg_min_area = profile.get("expansion_bg_min_area", EXPANSION_BG_MIN_AREA)
    bg_min_var = profile.get("expansion_bg_min_variance", EXPANSION_BG_MIN_VARIANCE)
    bg_max_sub = profile.get("expansion_bg_max_subregions", EXPANSION_BG_MAX_SUBREGIONS)

    sem_by_id = {r["region_id"]: r for r in semantic.get("regions", [])}
    mat_by_id = {r["region_id"]: r for r in material.get("regions", [])}
    pol_by_id = {r["region_id"]: r for r in policy.get("regions", [])}
    label_to_class = {r["label"]: r.get("semantic_class", "UNKNOWN")
                      for r in semantic.get("regions", []) if r.get("label") is not None}
    filled = _fill_lines(labels)                       # bridge thin outlines for adjacency
    edges = _edge_lengths(filled)
    dominant_nb = _adjacency_dominant(edges, label_to_class)
    embedded_labels = _subject_embedded(filled, edges, label_to_class)
    # classes that were actually split by the pixel engines (aggregate context).
    split_classes = {d.get("semantic_class") for d in split.get("split_details", [])}
    split_classes |= {d.get("semantic_class") for d in flow.get("split_details", [])}

    overlay = np.full((h, w, 3), 255, np.uint8)
    overlay[labels == 0] = (40, 40, 40)
    regions_out = []
    plan_entries = []       # PRIMARY split selection (legacy gate), semantic order
    expansion_entries = []  # GRADIENT_EXPANSION selection (VI1), semantic order

    for rid, s in sem_by_id.items():
        m = mat_by_id.get(rid, {})
        p = pol_by_id.get(rid, {})
        sclass = s.get("semantic_class", "UNKNOWN")
        bclass = m.get("behavior_class", "CONSERVATIVE")
        size = int(s.get("region_size", 0))
        lbl = s.get("label")
        split_strength = float(p.get("future_split_strength", 0.0))
        preserve_stripes = bool(p.get("preserve_stripes", False))
        nb_class = dominant_nb.get(lbl, "UNKNOWN")
        embedded = lbl in embedded_labels

        role = _role(sclass, size, embedded, preserve_stripes, split_strength)
        importance = round(min(1.0, IMPORTANCE[role] + 0.05 * (size / total > 0.03)), 3)

        # Protection flags — fused from role + QF9 policy (never weaker than policy).
        protect_shape = bool(role in ("FACIAL_FEATURE", "SUBJECT_DETAIL", "FLOWER_DETAIL")
                             or not p.get("allow_simplification", True))
        protect_highlight = bool(role in ("FACIAL_FEATURE", "FUR_PATTERN", "SUBJECT_CORE")
                                 or p.get("preserve_gradient"))
        protect_shadow = bool(p.get("preserve_gradient")
                              or role in ("SUBJECT_CORE", "FUR_PATTERN", "DECORATIVE_OBJECT"))
        protect_texture = bool(p.get("preserve_texture"))
        protect_flow = bool(p.get("preserve_flow") or preserve_stripes)

        # What future engines are PERMITTED to do.
        splittable_role = role in ("SUBJECT_CORE", "FUR_PATTERN", "LEAF_DETAIL", "DECORATIVE_OBJECT")
        split_permission = bool(splittable_role and split_strength >= 0.5 and size >= 1200)
        if role == "DECORATIVE_OBJECT" and sclass == "WOOD":
            split_style = WOOD_STYLE
        elif role == "DECORATIVE_OBJECT" and sclass == "CLOTH":
            split_style = CLOTH_STYLE
        else:
            split_style = SPLIT_STYLE.get(role, "NONE")
        if not split_permission:
            split_style = "NONE"

        palette_merge_permission = p.get("palette_merge_policy", "WITHIN_MATERIAL")
        if role in ("FACIAL_FEATURE", "FLOWER_DETAIL"):
            palette_merge_permission = "LOCKED"
        svg_precision = "HIGH" if role in ("FACIAL_FEATURE", "FLOWER_DETAIL", "SUBJECT_DETAIL") \
            else _prec(p.get("svg_smoothing_policy"))
        numbering_sensitivity = "HIGH" if role == "FACIAL_FEATURE" \
            else _num_sens(p.get("numbering_policy"))

        if importance >= 0.9 or role == "FACIAL_FEATURE":
            validation_priority = "CRITICAL"
        elif importance >= 0.65:
            validation_priority = "HIGH"
        elif importance >= 0.4:
            validation_priority = "MEDIUM"
        else:
            validation_priority = "LOW"

        if lbl is not None:
            overlay[labels == lbl] = ROLE_COLOR[role][::-1]

        # Authoritative split selection: the exact historical QF12 gate, evaluated
        # once HERE so QF13 is the single decision authority. QF12 obeys this flag
        # (via `split_plan`) instead of re-deriving policy from QF9. Byte-identical
        # to the pre-Sprint-1 QF12 filter (see core.gradient_common).
        split_selected = legacy_split_selected(p, sclass)

        regions_out.append({
            "region_id": rid,
            "label": (int(lbl) if lbl is not None else None),  # geometry key for actuators
            "semantic_class": sclass,
            "material_behavior": bclass,
            "artistic_role": role,
            "visual_importance": importance,
            "protect_shape": protect_shape,
            "protect_highlight": protect_highlight,
            "protect_shadow": protect_shadow,
            "protect_texture": protect_texture,
            "protect_flow": protect_flow,
            "split_permission": split_permission,
            "split_selected": bool(split_selected),
            "split_style": split_style,
            "palette_merge_permission": palette_merge_permission,
            "svg_precision": svg_precision,
            "numbering_sensitivity": numbering_sensitivity,
            "validation_priority": validation_priority,
            "rejection_reason_if_low_quality": REJECTION[role],
            "commercial_review": COMMERCIAL[role],
            "embedded_in_subject": bool(embedded),
            "dominant_neighbor_class": nb_class,
            "was_pattern_split_upstream": sclass in split_classes,
        })

        if split_selected:
            # PRIMARY tier — one plan entry per selected region, tagged with its QF9
            # processing priority. Collected in semantic order now; sorted by priority
            # below so the plan order matches the legacy policy-report order QF12 used
            # (identical split-label assignment => identical primary labels).
            plan_entries.append({
                "region_id": rid,
                "label": (int(lbl) if lbl is not None else None),
                "semantic_class": sclass,
                "split_style": _plan_split_style(role, sclass),
                "split_tier": "PRIMARY",
                "protect_shape": protect_shape,
                "protect_highlight": protect_highlight,
                "protect_shadow": protect_shadow,
                "protect_texture": protect_texture,
                "protect_flow": protect_flow,
                "validation_priority": validation_priority,
                "_processing_priority": int(p.get("processing_priority", 3)),
            })
        else:
            # Bounded tonal EXPANSION the legacy gate skipped. Two tiers:
            #   GRADIENT_EXPANSION  (VI1) — large body/fur/face subject areas,
            #   BACKGROUND_EXPANSION (VI2) — large graded backdrops, ≤2 tones so the
            #                                background never competes with the subject.
            # Never facial (FACIAL_FEATURE excluded by role), never tiny (area floor).
            # Appended AFTER all primary entries so primary splits stay byte-identical.
            cvar = float(s.get("color_variance", 0.0))
            exp_tier, exp_cap = None, 0
            if (role in EXPANSION_ROLES and size >= exp_min_area
                    and cvar >= exp_min_var and bool(p.get("preserve_gradient"))):
                exp_tier, exp_cap = "GRADIENT_EXPANSION", exp_max_sub
            elif (role in EXPANSION_BG_ROLES and size >= bg_min_area
                    and cvar >= bg_min_var):
                exp_tier, exp_cap = "BACKGROUND_EXPANSION", bg_max_sub
            if lbl is not None and exp_tier is not None:
                expansion_entries.append({
                    "region_id": rid,
                    "label": int(lbl),
                    "semantic_class": sclass,
                    "split_style": _plan_split_style(role, sclass),
                    "split_tier": exp_tier,
                    "max_subregions": exp_cap,
                    "artistic_role": role,
                    "protect_shape": protect_shape,
                    "protect_highlight": protect_highlight,
                    "protect_shadow": protect_shadow,
                    "protect_texture": protect_texture,
                    "protect_flow": protect_flow,
                    "validation_priority": validation_priority,
                    "color_variance": round(cvar, 2),
                    "_size": size,
                })

    _legend(overlay, Counter(r["artistic_role"] for r in regions_out))
    cv2.imwrite(str(overlay_path), overlay)

    # Order the split plan by QF9 processing priority (stable over semantic order)
    # so the PRIMARY block matches exactly the order QF12 historically iterated the
    # policy report — preserving the primary split-label assignment sequence. The
    # GRADIENT_EXPANSION block is appended AFTER (largest area first) so it never
    # perturbs the primary labels; it only adds new sub-regions.
    plan_entries.sort(key=lambda e: e["_processing_priority"])
    expansion_entries.sort(key=lambda e: -e["_size"])
    split_plan = [{k: v for k, v in e.items() if k != "_processing_priority"}
                  for e in plan_entries]
    split_plan += [{k: v for k, v in e.items() if k != "_size"}
                   for e in expansion_entries]

    # "Protected" = the brain flags an explicit thing to guard (shape/flow/texture) or
    # ranks it HIGH/CRITICAL — NOT merely "not splittable" (that would be everything).
    protected = [r for r in regions_out if (r["protect_shape"] or r["protect_flow"]
                 or r["protect_texture"] or r["validation_priority"] in ("HIGH", "CRITICAL"))]
    report = {
        "version": 1,
        "note": "Artistic decision layer only — fuses QF7/QF8/QF9 (+QF11/QF12/QF3 when "
                "present) into per-region artist decisions. No regions, palette, "
                "numbers, SVG, or export changed.",
        "sources": ["semantic_region_report.json", "material_behavior_report.json",
                    "processing_policy_report.json", "flow_gradient_report.json",
                    "intelligent_split_report.json", "border_quality_report.json"],
        "region_count": len(regions_out),
        "role_distribution": dict(Counter(r["artistic_role"] for r in regions_out)),
        "protected_region_count": len(protected),
        "split_permission_distribution": dict(Counter(
            "ALLOW" if r["split_permission"] else "DENY" for r in regions_out)),
        "split_allowed_region_ids": [r["region_id"] for r in regions_out if r["split_permission"]],
        # Authoritative split selection consumed by QF12 (the split engine obeys
        # this, not QF9 policy). `split_plan` is ordered exactly as QF12 processes it.
        # Two tiers: PRIMARY (legacy high-variance gate) + GRADIENT_EXPANSION (VI1:
        # bounded tonal split of large body/fur/face areas).
        "split_selected_count": len(split_plan),
        "split_primary_count": len(plan_entries),
        "split_expansion_count": len(expansion_entries),
        "split_selected_region_ids": [e["region_id"] for e in split_plan],
        "split_expansion_region_ids": [e["region_id"] for e in expansion_entries],
        "split_plan": split_plan,
        "palette_restriction_distribution": dict(Counter(
            r["palette_merge_permission"] for r in regions_out)),
        "svg_precision_distribution": dict(Counter(r["svg_precision"] for r in regions_out)),
        "validation_priority_distribution": dict(Counter(
            r["validation_priority"] for r in regions_out)),
        "future_modules_affected": {
            "split engine (QF12)": "WIRED — consumes split_plan (selection + style + protection)",
            "palette engine": "prepared — reads palette_merge_permission (Sprint 1: advisory)",
            "svg generator": "prepared — reads svg_precision (Sprint 1: advisory)",
            "numbering": "prepared — reads numbering_sensitivity (Sprint 1: advisory)",
            "validation/QA": "order by validation_priority; enforce rejection_reason_if_low_quality",
        },
        "border_baseline": {
            "jaggedness": border.get("jaggedness_score_after", border.get("jaggedness")),
            "region_leak_count": border.get("region_leak_count"),
        },
        "regions": sorted(regions_out, key=lambda r: -r["visual_importance"]),
    }
    Path(report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def _legend(overlay, counts):
    y = 12
    for role, col in ROLE_COLOR.items():
        if not counts.get(role):
            continue
        c = col[::-1]
        cv2.rectangle(overlay, (10, y), (34, y + 18), c, -1)
        cv2.rectangle(overlay, (10, y), (34, y + 18), (40, 40, 40), 1)
        cv2.putText(overlay, f"{role} ({counts[role]})", (40, y + 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.46, (30, 30, 30), 1, cv2.LINE_AA)
        y += 24
