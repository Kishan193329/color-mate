# Cat Processing Policy Report — Quality Fix 9

QF7 = **what** each region is, QF8 = **how** its material behaves. This layer
compiles both into a concrete, per-region **processing policy**: the exact
preserve flags, simplification limits, future split direction/strength, and
palette / SVG / numbering policies that every later fix must obey.

**Reuses** `semantic_region_report.json` (QF7) and `material_behavior_report.json`
(QF8) — recomputes neither. **Policy layer only** — no region, palette, number,
SVG, or export change.

- Engine: [`backend/services/processing_policy_engine.py`](backend/services/processing_policy_engine.py)
- Report: [`processing_policy_report.json`](processing_policy_report.json) (`sources`: QF7 + QF8)
- Overlay: [`research/07_validation/cat_policy/processing_policy_overlay.png`](research/07_validation/cat_policy/processing_policy_overlay.png)

## Validation — pipeline output identical except new reports
| Output | Before QF9 | After QF9 |
| --- | ---: | ---: |
| Region count | 66 | **66** |
| Palette colors | 12 | **12** |
| Numbered regions | 66 | **66** |
| SVG paths | 66 | **66** |
| `result.zip` policy/material/semantic files | — | **none** (export unchanged, 15 entries) |
| Frontend | — | untouched |

✔ Reuses QF7 + QF8 (no recompute) · ✔ no region/palette/number/SVG/export/
frontend change · only `processing_policy_report.json` +
`processing_policy_overlay.png` added.

---

## Per-region policy fields
Each region carries: `semantic_class`, `material_behavior`, `preserve_gradient`,
`preserve_texture`, `preserve_flow`, `preserve_stripes`, `allow_simplification`,
`maximum_simplification`, `minimum_region_area`, `future_split_direction`,
`future_split_strength`, `palette_merge_policy`, `svg_smoothing_policy`,
`numbering_policy`, `validation_priority`, `processing_priority` — all derived
from the reused QF7 measurements and QF8 behaviour scores/strategies.

## Policy distribution
| Policy class | Count | Behaviour |
| --- | ---: | --- |
| **GRADIENT_PRESERVE** | 34 | skin/leaf/cloth — split across tone, keep steps |
| **FLOW_PROTECT** | 23 | fur/wood — split along flow, minimal simplification |
| **DETAIL_PROTECT** | 5 | flowers — protect boundaries, lock palette |
| **SIMPLIFY** | 4 | background — free to reduce |

## Validation priority
- **HIGH 32 · MEDIUM 30 · LOW 4.** The engine should validate fur/flower/large-
  gradient regions first; background last.

## Future split policy
- `future_split_direction`: **ALONG_FLOW 20** (fur — cut with the grain),
  **ACROSS_TONE 11** (skin/leaf/cloth — cut across shading bands), **NONE 35**
  (protect / low-variance). `future_split_strength` peaks (~0.74) on the large
  high-variance fur/leaf regions and is capped ≤0.2 for flowers.
- `maximum_simplification` is lowest on fur (~0.12) and highest on background,
  and `minimum_region_area` is smallest for flowers (protect small petals) and
  largest for background (avoid tiny background specks).

## Future palette restrictions
| `palette_merge_policy` | Count | Meaning |
| --- | ---: | --- |
| WITHIN_MATERIAL | 55 | merge colours only inside a material's tonal range |
| LOCKED | 7 | flower accents must never be merged |
| FREE | 4 | background may be merged aggressively |

## Future SVG restrictions
| `svg_smoothing_policy` | Count | Meaning |
| --- | ---: | --- |
| KEEP_DETAIL | 43 | fur/leaf/flower edges preserved |
| STRONG | 23 | smooth-material paths may be simplified |

## Numbering policy
- **PROTECT 41** (feature regions keep their number) · **STANDARD 25**. Small
  regions (< threshold) are marked `HIDE_SMALL` where they occur.

---

## Honest note on Engineering Rule 8
Rule 8 says *"if this QF does not make the reference cat image closer to the
original illustration, reject it."* QF9 is, by the task's own spec, a
**policy/understanding layer that adds reports only and changes no pixels** — so
it does **not** by itself move the cat output closer to the original. Its value is
preparatory: it encodes, per region, the exact policies a *future* fidelity fix
must follow (split fur along flow, step gradients across tone, lock flower
colours, protect small features, simplify only the background) **without
redesigning the original** (Rule 1). If Rule 8 is enforced strictly as a gate,
QF9 should be treated as a foundation commit that a subsequent, pixel-changing fix
must "pay back" by using this policy to visibly improve fidelity.

## Notes
- Foundation/policy layer: fields only. Nothing acts on them yet.
- Fully **reuses** QF7 + QF8 (`sources` in the report) and the persisted
  `labels.npy`; recomputes no analysis.
- The understanding stack is now three layers: **QF7 what → QF8 how → QF9
  policy** — a future split/simplify/palette fix can read one file
  (`processing_policy_report.json`) for every per-region decision.
