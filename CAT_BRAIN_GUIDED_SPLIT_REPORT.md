# Visual Improvement 1 — Brain-Guided Gradient Expansion

**Date:** 2026-07-06 · **Validation:** cat.png only · **Profile:** medium · **AI:** local
**Goal:** reduce rendered ΔE on the cat (13.14) toward under 10 by expanding tonal splits
**only where QF13 permits** — large SUBJECT_CORE / fur / face / body gradient areas, never
facial features, never tiny regions, 2–3 bands each.

---

## Result — Before vs After

| Metric | Before | After | Δ |
|---|---|---|---|
| **rendered ΔE** (17-colour palette, as seen) | 13.136 | **12.609** | **−0.527 (−4.0%)** |
| **mean-fill ΔE** (per-region sampled) | 11.471 | **10.938** | **−0.533 (−4.6%)** |
| **rendered SSIM** | 0.8403 | **0.8414** | +0.0011 |
| mean-fill SSIM | 0.8451 | 0.8461 | +0.0010 |
| **region count** | 88 | 93 | +5 |
| **palette count** | 17 | 19 | +2 |
| **leak count** | 0 | **0** | 0 |
| **visual score** | 99 | **99** | 0 |

Splits: 9 primary (unchanged) + **4 expansion** → 22 → **27** sub-regions.
Determinism verified (two fresh runs identical). ΔE/SSIM measured over all non-line pixels.

---

## What was changed (architecture-respecting)

QF13 remains the single decision authority (Sprint 1). This adds a **second tier** to
QF13's authoritative `split_plan`, consumed by QF12 — no analysis is recomputed; the four
brain reports are read, not rebuilt.

**QF13 (`artistic_decision_engine`) — new `GRADIENT_EXPANSION` tier.** For each region it
already scores, QF13 now also selects it for a *bounded* tonal split when **all** hold:
- `artistic_role ∈ {SUBJECT_CORE, FUR_PATTERN}` (large body / fur / face; **FACIAL_FEATURE
  is not in the set → eyes/nose/mouth are never selected),
- `region_size ≥ 5000` (≈4× QF12's `MIN_REGION_AREA` — never a tiny region),
- `color_variance ≥ 4.0` (some real tonal range to benefit),
- `preserve_gradient` (the brain permits gradient preservation — protected-shape cores are
  allowed *when safe*: a tonal split adds only internal band boundaries, never moves the
  outer silhouette).

Expansion entries are appended to `split_plan` **after** the primary entries with
`split_tier="GRADIENT_EXPANSION"` and `max_subregions=3`, so the primary 9 splits are
byte-for-byte unchanged — the improvement is purely additive.

**QF12 (`intelligent_split_engine`) — obeys the per-entry cap.** For a `GRADIENT_EXPANSION`
entry it runs the same Lab k-means split but bounded: `k`-cap ≤ 3, ≤ 2 new labels, a relaxed
luminance floor (`EXPANSION_MIN_LUMA = 5`) so smoothly-shaded masses actually band, and an
expansion min-cluster (`450`) so every band stays numberable (no new clutter → score held).

**Inputs consumed (no recompute):** `artistic_decision_report.json` (authority),
`processing_policy_report.json`, `semantic_region_report.json`, `material_behavior_report.json`.

---

## What actually split (safety verification, PRE-split plan)

| rid | role | class | area px | cvar | bands | note |
|----|------|-------|--------:|-----:|------:|------|
| 35 | SUBJECT_CORE | SKIN | 31,122 | 4.6 | 2 | cat **face** — face volume |
| 62 | SUBJECT_CORE | FUR | 30,268 | 10.8 | 3 | **body** mass |
| 63 | FUR_PATTERN | FUR | 21,279 | 8.6 | 2 | **fur** |
| 66 | SUBJECT_CORE | FUR | 6,184 | 11.0 | 2 | body / **chest** |

✅ all `SUBJECT_CORE`/`FUR_PATTERN`, all ≥ 5000 px, all ≤ 3 bands, **0 facial features**,
**0 tiny regions**, **0 leaks**. Numbering/SVG engines unchanged; UI unchanged.

Honoured "Do NOT": no over-split (4 large areas, ≤3 bands each), no UI change, no numbering-
engine change, no global SVG-engine change, no tiny-region split, no eye/nose/mouth split.

---

## Why not under 10 (honest ceiling)

Rendered ΔE improved 4%, not to <10. A process-isolated threshold sweep (earlier in-process
sweeps were discarded as state-contaminated) shows ~12.6 is the practical floor for
*subject-side* splitting on this image, because:

1. **The background is out of scope and dominant.** Two background regions (208k + 154k px)
   contribute ≈ 4.5 of the total ΔE. The goal targets the subject, so they are untouched.
2. **The palette grouping (17–19 colours) re-merges bands.** Tonal bands < ~18 ΔE apart
   collapse back to one palette colour, so low-variance splits help mean-fill ΔE but not the
   rendered (grouped) ΔE — which is why the gate requires real tonal range.
3. **Over-splitting back-fires.** Splitting more/flatter regions churns the global running-
   mean palette grouping and *raises* rendered ΔE — so expansion is deliberately narrow.
4. **The body region is already primary-split.** The single biggest subject term (the 227k-px
   fur body) already gets 5 k-means bands; more bands there regress the palette.

The robust core of this change is the **mean-fill ΔE −4.6%** (independent of palette dynamics)
plus **SSIM up** and **score held** — i.e. genuinely better per-region colour fidelity on the
face, body and fur, with no regression anywhere.

## Next levers to reach < 10 (future work, out of this scope)

- **Background tonal split** (biggest single lever, ~4.5 ΔE) — a separate `BACKGROUND_SUPPORT`
  expansion tier with its own guardrails.
- **Per-material palette budget** — let QF13 raise the palette `delta`/colour count for
  high-importance subject regions so their tonal bands survive grouping (flip the palette
  `OBEY_QF13` seam).
- **Enforce `protect_highlight`** so catch-lights survive as their own tiny palette entries.

---

## Files changed
- `backend/services/artistic_decision_engine.py` — `GRADIENT_EXPANSION` tier + thresholds in `split_plan`.
- `backend/services/intelligent_split_engine.py` — per-entry bounded split (k-cap, luma floor, min-cluster) + tier stats.

*No UI, numbering-engine, or global SVG-engine changes. QF13 stays the single authority.*
