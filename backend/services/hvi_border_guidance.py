"""HVI-Guided Border Decisions — use the Human Visual Intelligence scores to guide the
per-segment border ACTION, on the SVG/vector layer only.

Given a segment's base action (from the Artistic Border Intelligence) and its region's HVI
scores, this returns an HVI-adjusted action. It only ever changes which artistic action the
renderer executes — it does NOT touch reveal, region masks, the original image, palette,
numbers, or regions. Every adjusted action is a `core.decision.Decision` (reason / confidence
/ evidence / override_point / source), so it is inspectable now and learnable later; the
`hvi_border.action` override point is seam-ready for a future experience policy.

Rules (a shape-protecting guard, then one emphasis / beauty / declutter rule):
  high tracing_priority        -> protect exact shape (no simplify/soften/remove)
  high fun_to_color            -> preserve interesting detail (no remove/simplify)
  high recognition/attention/drawing -> stronger, cleaner border (THICKEN silhouette / EMPHASIZE)
  high beauty                  -> elegant smooth curve (SOFTEN)
  low-importance background    -> soften (SIMPLIFY)
"""
from __future__ import annotations

from core.decision import Decision, decide

HI = 0.62          # "high" score threshold (emphasis / beauty)
LO = 0.30          # "low" score threshold (declutter)
# Protecting a shape *undoes* the engine's simplification, so it must require a GENUINELY high
# tracing priority (the score's mean sits around 0.65, so 0.62 would guard over half of all
# segments). 0.80 restricts protection to the true focal / signature edges.
PROTECT_HI = 0.80


def guide(base_action: str, hvi: dict, is_outer: bool, contour_class: str | None = None) -> Decision:
    """Return the HVI-adjusted border action for a segment as a Decision."""
    rec = float(hvi.get("recognition_weight", 0.0) or 0.0)
    att = float(hvi.get("viewer_attention", 0.0) or 0.0)
    beauty = float(hvi.get("beauty_score", 0.0) or 0.0)
    fun = float(hvi.get("fun_to_color_score", 0.0) or 0.0)
    tracing = float(hvi.get("tracing_priority", 0.0) or 0.0)
    drawing = float(hvi.get("drawing_priority", 0.0) or 0.0)
    artistic = float(hvi.get("artistic_importance", 0.0) or 0.0)

    action = base_action
    rule = None
    reason = None

    # 1) guard against over-SIMPLIFICATION (not against the engine's REMOVE noise-cleanup —
    #    a compact speck the engine dropped is noise, not shape). Rescues SIMPLIFY/SOFTEN to KEEP.
    if tracing >= PROTECT_HI and base_action in ("SIMPLIFY", "SOFTEN"):
        action, rule, reason = "KEEP", "protect_shape", \
            f"high tracing priority ({tracing:.2f}) — protect exact shape"
    elif fun >= HI and base_action == "SIMPLIFY":
        action, rule, reason = "KEEP", "preserve_detail", \
            f"high fun-to-color ({fun:.2f}) — preserve interesting detail (no over-simplify)"

    # 2) emphasis / beauty / declutter — one applies, gated on the ORIGINAL drawable action
    #    (a rescued REMOVE stays a plain KEEP; only genuinely-kept edges get emphasised or
    #    softened). Emphasis is stroke-only, so it composes with the shape guard.
    drawable = base_action in ("KEEP", "THIN", "SIMPLIFY", "SOFTEN")
    if drawable and (rec >= HI or att >= HI or drawing >= HI):
        if is_outer:
            action, rule, reason = "THICKEN", "emphasize_silhouette", \
                "high recognition/drawing on the silhouette — thicken for a stronger, cleaner border"
        else:
            action, rule, reason = "EMPHASIZE", "emphasize_focal", \
                f"high recognition/attention ({max(rec, att):.2f}) — stronger, cleaner border"
    elif base_action == "KEEP" and beauty >= HI and tracing < HI:
        action, rule, reason = "SOFTEN", "beautify", \
            f"high beauty ({beauty:.2f}) — elegant smooth curve"
    elif base_action == "KEEP" and tracing < HI and rec <= LO and att <= LO and fun <= LO and artistic <= LO:
        action, rule, reason = "SIMPLIFY", "declutter_bg", \
            "low-importance background — soften"

    if rule is None:
        rule, reason = "keep_base", "HVI scores within thresholds — base action kept"

    return decide(
        "hvi_border.action", action, reason=reason, confidence=0.5,
        evidence={"base_action": base_action, "rule": rule,
                  "recognition": round(rec, 3), "attention": round(att, 3),
                  "beauty": round(beauty, 3), "fun": round(fun, 3),
                  "tracing": round(tracing, 3), "drawing": round(drawing, 3),
                  "artistic": round(artistic, 3), "is_outer": bool(is_outer)},
        override_point="hvi_border.action")
