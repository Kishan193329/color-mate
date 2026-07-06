# Product Sprint 2 — Auto Profile Selection + Quality Retry

**Date:** 2026-07-06 · **Validation:** cat.png · **AI:** local
**Goal:** on any upload, ColorForge automatically picks the best output profile and retries if the
quality is poor. No algorithm redesign, no frontend change.

**Result on cat.png:** Medium ran first, was judged **too flat** (rendered ΔE 11.1 > 10), the engine
auto-retried **Hard**, which met the quality bar (ΔE 9.76), and Hard was kept — in **2 runs, no
wasted work**.

---

## How it works

Two new modules sit *above* the profile engine (Product Sprint 1); the pipeline itself is unchanged.

- **`auto_profile_selector.py`** — pure functions. `assess()` summarises a finished job into quality
  signals; `classify()` turns them into a verdict + direction; `score()` is the single "is this a good
  color-by-number" number used to pick the winner. Recomputes no analysis — it only reads artifacts
  the pipeline already wrote (`regions.json`, `palette.json`, `quality_report`, `border_quality_report`,
  `artistic_decision_report`, `labels.npy`).
- **`quality_retry_engine.py`** — `process_upload(job_id)` orchestrates: run **Medium** →
  assess → retry only if needed → keep the best-scoring attempt as the final output → finish once.

The `/api/process` endpoint's background task now calls `process_upload` instead of `pipeline.run`
(backend only — the frontend still just polls job status and downloads the same artifacts).

### Quality signals checked (per attempt)

| Signal | Source | Used for |
|---|---|---|
| leak count | QF3 `border_quality_report` | leaks → repair (step to a higher gap-closing profile) |
| rendered ΔE (as-painted) | grouped-palette fill vs original | "too flat" / fidelity, and the score |
| mean-fill ΔE (flatness) | per-region fill vs original | flatness floor (how much detail is unrepresented) |
| region count | `regions.json` | "too complex" |
| palette count | `palette.json` | "too complex" / "too simple", and the score |
| tiny-region count | `quality_report.small_regions_remaining` | clutter → "too complex" |
| protected-highlight loss | QF13 roles + palette assignment | catch-lights merged into a material (penalised) |
| visual score | `quality_report.score` | tie-break in the score |

### Decision rules (`classify`, priority order)

| Verdict | Trigger | Action |
|---|---|---|
| **LEAKS** | leak_count > 0 | step **down** (simpler profile closes gaps harder) |
| **TOO_COMPLEX** | palette > 34, or regions > 170, or tiny > 8 | step **down** |
| **TOO_FLAT** | rendered ΔE > 10 **and** palette < 34 (headroom) | step **up** (more fidelity) |
| **TOO_SIMPLE** | palette < 14 | step **up** |
| **OK** | none of the above | keep |

Retries are **stepwise and bounded** (Medium + up to 2 targeted moves along
Easy→Medium→Hard→Professional→Maximum Fidelity), so a poor Medium costs one extra run, and a good
Medium costs none. The winner is the highest `score()` among attempts; it is re-run only if it
isn't already the on-disk output (usually it is).

---

## Cat.png — the retry trace

| # | Profile | Rendered ΔE | Palette | Regions | Tiny | Verdict | Score |
|---|---|---:|---:|---:|---:|---|---:|
| 1 | Medium | 11.12 | 22 | 96 | 3 | **TOO_FLAT** → up | 66.63 |
| 2 | Hard | **9.76** | 26 | 109 | 0 | **OK** | **71.72** |

**Chosen: Hard** — "medium:TOO_FLAT → hard:OK. Chose hard (best score 71.72, rendered ΔE 9.76, 26
colours)." `reran_winner: false` (Hard was the last run). The decision + trace are written to the job
as `auto_profile_report.json`, and the chosen profile is recorded on the job record (`auto_profile`).

### Why Hard (the selector's score over all five)

| Profile | Rendered ΔE | Palette | Regions | Verdict | Score |
|---|---:|---:|---:|---|---:|
| Easy | 14.72 | 11 | 44 | TOO_FLAT | 49.83 |
| Medium | 11.12 | 22 | 96 | TOO_FLAT | 66.63 |
| **Hard** | **9.76** | **26** | **109** | **OK** | **71.72** ← best |
| Professional | 9.39 | 37 | 119 | TOO_COMPLEX | 66.83 |
| Maximum Fidelity | 7.81 | 46 | 128 | TOO_COMPLEX | 38.58 |

The score rewards fidelity (low rendered ΔE) but penalises palettes outside the usable 14–34 band and
any highlight loss — so it lands on Hard, the point where the cat is faithful (ΔE < 10) without
becoming an overwhelming 37–46-colour sheet. The stepwise retry reaches the same answer without
running all five.

---

## Determinism fix (found & fixed this sprint)

Running several profiles back-to-back in one process exposed a latent bug: OpenCV's **global k-means
RNG** (`cv::theRNG()`) carries state across calls, so a profile's output depended on what ran before it
(fresh Hard = 26/109/9.76; Hard-after-Medium = 27/108/9.86). Fix: `cv2.setRNGSeed(0)` at the start of
each run — which **is** OpenCV's fresh-process default state, so it is a **no-op for a single run** and
makes back-to-back runs reproducible. Re-validated: **Medium is still byte-identical** to the
committed output (snapshot diff clean); the retry engine now produces the same per-profile numbers as
isolated runs.

---

## Notes / limits
- Every upload now runs 1–3 pipelines. A good Medium = 1 run; the common "escalate one step" = 2 runs.
- `process_upload` holds the job in `processing` across attempts and finishes exactly once, so a
  polling client never sees a premature "done".
- Protected-highlight loss is a heuristic (it compares the final artistic roles against the palette);
  it reads 0 for Medium/Hard and only nudges the score away from the over-fine extremes.
- The game bundle's `difficulty` label is still colour-count-derived (separate from the profile name).

## Files changed
- `backend/auto_profile_selector.py` (new) — assessment + decision + scoring.
- `backend/quality_retry_engine.py` (new) — Medium-first orchestration with bounded retry.
- `backend/main.py` — `/api/process` background task → `quality_retry_engine.process_upload`.
- `backend/pipeline.py` — `cv2.setRNGSeed(0)` per run for cross-run determinism (no-op for one run).
