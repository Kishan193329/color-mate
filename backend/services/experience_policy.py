"""Experience Learning Policy v1 — read experience/patterns and register a SAFE policy
provider on the decision seam (core.decision).

When repeated rejection patterns exist, this nudges only **low-risk review decisions** toward
what the admin's rejections imply:

    review.complexity_score        review.artistic_score
    review.estimated_play_time_min review.recommendation

It NEVER touches reveal, masks, leak validation, region generation, or border rendering — it
hard-whitelists the four override points above and returns None for everything else. It also
does not change any image-processing algorithm: the only thing that changes is the review
layer's advisory scores/verdict, each carrying an explicit "experience:" reason.

Mechanism (v1): the patterns rollup holds, per reject category, the mean metric values of the
images that were rejected for that reason (`metric_signals_by_reject_category`) and how many
times (`by_reject_category`). For the image under review, the policy measures how close its
metric is to a rejection centroid; when a category has enough support (repeated) and the image
is near its centroid, it applies a bounded, evidence-weighted nudge. No data → no override →
byte-identical output.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

from config import PATTERNS_DIR
from core.decision import Decision, clear_policy_provider, has_policy_provider, set_policy_provider

# The ONLY decisions this policy may touch (safety whitelist).
ALLOWED = {"review.complexity_score", "review.artistic_score",
           "review.estimated_play_time_min", "review.recommendation"}

# Tuning (v1, conservative).
MIN_SUPPORT = 2          # a pattern must be REPEATED (>= this many rejections) to act
STRONG_SUPPORT = 5       # support at/above this saturates the confidence weight
BAND = 0.30              # a metric within +/-30% of a rejection centroid is "near"
SCORE_MAX_NUDGE = 12     # max points a 0..100 score can move
PLAY_MAX_NUDGE_MIN = 6   # max minutes play-time can move

# Which reject categories bear on which score, and the sign of the nudge (they raise/lower
# the SCORE). The metric compared is the score's own value vs the category centroid.
SCORE_LINKS = {
    "review.complexity_score": {
        "signal_metric": "complexity_score",
        "cats": {"over_complicated": +1, "too_many_tiny_regions": +1, "too_simple": -1},
    },
    "review.artistic_score": {
        "signal_metric": "artistic_score",
        "cats": {"not_fun_to_color": -1, "ugly_lineart": -1},
    },
    "review.estimated_play_time_min": {
        "signal_metric": "estimated_play_time_min",
        "cats": {"over_complicated": +1, "too_many_tiny_regions": +1},
    },
}

# For the recommendation verdict: the metric each category is matched on.
CAT_METRIC = {
    "over_complicated": "complexity_score",
    "too_many_tiny_regions": "tiny_regions",
    "too_simple": "complexity_score",
    "not_fun_to_color": "artistic_score",
    "ugly_lineart": "artistic_score",
}

_cache: dict = {"mtime": None, "data": {}}


def _load(p: Path):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def patterns() -> dict:
    """The current patterns rollup, reloaded when experience/patterns/patterns.json changes."""
    p = PATTERNS_DIR / "patterns.json"
    if not p.exists():
        _cache["mtime"], _cache["data"] = None, {}
        return {}
    m = p.stat().st_mtime
    if _cache["mtime"] != m:
        _cache["data"] = _load(p) or {}
        _cache["mtime"] = m
    return _cache["data"]


def _proximity(current: float, centroid: float) -> float:
    """1.0 at the centroid, decaying to 0 at the band edge (|delta| = BAND*|centroid|)."""
    denom = BAND * max(abs(centroid), 1.0)
    return max(0.0, 1.0 - abs(current - centroid) / denom)


def _strength(count: int) -> float:
    return min(1.0, count / STRONG_SUPPORT)


def _adjust_score(d: Decision, signals: dict, counts: dict) -> Optional[dict]:
    if not isinstance(d.value, (int, float)):
        return None
    link = SCORE_LINKS[d.override_point]
    metric = link["signal_metric"]
    current = float(d.value)
    best = None
    for cat, direction in link["cats"].items():
        count = int(counts.get(cat, 0) or 0)
        if count < MIN_SUPPORT:
            continue
        centroid = (signals.get(cat) or {}).get(metric)
        if not isinstance(centroid, (int, float)):
            continue
        weight = _strength(count) * _proximity(current, float(centroid))
        if weight > 0 and (best is None or weight > best["weight"]):
            best = {"weight": weight, "direction": direction, "cat": cat,
                    "centroid": float(centroid), "count": count}
    if not best:
        return None
    if d.override_point == "review.estimated_play_time_min":
        nudge = round(best["direction"] * PLAY_MAX_NUDGE_MIN * best["weight"])
        new_value = max(0, int(current) + nudge)
    else:
        nudge = round(best["direction"] * SCORE_MAX_NUDGE * best["weight"])
        new_value = int(min(100, max(0, current + nudge)))
    if new_value == int(current):
        return None
    verb = "raised" if best["direction"] > 0 else "lowered"
    reason = (f"experience: {best['count']} images near this were rejected as "
              f"'{best['cat']}' ({metric}≈{round(best['centroid'], 1)}); {verb} by {abs(nudge)}")
    return {"value": new_value, "reason": reason,
            "confidence": round(min(0.9, 0.5 + 0.4 * best["weight"]), 3)}


def _recommend(d: Decision, signals: dict, counts: dict) -> Optional[dict]:
    m = (d.evidence or {}).get("metrics") or {}
    matches = []
    for cat, metric in CAT_METRIC.items():
        count = int(counts.get(cat, 0) or 0)
        if count < MIN_SUPPORT:
            continue
        centroid = (signals.get(cat) or {}).get(metric)
        current = m.get(metric)
        if not (isinstance(centroid, (int, float)) and isinstance(current, (int, float))):
            continue
        weight = _strength(count) * _proximity(float(current), float(centroid))
        if weight > 0:
            matches.append({"category": cat, "count": count, "metric": metric,
                            "centroid": round(float(centroid), 1), "current": current,
                            "weight": round(weight, 3)})
    if not matches:
        return None
    matches.sort(key=lambda x: -x["weight"])
    top = matches[0]
    verdict = "likely_reject" if top["weight"] >= 0.5 else "needs_attention"
    value = {"verdict": verdict, "flags": list((d.value or {}).get("flags", [])),
             "matched_patterns": matches, "basis": "experience"}
    reason = (f"experience: resembles {len(matches)} repeated rejection pattern(s); closest "
              f"'{top['category']}' ({top['count']} rejections, {top['metric']}≈{top['centroid']} "
              f"vs {top['current']})")
    return {"value": value, "reason": reason,
            "confidence": round(min(0.9, 0.5 + 0.4 * top["weight"]), 3)}


def provider(d: Decision) -> Optional[dict]:
    """The registered policy provider. Safe by construction: only the four whitelisted review
    override points are ever considered; everything else (reveal, masks, leaks, regions,
    borders) returns None untouched. No patterns → None → identical output."""
    if d.override_point not in ALLOWED:
        return None
    pat = patterns()
    signals = pat.get("metric_signals_by_reject_category") or {}
    counts = pat.get("by_reject_category") or {}
    if not signals or not counts:
        return None
    if d.override_point == "review.recommendation":
        return _recommend(d, signals, counts)
    return _adjust_score(d, signals, counts)


def activate() -> None:
    """Register the experience policy on the decision seam (process-global)."""
    set_policy_provider(provider)


def deactivate() -> None:
    clear_policy_provider()


def is_active() -> bool:
    return has_policy_provider()


def status() -> dict:
    pat = patterns()
    return {
        "active": is_active(),
        "override_points": sorted(ALLOWED),
        "min_support": MIN_SUPPORT,
        "patterns_loaded": bool(pat),
        "totals": pat.get("totals", {}),
        "by_reject_category": pat.get("by_reject_category", {}),
    }
