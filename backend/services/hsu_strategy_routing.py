"""HSU-Guided Strategy Routing — use human_scene_report.json to guide SAFE processing.

Given the scene's `recommended_scene_strategy` and its region-role lists, this steers the
artistic border rendering (the SVG/vector layer) per scene type, and exposes an ADVISORY
router/profile start hint. It changes no reveal mode, no original image, and no regions — only
the border action a segment renders with (gated by the renderer's shape-fidelity check).

Strategy → border behaviour:
  PORTRAIT_PROTECT_FACE   protect focal (face) edges from simplification + emphasise them;
                          simplify background
  BACKGROUND_SIMPLIFY     simplify background/low-importance edges; emphasise the subject
  SUBJECT_FOCUS           emphasise the main subject; simplify background
  OBJECT_CENTERED         emphasise the central object; simplify background
  LANDSCAPE_DEPTH_LAYERS  emphasise near (subject/foreground); simplify far background
  MANDALA_KEEP_SYMMETRY   never simplify/soften (preserve symmetric detail)
  SKETCH_CLEAN_LINES      no heavy strokes (thicken -> keep); declutter background
  UNKNOWN_CONSERVATIVE    no change

Each adjusted action is a `core.decision.Decision` (`hsu_border.action`), seam-ready. HSU
never rescues the engine's REMOVE (a dropped speck is noise, not shape).
"""
from __future__ import annotations

import json
from pathlib import Path

from core.decision import Decision, decide

# Advisory only — a scene-appropriate retry START profile for the router. NOT forced (forcing
# it would change regions); exposed for the router to consult. None = defer to the router.
STRATEGY_START_PROFILE = {
    "SKETCH_CLEAN_LINES": "easy",
    "BACKGROUND_SIMPLIFY": "medium",
    "SUBJECT_FOCUS": "medium",
    "OBJECT_CENTERED": "medium",
    "PORTRAIT_PROTECT_FACE": "hard",
    "LANDSCAPE_DEPTH_LAYERS": "hard",
    "MANDALA_KEEP_SYMMETRY": "hard",
    "UNKNOWN_CONSERVATIVE": None,
}

# region-role fields in ascending priority (later overwrites earlier -> main subject wins)
_FIELD_ROLE = [("low_importance_regions", "low_importance"), ("background_regions", "background"),
               ("foreground_regions", "foreground"), ("secondary_subject_regions", "secondary_subject"),
               ("main_subject_regions", "main_subject")]


def _load(p: Path):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def start_profile_hint(strategy: str) -> str | None:
    return STRATEGY_START_PROFILE.get(strategy)


def load_plan(folder: Path) -> dict | None:
    """Read human_scene_report.json into a routing plan: strategy, per-label scene role, focal
    set, and the advisory start-profile hint. None if the report is absent."""
    rep = _load(Path(folder) / "human_scene_report.json")
    if not rep:
        return None
    scene = rep.get("scene", {}) or {}
    strategy = scene.get("recommended_scene_strategy", "UNKNOWN_CONSERVATIVE")
    role_of: dict = {}
    for field, role in _FIELD_ROLE:
        for lbl in scene.get(field, []) or []:
            role_of[lbl] = role
    return {"strategy": strategy, "role_of": role_of,
            "focal_set": set(scene.get("focal_regions", []) or []),
            "start_profile_hint": start_profile_hint(strategy)}


def border_action(strategy: str, base_action: str, role: str, is_focal: bool,
                  is_outer: bool) -> Decision:
    """Modulate a segment's border action from the scene strategy + the segment's scene role.
    Returns a Decision (value = the adjusted action). REMOVE is never rescued."""
    action, rule = base_action, None
    drawable = base_action in ("KEEP", "THIN", "SIMPLIFY", "SOFTEN")
    bg = role in ("background", "low_importance")

    if strategy == "PORTRAIT_PROTECT_FACE":
        if is_focal:
            if base_action in ("SIMPLIFY", "SOFTEN"):
                action, rule = "KEEP", "protect_face"
            elif base_action in ("KEEP", "THIN"):
                action, rule = "EMPHASIZE", "emphasize_face"
        elif bg and base_action == "KEEP":
            action, rule = "SIMPLIFY", "simplify_background"
    elif strategy == "MANDALA_KEEP_SYMMETRY":
        if base_action in ("SIMPLIFY", "SOFTEN"):
            action, rule = "KEEP", "keep_symmetry"
    elif strategy == "SKETCH_CLEAN_LINES":
        if base_action == "THICKEN":
            action, rule = "KEEP", "clean_lines"
        elif bg and base_action == "KEEP":
            action, rule = "SIMPLIFY", "clean_declutter"
    elif strategy in ("SUBJECT_FOCUS", "OBJECT_CENTERED"):
        if role == "main_subject" and drawable:
            action, rule = ("THICKEN" if is_outer else "EMPHASIZE"), "emphasize_subject"
        elif bg and base_action == "KEEP":
            action, rule = "SIMPLIFY", "simplify_background"
    elif strategy == "LANDSCAPE_DEPTH_LAYERS":
        if role in ("main_subject", "foreground") and drawable:
            action, rule = ("THICKEN" if is_outer else "EMPHASIZE"), "emphasize_near"
        elif role == "background" and base_action == "KEEP":
            action, rule = "SIMPLIFY", "simplify_far"
    elif strategy == "BACKGROUND_SIMPLIFY":
        if bg and base_action == "KEEP":
            action, rule = "SIMPLIFY", "simplify_background"
        elif role == "main_subject" and base_action in ("KEEP", "THIN"):
            action, rule = "EMPHASIZE", "emphasize_subject"

    if rule is None:
        rule = "keep_base"
    return decide(
        "hsu_border.action", action,
        reason=f"{strategy}: {rule}" if rule != "keep_base" else f"{strategy}: no scene change",
        confidence=0.5,
        evidence={"strategy": strategy, "base_action": base_action, "scene_role": role,
                  "is_focal": bool(is_focal), "rule": rule},
        override_point="hsu_border.action")
