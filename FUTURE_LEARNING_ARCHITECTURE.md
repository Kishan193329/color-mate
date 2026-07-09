# Future-Learning Compatible Architecture

**Engineering rule.** Every new module must be **future-learning compatible**. Do not hardcode
a *heuristic* decision that could later be replaced by experience. Each such decision must
expose:

- **reason** — why the rule chose this
- **confidence** — 0..1, how sure the rule is
- **evidence** — the measurements/inputs that drove it
- **future override point** — a stable id where an experience database can replace it

Design every module so an experience database can replace static rules later **without
rewriting the architecture**. Learning is **not** implemented yet — this only keeps the
architecture ready.

---

## The seam: `core/decision.py`

One primitive and one hook. Every heuristic choice flows through `decide(...)`, which returns
a `Decision`:

```python
from core.decision import decide

d = decide(
    "review.complexity_score", value,           # key + the chosen value (use d.value)
    reason="0.5*regions + 0.35*border-density + 0.15*tiny-fraction (capped)",
    confidence=0.55,                            # static rules are moderate; learning raises this
    evidence={"region_count": n, "border_density": bd, "weights": {...}, "caps": {...}},
    override_point="review.complexity_score",   # stable id a future policy keys on
)
use(d.value)                                    # today == the rule value
```

`Decision` serialises via `to_dict()` → `{key, value, reason, confidence, evidence,
override_point, source, alternatives}`.

### The single future override point

An experience database registers **one** provider — the only place learning ever plugs in:

```python
from core.decision import set_policy_provider

def experience_policy(dec):          # dec is the rule's Decision
    row = experience_db.lookup(dec.override_point, dec.evidence)
    if row and row.samples > THRESHOLD:
        return {"value": row.value, "reason": f"learned from {row.samples} plays",
                "confidence": row.confidence}
    return None                      # leave the static rule standing

set_policy_provider(experience_policy)
```

When a provider overrides a decision, `decide()` sets `source="experience"`, keeps the rule's
value in `evidence["rule_value"]` for audit, and every call site is unchanged — they already
call `decide(...)` and read `.value`.

**Guarantees today (no provider registered):**
- `.value` is exactly the static rule → behaviour is identical to before.
- A provider that raises is ignored (a policy must never break a decision).
- `has_policy_provider()` / `clear_policy_provider()` make the state explicit and testable.

---

## What is (and isn't) a Decision

- **Route through the seam** — *heuristics* that experience could improve: scores, thresholds,
  action choices, profile/mode selection, time/difficulty estimates, category suggestions.
- **Do NOT route through the seam** — *hard invariants* that correctness depends on: reveal
  ΔE = 0, package completeness, no-leak, legal state transitions. These stay enforced in code;
  they are not something to "learn away."

---

## Adoption checklist (every new module)

1. Is this value a heuristic experience could later improve? If yes → `decide(...)`.
2. Give it a **stable `override_point`** id (`namespace.name`) — never renamed once shipped.
3. Populate **evidence** with the actual inputs (so a policy can learn from them), not prose.
4. Set a **confidence** that reflects the rule's grounding (concrete signals → higher; blended
   soft scores → lower).
5. **Expose** the `Decision.to_dict()` in the module's report/output so it is inspectable.
6. Keep hard invariants in code — do not route them through the seam.

---

## Reference implementation

`services/review_engine.py` emits its four heuristic scores as Decisions; `review.json` now
carries a `decisions` block alongside `statistics` (the `.value`s are unchanged):

| override_point | confidence | evidence |
|----------------|:----------:|----------|
| `review.complexity_score` | 0.55 | region_count, border_density, tiny_fraction, weights, caps |
| `review.border_quality_score` | 0.70 | leak/closure/shape passes, jaggedness, fragments, penalties |
| `review.artistic_score` | 0.50 | the five artistic mean-scores |
| `review.estimated_play_time_min` | 0.45 | region_count, per-region time model params |

Verified: with no provider the scores are byte-for-byte the previous values; a registered
policy overrides them (source flips to `experience`, rule value retained), and clearing the
policy restores the rule.

---

## Reserved override-point registry

These existing heuristic decision points adopt the same `decide(...)` seam **when next
created or modified** (the rule binds new work; existing engines already carry `reason` and
graduate to full Decisions as they are touched). Ids are reserved now so they stay stable:

| override_point | where | status |
|----------------|-------|--------|
| `review.*` (4 above) | review_engine | **emitting** |
| `autoprofile.start_profile` | auto_profile_selector.recommended_start | reserved |
| `autoprofile.next_profile` | auto_profile_selector.classify / next_profile | reserved |
| `artistic_border.action` | artistic_border_engine._decide (already emits `reason`) | reserved |
| `artistic_border_render.stroke` | artistic_border_renderer.STROKE_STYLE | reserved |
| `region.insignificant_merge_delta_cap` | region_detector.INSIGNIFICANT_DELTA_CAP | reserved |
| `publishing.category_suggestion` | publishing (future auto-categorisation) | reserved |

---

## Explicitly out of scope (for now)

No experience database, no data collection, no online/offline learning, no provider
implementation. Only the architecture: the `Decision` contract, the single `set_policy_provider`
seam, and the adoption convention. When learning is built, it registers one provider and
reads these override points — nothing in the modules changes.
