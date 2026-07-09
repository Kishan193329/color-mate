"""Future-learning-compatible decisions — the architectural seam, not the learning.

Engineering rule (ColorForge): a module must never hardcode a *heuristic* choice that could
later be replaced by experience. Instead it expresses the choice as a `Decision` that carries

    * value           — the chosen value / action (what a module actually uses)
    * reason          — why the rule chose it (human-readable)
    * confidence      — 0..1, how sure the rule is (static rules are moderate; learning raises it)
    * evidence        — the measurements / inputs that drove it (so a policy can learn from them)
    * override_point  — a STABLE id a future experience policy keys on to replace this decision

Every heuristic decision flows through `decide(...)`. Today the static rule's value stands
(`source="rule"`). Later, an experience database registers ONE provider via
`set_policy_provider(...)` — the single override point — which, given a Decision, may return a
better value learned from accumulated evidence. No module has to be rewritten: the call site
is already `decide(...)`, and the seam is already here.

**Learning is intentionally NOT implemented here.** This module only keeps the architecture
ready: with no provider registered, behaviour is exactly the static rules. Hard invariants
(reveal ΔE = 0, package completeness, no-leak, …) are NOT heuristics and must NOT be routed
through this seam — they stay enforced in code.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Optional

SOURCE_RULE = "rule"
SOURCE_EXPERIENCE = "experience"


def _clamp01(x: Any) -> float:
    try:
        x = float(x)
    except (TypeError, ValueError):
        return 0.0
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else x


@dataclass
class Decision:
    """A single future-learning-compatible choice. Use `.value`; serialise with `to_dict()`."""
    key: str
    value: Any
    reason: str
    confidence: float
    evidence: dict = field(default_factory=dict)
    override_point: str = ""
    source: str = SOURCE_RULE
    alternatives: list = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "key": self.key,
            "value": self.value,
            "reason": self.reason,
            "confidence": round(float(self.confidence), 3),
            "evidence": self.evidence,
            "override_point": self.override_point,
            "source": self.source,
            "alternatives": self.alternatives,
        }


# A policy provider is the future experience database's hook. Given a rule Decision it may
# return an override dict {value, reason?, confidence?} (or None to leave the rule standing).
PolicyProvider = Callable[[Decision], Optional[dict]]

_provider: Optional[PolicyProvider] = None


def set_policy_provider(fn: Optional[PolicyProvider]) -> None:
    """Register THE seam where a future experience database overrides static rules. Passing
    None restores rules-only behaviour. (Learning itself is not implemented in this module.)"""
    global _provider
    _provider = fn


def clear_policy_provider() -> None:
    set_policy_provider(None)


def has_policy_provider() -> bool:
    return _provider is not None


def decide(key: str, value: Any, *, reason: str, confidence: float,
           evidence: Optional[dict] = None, override_point: Optional[str] = None,
           alternatives: Optional[list] = None) -> Decision:
    """Express a heuristic choice as a Decision. Returns the rule's Decision unless a policy
    provider overrides it (keyed on `override_point`), in which case the rule value is kept in
    `evidence['rule_value']` for audit. A provider that raises is ignored — a policy must never
    break a decision."""
    d = Decision(
        key=str(key),
        value=value,
        reason=str(reason),
        confidence=_clamp01(confidence),
        evidence=dict(evidence or {}),
        override_point=str(override_point or key),
        source=SOURCE_RULE,
        alternatives=list(alternatives or []),
    )
    if _provider is None:
        return d
    try:
        override = _provider(d)
    except Exception:  # noqa: BLE001 - a policy provider must never break a decision
        override = None
    if not override:
        return d
    d.evidence = {**d.evidence, "rule_value": d.value,
                  "rule_confidence": d.confidence, "rule_reason": d.reason}
    d.value = override.get("value", d.value)
    if override.get("reason") is not None:
        d.reason = str(override["reason"])
    d.confidence = _clamp01(override.get("confidence", d.confidence))
    d.source = SOURCE_EXPERIENCE
    return d
