# Local AI Line-Art — Model Evaluation & Line-Art→Regions (Stages 1–3)

**Goal.** Replace the weak line-art source with a local, subject-aware model **while keeping
ColorForge a true Number-by-Color game** — every playable region follows a real visible shape,
each region gets a colour number, tapping the matching region reveals the original pixels inside
that exact region, the border stays on top, and completing all regions == the original.

Run on the two requested images only: **br_03 (mandala)** and **br_14 (girl/house)**. The full
27-image batch was **not** run. **No production commit** — awaiting your visual approval.

---

## Stage 1 — model comparison (side-by-side only, not wired)

`research/08_reports/lineart_eval/model_comparison_board.png`

Quantitative proxies (ink% / largest-black-blob% / closed-cells / left-right symmetry):

| Image | Model | ink% | blob% | closed cells | LR-sym |
|-------|-------|------|-------|--------------|--------|
| mandala | Canny (current) | 35.9 | **35.5** | 318 | 0.70 |
| mandala | **Informative-Drawings** | 7.1 | 0.29 | 117 | 0.87 |
| mandala | Anime2Sketch | 0.3 | 0.01 | **1** | 0.93 |
| girl/house | Canny (current) | 20.9 | **18.6** | 187 | 0.67 |
| girl/house | **Informative-Drawings** | 4.4 | 0.22 | 32 | 0.89 |
| girl/house | Anime2Sketch | 4.9 | 0.34 | 6 | 0.87 |

Scores (0–5 each):

| Criterion | Canny (current) | **Informative-Drawings** | Anime2Sketch |
|-----------|-----------------|--------------------------|--------------|
| original shape fidelity | 3 | 5 | 4 |
| clean black outlines | 2 | 4 | 1 (too faint) |
| no black blobs | 0 (18–35%) | 5 | 5 |
| no texture noise | 3 | 4 | 2 (hatching) |
| face/hair preservation | 0 (blank subject) | 5 | 4 |
| mandala symmetry | 3 | 5 | 3 (0.3% ink) |
| background simplification | 1 | 5 | 3 (shaded) |
| suitability for closed regions | 2 (blobs) | 5 | 1 (1–6 cells) |
| **Total / 40** | **14** | **38** | **23** |

- **Canny (current)** turns shadows/texture into **huge black blobs** (18–35%) and leaves the
  subject **blank** — the exact failure that motivated this task.
- **Anime2Sketch** is a soft pencil sketch — too faint to close regions (1–6 usable cells;
  0.3% ink on the mandala). A stylistic alternative only.
- **Informative-Drawings** — clean thin ink, **no blobs**, best face/hair, best symmetry, and the
  most usable closed cells.

## Stage 2 — winner

| Category | Winner | Why |
|----------|--------|-----|
| illustration / anime / photo-like (girl/house) | **Informative-Drawings** | best face/hair, clean outlines, no blobs, simplified background |
| mandala / symmetrical (mandala) | **Informative-Drawings** | clean, complete, symmetric; Anime2Sketch too faint, Canny blobby |

Same model wins both. Anime2Sketch is kept only as an optional pencil style (not for regions).

## Stage 3 — build regions FROM the line-art (standalone; NOT wired to production)

`research/08_reports/lineart_eval/stage3_lineart_regions.py`. Pipeline:

```
original → AI clean line-art (Informative-Drawings)
  → binarize (solid ink) → close ONLY small gaps (3px)
  → flood-fill the CLOSED white cells between the lines   (no Voronoi, no polygons)
  → merge tiny cells into their dominant neighbour         (reject arbitrary tiny cells)
  → absorb the line pixels into the nearest cell           (masks tile the image 100%)
  → sample the original colour per cell → k-means group into palette numbers
  → place a number inside each cell, sized to fit its cell (numbers never overlap)
  → tap the matching number's region → reveal original pixels there
  → all revealed → canvas == original
```

Verification (`stage3_verification.json`):

| Image | regions (from line-art) | palette numbers | cells tile 100% | reveal == original (max diff) |
|-------|-------------------------|-----------------|-----------------|-------------------------------|
| br_03 mandala | 303 | 24 | ✅ | ✅ (**0**) |
| br_14 girl/house | 226 | 24 | ✅ | ✅ (**0**) |

**Hard rules — all honoured:** regions are the line-art's own closed shapes (no Voronoi/polygon
fallback); shadows/texture are **not** made into borders (Canny rejected); masks tile the image;
reveal ΔE = **0**; numbers are sized to their cell, so they cannot overlap.

## Deliverables 2–6 — game previews

- **Model comparison board** — `model_comparison_board.png`
- **br_03 previews** — `previews_br_03.png` (clean border · numbered · one-region reveal · 25% · completed)
- **br_14 previews** — `previews_br_14.png` (same five)

Both show the true Number-by-Color loop: clean line-art page → tap a numbered region → its
original pixels reveal in that exact shape → 25% → completed == original, border on top.

## One honest tuning note (before integration)

The **mandala** numbers well (petal cells are uniform: 208/303 cells fit a legible number). The
**girl/house** has very uneven cell sizes (one huge sky cell + many tiny hair/fold cells), so only
58/226 cells are large enough to draw a number. For the production integration I recommend one
tunable: **merge small cells a bit more (and split only by colour inside oversized cells)** so most
regions are numberable, while still preserving meaningful shapes. This does not change any hard
rule; it only balances cell sizes for playability.

## Production integration (approved — "run")

The line-art→regions pipeline is now wired into the game:

- **`backend/services/lineart_region_builder.py`** (new) — `build_regions` (binarize at
  `line_thresh=165` so soft grey lines don't leak → close small gaps (5px) → flood-fill closed
  cells → **merge every region whose inscribed radius < 13px into its dominant neighbour** so all
  regions are numberable and thin folds fold in → absorb line pixels so masks tile 100%),
  `palette_numbers` (k-means group matching colours), `region_meta` (number at the most-interior
  point, sized to the inscribed radius).
- **`backend/services/reveal_game_builder.py`** — the `/play` game now builds **regions from the
  line-art shapes** + palette numbers (not colour labels); the border is the AI line-art; numbers
  fit their cell (canvas measure-and-shrink, no overlap); reveal source stays the master.
- **`backend/pipeline.py`** — the AI line-art is generated crisp (`darken=1.5`) so it closes cells.

**Final integrated results** (`final_previews_br_03.png`, `final_previews_br_14.png`):

| Image | regions (from line-art) | palette # | biggest region | tiles 100% | reveal == original |
|-------|-------------------------|-----------|----------------|------------|--------------------|
| br_03 mandala | 139 | 24 | 38% (white background) | ✅ | ✅ (0 diff) |
| br_14 girl/house | 119 | 24 | 20% (sky, separate from houses) | ✅ | ✅ (0 diff) |

All hard rules hold: line-art shapes (no polygon/Voronoi), no shadows/texture as borders, masks
tile the image, **reveal ΔE = 0**, numbers sized to their cell (no overlap), every region numbered.
The colour-label path remains as an automatic fallback when the model is unavailable.

## Status: integrated. Play `http://127.0.0.1:8000/api/job/br_03/play` and `.../br_14/play`.
