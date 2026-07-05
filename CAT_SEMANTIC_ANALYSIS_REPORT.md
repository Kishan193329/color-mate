# Cat Semantic Analysis Report — Quality Fix 7

The **semantic understanding foundation**: the engine now classifies *what* each
region is (material/object class) and measures the visual properties every future
split decision will depend on. **Analysis only** — no region, palette, number,
SVG, or export was changed, and nothing was split.

- Analyzer: [`backend/services/semantic_region_analyzer.py`](backend/services/semantic_region_analyzer.py)
- Report: [`semantic_region_report.json`](semantic_region_report.json)
- Overlay: [`research/07_validation/cat_semantic/semantic_overlay.png`](research/07_validation/cat_semantic/semantic_overlay.png)

## Validation — pipeline output identical except new reports
| Output | Before QF7 | After QF7 |
| --- | ---: | ---: |
| Region count | 66 | **66** |
| Palette colors | 12 | **12** |
| Numbered regions | 66 | **66** |
| SVG paths | 66 | **66** |
| `result.zip` semantic files | — | **none** (export unchanged, 15 entries) |
| Frontend | — | untouched |

✔ No auto-split · ✔ palette/numbering/SVG/export/frontend all unchanged · only
`semantic_region_report.json` + `semantic_overlay.png` were added.

---

## Region statistics
- **66 regions analyzed.** For each: `semantic_class`, `confidence`, and the full
  measurement set — `texture_score`, `gradient_score`, `edge_flow_score`,
  `color_variance`, `region_size`, `major_orientation`, `minor_orientation`,
  `structure_tensor_strength`, `dominant_gradient_direction`, `local_edge_density`
  — plus a `recommended_future_action`.

## Class distribution
| Class | Count | | Class | Count |
| --- | ---: | --- | --- | ---: |
| **FUR** | 20 | | LEAF | 13 |
| CLOTH | 18 | | FLOWER | 7 |
| BACKGROUND | 4 | | WOOD | 3 |
| SKIN | 1 | | | |

The whole cat body reads as **FUR** (dominant, correct), backgrounds as
**BACKGROUND**, the plant as **LEAF/FLOWER**, soft goods (cushion, collar, yarn)
as **CLOTH**, and warm frame/pot edges as **WOOD**. (Small features can misread —
green eyes fall to LEAF, some lavender flowers to CLOTH — expected for a heuristic
foundation, reflected in moderate confidence.)

## Confidence summary
- **mean 0.634**, min 0.50, max 0.78. Deliberately moderate: this is a coarse
  material understanding, not a certainty. Textured, directional classes
  (FUR/HAIR/WOOD with high edge-flow) get a small confidence boost.

## Largest semantic regions
| id | class | conf | size (px) | texture | edge-flow | colour-var | tensor-str | action |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 7 | FUR | 0.68 | 227,562 | 0.27 | 0.19 | 20.8 | 0.64 | REQUIRES_FUTURE_SPLIT |
| 2 | BACKGROUND | 0.70 | 208,496 | 0.25 | 0.27 | 12.6 | 0.67 | SAFE_TO_SIMPLIFY |
| 61 | BACKGROUND | 0.70 | 154,237 | 0.12 | 0.30 | 7.7 | 0.13 | SAFE_TO_SIMPLIFY |
| 45 | FUR | 0.78 | 101,975 | 0.25 | 0.54 | 20.3 | 0.84 | REQUIRES_FUTURE_SPLIT |
| 5 | BACKGROUND | 0.70 | 69,929 | 0.05 | 0.80 | 4.9 | 0.04 | SAFE_TO_SIMPLIFY |
| 1 | BACKGROUND | 0.70 | 65,209 | 0.29 | 0.37 | 5.0 | 0.84 | SAFE_TO_SIMPLIFY |

## Potential future split candidates
`REQUIRES_FUTURE_SPLIT` (large + high internal colour variance, non-background):

| id | class | why |
| ---: | --- | --- |
| 7 | FUR | huge head/upper-body fur, colour-var 20.8 |
| 45 | FUR | body/chest fur, colour-var 20.3, strong flow 0.54 |
| 44, 50 | FUR | large fur areas with tonal variation |
| 27 | LEAF | large foliage clump with internal colour variation |

These agree with QF5's merged-region suspects — but now each carries a **class**,
so a future split can be class-aware (e.g. split fur *along* its flow, not across).

## Action distribution (future policy hints)
`ALLOW_COLOR_GROUPING` 30 · `PRESERVE_FUR_FLOW` 16 · `DO_NOT_GROUP` 7 ·
`REQUIRES_FUTURE_SPLIT` 5 · `SAFE_TO_SIMPLIFY` 4 · `PRESERVE_STRIPES` 3 ·
`PRESERVE_GRADIENT` 1.

---

## Future palette restrictions (advisory, not applied)
- **DO_NOT_GROUP** (7 regions, mostly FLOWER): petal / accent colours must stay
  distinct — the future palette grouper should be blocked from merging these even
  if LAB-close.
- **ALLOW_COLOR_GROUPING** (30 regions: LEAF/CLOTH/STONE/BACKGROUND): safe to
  merge perceptually-similar colours to keep the palette compact.
- **SAFE_TO_SIMPLIFY** (4 BACKGROUND): can be reduced/merged aggressively.

## Future fur restrictions (advisory, not applied)
- **PRESERVE_FUR_FLOW** (16 FUR regions): fur must not be flattened into one blob
  or over-smoothed; its silhouette stays smooth while interior strand *flow*
  (dominant gradient direction, edge-flow) is preserved.
- FUR split candidates (7, 45, 44, 50) should be split **along the flow
  orientation**, never across it, and never merged with non-fur neighbours.

## Future gradient restrictions (advisory, not applied)
- **PRESERVE_GRADIENT** classes (SKIN/SKY/WATER/METAL): smooth tonal transitions
  must be rendered as *stepped* regions, never a single flat fill.
- The large FUR/LEAF split candidates carry gradient-like internal variance
  (`color_variance` ≈ 20) — a future gradient/stepping pass should target these
  rather than forcing a flat colour.

---

## Notes
- Purely a foundation layer: `semantic_class` + measurements + advisory actions.
  Nothing acts on them yet — no split, no palette/number/SVG/export change.
- Region ids are the game region numbers (aligned with `regions.json`).
- Every future Quality Fix (class-aware splitting, fur-flow preservation, gradient
  stepping, palette restriction) can now read `semantic_region_report.json`
  instead of treating every object the same.
