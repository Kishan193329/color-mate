# Visual Improvement — Brain-Guided Gradient Expansion (cat.png)

**Date:** 2026-07-06 · **Validation:** cat.png only · **Profile:** medium · **AI:** local
**Goal:** reduce rendered ΔE on the cat from 13.14 **under 10**, expanding tonal splits only
where QF13 permits. **Achieved: 9.58.**

Delivered in two stages on top of Engineering Sprint 1 (QF13 = single decision authority):
- **VI1 — subject tier:** bounded tonal split of large SUBJECT_CORE / fur / face / body areas.
- **VI2 — background tier + palette budget:** the large graded backdrop (the biggest remaining
  ΔE term) gets ≤2 tones, and the palette merge threshold is tightened so the new tonal bands
  survive grouping (rendered ΔE can't beat the per-region mean-fill floor unless the palette
  can represent it).

---

## Result — Before vs After

| Metric | Before | After | Δ |
|---|---|---|---|
| **rendered ΔE** (as painted, grouped palette) | 13.136 | **9.580** | **−3.556 (−27.1%)** |
| **mean-fill ΔE** (per-region sampled) | 11.471 | **9.595** | −1.876 (−16.4%) |
| **SSIM** (rendered / mean-fill) | 0.8403 / 0.8451 | **0.8420 / 0.8462** | +0.0017 / +0.0011 |
| **region count** | 88 | 96 | +8 |
| **palette count** | 17 | 26 | +9 |
| **leak count** | 0 | **0** | 0 |
| **visual score** | 99 | **99** | 0 |

Waypoint: VI1 (subject only) reached rendered 12.609; VI2 (background + palette budget) reached
**9.580**. Determinism verified (two fresh runs identical). ΔE/SSIM over all non-line pixels.
Splits: 9 primary + **4 subject-expansion** + **1 background-expansion** → 22 → **30** sub-regions.

---

## What was changed (QF13 stays the authority; no analysis recomputed)

**QF13 (`artistic_decision_engine`) — two bounded expansion tiers in the authoritative `split_plan`:**

| Tier | Gate | Cap |
|---|---|---|
| `GRADIENT_EXPANSION` (VI1) | role ∈ {SUBJECT_CORE, FUR_PATTERN}, area ≥ 5000, cvar ≥ 4, `preserve_gradient` | 3 bands |
| `BACKGROUND_EXPANSION` (VI2) | role = BACKGROUND_SUPPORT, area ≥ 20000, cvar ≥ 8 | **2 bands** — a background must never compete with the subject |

Both are appended **after** the primary entries, so the primary 9 splits stay byte-identical —
the improvement is purely additive. `FACIAL_FEATURE` is never in either set (eyes/nose/mouth
excluded by role); tiny regions excluded by the area floors.

**QF12 (`intelligent_split_engine`)** obeys the per-entry tier: same Lab k-means split, bounded
`k` (≤ cap), relaxed luma floor, expansion min-cluster so every band stays numberable.

**`config.py` — medium `palette_delta` 18 → 12 (palette fidelity budget).** Tonal bands < ~18 ΔE
apart re-merged in the LAB palette grouper, capping rendered ΔE at the mean-fill floor + a ~1.6
grouping penalty. The finer threshold lets the bands survive, closing that gap
(rendered 11.2 → 9.6 ≈ the 9.595 floor). This is why palette count rises 17 → 26 — the colours
are **required** to represent the fidelity, not waste.

**Inputs consumed (no recompute):** `artistic_decision_report.json` (authority),
`processing_policy_report.json`, `semantic_region_report.json`, `material_behavior_report.json`.

---

## What actually split (safety verification, PRE-split plan)

**Subject (`GRADIENT_EXPANSION`) — face + body + fur, all large, 0 facial features:**

| rid | role | class | area | cvar | bands |
|----|------|-------|-----:|-----:|------:|
| 35 | SUBJECT_CORE | SKIN | 31,122 | 4.6 | 2 | face volume |
| 62 | SUBJECT_CORE | FUR | 30,268 | 10.8 | 3 | body |
| 63 | FUR_PATTERN | FUR | 21,279 | 8.6 | 2 | fur |
| 66 | SUBJECT_CORE | FUR | 6,184 | 11.0 | 2 | chest |

**Background (`BACKGROUND_EXPANSION`) — the one big backdrop, gentle 2-tone:**

| rid | role | class | area | cvar | bands |
|----|------|-------|-----:|-----:|------:|
| 2 | BACKGROUND_SUPPORT | BACKGROUND | 208,496 | 12.6 | 2 |

✅ **expansion tiers: 0 facial features, 0 tiny regions, background ≤ 2 tones, 0 leaks, score held.**
Honoured "Do NOT": no over-split (5 large areas, ≤3 bands), no UI change, no numbering-engine
change, no global SVG-engine change, no tiny-region split, **no eye/nose/mouth split**.

### Transparency note — pre-existing legacy facial splits (unchanged)
The **primary** (legacy) gate — the pre-VI1 engine, byte-identical here — does split 4 small
high-variance facial regions (eye/nose catch-lights, cvar 16–23) into base + highlight bands.
This is **not** introduced by VI1/VI2, and it *preserves* the catch-lights (aligned with
`protect_highlight`). Extending QF13's facial protection to the primary gate is a clean, separate
follow-up (it would slightly raise ΔE and could dull catch-lights, so it was left as-is).

---

## Honest reading

- **rendered ΔE 9.58 is at the mean-fill floor (9.595)** — the palette now represents the split
  regions faithfully, so there is little left to gain from *this* image without finer regions.
- **Palette count 26** classifies the exported `game_config` as "hard" difficulty (>20 colours).
  That is the intended trade for fidelity; a per-difficulty budget could keep "medium" lighter.
- The `palette_delta` change is **global to the medium profile** (all medium jobs get finer
  colour). A fully **brain-guided per-region** budget (fine delta only for high-importance /
  split regions) needs QF13 sequenced *before* the palette pass in the rebuild — noted as the
  clean follow-up (it also removes the global side-effect).
- Further ΔE gains would come from finer subject regions and enforcing `protect_highlight` as its
  own palette entry, not from more splitting (splitting more flat regions churns the palette).

---

## Files changed
- `backend/services/artistic_decision_engine.py` — `GRADIENT_EXPANSION` + `BACKGROUND_EXPANSION` tiers.
- `backend/services/intelligent_split_engine.py` — bounded per-tier split (k-cap, luma floor, min-cluster).
- `backend/config.py` — medium `palette_delta` 18 → 12 (palette fidelity budget).

*No UI, numbering-engine, or global SVG-engine logic changes. QF13 remains the single authority.*
