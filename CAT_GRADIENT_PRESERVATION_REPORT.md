# Cat Gradient Preservation Report — Quality Fix 10

The **first pixel-changing fix**. It makes the cat flat fill visibly closer to the
original by preserving important shading as **stepped paint regions** — splitting
only the regions QF9's policy selected into a few luminance bands, coloured from
the original. Everything else is untouched.

**Reuses** QF7 `semantic_region_report.json` (region_id → label) and QF9
`processing_policy_report.json` (selection + limits). No previous analysis is
recomputed.

- Engine: [`backend/services/gradient_preservation_engine.py`](backend/services/gradient_preservation_engine.py)
- Report: [`gradient_preservation_report.json`](gradient_preservation_report.json)
- Preview: [`research/07_validation/cat_gradient/gradient_preview_map.png`](research/07_validation/cat_gradient/gradient_preview_map.png)
- Before/after flat fill: `before_flat.png` / `after_flat.png` in the same folder.

## Fidelity result (Engineering Rule 8 ✔)
| Metric | Before | After | Change |
| --- | ---: | ---: | --- |
| **Flat-fill ΔE to original** (whole image) | 18.27 | **13.22** | **−27.6%** closer |
| **ΔE on the split regions** | 32.99 | **15.54** | **−52.9%** closer |
| gradient_fidelity_score | — | **27.63** | (global ΔE improvement %) |

The flat single-orange cat now has a **cream chest, shaded head, tabby markings,
and shaded fur** — visibly closer to the original illustration. This QF *does*
make the reference cat closer to the original, so it passes Rule 8.

## What was split
- **9 regions selected** by policy (`GRADIENT_PRESERVE`/`FLOW_PROTECT` +
  `preserve_gradient` + `future_split_strength ≥ 0.5`); **all 9 split**, into
  **3 tonal bands each → +18 sub-regions.**

| region | class | strength | bands |
| ---: | --- | ---: | ---: |
| 7, 44, 45, 50 | FUR | 0.61–0.74 | 3 each |
| 23, 27 | LEAF | 0.67–0.73 | 3 each |
| 22, 28 | WOOD | 0.51–0.54 | 3 each |
| 41 | CLOTH | 0.52 | 3 |

Splitting is by balanced luminance quantiles (median-smoothed), capped at 3 bands,
with a minimum luminance range before any split — so the result is coherent
shading steps, **not** random fragments.

## Consistent updates
| Output | Before | After |
| --- | ---: | ---: |
| Regions | 66 | **84** |
| Palette colors | 12 | **15** |
| SVG paths | 66 | **84** |

Regions, `game_config.json`, palette, numbering, SVG, and previews were all
regenerated **consistently** from the edited label map (via the existing
`rebuild_from_labels`). New tonal-band boundaries were also drawn into the line
art so the Line-Art view matches the new regions.

## Validation (all pass)
- **Consistency verifier / no leaks:** flood-fill leak test `leak_pass = true`, `region_leaks = 0`.
- **Numbers valid:** 84 / 84 regions numbered.
- **Palette valid:** every region's `color_id` references a palette entry.
- **SVG paths valid:** all path `d` attributes are `Z`-closed.
- **Visual before/after:** reviewed — after flat fill is clearly closer to the original.

## Backward compatibility
QF10 is **policy-gated**: on a flat synthetic image (solid shapes) **0 regions
were selected and nothing changed** (regions unchanged, `changed = false`). Only
gradient-rich content is enriched; simple content and the output *format* are
unchanged.

## Remaining issues
- Bands are cut by luminance quantiles, so fur is stepped by tone rather than
  strictly *along flow* (QF9's `future_split_direction: ALONG_FLOW`). A future
  fix could orient the bands to the fur flow for even more natural shading.
- Palette grew 12 → 15; still compact, but a future palette pass could group the
  new near-identical band colours within a material.
- Green cat eyes remain classified LEAF upstream (QF7) — not split here, but a
  reminder that small-feature semantics are still heuristic.
- Disconnected same-tone band pixels share one label/number (valid, same colour),
  which is fine for painting but slightly increases path complexity.

## Notes
- Reuses QF7 + QF9 (`sources` in the report); recomputes no analysis.
- Integrated into `pipeline.run` as a conservative, policy-gated fidelity pass
  after QF7–9; `rebuild_from_labels` keeps every artifact consistent.
- Rule 1 respected: the original image is never edited; colours are **sampled**
  from it, never invented.
