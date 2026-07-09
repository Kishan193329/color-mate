# Experience Learning Policy v1 — Report

**Goal.** Read `experience/patterns` and register a **safe** policy provider on the decision
seam (`backend/core/decision.py`), so the review layer's advisory decisions start reflecting
the admin's accumulated rejections. This closes the loop begun by the
[Future-Learning Architecture](FUTURE_LEARNING_ARCHITECTURE.md) (the seam) and the
[Rejection Feedback System](REJECTION_FEEDBACK_SYSTEM_REPORT.md) (the data).

**No image-processing algorithm is changed.** The policy overrides only four low-risk review
decisions and nothing else.

---

## What it may / may not touch

Hard whitelist in `experience_policy.py`:

| Overrides (low-risk review decisions) | Never touches |
|---------------------------------------|---------------|
| `review.complexity_score` | reveal |
| `review.artistic_score` | masks |
| `review.estimated_play_time_min` | leak validation |
| `review.recommendation` | region generation |
| | border rendering |

The provider returns `None` for **any** override point outside the whitelist, so even when
reveal / mask / region / border engines start emitting Decisions, this policy will not affect
them. `review.border_quality_score` is deliberately **not** whitelisted (it is grounded in
concrete leak/pass signals) and stays a rule.

---

## How it learns (v1)

The patterns rollup holds, per reject category, the **mean metric values** of the images
rejected for that reason (`metric_signals_by_reject_category`) and the **count**
(`by_reject_category`). For the image under review the policy measures how close its metric is
to a rejection centroid:

- **support** — a category must be *repeated*: `count ≥ MIN_SUPPORT (2)`; weight saturates at
  `STRONG_SUPPORT (5)`.
- **proximity** — `1.0` at the centroid, decaying to `0` at the band edge (`±30%`).
- **weight = support × proximity**; a bounded, weighted nudge is applied
  (scores ≤ ±12 points, play-time ≤ ±6 min), and the decision's `source` flips to
  `experience` with an explicit reason. No data / far from any centroid → no override.

Category → score links (sign = direction the *score* moves):

| Decision | raises | lowers |
|----------|--------|--------|
| complexity_score | over_complicated, too_many_tiny_regions | too_simple |
| artistic_score | — | not_fun_to_color, ugly_lineart |
| estimated_play_time_min | over_complicated, too_many_tiny_regions | — |
| recommendation | flags `likely_reject` / `needs_attention` when the image resembles repeated rejection patterns | |

Correctness categories (bad_border, broken_shape, wrong_numbering, bad_mask, …) are collected
but drive **no** score override in v1 — they belong to border/mask/region policies, which this
policy does not touch.

Activation: `experience_policy.activate()` runs at app startup (process-global). Patterns are
reloaded when `experience/patterns/patterns.json` changes. Status: `GET /api/admin/experience-policy`.

---

## Validation (`research/08_reports/experience_policy/validation.log`)

Baseline vs policy on a real job, with the experience store seeded by repeated rejections near
the image's own metrics.

| Requirement | Result |
|-------------|--------|
| **No experience data = identical output** | stats identical, recommendation identical, all 5 decisions `source=rule`; processing outputs byte-identical |
| **With rejection data = source changes to experience** | complexity/artistic/play-time/recommendation → `source=experience` |
| complexity_score 76 → **83**, artistic 34 → **29**, play-time 21 → **25** | bounded, evidence-weighted |
| recommendation → **likely_reject** | matched `over_complicated`, `too_many_tiny_regions`, `not_fun_to_color` |
| **Processing outputs unchanged** | `game_config` / `labels.npy` / `region_mask.png` / `reveal_completed.png` / `reveal_master.png` all byte-identical |
| **Decisions explain the override** | each carries an `experience:` reason (see below) |
| `border_quality_score` (not whitelisted) | stays `source=rule` |
| **Far-from-centroid data** | no override — all `source=rule`, scores back to baseline |

Example override reasons (from the run):

```
review.complexity_score:        experience: 3 images near this were rejected as
                                'over_complicated' (complexity_score≈76.0); raised by 7 (conf 0.74)
review.artistic_score:          experience: 2 images near this were rejected as
                                'not_fun_to_color' (artistic_score≈34.0); lowered by 5 (conf 0.66)
review.estimated_play_time_min: experience: 3 images near this were rejected as
                                'over_complicated' (…play_time≈21.0); raised by 4 (conf 0.74)
review.recommendation:          experience: resembles 3 repeated rejection pattern(s);
                                closest 'over_complicated' (3 rejections, complexity≈76 vs 76)
```

Evidence: `validation.log`, `sample_experience_decisions.json`, `test_experience_policy.py`.

---

## Files

- `backend/services/experience_policy.py` — reads patterns, the whitelisted provider
  (proximity-to-centroid matching, bounded nudges), `activate` / `deactivate` / `status`.
- `backend/services/review_engine.py` — raw-value helpers split out; new `review.recommendation`
  decision (matched on a raw metrics snapshot); `review["recommendation"]` exposed.
- `backend/main.py` — `experience_policy.activate()` at startup;
  `GET /api/admin/experience-policy` status.

## Verdict

The review layer now learns from rejections: when a repeated rejection pattern matches, the
four low-risk review decisions move toward what the admin's rejections imply, each explaining
why — while reveal, masks, leak validation, region generation and border rendering are provably
untouched and every processing output stays byte-identical. With no experience data the system
is bit-for-bit the static rules. This is a v1 nudge over the *review* layer only; feeding the
signals into processing engines remains deliberately out of scope.
