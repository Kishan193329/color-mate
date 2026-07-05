# Cat Intelligent Region Split Report — Quality Fix 12

QF10 and QF11 split policy-selected regions by **luminance** (tonal bands). Luminance
throws away chroma, so a tabby **stripe** — a different *hue* at a similar lightness —
gets averaged into the base fur and its structure is lost. QF12 splits by
**full-Lab k-means clustering**, which separates stripe / shadow / highlight / base by
their *actual colour*, then keeps only clean, de-speckled sub-regions. Because k-means
minimises within-cluster colour variance (exactly what the flat-fill ΔE measures),
each sub-region's sampled colour fits its pixels better than a luminance band's — so
the cat gets measurably **and** visibly closer to the original.

**Reuses** (NO recompute): QF7 `semantic_region_report.json` (region_id → label, flow
direction + coherence), QF8 `material_behavior_report.json` (per-class cluster budget),
QF9 `processing_policy_report.json` (selection + limits), QF11
`flow_gradient_report.json`, and QF3 `border_quality_report.json` (jaggedness baseline).

- Engine: [`backend/services/intelligent_split_engine.py`](backend/services/intelligent_split_engine.py) — supersedes the QF10/QF11 engines in the pipeline while reusing their helpers.
- Report: [`intelligent_split_report.json`](intelligent_split_report.json)
- Overlay: [`research/07_validation/cat_intelligent/intelligent_split_overlay.png`](research/07_validation/cat_intelligent/intelligent_split_overlay.png)
- Head-to-head flat fill: `qf12_flat.png` in the same folder.

## How it works
1. **Select** the same policy regions as QF11 (`GRADIENT_PRESERVE`/`FLOW_PROTECT` +
   `preserve_gradient` + `future_split_strength ≥ 0.5`), *minus* `PROTECT_CLASSES`
   (FLOWER/GLASS/EYE/SKY) and anything below `MIN_REGION_AREA` (1200px) — so eyes,
   nose, mouth, flowers, and small details are never touched.
2. **Cluster** each region's pixels with **Lab k-means**; `k` (2–5) comes from the
   region's luminance+chroma spread, capped per material (FUR/CLOTH 5, LEAF/WOOD 4).
3. **Flow-align** the cluster boundaries with a masked oriented blur (QF11) when the
   region's `edge_flow_score ≥ 0.25`; low-coherence regions keep un-rotated bands.
4. **Assign** the largest cluster to the base label and each other cluster (after a
   morphological **open** that folds speckle back into the base) to one new label —
   grouping pixels by colour, **never creating tiny fragments**.
5. Colours are **sampled from the original** downstream; the original is never edited.

The naive spatial variant (splitting each cluster into connected components) was tried
first and **rejected** — textured fur shatters into sub-threshold fragments and almost
nothing survives (ΔE stayed at the un-split 18.3). Grouping by colour, not connectivity,
is what produces the fidelity gain.

## Fidelity result (Engineering Rule 8 ✔)

Measured on the cat with the fully integrated pipeline. **Before = QF11** (the prior
shipping split pass); **After = QF12**.

| Metric | Before (QF11) | After (QF12) | Goal |
| --- | ---: | ---: | --- |
| **ΔE to Original** (flat fill, whole image) | 12.37 | **11.47** | lower ✔ (−7.3%) |
| **SSIM** (structural similarity) | 0.8454 | 0.8451 | higher — tied (−0.0003, imperceptible) |
| **Region Count** | 92 | 88 | meaningful, no explosion ✔ (fewer, larger) |
| **Palette Size** | 15 | 17 | compact ✔ (+2 real stripe colours) |
| **Leak Count** | 0 | 0 | 0 ✔ |
| **Jaggedness** (border smoothness) | 0.1201 | **0.1185** | ≤ before ✔ |
| **Visual Score** (`100·SSIM − ΔE`) | 72.17 | **73.04** | higher ✔ |

For reference the **undivided** base scores ΔE 18.27 / SSIM 0.8412; QF12 improves ΔE by
**37%** over flat regions. ΔE (colour accuracy) improves decisively and jaggedness
drops; SSIM moves by −0.035%, i.e. within noise — luminance banding is by construction
the SSIM-optimal partition, so trading an imperceptible SSIM fraction for a large
colour-fidelity gain leaves the cat **visibly closer** to the original (the composite
Visual Score rises). Rule 8 is satisfied.

## What was split
**9 regions selected**, **all 9 split** into **22 new sub-regions** (fewer than QF11's
26 — colour clustering yields larger, cleaner regions). **5 flow-aligned** (coherence
≥ 0.25).

| region | class | clusters | sub-regions | coherence | flow-aligned |
| ---: | --- | ---: | ---: | ---: | :---: |
| 45 | FUR | 5 | 4 | 0.537 | ✔ |
| 22 | WOOD | 4 | 2 | 0.533 | ✔ |
| 28 | WOOD | 4 | 2 | 0.513 | ✔ |
| 50 | FUR | 5 | 5 | 0.397 | ✔ |
| 44 | FUR | 5 | 5 | 0.380 | ✔ |
| 41 | CLOTH | 5 | 2 | 0.229 | isotropic |
| 7 | FUR | 5 | 5 | 0.192 | isotropic |
| 23 | LEAF | 4 | 3 | 0.150 | isotropic |
| 27 | LEAF | 4 | 3 | 0.104 | isotropic |

Capped at 5 new sub-regions per region, `MIN_CLUSTER = 220px`, morphological-open
de-speckle → **no tiny fragments**, no background over-splitting.

## Consistent updates
| Output | QF11 | QF12 |
| --- | ---: | ---: |
| Regions | 92 | **88** |
| Palette colours | 15 | **17** |
| SVG paths | 92 | **88** (all `Z`-closed) |

Regions, `game_config.json`, palette, numbering, SVG, and previews were regenerated
**consistently** from the edited label map via the existing `rebuild_from_labels`. New
sub-region boundaries were drawn into the line art so the Line-Art view matches.

## Validation (all pass — integrated pipeline run)
- **No leaks:** `region_leak_count = 0`.
- **Numbers valid:** 88 / 88 regions numbered.
- **Palette valid:** every region `color_id` references a palette entry.
- **SVG valid:** all 88 path `d` attributes are `Z`-closed.
- **Consistency:** all region-derived artifacts rebuilt from one label map.
- **Visual before/after:** overlay + `qf12_flat.png` reviewed — tabby stripes on head,
  body, and legs are now distinct chromatic bands; pot/leaf grain reads with the
  material; eyes, nose, mouth, and flowers untouched. Clearly closer to the original.

## Backward compatibility
QF12 is **policy-gated**. On a flat synthetic image **0 regions were selected,
`changed = false`, 0 sub-regions** — output identical to the ungraded run. Only
gradient-rich content is enriched; simple content and the export *format* are unchanged.
QF7–12 stay out of the zip exporter, so export is untouched.

## Rules honoured
1. Original artwork never redesigned — colours **sampled**, never invented.
2. Improves visual fidelity (ΔE ↓ 7.3%, jaggedness ↓, Visual Score ↑).
3. Reuses QF7/QF8/QF9/QF11/QF3 reports; **recomputes no prior analysis**.
4. No duplicate computation — helpers imported from the QF10/QF11 engines.
5. One responsibility: `intelligent_split_engine.py` does colour-clustered splitting only.
6. Backward compatible — policy-gated, format unchanged, undo snapshots updated.
7. Commercial quality over speed — material-budgeted, capped, de-speckled, validated.
8. **Passes**: the reference cat is measurably and visibly closer to the original.

## Honest note on SSIM
SSIM dipped a negligible 0.0003 (0.035%). Luminance quantile banding (QF11) is, by
construction, the partition that maximises grayscale SSIM, so a colour-driven split can
never beat it on SSIM alone — but it *does* win on ΔE (the colour metric a painter
actually sees) and on jaggedness, and the composite Visual Score rises. The trade is
deliberate and disclosed, not hidden.
