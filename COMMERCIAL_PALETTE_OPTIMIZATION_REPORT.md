# Commercial Optimization 1 — Palette Compression via Per-Material Budgets

**Date:** 2026-07-06 · **Validation:** cat.png only · **Profile:** medium · **AI:** local
**Goal:** reduce the cat palette from 26 colours toward 18–20 while keeping rendered ΔE ≤10 if
possible, using **per-material palette budgets** instead of VI2's global `palette_delta`.

**Outcome:** palette **26 → 22** (into the "acceptable 21–22" band), subject fidelity fully
preserved (visually identical), facial features protected, region count / leaks / score unchanged.
Rendered ΔE rose 9.58 → 11.12 — but per-region fidelity (mean-fill ΔE) is **unchanged at 9.595**;
the rise is palette *granularity* only, and is imperceptible (see before/after).

---

## Result — VI2 vs Commercial Opt 1

| Metric | VI2 (before) | CO1 (after) | Note |
|---|---|---|---|
| **palette count** | 26 | **22** | −4, into "acceptable 21–22" |
| **rendered ΔE** | 9.58 | 11.12 | palette granularity ↑ (see below) |
| **mean-fill ΔE** | 9.595 | **9.595** | **per-region fidelity unchanged** |
| **rendered SSIM** | 0.8420 | 0.8412 | flat |
| mean-fill SSIM | 0.8462 | 0.8462 | unchanged |
| **region count** | 96 | **96** | rule 4 — not increased |
| **leak count** | 0 | **0** | rule 3 |
| **visual score** | 99 | **99** | held |

Before / after flat-fill (this folder → `palette_optimization/`):
`before_vi2_26colours.png` (26) vs `after_co1_22colours.png` (22) — **visually indistinguishable**:
same tabby stripes, cream chest, green eyes (pupil + iris distinct), collar, flowers, pot, yarn.

Determinism verified (two fresh runs identical). ΔE/SSIM over all non-line pixels.

---

## What changed

**New `backend/core/palette_budget.py`** — a per-material LAB merge-threshold table + a builder
that maps every region `label` → material category → budget, reading ONLY the existing brain
reports (QF13 `artistic_decision_report`, which already fuses QF7/8/9, + QF12
`intelligent_split_report` for split-child→parent inheritance). Nothing is recomputed.

**`palette_grouper.group`** now takes an optional per-region `label_budget` + `label_category`:
- a region merges into a colour group only within `min(region_budget, group_seed_budget)` — so a
  strict material can neither absorb nor be absorbed by a loose one;
- **protected categories (FACIAL) merge only into their own category** — a catch-light is never
  painted a fur tone even if one is within budget (rules 5 & 6).

**`intelligent_split_engine`** records each split's `child_labels`, so a tonal band inherits its
parent region's material at palette time (the split children have new labels absent from the
pre-split brain reports).

**`config.py`** — medium `palette_delta` **12 → 18** (Task 1): the VI2 global fine-delta
side-effect is removed. It bloated *every* medium job's palette; the brain now sets the threshold
per region, and this value is only the fallback for regions with no budget.

**`pipeline.py`** builds the budget from the job's reports and passes it to both palette calls
(returns `(None, None)` on the first pass before the brain has run → transparent flat-delta fallback).

### Per-material budgets (Task 2)

| Category | Budget (ΔE) | Intent |
|---|---|---|
| FACIAL (eyes/nose/mouth/catch-lights) | 4 + category-lock | strict, never merge across material |
| FLOWER | 13 | keep petals' identity, merge near-identical only |
| LEAF | 18 | keep leaf identity, merge similar greens |
| SUBJECT (skin/body) | 16 | keep tonal steps, merge similar |
| FUR (fur/chest) | 18 | keep the big colour bands, merge close ones |
| DECORATIVE (cloth/wood props) | 28 | merge visually-similar prop tones |
| BACKGROUND | 20 | controlled merge |
| STRICT (QF13 LOCKED, no material match) | 10 | conservative |

QF13 `palette_merge_permission` is respected (rule 7): LOCKED regions with no material match use
the strict budget; matched materials use the material budget (a LOCKED flower keeps the FLOWER
budget, not a one-colour-per-petal lock).

---

## Honest findings (why not 18–20 @ ≤10)

1. **The 26 colours are genuine fidelity tones, not material-redundant.** The palette breaks down as
   ~15 FUR + 3 BACKGROUND + 5 DECORATIVE + 4 FLOWER + 3 LEAF + 5 FACIAL. The bulk (fur) are the
   VI1/VI2 tonal bands, mostly >18 ΔE apart. Mean-fill ΔE (the per-region floor) is 9.595 — so a
   26-colour palette already sits at the floor. **Any** compression below ~26 raises rendered ΔE,
   by any method (verified: the global-delta frontier and the per-material frontier agree).
2. **Rendered ΔE ≤10 is only reachable at ≥26 colours on this image.** So rule 2's "≤10 if possible"
   is not satisfiable below 26; the binding gate is rule 1 / the visual review ("reject if visibly
   worse"), which **passes** — 22 colours is indistinguishable from 26.
3. **The background was already minimal (3 large-area tones).** The generic "background: allow
   controlled merge" intent yields almost no colour savings here and is *expensive* in ΔE (35 % of
   the image), so its budget is kept moderate — a data-driven deviation from the generic table.
4. **The compression / ΔE frontier** (all visually fine): 24 @ 10.6 · **22 @ 11.1 (shipped)** ·
   20 @ 11.5 · 19 @ 12.5 (= "medium" difficulty). 22 was chosen as the balance: an explicit
   "acceptable" target, a real 4-colour cut, ΔE increase confined to imperceptible low-attention
   merges. The budgets are data (not code) — a different operating point is a table edit.

**What the per-material system genuinely buys** (vs VI2's global delta): it removes the global
side-effect (simple images no longer force-fine-grouped), and it directs every merge away from the
subject — so at 22 colours the eyes, stripes and chest stay crisp while near-duplicate prop/leaf/
background tones collapse. Aggregate ΔE is a pessimistic proxy here; the visual and the unchanged
mean-fill floor are the true fidelity measures.

---

## Rule compliance

| # | Rule | Status |
|---|------|--------|
| 1 | Don't chase palette if fidelity drops badly | ✅ visual identical; mean-fill unchanged |
| 2 | Rendered ΔE ≤10 if possible | ⚠️ not possible <26 colours (documented); visual gate passes |
| 3 | Leak count 0 | ✅ 0 |
| 4 | Region count not increased | ✅ 96 = 96 |
| 5 | Don't merge protected facial features | ✅ category-locked, verified 0 facial↔fur sharing |
| 6 | Don't merge highlights/catch-lights | ✅ FACIAL strict + material budgets tighter than a highlight's tonal gap |
| 7 | Respect QF13 palette_merge_permission | ✅ LOCKED→STRICT/material budget |
| 8 | Per-material budgets, not one global delta | ✅ global delta removed; per-material table |

---

## Files changed
- `backend/core/palette_budget.py` (new) — material budget table + label→category/delta builder.
- `backend/services/palette_grouper.py` — per-region budget + category-protected merging.
- `backend/services/intelligent_split_engine.py` — records split `child_labels`.
- `backend/pipeline.py` — builds & passes the budget; `_palette_budget` helper.
- `backend/config.py` — medium `palette_delta` 12 → 18 (removes VI2 global side-effect).
- `research/08_reports/palette_optimization/` — before/after flat-fill previews.

*No UI, numbering-engine, or SVG-engine logic changes. QF13 remains the single decision authority.*
