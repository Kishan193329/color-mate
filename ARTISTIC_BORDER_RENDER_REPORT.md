# Artistic Border Render Pass

**Date:** 2026-07-08 · **Validation:** all 27 batch images · **Scope:** line-art / SVG rendering only.
**Goal:** *execute* the border actions the intelligence layer already decided
(`artistic_border_report.json`) — on the vector/SVG layer only, gated so it can never make a border worse.

**Result: 27 / 27 complete · 0 crashes · 0 leaks · reveal completed == original (ΔE 0) on all 27 · SVG valid
on all 27 · 27 / 27 PASS.** Render applied on **21 / 27**; on the other **6** the quality gate rejected the
change and kept the QF14 baseline (rule 6). Across the batch: **9,331 speck contours pruned, fragments
19,046 → 9,715 (−49 %)**, with region-shape fidelity preserved.

---

## 1. What it changes — and what it provably does not

The pass reads `artistic_border_report.json` (the decided action per segment),
`contour_character_report.json` (the QF14 per-contour style), and `artistic_decision_report.json`, then
re-emits the **vector border layer**:

- `outline.svg` — re-rendered per contour, with the action applied
- region `path` strings in `regions.json` / `game_config.json` — geometry updated

It **re-detects no borders** (it re-enumerates the *existing* QF14 contours, deterministically, so a
segment's `(label, contour_index)` still maps to the same contour). It touches **nothing else** — proven
byte-identical on the cat (render pass vs the git-stashed pipeline):

| artifact | result |
|---|---|
| `labels.npy`, `region_mask.png` | **byte-identical** — no region-id / mask change (rules 3, 4) |
| `palette.json` | **byte-identical** — no palette change |
| `regions.json` / `game_config.json` **except `path`** | **byte-identical** — numbers, colours, centroids, reveal manifest unchanged |
| `lineart.png` | **byte-identical** — raster line art untouched ⇒ leaks impossible (leaks are a raster/label property) |
| every `reveal_*` asset + `reveal_validation.json` | **byte-identical** — completed reveal still equals the original |
| all brain reports, previews | **byte-identical** |
| `outline.svg`, region `path` fields | **changed (intended)** |
| `artistic_border_render_report.json`, `artistic_border_overlay.png` | **new** |

So the completed reveal remains the original by construction (reveal draws from the label grid + original
master, both untouched), and no leak can be introduced (the raster line art is never modified).

## 2. How each action is rendered

Geometry reuses QF14's adaptive machinery, so **KEEP is byte-for-byte the QF14 baseline**. Only the two
*smoothing* actions change geometry; the rest change only the stroke — deliberately, so a protected
feature is never re-approximated (that could distort an eye or petal).

| action | render |
|---|---|
| **REMOVE** | drop the contour (compact specks only — pre-gated by the intelligence layer to non-critical, non-focal micro-fragments) |
| **SOFTEN** | gentle smoothing Bézier (fur / hair / petal flow) |
| **THICKEN** | stroke ×1.5 (3.0px), geometry unchanged — outer silhouettes |
| **THIN** | stroke ×0.6 (1.2px), geometry unchanged — dense non-critical detail |
| **EMPHASIZE** | bolder + darker stroke (2.6px, #000), **geometry unchanged** — eyes / petals / accessories |
| **SIMPLIFY** | restrained coarser Bézier (eps ×1.6) — low-priority background |
| **KEEP** | QF14 policy unchanged |

## 3. Safety gate — "if visual border quality gets worse, reject"

Two robust checks; on failure the QF14 output is kept untouched:

1. **Faithful** — the transformed contours still enclose their regions as well as the QF14 baseline did
   (`shape_iou_after ≥ shape_iou_before − 0.01`). REMOVE is excluded from **both** sides, so intentional
   decluttering is not scored as a fidelity loss; this isolates SOFTEN/SIMPLIFY distortion.
2. **Bounded removal** — removed contours are < 20 % of total border length (they are compact specks;
   never strip a large share of real border).

Raster jaggedness is **deliberately not** used as a gate: it penalises the intended vertex reduction of
SIMPLIFY (a coarser polygon carries the same total curvature over fewer points, so mean-angle-per-point
rises even though the shape is smoother). Fidelity (IoU) is the correct, robust proxy for "does the border
still represent the shape."

## 4. Examples (from the per-image render reports)

- **Cat (accepted):** 100 specks removed (464 → 364 fragments), 66 EMPHASIZE (eyes/petals bolder), 28 THIN,
  2 THICKEN, 2 SOFTEN; transformed-contour IoU 0.9595 → 0.9554 (−0.0041, within tolerance) → **applied**.
- **Landscape (accepted):** 227 specks removed (662 → 435), 20 THICKEN (horizon/silhouette), 57 THIN,
  62 EMPHASIZE; IoU 0.9295 → 0.9266 (−0.0029) → **applied**.
- **Portrait (rejected):** 90 SIMPLIFY on facial detail dropped transformed-contour IoU 0.9247 → 0.8994
  (−0.0253, > 1 %) → **rejected, QF14 baseline kept** — the gate protecting a detailed face (rule 6).

Before/after line-art comparisons: `overlay_{cat_accepted,landscape_accepted,portrait_rejected}.png`
(left = QF14 with removed specks tinted red, right = artistic render).

## 5. Validation — all 27 images

| Metric | Result |
|---|---|
| completed | **27 / 27** |
| crashes | **0** |
| leaks | **0 on every image** |
| reveal completed == original | **27 / 27** (max ΔE **0.0**) |
| SVG valid (XML parse) | **27 / 27** |
| border quality improves or stays safe | **27 / 27** (21 applied & fidelity-preserved, 6 rejected → baseline kept) |
| **PASS** | **27 / 27** |
| clutter | **9,331 specks pruned; 19,046 → 9,715 fragments (−49 %)** |

The 6 rejects (transformed-contour IoU drop 1.3 %–2.5 %) are heavily-detailed images where even restrained
SIMPLIFY would shift facial/fine borders past the 1 % gate — correctly kept at the QF14 baseline.

Per-image data: `research/08_reports/artistic_border_render/batch_abrender_summary.csv`
(+ `batch_abrender.jsonl`). Samples: `sample_render_report_*.json`, `overlay_*.png`. Harness:
`batch_abrender.py`.

> Robustness note: one image hit a transient Windows `job.json` rename race under 4-way parallel batch
> runs (an infra artifact, not the render pass); it completed cleanly on a solo re-run, and the final
> 3-way-parallel batch had 0 such errors.

## 6. Files changed
- `backend/services/artistic_border_renderer.py` (new) — applies the decided actions to the SVG/vector
  layer; reuses QF14 contour enumeration + geometry; quality-gated (fidelity + bounded removal); writes
  `artistic_border_render_report.json` + a before/after overlay. Touches no raster line art, labels,
  masks, palette, numbers, or reveal.
- `backend/pipeline.py` — `_apply_artistic_border_render` runs after the intelligence pass in `run()` and
  `rebuild_from_labels` (best-effort, never blocks output).
- `backend/config.py` — render report + overlay artifacts registered.
- `research/08_reports/artistic_border_render/` — batch summary CSV/JSONL, 3 sample reports, 3 overlays,
  harness.

## 7. Honest notes & next
- The render is applied where it is a **clean improvement** (declutter + gentle smooth + stroke emphasis,
  fidelity preserved) and **withheld** where it would distort — a conservative, safe default.
- The remaining 6 rejects point at the same root cause the intelligence report flagged: **upstream
  line-art over-fragmentation**. Reducing fragmentation at the source (tiny-region merging) would let the
  render apply on those too.
- Stroke-weight actions (THICKEN/THIN/EMPHASIZE) live in `outline.svg`; the game must render borders from
  the SVG (not a uniform per-region stroke) to show them. Surfacing the SVG as the border layer in the UI
  is the natural next step.
