# Cat Material Behavior Report — Quality Fix 8

QF7 established **what** each region is. This layer establishes **how** each
material should behave in future processing — flow, gradient, texture, and noise
handling, plus the future split / palette / SVG / number strategy per region.

**Reuses** QF7's `semantic_region_report.json` (semantic analysis is **not**
recomputed). **Understanding only** — no region, palette, number, SVG, or export
change.

- Engine: [`backend/services/material_behavior_engine.py`](backend/services/material_behavior_engine.py)
- Report: [`material_behavior_report.json`](material_behavior_report.json) (`source: semantic_region_report.json`)
- Overlay: [`research/07_validation/cat_material/material_behavior_overlay.png`](research/07_validation/cat_material/material_behavior_overlay.png)

## Validation — pipeline output identical except new reports
| Output | Before QF8 | After QF8 |
| --- | ---: | ---: |
| Region count | 66 | **66** |
| Palette colors | 12 | **12** |
| Numbered regions | 66 | **66** |
| SVG paths | 66 | **66** |
| `result.zip` material/semantic files | — | **none** (export unchanged, 15 entries) |
| Frontend | — | untouched |

✔ Reuses QF7 output (no semantic recompute) · ✔ no region/palette/number/SVG/
export/frontend change · only `material_behavior_report.json` +
`material_behavior_overlay.png` added.

---

## Behavior statistics
- **66 regions** mapped to a behavior profile via their QF7 semantic class.
- Per region: `material_behavior` (behavior tokens), four preservation scores
  (`flow`, `gradient`, `texture`, `noise_removal`), and four future strategies
  (`split`, `palette`, `svg`, `number`).
- Scores are the class base **modulated by the reused QF7 measurements**
  (edge-flow, colour variance, texture) — so a fur region with strong measured
  flow scores higher on flow preservation than a flat one.

## Behavior distribution
| Behavior class | Count | Drawn from semantic classes |
| --- | ---: | --- |
| **GRADIENT** | 32 | SKIN, LEAF, CLOTH |
| **FLOW** | 23 | FUR, WOOD |
| **DETAIL** | 7 | FLOWER |
| **SIMPLIFY** | 4 | BACKGROUND |

The overlay colours the cat body **FLOW** (orange), the plant / soft goods
**GRADIENT** (blue), the accent hearts/collar **DETAIL** (pink), and the
background **SIMPLIFY** (grey).

## Flow preservation summary
- mean **0.43**, min 0.06, max **0.845**. The high end is the cat's fur regions
  (FLOW class × strong measured edge-flow) — these must be split *along* the flow
  and never flattened.

## Gradient preservation summary
- mean **0.329**, min 0.207, max 0.575. Highest on SKIN/CLOTH/LEAF regions with
  real internal colour variance — these should be stepped, not flattened.

---

## Future split restrictions (advisory, not applied)
| Strategy | Count | Meaning |
| --- | ---: | --- |
| `PRESERVE_GRADIENT` | 32 | split only along tonal bands (skin/leaf/cloth) |
| `FOLLOW_FLOW` | 23 | split **along** fur/wood flow direction, never across |
| `PROTECT_DETAIL` | 7 | do not split flowers below feature size |
| `SAFE_SIMPLIFY` | 4 | background may be split/merged freely |

A future split engine must read this: e.g. fur splits follow
`dominant_gradient_direction`; flowers are protected; background is free.

## Future palette restrictions (advisory, not applied)
| Strategy | Count | Meaning |
| --- | ---: | --- |
| `PRESERVE_GRADIENT` | 55 | keep tonal steps; group only within a material |
| `DO_NOT_SIMPLIFY` | 7 | FLOWER accents must stay distinct colours |
| `SAFE_SIMPLIFY` | 4 | background colours may be merged aggressively |

The future palette grouper must **not** merge `DO_NOT_SIMPLIFY` (flower) colours
even when LAB-close, and must preserve gradient steps on the 55 gradient regions.

## Future SVG restrictions (advisory, not applied)
| Strategy | Count | Meaning |
| --- | ---: | --- |
| `KEEP_TEXTURE` | 36 | fur/leaf/wood edges keep detail; don't over-simplify paths |
| `SAFE_SIMPLIFY` | 23 | smooth-material paths may be simplified |
| `PROTECT_DETAIL` | 7 | flower/petal paths preserved exactly |

## Commercial observations
- A commercial color-by-number of this cat should treat the **fur as one flowing
  material** (23 FLOW regions) — future splitting must honour fur flow so the
  result still reads as fur, not random patches.
- The **background (4 SIMPLIFY)** is the safe place to reduce region/colour count
  for a cleaner, cheaper-to-print page.
- **Flower accents (7 DETAIL)** are the identity pop of the scene — protect their
  boundaries and colours; never group them away.
- The gradient-bearing fur/skin/cloth regions (high `color_variance`) are the
  natural candidates for **stepped shading**, which is what makes a commercial
  page look rich rather than flat — but only along their material behaviour
  (flow for fur, tonal bands for cloth/skin).

---

## Notes
- Foundation layer: profiles + scores + strategies only. Nothing acts on them —
  no split, no palette/number/SVG/export change.
- Fully **reuses** QF7 (`source: semantic_region_report.json`) and the persisted
  `labels.npy`; it recomputes no semantic analysis.
- Every future Quality Fix (class-aware split, palette restriction, SVG detail
  control) can now read `material_behavior_report.json` for per-region policy.
