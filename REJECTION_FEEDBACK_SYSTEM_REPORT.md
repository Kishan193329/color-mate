# Experience Learning — Rejection Feedback System — Report

**Goal.** When an admin rejects an image, capture **why** — as structured data a future
learning policy can use. This is the first layer of the experience database that the
[Future-Learning Architecture](FUTURE_LEARNING_ARCHITECTURE.md) seam
(`core.decision.set_policy_provider`) will later consume.

**This sprint only collects.** It changes no processing algorithm and no engine reads the
store back yet.

---

## The admin reject flow

The Reject button on the admin publishing dashboard now opens a **structured feedback modal**
instead of a bare prompt:

1. **Reject** clicked
2. **Required reason category** (a dropdown of the 12 categories) + a **required free-text reason**
3. **Optional screenshot upload**
4. **Optional notes** — *what is wrong · why it is not useful · which area failed · what should
   happen next time*

Submitting posts multipart to `POST /api/admin/job/{id}/reject-feedback`, which records the
feedback and advances the lifecycle to `rejected` (publishing + review in sync).

### Reject categories (required taxonomy)

`bad_border · broken_shape · too_many_tiny_regions · important_detail_lost · ugly_lineart ·
wrong_numbering · bad_mask · not_fun_to_color · over_complicated · too_simple ·
wrong_category_profile · other`

Served by `GET /api/admin/reject-categories`; an unknown category or empty reason is a `400`.

---

## The experience database

Durable store at the repo root (structure version-controlled via `.gitkeep`; the accumulated
records are runtime data, gitignored):

```
experience/
  rejections/   one <record_id>.json (+ <record_id>_screenshot.*) per rejection
  approved/     one <record_id>.json per approval (a positive signal)
  patterns/     patterns.json — a rolled-up summary for learning
```

### Rejection record — every field saved

```
record_id, kind, job_id,
image_id                     # sha1 of original.png — stable across reprocesses
category                     # the required reject taxonomy (one of 12)
image_category               # the image's content category (context)
profile                      # the processing profile at rejection
reject_reason                # the required free-text reason
reject_notes                 # { what_is_wrong, why_not_useful, which_area_failed, what_next_time }
uploaded_screenshot_path     # repo-relative path to the saved screenshot (or null)
affected_area_description
metrics_at_rejection         # review.json statistics + the future-learning Decisions
reports_snapshot             # [{ name, reference, sha1 }] for every brain report present
created_at
```

The record is also written to the job's `rejection_feedback.json`. The `metrics_at_rejection`
block embeds the review layer's **Decisions** (reason/confidence/evidence per score), so each
rejection is tied to the exact heuristic values that produced it.

### Patterns rollup (`patterns/patterns.json`)

Aggregated on every record: totals + reject-rate, counts `by_reject_category` / `by_profile`
/ `by_image_category`, and — the key learning material — **`metric_signals_by_reject_category`**:
the mean of each metric (region_count, tiny_regions, complexity, border-quality, artistic, …)
for the rejections in each category. Example:

```json
"too_many_tiny_regions": { "region_count": 94, "tiny_regions": 5,
                           "complexity_score": 58, "artistic_score": 48, ... }
```

A future learning policy reads these signals to override static rules via the decision seam
(e.g. "images an admin calls *too_many_tiny_regions* cluster at tiny_regions ≥ N → raise the
merge aggressiveness"). No such policy exists yet — this sprint only supplies the data.

---

## API

| Method + endpoint | Purpose |
|-------------------|---------|
| `GET /api/admin/reject-categories` | the 12-category taxonomy for the reject form |
| `POST /api/admin/job/{id}/reject-feedback` | multipart: `category`, `reason`, 4 notes, `affected_area`, `screenshot` → record + set `rejected` |
| `GET /api/admin/experience` | store summary (taxonomy + rolled-up patterns) |

Approval capture is wired into the existing `POST …/review/approve`, which now also writes an
`experience/approved/` record (best-effort).

---

## Constraints honoured

- **No processing algorithm changed** — the store reads existing pipeline / review outputs.
- **No engine consumes the store yet** — collection only; the learning policy is future work.
- Hard-invariant behaviour (reveal, packaging, lifecycle guards) is untouched.

---

## End-to-end test (cat image)

Driving the reject-feedback endpoint on the published cat job:

```
reject-categories                 -> 12
REJECT (category=too_many_tiny_regions, reason, 4 notes, screenshot)
   -> status rejected; record with ALL required fields present
   -> image_id img_4cc3f68ee7f1, profile hard, 12 reports referenced (with sha1),
      metrics incl. 4 review Decisions, screenshot saved, rejection_feedback.json written
validation                        -> bad category 400, empty reason 400
APPROVE (br_14)                   -> experience/approved record
patterns rollup                   -> totals {rejections:1, approvals:1, reject_rate:0.5};
                                     metric_signals per reject category
```

Evidence: `research/08_reports/rejection_feedback/sample_rejection.json`,
`sample_patterns.json`, `test_reject_feedback.py`.

---

## Files

- `backend/services/experience_store.py` — the store: 12 categories, `record_rejection`,
  `record_approval`, `rebuild_patterns`, `summary`.
- `backend/config.py` — `EXPERIENCE_DIR` + `rejections/ approved/ patterns/` (created on start).
- `backend/main.py` — reject-categories, reject-feedback (multipart), experience-summary
  endpoints; approval capture in the approve bridge; the structured reject modal on the admin
  publishing page.
- `.gitignore` + `experience/*/.gitkeep` — versioned structure, ignored runtime records.

## Verdict

Every rejection now yields a complete, structured experience record — required category +
reason, optional notes and screenshot, the metrics and Decisions at rejection time, and
snapshot references to every report — rolled up into per-category learning signals. Approvals
are captured as positive signals. Nothing consumes the store yet; the architecture (the
decision seam) is ready to when learning is built next.
