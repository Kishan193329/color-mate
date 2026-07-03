# Cat Border Fix Report — Quality Fix 3 (PBSE v1)

Before/after of the **Perfect Border & Shape Engine v1** on the cat reference
image (`cat.png`, 1254×1254). PBSE v1 runs on the **border/line-art layer only** —
it does not touch palette, numbers, color, or region structure. Line art remains
the source of truth.

- Engine: [`backend/services/border_engine.py`](backend/services/border_engine.py)
- Machine report: [`border_quality_report.json`](border_quality_report.json)
- Assets: [`research/07_validation/cat_border_fix/`](research/07_validation/cat_border_fix/)
  (`before_lineart.png`, `after_lineart.png`, `before_flat.png`, `after_flat.png`,
  `before.svg`, `after.svg`)

---

## Before / After summary

| Metric | Before (no PBSE) | After (PBSE v1) | Change |
| --- | ---: | ---: | --- |
| Line art | `before_lineart.png` | `after_lineart.png` | gaps closed, breaks healed |
| Flat fill | `before_flat.png` | `after_flat.png` | closed regions, no new leaks |
| SVG render (paths) | 66 | 66 | unchanged (no false merge) |
| **Region count** | **66** | **66** | **unchanged** ✔ |
| Open endpoints | 157 | 141 | −16 (fewer open ends) |
| Fragments (disconnected line pieces) | 26 | 18 | −8 (better connected) |
| Suspected leaks (bridgeable gaps) | 13 | 2 | −11 (leak risk down 85%) |
| Structural jaggedness | 0.1135 | 0.1093 | −0.0042 (smoother) |
| Repaired gaps | — | 7 | 7 real 1–2px gaps bridged |

**Passes:** closure ✅ · leak ✅ · shape ✅ · **final ✅**

The **region count is identical (66)** before and after — the single most
important safety result: PBSE improved closure and smoothness **without merging
or destroying any region**, so palette and numbers downstream are unaffected.

---

## Detailed observations

### Line art (before → after)
The input is already a clean coloring-page illustration, so changes are
intentionally subtle: **7 near-miss 1–2px gaps were bridged**, hairline breaks
from the fur strokes were healed (fragments 26 → 18), and structural edges (ear
outlines, silhouette, window frame) are slightly smoother. The art is **not
redrawn** — Rulebook "original must not be redesigned" is respected.

### Flat fill (before → after)
`after_flat.png` fills all 66 regions with colors sampled from the original
(orange fur, green eyes, pink collar/heart, flowers, terracotta pot, yarn ball,
cushion). Regions stay closed; no fill crosses a border. Closing the 7 gaps
removes the pre-existing risk of a fill leaking through a broken fur/ear stroke.

### SVG render
66 closed `Z`-terminated paths in both, matching the region count. PBSE did not
change region topology; it cleaned the boundaries those paths are traced from.

### Open endpoints
157 → 141. The remaining 141 are dominated by **legitimate fur/whisker stroke
terminals** that dangle in open space by design (not region leaks) — which is
why PBSE v1's leak metric is defined as *risk reduction*, not "zero open ends".

### Leaks
Suspected bridgeable gaps: **13 → 2** (−85%). The 2 residual candidates are short
fur-tip pairs that are not region-separating; bridging them was correctly skipped
to avoid false merging.

### Jaggedness
Structural jaggedness (measured on silhouette / large shape contours only, not
fur texture) improved 0.1135 → 0.1093. Fur interior detail is deliberately left
intact (BORD-022: smooth outside, detailed inside).

### Visual quality
Cleaner, better-connected outlines and faithful flat fills with no visible leaks.
The improvement is real but restrained — appropriate for an already-clean input.

---

## border_quality_report.json

```json
{
  "version": 1,
  "fields": {
    "edge_confidence": 0.1762,
    "local_contrast": 0.0333,
    "edge_density": 0.09474,
    "endpoints": 157
  },
  "endpoints_before": 157,
  "endpoints_after": 141,
  "fragments_before": 26,
  "fragments_after": 18,
  "suspected_gaps": 13,
  "repaired_gaps": 7,
  "suspected_leaks": 2,
  "jaggedness_score_before": 0.1135,
  "jaggedness_score_after": 0.1093,
  "closure_pass": true,
  "leak_pass": true,
  "shape_pass": true,
  "final_pass": true
}
```

---

## Remaining issues (for future PBSE versions)

1. **Leak metric is proximity-based, not region-aware.** It counts nearby
   endpoint pairs; it cannot yet tell a *region-separating* gap from a harmless
   dangling fur tip. A true leak test needs the region/topology context (PBSE S8,
   Border Graph faces) — a v2 item.
2. **Shape refinement is global and mild.** It uses a sub-pixel supersample pass
   that improves structural edges only slightly and does not yet vary by
   silhouette-vs-interior (architecture S6/BORD-022) or fit curves (S7/BORD-015–017).
3. **No feature protection yet.** Eyes, pupils, nose, and thin loops
   (architecture S4) are not explicitly protected in v1; they survive here because
   the input is clean, but a photo input would need S4.
4. **Gap radius is a fixed 6px.** Not yet mode-aware or resolution-scaled
   (architecture Control/Mode Layer).
5. **No silhouette/figure-ground stage (S2).** v1 works on the whole line layer;
   it does not yet single out and protect the outer contour.

These map directly to the un-implemented PBSE stages (S2, S4, S6-advanced, S7) and
are the natural scope for PBSE v2.

---

## Scope compliance

- ✔ Palette unchanged (12 grouped colors, same LAB grouping).
- ✔ Numbers unchanged (66/66 regions numbered by the same downstream stage).
- ✔ UI unchanged (border report is a backend artifact; downloadable via the
  existing generic download route — no frontend edit).
- ✔ Region count unchanged (66 → 66); no false merges.
- ✔ Original image never modified.
