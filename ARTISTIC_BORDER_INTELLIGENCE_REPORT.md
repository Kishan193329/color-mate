# Artistic Border Intelligence — Foundation

**Date:** 2026-07-08 · **Validation:** all 27 batch images · **Scope:** intelligence only.
**Goal:** not better edge detection, not better segmentation — better **artistic judgement** of the
border that already exists. For every border segment the engine scores six artistic dimensions and
decides one action. It writes `artistic_border_report.json` and **changes nothing else**: no rendering,
no border modification, no geometry / palette / numbers / SVG / export / reveal / line-art change.

**Result: 27 / 27 analysed · 0 crashes · all 7 actions exercised · every existing artifact byte-identical
(only the new report is added).**

---

## 1. What a "border segment" is (no re-detection)

The engine does **not** find edges or contours — that is already done. It reuses the QF14
`contour_character_report.json`, whose rows are exactly the border segments: one contour per region
boundary, already classified (OUTER_SILHOUETTE, FACIAL_EDGE, PETAL_EDGE, LEAF_EDGE, FUR_EDGE, FLOW_EDGE,
WHISKER, DECORATIVE, INNER_STRUCTURE) with geometry (length, curvature, local complexity, organic-ness,
corner count, area) and protection flags. It joins each segment to its region's brain records —
QF13 `artistic_decision_report` (role, visual_importance, protect_* flags, validation_priority),
QF8 `material_behavior_report` (behavior_class, noise_removal_score), QF7 `semantic_region_report`
(semantic_class, texture / flow). **Segmentation and edges are untouched; only judged.**

## 2. Six scores per segment (each in [0, 1])

| score | what it measures |
|---|---|
| `visual_importance` | how much the segment matters to the whole picture (role importance, silhouette/face, length) |
| `colouring_enjoyment` | how satisfying the region is to actually paint (pleasant mid-size shape = high; speck / jagged mess = low) |
| `structural_importance` | how much it defines structure (silhouette, major shape, facial features) vs inner micro-detail |
| `artistic_importance` | the "an illustrator would guard this" value (eyes, petals, decorative detail, elegant strands) |
| `simplification` | how much it *should* be simplified (SIMPLIFY behaviour, noise, low-value complexity; suppressed by protection/CRITICAL) |
| `playability` | contribution to a good color-by-number to play (clean colourable regions raise it; tiny clutter / over-jagged borders lower it) |

## 3. Seven actions (priority-ordered decision)

`REMOVE → EMPHASIZE → SOFTEN → THICKEN → THIN → SIMPLIFY → KEEP` (first match wins).

| action | fires when | illustrator intent |
|---|---|---|
| **REMOVE** | compact micro-fragment: area ≤ 30px **and** length ≤ 40px, non-focal, non-CRITICAL | prune noise specks / micro-wrinkles |
| **EMPHASIZE** | high artistic **and** visual importance on a focal class / silhouette (protected or flower-centre) | protect & feature eyes, lips, petals, decorative accents |
| **SOFTEN** | organic flowing edge (fur / flow / petal / organic silhouette), not a simplify target | preserve hair/fur/petal flow |
| **THICKEN** | primary outer silhouette, high structural importance | bolden the main outline for legibility |
| **THIN** | small, high-value, high-complexity, over-inked (sharp / mechanical / micro-detail) | keep delicate detail crisp, not blobby |
| **SIMPLIFY** | high simplification score, non-focal, non-CRITICAL | clean cheeks / leaf veins / minor folds / busy background |
| **KEEP** | balanced / already good | major folds, elegant strands, decent regions |

### The design insight — a contour is not a region

Region-level `protect_shape` guards a region's **shape**; it does not mean every sub-pixel contour inside
it is sacred. The cat's face region (importance 0.95, protected) carries 114 interior sub-contours of
area 0–22px — clutter, not shape. So REMOVE is gated on a **compact-speck floor (small in area *and*
length)** rather than on the region's protection flag, while the **length** floor spares an elegant thin
strand or a whisker (tiny area, long length). Removal still never touches a FACIAL_EDGE, a silhouette, a
whisker, a petal, or a **CRITICAL** region — so the interior detail of the face core is protected, and
only genuine non-critical noise (fur flecks, decorative specks, leaf flecks) is flagged. This is exactly
the Hair example: short compact fur fragments → **REMOVE**, long thin strands → **KEEP/SOFTEN**.

## 4. Example alignment (from the cat `artistic_border_report.json`)

| brief | segment | action | reason (verbatim) |
|---|---|---|---|
| Face — protect eyes/lips | FACIAL_EDGE | **EMPHASIZE** | high-value facial feature (protect eyes/lips) — make it stand out |
| Hair — preserve flow | FUR_EDGE | **SOFTEN** | organic fur/hair flow — preserve smooth flow |
| Hair — remove noise | FUR_EDGE | **REMOVE** | compact micro-fragment fur noise — declutters, shape unaffected |
| Hair — keep elegant strand | FLOW_EDGE (len>60) | **KEEP** | balanced value — retain as drawn |
| Flower — enhance centre/petal | PETAL_EDGE | **EMPHASIZE** | high-value petal focal — make it stand out |
| Accessory — protect decorative | DECORATIVE | **EMPHASIZE** | high-value decorative detail (protect accessory) |
| Silhouette — bolden | OUTER_SILHOUETTE | **THICKEN** | primary silhouette — thicken for legibility |
| Detail — keep crisp | dense micro-detail | **THIN** | delicate over-inked detail — thin to keep it crisp |
| Cheeks / veins / background | reducible region | **SIMPLIFY** | reducible … — simplify while keeping the shape |

## 5. Validation — all 27 images

Every image analysed with **0 crashes**, and all seven actions appear across the batch.

Aggregate over 27 images / 19,046 segments:

| action | share |
|---|---|
| KEEP | 20.7% |
| REMOVE | 49.0% |
| SIMPLIFY | 14.9% |
| THIN | 9.9% |
| EMPHASIZE | 5.2% |
| SOFTEN | 0.2% |
| THICKEN | 0.2% |

**REMOVE tracks source fragmentation — that is the diagnostic value.** A clean sheet (cat, 464 segments)
is only 21% REMOVE; a pathologically over-fragmented line art (images #7 / #23 / #24 at 2,000–2,400
contours) runs ~60%, because thousands of those "contours" are sub-pixel interior specks an illustrator
would never draw. The engine is *measuring* that over-fragmentation, not acting on it. SOFTEN and THICKEN
are deliberately specialised (organic-flow and primary-silhouette only), so they are rare by design.

Per-image data: `research/08_reports/artistic_border/artistic_border_batch_summary.csv`.
Samples: `sample_artistic_border_{cat_illustration,portrait,mandala,landscape}.json`.

### No visual change (byte-identical proof)
Full-folder snapshot of the cat run through `process_upload`, this engine vs the git-stashed pre-sprint
code: **all 42 pre-existing artifacts byte-identical** (line art, regions, palette, labels, game_config,
result.zip members, every reveal asset, and every brain report). The only differences are the **new**
`artistic_border_report.json` and its one additive download-URL entry in `job.json`. ⇒ no rendering, no
border modification, no geometry / palette / number / SVG / export / reveal change.

## 6. Files changed
- `backend/services/artistic_border_engine.py` (new) — six-score + seven-action per-segment analysis;
  reads the QF14/QF13/QF8/QF7 reports; writes `artistic_border_report.json`; recomputes no edges or
  segmentation; never modifies any pixel or geometry.
- `backend/pipeline.py` — `_apply_artistic_border` runs after QF14 in `run()` and `rebuild_from_labels`
  (analysis only, best-effort, never blocks output).
- `backend/config.py` — `artistic_border_report` artifact registered.
- `research/08_reports/artistic_border/` — batch summary CSV + four sample reports.

## 7. Honest notes & next
- This is a **v1 heuristic** judgement layer (hand-tuned weights), not a trained model — like the image
  router foundation. Its scores are explainable and its actions are conservative on protected/critical
  regions and aggressive only on genuine noise.
- **No rendering / no border modification yet — by design.** This report is the intelligence a future
  render pass will *obey* (REMOVE → drop the contour; SOFTEN → smoother spline; THICKEN/THIN → stroke
  weight; EMPHASIZE → contrast/retain; SIMPLIFY → higher eps). Wiring any of those is the next sprint.
- The high REMOVE share on some images is a strong signal that the **upstream line art is
  over-fragmented**; feeding this report back as a pruning pass (or tightening tiny-region merging) is the
  most valuable follow-up.
