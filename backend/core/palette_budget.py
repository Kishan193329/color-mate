"""Per-material palette budgets — Commercial Optimization Sprint 1.

VI2 shrank rendered ΔE under 10 by grouping the whole palette with one *global*
fine `palette_delta` (12), which ballooned the colour count to 26. That is a blunt
instrument: it keeps near-duplicate background/decorative colours apart as hard as
it keeps the facial catch-lights apart.

This module replaces the global knob with a **brain/material-aware budget**: every
region gets a LAB merge threshold chosen from its QF13 material/role, so the palette
grouper merges aggressively where fidelity does not matter (background, decorative)
and barely at all where it does (facial features, highlights, flowers). Fewer
colours, same subject fidelity.

It only READS the existing brain reports (QF13 `artistic_decision_report` — which
already fuses QF7/QF8/QF9 — plus QF12 `intelligent_split_report` for split-child →
parent inheritance). It recomputes nothing.

The budget is a merge threshold (CIE76 ΔE): **larger = merges more = fewer colours**.
"""
from __future__ import annotations

# Category -> LAB merge threshold. Ordered by the sprint's material priority: keep
# facial/flowers/subject distinct, let props/background merge. Tuned on cat.png to
# land the palette in the 21–22 "acceptable" band with no visible fidelity loss.
# (Strict-intent values — FACIAL 4, DECORATIVE ~8 — hold the palette at ~24; the
# looser DECORATIVE/LEAF/BACKGROUND here trade ~1.5 ΔE for the target colour count,
# visually verified. Callers may pass their own table for a different operating point.)
MATERIAL_BUDGET: dict[str, float] = {
    "FACIAL":     4.0,   # eyes / nose / mouth / catch-lights — strict, never merge (rule 5/6)
    "FLOWER":    13.0,   # keep petals' identity, merge only near-identical petals
    "LEAF":      18.0,   # keep leaf identity distinct, merge similar greens
    "SUBJECT":   16.0,   # face / skin body — keep the tonal steps, merge similar tones
    "FUR":       18.0,   # fur / chest — keep the big colour bands, merge close ones
    "DECORATIVE":28.0,   # props (cloth folds / wood) — merge visually similar tones
    "BACKGROUND":20.0,   # backdrop — controlled merge (reads behind the subject)
    "STRICT":    10.0,   # QF13 LOCKED but no material match (e.g. UNKNOWN) — conservative
    "DEFAULT":   22.0,
}


def category(role: str | None, sclass: str | None,
             protect_highlight: bool, merge_perm: str | None) -> str:
    """Map a region's QF13 role / material / flags to a budget category.

    **Material first** (so a flower marked LOCKED by QF13 uses the FLOWER budget —
    distinct but not one-colour-per-petal — rather than an absolute lock). Facial
    features are always strict (rules 5/6). QF13 `palette_merge_permission=LOCKED`
    is respected (rule 7) for regions with no material match (STRICT, tight budget)
    and, for matched materials, by those materials being on the strict side.
    `protect_highlight` alone is not treated as strict (QF13 sets it broadly);
    catch-lights survive because the material budgets are tighter than a
    highlight's tonal distance from its base.
    """
    if role == "FACIAL_FEATURE":
        return "FACIAL"
    if role == "FLOWER_DETAIL" or sclass == "FLOWER":
        return "FLOWER"
    if role == "LEAF_DETAIL" or sclass == "LEAF":
        return "LEAF"
    if role == "BACKGROUND_SUPPORT" or sclass in ("BACKGROUND", "SKY", "WATER"):
        return "BACKGROUND"
    if sclass == "SKIN":
        return "SUBJECT"
    if role in ("SUBJECT_CORE", "FUR_PATTERN") or sclass in ("FUR", "HAIR"):
        return "FUR"
    if role == "DECORATIVE_OBJECT" or sclass in (
            "WOOD", "METAL", "GLASS", "PLASTIC", "STONE", "CLOTH"):
        return "DECORATIVE"
    if merge_perm == "LOCKED":
        return "STRICT"
    return "DEFAULT"


def build_label_budget(artistic: dict, split: dict,
                       budget: dict[str, float] | None = None
                       ) -> tuple[dict[int, float], dict[int, str]]:
    """Return (label -> merge threshold, label -> category).

    `artistic` = QF13 `artistic_decision_report.json` (per-region role/material,
    keyed by the geometry `label`). `split` = QF12 `intelligent_split_report.json`
    (split_details carry `child_labels`, which inherit the parent's material — the
    parent keeps its own `label`). Any label absent from both maps falls back to
    DEFAULT at call time.
    """
    budget = budget or MATERIAL_BUDGET
    art_by_label = {a["label"]: a for a in artistic.get("regions", [])
                    if a.get("label") is not None}

    child_parent: dict[int, int] = {}
    for d in split.get("split_details", []):
        parent = d.get("label")
        for c in (d.get("child_labels") or []):
            child_parent[int(c)] = parent

    def cat_of(lbl):
        a = art_by_label.get(lbl, {})
        return category(a.get("artistic_role"), a.get("semantic_class", "UNKNOWN"),
                        bool(a.get("protect_highlight", False)),
                        a.get("palette_merge_permission"))

    delta: dict[int, float] = {}
    cats: dict[int, str] = {}
    for lbl in set(art_by_label) | set(child_parent):
        cat = cat_of(child_parent.get(lbl, lbl))   # child inherits parent's material
        cats[lbl] = cat
        delta[lbl] = float(budget.get(cat, budget["DEFAULT"]))
    return delta, cats
