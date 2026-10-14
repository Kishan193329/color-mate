# Current cat.png — Visual Review (post Engineering Sprint 1)

**Date:** 2026-07-06 · **Pipeline:** full current engine QF3–QF14 · **Profile:** medium · **AI:** local
**Inputs/outputs:** `research/08_reports/current_cat_run/` · **Run:** `done`, no errors

> Sprint 1 was an **architecture-only** consolidation (QF13 became the single decision authority).
> This run's pixels are **byte-identical** to the pre-sprint baseline — so this is a review of the
> *current engine's* visual quality, and "what improved" is architectural, not a pixel change.

---

## Headline numbers

| Metric | Value |
|---|---|
| **Region count** | **88** |
| **Palette count** | **17 colours** |
| **ΔE to original** — per-region mean fill (engine metric) | **11.47** |
| **ΔE to original** — as rendered with the 17-colour palette | **13.14** |
| **SSIM** — mean fill / palette | **0.845 / 0.840** |
| **Leak count** | **0** (0 region leaks, 0 background leaks — leak pass ✓) |
| **Visual score** (cleanliness) | **99 / 100** |

Supporting: borders closed (closure/shape/final pass ✓, jaggedness 0.1135→0.1093, 7 gaps repaired);
QF12 split **9** regions → **22** sub-regions (5 flow-aligned) with **QF13** as the selection authority;
QF14 contours IoU **0.9548** (shape preserved), smoothness 0.1275→0.1182; semantic mean confidence 0.642.

**ΔE reading:** on the CIE76 scale roughly `<5 excellent · 5–10 good · 10–15 fair · >15 poor`.
At **11.5–13.1** the fill is **fair** — visibly close but with flattened gradients.

---

## What improved

Architectural (Sprint 1), visible in the reports rather than the pixels:

- **The brain is now obeyed.** `intelligent_split_report` shows `selection_authority = QF13` and
  `selection_source = artistic_decision_report.split_plan` — the split engine no longer re-derives
  policy from QF9; it consumes QF13's ordered plan. Same 9 splits, now driven by the single authority.
- **One selection predicate, one owner** (`core.gradient_common.legacy_split_selected`); no duplicate
  gate in QF12.
- **Actuators wired** to read QF13 (`core.artistic_decisions`), ready to obey `palette_merge_permission` /
  `svg_precision` / `numbering_sensitivity` once activated.

Genuine strengths of the current engine (unchanged, still good):

- **Border integrity is excellent** — 0 leaks, fully closed line layer, 99/100 cleanliness, only 3
  small leftover regions.
- **Structure-aware splitting works** — the 9 high-gradient regions (fur / leaf / wood / cloth) were
  split into 22 sub-regions by Lab k-means, recovering stripe/shadow/highlight bands that a flat fill
  would lose; 5 followed material flow.
- **Contours are shape-faithful** — QF14 smoothed silhouettes (jaggedness ↓) while keeping IoU 0.955,
  so curves got more elegant without drifting from the mask.
- **Role reasoning is protecting the right things** — 83/88 regions protected, incl. 10 facial features
  guarded (eyes/nose/mouth), the enclosure logic reclaiming mislabeled subject islands.

---

## What still looks bad

1. **Colour fidelity is the #1 gap (ΔE ≈ 11.5–13.1).** Flat fills flatten smooth gradients everywhere
   **except** the 9 split regions. The as-rendered 17-colour palette adds ~1.7 ΔE over the ideal
   mean-fill — the paint-by-number look, not painting.
2. **The split gate is narrow.** Only 9 regions were selected for tonal splitting; most gradient areas
   (face volume, soft body shading, cast shadows) stay single flat tones → the largest contributor to ΔE.
3. **Highlights / catch-lights are lost.** Specular dots fall below `MIN_CLUSTER`/`MIN_REGION_AREA` and
   fold into the base. QF13 flags `protect_highlight`, but **no engine enforces it yet** (actuator
   seams are inert this sprint).
4. **Fine fur texture is averaged away.** k-means recovers major bands, but directional single-stroke
   fur detail is smoothed into flats (SSIM 0.845).
5. **Semantic classifier is shaky.** Mean confidence 0.642 (min 0.42); some regions are mislabeled
   (e.g. cloth/wood on the subject body) and only rescued by QF13's adjacency override — a learned or
   better-tuned classifier would raise the ceiling.

---

## Is this worth continuing? **YES**

The engine produces a **coherent, leak-free, playable** color-by-number (88 regions, 17 colours,
99/100 cleanliness, shape-faithful contours), and after Sprint 1 the architecture is clean with a
single decision authority. The remaining weakness is **one well-understood axis — colour/gradient
fidelity (ΔE ~12)** — and it is directly addressable, not a structural dead end. Stop-ship risk is low.

---

## Next best improvement

**Attack ΔE directly by widening tonal fidelity where the brain already says to protect it.** Highest
ROI, lowest risk, no algorithm redesign:

1. **Widen QF12's split selection** beyond the current narrow gate so large `SUBJECT_CORE` / `SKIN`
   gradient regions (face volume, soft body shading) each get 2–3 tonal sub-regions. This alone should
   move mean-fill ΔE from ~11.5 toward **< 8**.
2. **Enforce `protect_highlight`** — lower `MIN_CLUSTER`/`MIN_REGION_AREA` for regions QF13 marks, so
   catch-lights survive instead of folding into the base.
3. **Then flip `OBEY_QF13=True` on the palette engine** so `LOCKED` facial features/flowers keep their
   own colour instead of being merged into a neighbour group (recovers some of the ~1.7 ΔE the 17-colour
   grouping adds).

Sequence: (1) is the big lever; (2) and (3) are cheap follow-ons that need QF13 sequenced ahead of the
palette pass (already scoped in the Sprint 1 report's migration plan).

---

### Artifacts (in `research/08_reports/current_cat_run/`)

`original.png` · `lineart.png` · `numbered_preview.png` · `flat_fill_preview.png` · `outline.svg` ·
`palette.json` · `regions.json` · `game_config.json` · `border_quality_report.json` ·
`semantic_region_report.json` · `material_behavior_report.json` · `processing_policy_report.json` ·
`artistic_decision_report.json` · `contour_character_report.json` · `final_quality_summary.md`
