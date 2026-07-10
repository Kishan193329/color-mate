# DFL-Style SVG Analysis — Playable Color-by-Number Reveal

**Sources studied:** `IMG_1376.jpeg` (the target artwork — glossy red biting lips with black
dripping paint, red roses, two wine glasses on a checkered floor) and `contourv(6).svg` (a vector
trace of a 1800×3200 render of that artwork).

**Framing (per instruction).** This SVG is **not** wrong — it is the style to reproduce. The
red paths are **not** invalid; they are an internal **construction / reference layer** (region
cut-lines and partition closures). The black paths are the visible art. No ColorForge code is
changed here; this is analysis only.

---

## 0. The reference image — what we are reproducing

`IMG_1376` is a bold, high-gloss graphic illustration. Its visual language is exactly what the
vector must carry:

- **Heavy black ink that is mass, not just outline** — the dripping black "paint" running off the
  lips and down the canvas is large *filled* black area, not a thin border.
- **Weighted line-art** — the lip ridges are many fine parallel lines; the rose petals and glass
  rims are medium lines; the silhouette is heavy.
- **Vivid, glossy colour underneath** — reds, wine, green leaves, white highlights — that the
  black art frames rather than replaces.

So the target border is **illustrative** (strokes *and* filled masses), not a uniform thin
coloring-book outline.

---

## 1. Anatomy of `contourv(6).svg`

**Canvas.** `viewBox="0 0 1800 3200"`, i.e. 9:16 portrait — the same aspect as the 1152×2048
source (scaled ~1.56×). One outer wrapper `<g fill-opacity:0 stroke-opacity:0>` (an invisible
bounds placeholder) and one main `<g stroke-width:14 stroke-linecap:round stroke-linejoin:round
clip-path=url(#clipPath1)>` holding every path. Everything is clipped to `clipPath1` (the canvas
rect), so nothing bleeds past the frame.

**Four path classes** (unambiguous by their `style`):

| # | Style signature | What it is | Example first coord |
|---|-----------------|------------|---------------------|
| A | `fill:none; stroke-width:{1..16}` (black) | **Black outline strokes** — the visible line-art, weighted from hairline (1) to heavy (16) | `M-18 711 C…` (sw 14), `M448 1507 …` (sw 10) |
| B | `stroke-width:{…}; stroke:none;` (inherits root `fill:black`) | **Black filled masses** — solid ink: drips, deep shadow, bold features. A thick stroke drawn as a *filled outline* | `M415.7 2289.9 …` (fill black) |
| C | `fill:none; stroke-width:1; stroke:red` | **Red construction strokes** — invisible region cut-/partition-lines | `M969.5 584.5 …` |
| D | `fill:red; stroke-width:1; stroke:none` | **Red filled slivers** — the same partition layer drawn as filled outlines | `M1648.6 1079.1 …` |

Observations:

- **Black = A + B.** The line-art is expressed two ways: as *strokes* (open curves at graded
  widths) and as *filled shapes* (`stroke:none`, inheriting `fill:black`). Vector tracers emit a
  thick or solid ink region as the **filled outline of its area** — this is where the "dripping
  paint" masses live. A pure-stroke border could never render those solid black pools; the filled
  class is essential to the DFL look.
- **Stroke-weight hierarchy.** Widths observed: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16. Heavy
  weights carry the silhouette/drips; hairlines carry lip ridges and rose detail. The weight *is*
  the illustration's depth cue.
- **Red = C + D = the invisible partition.** The red curves run *through* the interior of shapes
  where no black line exists. Their job is to **cut large areas into hand-sized, fully-closed
  cells** and to **close regions the black art leaves open**. They are rendered red purely so a
  human/QA can see the construction; at play time they are not drawn.
- **No raster, no ids, no colours.** The SVG holds geometry only. Region → number → colour and the
  original pixels live in the surrounding package (see §7). **The SVG is the border+partition layer,
  not the whole game.**

---

## 2. Study answers

### Black border structure
Two cooperating sub-layers: (A) graded **strokes** for contour/line detail, and (B) **filled
black masses** for solid ink and drips. Together they form the single *visible top layer*. Round
caps/joins + graded widths give the hand-inked feel; the filled masses give the heavy graphic
blacks the reference demands.

### Closed regions
Color-by-number requires **every** colorable area to be a closed cell. The black art only closes
*some* areas; the **red construction layer (C+D)** supplies the rest — extra "cut" curves that
(i) subdivide oversized areas into fillable cells and (ii) seal open contours. The union of
{black edges ∪ red partition} tiles the whole canvas into closed cells. (This is exactly what a
label map does in raster form.)

### How filled areas reveal original pixels
Filling a cell does **not** paint a flat colour. It **un-masks the original image** inside that
cell's geometry: the engine composites the original raster, showing it only where completed cells
allow. The SVG cell defines *where*; the original raster supplies *what*. So a filled petal shows
the real glossy-red petal pixels, not a swatch.

### How the border stays on top
Fixed z-order: **original-pixel reveals render *below* the black border layer.** The black
strokes+masses are always the topmost draw, so the picture always reads as a coloured-in inked
illustration, never as paint covering the lines. The red partition layer renders **invisibly**
(or not at all) and never occludes anything.

### How the image progressively becomes the original
Each correct fill flips one cell from *masked* (blank/flat) to *original pixels*. As cells
accumulate, more of the true artwork shows through the fixed black art — the image **grows into**
the original region by region.

### How the final completed image matches the original (not replaced)
When all cells are filled, every pixel under the border is the **original** raster — because the
mechanic reveals rather than repaints. The finished screen = original artwork with the black
line-art overlaid on top. (ColorForge already enforces this as a hard invariant: **reveal
completed-image ΔE = 0** — the completed reveal is byte-for-colour identical to the original under
the masks.)

---

## 3. What makes this SVG playable

The SVG *by itself* is not playable — it is playable **as the border+partition layer of a
package**. What it contributes to playability:

1. **A closed cell graph.** Black edges + red partition ⇒ every colorable area is a bounded cell
   with a well-defined interior (fillable, hit-testable).
2. **A stable visible frame.** The black layer is a single, always-on-top artifact independent of
   fill state — so the art never breaks as cells fill.
3. **A separation of *where* from *what*.** Geometry (SVG) is decoupled from pixels (raster) and
   from labels (number/colour), which is what lets "fill" mean "reveal original," giving the
   ΔE = 0 completion guarantee.
4. **Resolution independence.** Vector geometry scales to any device without re-tracing; the raster
   reveal is sampled under it.

---

## 4. What layers are needed

| Layer | Source in DFL SVG | Visible to player? | Purpose |
|-------|-------------------|--------------------|---------|
| **Original raster** | *not in the SVG* (separate asset) | only through filled cells | the "what" that gets revealed; the completion target |
| **Border — strokes** | class A | yes (top) | weighted line-art |
| **Border — filled masses** | class B | yes (top) | solid ink / drips / bold shadow |
| **Region partition (cells)** | classes C + D | no (reference/hit-test only) | closes + subdivides every colorable cell |
| **Per-cell metadata** | *not in the SVG* (map: cell→number→target colour) | number shown until filled | drives numbering + validation |
| **Reveal masks** | *not in the SVG* (per-cell alpha) | drive the reveal | expose original pixels per completed cell |

Minimum to play = original raster **+** border (A+B) **+** closed cells (C+D) **+** cell→number→colour
map **+** reveal masks. The DFL SVG delivers the two middle pieces.

---

## 5. What the user (player) sees

- **Start:** black art (A+B) over a blank/greyed field; each cell shows its number and its target
  swatch on the palette bar.
- **During play:** tapping a cell with the correct colour reveals the **original pixels** inside
  it; the number disappears; the black art stays crisp on top. The picture fills in region by
  region.
- **Finish:** the full original artwork under the black line-art — glossy lips, wine, roses exactly
  as in `IMG_1376`, framed by the ink. Nothing is a flat approximation.
- **Never sees:** the red partition layer, region ids, or the raw label map.

---

## 6. What the admin sees

The admin needs the **construction view** the red layer represents:

- **Region/partition overlay** — the red cut-lines and cell boundaries, to confirm the canvas is
  fully closed, no cell is too large or too small, and no area is unreachable.
- **Border preview** (strokes + masses) and the **numbered preview**.
- **Reveal / completed preview** and the **ΔE = 0** check (completed == original).
- **Cell/palette stats** — cell count, palette count, tiny-cell count, difficulty/playtime, a
  quality verdict.
- Approve / Reject / Regenerate.

ColorForge already surfaces most of this (review previews, region heatmap, tiny-region overlay,
border-density map, the admin gallery + verdicts). The DFL "red layer" corresponds to ColorForge's
**region-boundary/partition overlay** — an admin/QA artifact, never shipped to the player.

---

## 7. What the Android app needs

A package (ColorForge already emits `game_package.json` + `game_assets.zip`) containing:

| Need | Package field | Notes |
|------|---------------|-------|
| Completion target | `original` / `reveal_master` | the pixels revealed under the border |
| Visible border | `border_svg` (+ `border_png`) | must include **strokes *and* filled masses** (DFL style) |
| Cell geometry / hit-testing | region geometry (`regions` / `game_config` paths, or `region_mask`) | the closed cells — DFL's red partition, but tagged with ids |
| Per-cell number + target colour | `game_config.regions` + `palette` | drives numbering + validation |
| Reveal per cell | `reveal_{blank,partial,highlight,completed}` masks | expose original pixels for completed cells |
| Numbered preview / thumbnail | `numbered_preview`, `thumbnail` | menu + guidance |
| Metadata | `scores`, `category`, `review_status`, `dimensions`, `counts` | difficulty/playtime/fun, gating |
| **Layer order (should add)** | *proposed* `layers`/z-order field | make "reveal below, border on top" explicit |

The app's render contract: **draw revealed-original (per completed cell) → then the border layer
on top; the partition/cell geometry is used for hit-testing/fill only, never drawn as lines.**

---

## 8. How ColorForge should generate the same kind of SVG/package

ColorForge is already close: `labels.npy` fully partitions the canvas (every pixel labeled ⇒ every
region closed — the raster equivalent of DFL's black∪red tiling); `game_config`/`regions.json`
carry per-region closed `path` + number + colour; the reveal masks + **reveal ΔE = 0** invariant
already implement "fill = reveal original, completed = original, not replaced"; and
`game_package.json` already bundles the assets in §7. The gaps are **visual style** and **explicit
layering**, not mechanics:

1. **Add filled black masses to the border (the biggest gap).** `outline.svg` today emits *stroked*
   paths only (per-action width/colour/opacity, tagged `data-region-id`/`data-action`). To match
   DFL, the artistic border renderer must **also emit `fill:black; stroke:none` masses** for
   ink/drip/deep-shadow areas — e.g. when a region's semantic/material role is "solid ink / deep
   shadow / bold silhouette" (near-black target, border-like role), render it into the **border
   fill layer** instead of (or in addition to) a thick stroke. This is what produces the DFL
   "dripping paint" look; a pure-stroke SVG cannot.
2. **Broaden the stroke-weight range.** DFL spans ~1–16px. ColorForge's semantic widths
   (KEEP 2.0 … THICKEN 3.2 …) are narrower. Widen the range: hairline (~1) for fine detail (lip
   ridges), heavy (~14–16) for silhouettes/drips — the HVI/HSU signals already rank which edges
   deserve which weight.
3. **Emit an explicit region-geometry (partition) layer.** Keep the visible border and the
   **invisible cell partition** as distinct groups, e.g. `<g id="border">` (strokes + masses) and
   `<g id="regions" fill-opacity:0>` (the closed cell paths, id-tagged) — mirroring DFL's
   black-vs-red split, but with the partition **id-tagged** for the engine (DFL's red layer is
   untagged; ColorForge is ahead here — keep the ids).
4. **Verify cell-size distribution.** DFL's red cut-lines exist to keep cells hand-fillable.
   ColorForge already has tiny-region merging + intelligent-split; extend the check to also **split
   oversized cells** so the playable size band is enforced end to end.
5. **Make z-order a package contract.** Add a `layers` order to `game_package.json`
   (`reveal → border`) so any client renders reveals below the border and never draws the partition
   layer.
6. **Keep the ΔE = 0 reveal (already done).** This is the exact DFL "completed == original, not
   replaced" property — do not regress it.

### DFL ↔ ColorForge mapping

| DFL SVG element | ColorForge equivalent | Status |
|-----------------|-----------------------|--------|
| Black strokes (A) | `outline.svg` stroked paths (weighted per action) | ✅ have; broaden weights |
| Black filled masses (B) | *none yet* | ⚠️ **add fill masses** |
| Red partition (C+D) | `labels.npy` + `regions.json`/`game_config` region paths | ✅ have (id-tagged); emit as explicit invisible layer |
| Original pixels revealed | `original` + reveal masks, ΔE = 0 | ✅ have |
| Border-on-top / z-order | implicit (outline is top layer) | ⚠️ make explicit in package |
| Package (raster+border+cells+meta) | `game_package.json` + `game_assets.zip` | ✅ have |

---

## 9. Verdict

`contourv(6).svg` is a valid, well-formed **border + partition** layer in the DFL style: graded
black **strokes** plus solid black **filled masses** for the visible art, and a red **construction
layer** that closes and subdivides every colorable cell. It is playable only together with the
original raster, the cell→number→colour map, and the reveal masks — the "fill = reveal original"
mechanic that makes the completed image *equal* the original, not a repaint. ColorForge already
owns that mechanic and package (closed cells, reveal masks, ΔE = 0, `game_package.json`); to
reproduce this exact style it needs to (1) add **filled black masses** to the border, (2) **widen
the stroke-weight range**, and (3) export the **cell partition as an explicit invisible, id-tagged
layer** with a stated **reveal → border** z-order. No mechanic change — a border-style and
packaging upgrade.
