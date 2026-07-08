# Line-Art Fragmentation Reduction

**Date:** 2026-07-08 · **Validation:** all 27 batch images.
**Goal:** reduce the tiny contour/fragment explosion at the source so the Artistic Border Render can
safely apply on **all** images (raise acceptance above 21/27).

**Result: 27 / 27 complete · 0 crashes · 0 leaks · reveal completed == original (ΔE 0) on all 27 · SVG
valid on all 27 · 27 / 27 PASS.** Source fragments **19,465 → 16,617 (−14.6 %)** with the union preserved
exactly and **0 numbered regions lost**; **render acceptance 21/27 → 27 / 27.**

---

## 1. What was actually blocking acceptance (the investigation)

The render rejected 6 images because SIMPLIFY/SOFTEN shifted shape past the 1 % fidelity gate. Decomposing
that distortion (cat-style portrait #5) showed it was **not** the tiny fragments — those are already dropped
by the render's REMOVE and excluded from the gate. It was the **large regions**: SIMPLIFY's tolerance
`eps = eps_frac × perimeter`, so a big region gets an ~11 px boundary shift. On image #5, the 15 contours
over 1,000 px caused **136,408 px** of boundary movement vs **462 px** from every smaller contour combined
(≈ 99.6 %). So the sprint has two complementary parts:

1. **Source fragment cleanup** — removes the tiny-contour explosion (the named goal: fewer fragments,
   cleaner line art).
2. **A cap on SIMPLIFY's absolute boundary shift** — bounds the large-region distortion that actually
   gated acceptance.

Both are needed; together the render safely applies on every image.

## 2. Source fragment cleanup (`fragment_cleanup_engine`)

Before the SVG/render stage, it dissolves each **non-primary, compact** contour fragment (a label's
smaller disconnected crumbs and filled-in holes) into the region that already surrounds it, writing a
cleaned label map (`labels_render.npy`) the border chain draws from. Hard invariants make it safe:

| invariant | how |
|---|---|
| reveal completed == original / masks stay closed | a fragment is only reassigned to a **non-zero neighbour** label, never to the line skeleton — `labels > 0` is byte-identical before/after (union preserved on **27/27**) |
| palette & numbers unchanged | only **non-primary** components dissolve; every label keeps its largest component, so no numbered region is lost (**0 labels lost on 27/27**) |
| features preserved | facial / flower / leaf / fur / decorative labels (and any protect flag) are never dissolved; the compact-only test (small area **and** short perimeter) additionally spares thin hair strands |
| no leaks | no line art is touched (leaks are a raster property) — **0 leaks on 27/27** |

`labels.npy`, `region_mask.png`, the palette, the numbers and every reveal asset are left exactly as they
were — the cleaned map is a **separate** file used only by QF14 + the artistic border chain.

Effect: **6,144 components dissolved, fragments 19,465 → 16,617 (−14.6 %)**. Heavily-fragmented images
benefit most — portrait #7 **2,433 → 1,074 (−56 %)** (3.1 % of pixels re-absorbed, 74 labels protected,
0 lost), illustration #12 **137 → 21**, #9 **413 → 107**.

## 3. Large-region distortion cap (`artistic_border_renderer`)

The render's SIMPLIFY/SOFTEN tolerance is now capped at an absolute **4 px** (`MAX_EPS_PX`). This binds
only on large contours (perimeter > ~1,400 px); small-contour simplification is unchanged. It bounds the
boundary shift on big regions so they stay shape-faithful — and, because the capped tolerance yields a
gentle Bézier that tracks the region better than the old uniform polyline, the transformed-contour IoU now
**improves** on the formerly-rejected images instead of dropping.

## 4. Validation — all 27 images

| Metric | Result |
|---|---|
| completed / crashes | **27 / 27** · **0** |
| leaks | **0 on every image** |
| reveal completed == original | **27 / 27** (max ΔE **0.0**) |
| SVG valid | **27 / 27** |
| cleanup union preserved · labels lost | **27 / 27** · **0** |
| source fragments | **19,465 → 16,617 (−14.6 %)** |
| **render acceptance** | **27 / 27** (was **21 / 27**) |
| **PASS** | **27 / 27** |

The 6 formerly-rejected images now accept with a **positive** transformed-contour IoU delta
(#4 +0.041, #5 +0.016, #7 +0.004, #14 +0.010, #15 +0.004, #18 +0.017); the previously-accepted 21 stay
accepted. Every image's IoU delta is now ≥ −0.0013.

### No change to protected outputs (byte-identical proof)
Full-folder snapshot of the cat, this sprint vs the render-sprint code: **`labels.npy`, `region_mask.png`,
`palette.json`, `lineart.png`, every reveal asset, `reveal_validation.json`, and every upstream report are
byte-identical.** `regions.json` / `game_config.json` differ **only** in the `path` field (numbers,
colours, reveal manifest identical). The only new files are `labels_render.npy` and
`fragment_cleanup_report.json`. ⇒ no change to reveal, completed output, palette, or numbers.

Per-image data: `research/08_reports/fragmentation_reduction/batch_frag_summary.csv` (+ `batch_frag.jsonl`).
Samples: `sample_fragment_cleanup_{portrait_bigreduction,cat,illustration}.json`. Harness: `batch_frag.py`.

> Robustness note: one image hit the transient Windows `job.json` rename race under 3-way parallel batch
> runs (infra, not the engine); it passed cleanly on a solo re-run.

## 5. Files changed
- `backend/services/fragment_cleanup_engine.py` (new) — dissolves non-primary compact fragments into their
  dominant neighbour; preserves the union, every numbered region, and all feature labels; writes
  `labels_render.npy` + a report. `labels.npy` is never modified.
- `backend/services/artistic_border_renderer.py` — SIMPLIFY/SOFTEN tolerance capped at 4 px absolute
  (`MAX_EPS_PX`) so large regions stay shape-faithful.
- `backend/pipeline.py` — `_apply_fragment_cleanup` runs before QF14 in `run()` / `rebuild_from_labels`;
  QF14 and the render pass draw from the cleaned label map when present (`_render_labels`).
- `backend/config.py` — `labels_render` + `fragment_cleanup_report` artifacts registered.
- `research/08_reports/fragmentation_reduction/` — batch summary CSV/JSONL, 3 sample reports, harness.

## 6. Honest notes & next
- Fragment reduction is bounded by the "keep every numbered region" rule: where fragments are standalone
  tiny **numbered** regions (not disconnected crumbs), they cannot be dissolved without changing the
  numbers, so some images (#1/#3/#17/#21) reduce by 0 — but they already accept. The cap is what carries
  acceptance for those.
- The biggest remaining lever is upstream: tightening tiny-region **merging** in region detection so fewer
  standalone micro-regions are created in the first place would cut fragments further and simplify every
  downstream stage.
