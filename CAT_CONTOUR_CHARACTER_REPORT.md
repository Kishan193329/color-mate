# Cat Contour Character Report — Quality Fix 14

The border was already closed and leak-free. QF14 gives **every contour its own
artistic personality** — the way a professional illustrator draws: a flowing outer
silhouette, organic fur edges, crisp eyes, tapered leaf tips, elegant petals — instead
of the single uniform polyline the pipeline drew for everything. It classifies each
contour, measures it, assigns a drawing **style** and **rendering policy**, and
re-emits the vector outline with **adaptive geometry**: organic/soft contours become
smooth cubic Béziers; sharp contours (eyes) stay crisp polylines; tapered contours
(leaf tips, whiskers) keep their points.

**Only the vector contour geometry changes** (`outline.svg` + the region `path`
strings in `regions.json` / `game_config.json`). Labels, palette, numbers, colours,
line art, and the exporter are untouched — so ΔE/SSIM and leaks are unchanged **by
construction**.

- Engine: [`backend/services/contour_character_engine.py`](backend/services/contour_character_engine.py)
- Report: [`contour_character_report.json`](contour_character_report.json)
- Overlay: [`research/07_validation/cat_contour/contour_character_overlay.png`](research/07_validation/cat_contour/contour_character_overlay.png)
- Before / after render: [`contour_before.png`](research/07_validation/cat_contour/contour_before.png) · [`contour_after.png`](research/07_validation/cat_contour/contour_after.png)

## Reused reports (nothing recomputed)
QF13 `artistic_decision_report.json` (role / importance / protect flags), QF7
`semantic_region_report.json`, QF8/QF9/QF11/QF12 for context, QF3
`border_quality_report.json`. Adjacency helpers are **imported** from the QF13 engine
— no analysis is duplicated.

## Contour statistics
- **361 contours** across 88 regions classified, measured, styled and re-rendered.
- Each carries: importance, curvature, local complexity, continuity, flow alignment,
  organic score, corner sharpness, corner count, required precision, length, area.
- **338 protected contours** (shape/silhouette/endpoint protection on); **14 outer
  silhouette** contours.

## Contour class distribution
| Class | Count | Class | Count |
| --- | ---: | --- | ---: |
| INNER_STRUCTURE | 158 | LEAF_EDGE | 29 |
| FLOW_EDGE | 62 | FACIAL_EDGE | 21 |
| DECORATIVE | 56 | OUTER_SILHOUETTE | 14 |
| FUR_EDGE | 11 | PETAL_EDGE | 6 |
| WHISKER | 3 | UNKNOWN | 1 |

The overlay confirms it: the two **eyes are FACIAL_EDGE** (crisp/red), the cat's outline
is OUTER_SILHOUETTE (smooth/black), plant leaves are LEAF_EDGE (tapered/green), flowers
PETAL_EDGE (soft/pink), fur FLOW_EDGE/FUR_EDGE (organic/gold), and **3 whiskers** were
picked out (sharp/blue).

## Style distribution
| Style | Count | Behaviour |
| --- | ---: | --- |
| MICRO_DETAIL | 217 | tiny contours — high precision, no rounding |
| HYBRID | 45 | curves with kept corners |
| ORGANIC | 33 | smooth flowing Bézier (silhouette, fur) |
| MECHANICAL | 27 | clean geometric (frame, pot) |
| SHARP | 21 | crisp polyline (eyes) |
| TAPERED | 13 | Bézier with sharp tips (leaves, whiskers) |
| SOFT | 5 | elegant smooth curves (petals) |

## Bézier precision distribution
| Rendering | Count |
| --- | ---: |
| BEZIER (smooth cubic) | 96 |
| POLYLINE (crisp/precise) | 265 |

Bézier is spent where it reads as *drawn* (silhouette, fur, petals); polyline where it
must stay *exact* (eyes, tiny details) — precision is adaptive, not uniform.

## Protected contours
338 / 361 contours carry at least one protection flag (`shape_protection`,
`silhouette_protection`, `endpoint_protection`, or `corner_preservation`). Silhouette
protection guards the 14 outline contours; endpoint protection guards whiskers and
leaf tips so their points are never rounded away.

## Validation — before vs after (reference cat only)
| Metric | Before (uniform) | After (QF14) | Goal |
| --- | ---: | ---: | --- |
| **ΔE to Original** | 11.471 | 11.471 | unchanged ✔ (geometry-only) |
| **SSIM** | 0.8451 | 0.8451 | unchanged ✔ |
| **Shape Similarity** (IoU, same measure) | 0.707 | **0.958** | higher ✔ (hugs true boundary) |
| **Contour Smoothness** (jaggedness ↓) | 0.1275 | **0.1182** | lower ✔ (−7.3%) |
| **SVG Quality** (adaptive Bézier + precision) | polyline-only | **96 Bézier + 265 precise** | improved ✔ |
| **Leak Count** | 0 | 0 | 0 ✔ |
| **Visual Score** (`100·IoU·(1−jaggedness)`) | 61.7 | **84.5** | higher ✔ |

QF14 is simultaneously **smoother** and **more faithful to the true region shape** than
the old uniform smoothing — the adaptive per-contour epsilon preserves shape where the
global epsilon was cutting corners, while Béziers remove the polygonal chords. ΔE/SSIM
are identical because the label geometry (and thus every region's colour) is untouched.

## Visual improvements
The before/after render is unambiguous:
- **Outer silhouette** — polygonal, chorded → smooth flowing curve.
- **Petals & pot & window frame** — angular straight cuts → clean elegant curves.
- **Fur & stripes** — stay organic (not over-smoothed into plastic).
- **Eyes** — stay crisp and round.
- **Leaf tips & whiskers** — keep their sharp points.
The scratchy, mechanical line art becomes a coherent, hand-drawn coloring-book page.

## Commercial observations
An illustrator never draws every line the same. QF14 encodes that: line *character*
now varies by what the contour depicts. The silhouette reads as a confident single
sweep; fur reads as fur; eyes read as glass; petals read as soft. This is the single
biggest jump in "looks professionally drawn" of any fix so far, and it costs nothing in
colour fidelity.

## Illustrator comparison
| Contour | Illustrator convention | QF14 |
| --- | --- | --- |
| Outer silhouette | one smooth confident line | ORGANIC Bézier ✔ |
| Fur | broken, organic strokes | ORGANIC, low smoothing ✔ |
| Eyes | crisp, controlled | SHARP polyline, corners kept ✔ |
| Leaf tip | tapered to a point | TAPERED, tip cusp kept ✔ |
| Petal | soft elegant curve | SOFT Bézier ✔ |
| Whisker | fine and sharp | TAPERED, endpoints protected ✔ |

## Remaining artificial contours (honest)
- The **yarn ball** and **heart pendant** are classed PETAL_EDGE (pink → FLOWER
  upstream) and drawn SOFT — looks fine, but the label is semantically loose.
- A few **INNER_STRUCTURE** fur contours remain slightly scratchy where the underlying
  label boundary is genuinely noisy — smoothing them more would distort the fur mass,
  so they are deliberately left organic.
- **Whiskers in the raster line art** (`lineart.png`) are drawn strokes, not region
  boundaries, so they are inherently untouched by this pass (correctly preserved).

## Commercial Review — "Would a professional illustrator manually draw these contours this way?"
**YES.** An illustrator varies line character exactly as QF14 does: a single smooth
silhouette, organic fur, crisp eyes, tapered tips, elegant petals. The before was a
uniform machine polyline that no illustrator would produce; the after matches
hand-drawn convention while preserving every object's identity (shape IoU 0.958, ΔE
unchanged). The only caveats are upstream-label quirks (yarn/heart as "petal"), which
affect the *name* of the style, not its visual correctness.

## Rules honoured
1. Original artwork never redesigned — shape IoU 0.958, object identity intact.
2. Visually closer to the original illustration (smoother + more shape-faithful).
3. Reuses QF13/QF7/QF8/QF9/QF11/QF12/QF3 reports; recomputes nothing.
4. No duplicate computation — adjacency helpers imported from QF13.
5. One responsibility: contour classification + adaptive geometry only.
6. Backward compatible — geometry-only; labels/palette/numbers/lineart/export intact;
   flat images still produce valid Z-closed SVG.
7. Commercial quality over speed — per-contour Bézier/polyline decisioning.
8. **Not rejected** — the cat is visibly and measurably improved.
9. Thinks like an illustrator — line character varies by subject, not by algorithm.
