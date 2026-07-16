# Report — "One tap filled a huge part" (Krishna image, job `0d803ba32745`)

**Visual:** `research/08_reports/ai_lineart/krishna_diagnose.png`

## What happened
Tapping once filled a very large area (Krishna's arm + a big chunk of the scene). Expected: one
tap → a small piece.

## The data
Image 1254×1254. **545 regions, 110 colours** — but the size distribution is extremely uneven:

| Region | Area (one tap) | bbox spread | What it is |
|--------|----------------|-------------|------------|
| #1 | **26.9%** | 100% of frame | blurry background (garden bokeh) **+ Krishna's raised arm merged in** |
| #2 | **20.6%** | 100% | more soft background / bright garden |
| #3 | **14.1%** | 100% | more soft background |
| … 542 others | each < 3.5% | small | face, jewellery, flute, garland, pot, cows — nicely detailed |

- **Top 3 regions = ~62% of the whole image.** One tap on any of them fills 14–27%.
- Median region = **0.03%**, average **0.18%** — so *most* regions are tiny; the problem is a
  handful of giant ones.
- `bbox spread = 100%` means those 3 regions stretch across the entire frame — a tell-tale sign of a
  background that leaked together, not a compact object.

## Why (root cause)
1. **The photo has a soft, blurry, out-of-focus background** (bokeh garden). Region borders come
   from the AI line-art, which only draws a line where there is a real edge/contrast. The blurry
   background has **almost no edges**, so the whole soft area stays as one connected colour blob →
   2–3 giant regions.
2. **Soft body edges leak into the background.** Krishna's smooth blue body has soft, anti-aliased
   edges against the equally soft background. The line-art doesn't form a *closed* outline there, so
   the flood-fill leaks — the **arm merges with the background** into one 27% region.
3. **Large uniform-colour areas are not sub-divided.** A big same-colour surface stays one region,
   so one tap fills all of it.
4. Why it didn't show earlier: the 27 test images were **flat anime illustrations** with crisp
   outlines everywhere. A **photoreal image with soft focus** exposes this.

## Fix (recommended)
**Cap the maximum area a single region may cover.** Any region larger than ~2–3% of the canvas is
Voronoi-tiled into smaller **same-number** sub-cells (same colour, so the palette/reveal is
unchanged — only the *tap granularity* shrinks). Result: one tap fills at most ~2–3%, the giant
background regions become many small taps, colours stay identical. (This is the
`subdivide_large_regions` approach built earlier on branch `wip-2026-07-13-today`.)

Optionally also raise line-detection sensitivity on soft images so the body/background separate
better — but the area-cap fix alone solves the "one tap = huge fill" complaint.

## Recommendation
Add area-cap subdivision to the colour-fill pipeline (`build_color`), re-generate, and re-test on
this Krishna image. Expected after fix: no region over ~2–3%; background becomes many small
same-colour taps instead of 3 giant ones.
