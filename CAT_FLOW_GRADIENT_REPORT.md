# Cat Flow-Aware Gradient Report — Quality Fix 11

QF10 preserved shading as **stepped tonal bands**, but the bands were cut by pure
luminance quantiles, so fur/leaf/wood/cloth read as isotropic *blobs* rather than
strokes. **QF11 makes the bands FOLLOW MATERIAL FLOW** — fur/stripe direction,
leaf vein direction, wood grain, cloth fold — so the stepped shading reads as the
material instead of patches. It is the second **pixel-changing** fix and it makes
the reference cat measurably *and* visibly closer to the original illustration.

**Reuses** (no recompute): QF7 `semantic_region_report.json` (region_id → label,
`dominant_gradient_direction`, `edge_flow_score` coherence), QF8
`material_behavior_report.json`, QF9 `processing_policy_report.json` (selection +
limits), and QF10 `gradient_preservation_report.json`.

- Engine: [`backend/services/flow_gradient_engine.py`](backend/services/flow_gradient_engine.py) — supersedes the QF10 engine in the pipeline while reusing its helpers.
- Report: [`flow_gradient_report.json`](flow_gradient_report.json)
- Overlay: [`research/07_validation/cat_flow/flow_gradient_overlay.png`](research/07_validation/cat_flow/flow_gradient_overlay.png) — after-flat-fill + orange band boundaries + per-region flow arrows.
- Head-to-head flat fills: `qf10_flat.png` / `qf11_flat.png` in the same folder.

## How it works (tone accuracy + directional shape, separated)
1. **Band by RAW luminance** (5–95th percentile, up to **4** quantile steps) — this
   keeps each band's colour tone-accurate, so ΔE stays low. Colours are **sampled**
   from the original, never invented (Rule 1).
2. **Flow-align only the band BOUNDARIES**: the integer band map is smoothed with an
   **oriented line kernel** rotated to the region's flow axis (dominant gradient
   direction **+ 90°**, since strokes run ⊥ to the tonal gradient), then re-quantised
   (`rint`). Only boundary pixels move, so bands elongate along fur/vein/fold with
   near-zero colour cost.
3. **Coherence gate**: flow-alignment is applied only when QF7 `edge_flow_score ≥ 0.25`.
   Low-coherence regions fall back to clean isotropic bands (QF10 behaviour) instead
   of being forced into a false direction.

The earlier naive approach (smoothing *luminance* along the flow, then banding)
**hurt** fidelity (ΔE 13.35, SSIM 0.834) and was rejected under Rule 8. Separating
tone-banding from boundary-shaping is what makes QF11 a net improvement.

## Fidelity result (Engineering Rule 8 ✔)

Measured on the cat with the fully integrated pipeline. **Before = QF10** (the prior
shipping gradient pass); **After = QF11**.

| Metric | Before (QF10) | After (QF11) | Goal |
| --- | ---: | ---: | --- |
| **ΔE to Original** (flat fill, whole image) | 13.22 | **12.37** | lower ✔ (−6.4%) |
| **SSIM** (structural similarity to original) | 0.8399 | **0.8454** | higher ✔ |
| **Region Count** | 84 | 92 | controlled, no explosion ✔ |
| **Palette Size** | 15 | 15 | stable ✔ |
| **Leak Count** | 0 | 0 | 0 ✔ |
| **Jaggedness** (border smoothness) | 0.1204 | **0.1201** | ≤ before ✔ |
| **Visual Score** (`100·SSIM − ΔE`) | 70.77 | **72.17** | higher ✔ |

For reference, the **undivided** base (no gradient pass) scores ΔE 18.27 / SSIM
0.8412 — so QF11 improves ΔE by **32%** over flat regions, and now also lifts SSIM
*above* the base (QF10's isotropic bands had dipped SSIM slightly below it).

*Visual Score* is a transparent composite of the two fidelity metrics (higher is
better); it is not a separate measurement, just a single-number summary of the row
above.

## What was split
**9 regions selected** by QF9 policy (`GRADIENT_PRESERVE`/`FLOW_PROTECT` +
`preserve_gradient` + `future_split_strength ≥ 0.5`); **all 9 split** into **26 new
sub-regions**. **5 were flow-aligned** (coherence ≥ 0.25); the other 4 fell back to
isotropic banding because their measured flow coherence was too low to trust.

| region | class | bands | flow angle | coherence | flow-aligned |
| ---: | --- | ---: | ---: | ---: | :---: |
| 45 | FUR | 4 | 102.7° | 0.537 | ✔ |
| 22 | WOOD | 4 | 109.4° | 0.533 | ✔ |
| 28 | WOOD | 4 | 108.1° | 0.513 | ✔ |
| 50 | FUR | 4 | 89.3° | 0.397 | ✔ |
| 44 | FUR | 4 | 172.1° | 0.380 | ✔ |
| 41 | CLOTH | 3 | 151.0° | 0.229 | isotropic fallback |
| 7 | FUR | 4 | 158.1° | 0.192 | isotropic fallback |
| 23 | LEAF | 4 | 108.7° | 0.150 | isotropic fallback |
| 27 | LEAF | 4 | 135.1° | 0.104 | isotropic fallback |

Banding is capped at **4 steps** with a minimum luminance range before any split, so
the result is coherent directional shading — **not** random fragments or over-split
tiny regions.

## Consistent updates
| Output | QF10 | QF11 |
| --- | ---: | ---: |
| Regions | 84 | **92** |
| Palette colours | 15 | **15** |
| SVG paths | 84 | **92** (all `Z`-closed) |

Regions, `game_config.json`, palette, numbering, SVG, and previews were regenerated
**consistently** from the edited label map via the existing `rebuild_from_labels`.
New band boundaries were drawn into the line art so the Line-Art view matches.

## Validation (all pass — integrated pipeline run)
- **No leaks:** `region_leak_count = 0`.
- **Numbers valid:** 92 / 92 regions numbered.
- **Palette valid:** every region `color_id` references a palette entry.
- **SVG valid:** all 92 path `d` attributes are `Z`-closed.
- **Consistency:** all region-derived artifacts rebuilt from one label map.
- **Visual before/after:** `qf10_flat.png` vs `qf11_flat.png` and the flow overlay
  reviewed — the tabby stripes, ears, cheeks, and cream chest now step *along* the
  fur, and pot/leaf grain runs with the material. Clearly closer to the original.

## Backward compatibility
QF11 is **policy-gated**. On a flat synthetic image (solid shapes) **0 regions were
selected, `changed = false`, 0 sub-regions created** — regions, palette, numbering,
and SVG were identical to the ungraded output. Only gradient-rich content is
enriched; simple content and the export *format* are unchanged. QF7–11 remain out of
the zip exporter, so export is untouched.

## Rules honoured
1. Original artwork never redesigned — colours **sampled**, never invented.
2. Improves visual fidelity (ΔE ↓, SSIM ↑, Visual Score ↑).
3. Reuses QF7/QF8/QF9/QF10 reports; **recomputes no prior analysis**.
4. No duplicate computation — helpers imported from the QF10 engine.
5. One responsibility: `flow_gradient_engine.py` does flow-aware banding only.
6. Backward compatible — policy-gated, format unchanged, undo snapshots updated.
7. Commercial quality over speed — coherence-gated, capped, validated end-to-end.
8. **Passes**: the reference cat is measurably and visibly closer to the original.
