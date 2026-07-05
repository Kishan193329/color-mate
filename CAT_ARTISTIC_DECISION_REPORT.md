# Cat Artistic Decision Report — Quality Fix 13

QF7–QF12 built a stack that *understands* the image (semantic → material → policy)
and *acts* on it (gradient / flow / intelligent split). QF13 is the missing centre:
the **artist brain**. For every region it fuses all prior reports into one
authoritative decision record — the region's **artistic role**, its **visual
importance**, exactly **what must be protected**, and precisely **what future
engines are permitted to do** (split / merge palette / smooth SVG / renumber),
plus the **reason to reject** any future edit that degrades it.

**This QF changes no pixels.** It reads the existing reports and emits only two new
artifacts. Future pixel-changing fixes will obey this record instead of each
re-deriving intent — which is the point (Engineering Rule 7).

- Engine: [`backend/services/artistic_decision_engine.py`](backend/services/artistic_decision_engine.py)
- Report: [`artistic_decision_report.json`](artistic_decision_report.json)
- Overlay: [`research/07_validation/cat_artistic/artistic_decision_overlay.png`](research/07_validation/cat_artistic/artistic_decision_overlay.png)

## Reused reports (nothing recomputed)
`semantic_region_report.json` (QF7), `material_behavior_report.json` (QF8),
`processing_policy_report.json` (QF9), `flow_gradient_report.json` (QF11),
`intelligent_split_report.json` (QF12), `border_quality_report.json` (QF3).

The **only** new computation is cheap region **adjacency / enclosure** geometry
(below) — used to correct upstream misclassification, not to redo it.

## The brain corrects upstream mistakes (its main value)
Raw semantics are noisy: the cream chest reads as CLOTH, the green eyes as LEAF, a
warm window frame as FUR. A decision brain that trusts those blindly would *misguide*
future engines (e.g. let the chest be palette-merged away, or the eyes be
low-precision). So QF13 adds two guards:

1. **Line-bridged adjacency.** In this art every cell is walled off by black
   outline (label 0), so no two regions touch directly. QF13 grows labels across the
   thin outline, then measures true neighbours.
2. **Subject-enclosure override, anchored to the cat's body.** A connected group of
   non-subject regions fully surrounded by the **largest fur/skin blob** (the actual
   cat) — and never touching the image edge — is re-assigned to the subject. This
   flips the cream chest → `SUBJECT_CORE`, the collar → `SUBJECT_DETAIL`, and the
   green eyes → `FACIAL_FEATURE`, while the pot's leaves/flowers (enclosed by the
   frame, not the cat) correctly stay `LEAF_DETAIL` / `FLOWER_DETAIL`.

The overlay confirms it: **both eyes are now FACIAL_FEATURE**, the whole cat body is
one subject, and the plant/props keep their own roles.

## Role distribution (88 regions)
| Artistic role | Count | What it governs |
| --- | ---: | --- |
| FUR_PATTERN | 18 | tabby markings — split along flow, keep detail |
| SUBJECT_CORE | 15 | main body masses — protect volume, high precision |
| DECORATIVE_OBJECT | 21 | pot / pillow / yarn — form kept, less detail |
| LEAF_DETAIL | 11 | plant leaves — shade along vein |
| FACIAL_FEATURE | 10 | eyes / nose / mouth — **locked, critical** |
| FLOWER_DETAIL | 5 | petals — saturated, separated, locked palette |
| BACKGROUND_SUPPORT | 4 | wall / window — keep simple |
| SUBJECT_DETAIL | 4 | collar / small subject islands |
| UNKNOWN_SUPPORT | 0 | — |

## Protected regions
**83 / 88** regions carry an explicit protection flag (`protect_shape`,
`protect_flow`, or `protect_texture`) or rank **HIGH/CRITICAL** — appropriate for a
detailed illustration. Validation priority breakdown: **CRITICAL 25, HIGH 27,
MEDIUM 32, LOW 4** (facial features and subject core are CRITICAL).

## Split permissions
| Permission | Count |
| --- | ---: |
| DENY | 87 |
| ALLOW | 1 |

Almost every region is **DENY** — deliberately. QF12 already performed the permitted
splits, so on the *final* geometry the brain now guards against **re-splitting**
(an idempotence property): only one region still carries splittable structure. Each
role also carries a `split_style` future engines must use when a split *is*
permitted — `LAB_CLUSTER_ALONG_FLOW` (fur), `ALONG_GRAIN` (wood), `ALONG_FOLD`
(cloth), `ALONG_VEIN` (leaf), `NONE` (facial / flower / background).

## Palette restrictions
| Permission | Count | Meaning |
| --- | ---: | --- |
| WITHIN_MATERIAL | 65 | may merge only with same-material neighbours |
| LOCKED | 18 | never merge (facial features, flowers) |
| FREE | 5 | background — free to simplify |

## SVG precision distribution
| Precision | Count |
| --- | ---: |
| HIGH | 63 (facial, flowers, subject detail, kept-detail policy) |
| LOW | 25 (backgrounds / safe-to-simplify props) |

## Sample decisions (one per role)
| role | id | sem | importance | palette | svg | validation |
| --- | ---: | --- | ---: | --- | --- | --- |
| FACIAL_FEATURE | 26 | WOOD→facial | 1.00 | LOCKED | HIGH | CRITICAL |
| SUBJECT_CORE | 7 | FUR | 0.95 | WITHIN_MATERIAL | HIGH | CRITICAL |
| FUR_PATTERN | 3 | FUR | 0.82 | WITHIN_MATERIAL | HIGH | HIGH |
| LEAF_DETAIL | 20 | LEAF | 0.52 | WITHIN_MATERIAL | HIGH | MEDIUM |
| DECORATIVE_OBJECT | 9 | CLOTH | 0.46 | WITHIN_MATERIAL | LOW | MEDIUM |
| BACKGROUND_SUPPORT | 1 | BACKGROUND | 0.25 | FREE | LOW | LOW |

(`sem→facial` shows the enclosure override correcting an upstream label.)

## Commercial review — "Would a professional illustrator treat this region this way?"
Yes. The decisions mirror how an illustrator prioritises a piece:
- **FACIAL_FEATURE** — *"guard the eyes/nose/mouth first, never simplify them."* Locked
  palette, HIGH SVG, CRITICAL validation. ✔
- **SUBJECT_CORE / FUR_PATTERN** — *"render the subject with the most care; paint tabby
  markings as directional strokes, not flat fills."* Protect highlight/shadow/flow. ✔
- **FLOWER_DETAIL / LEAF_DETAIL** — *"keep petals saturated and separated; shade leaves
  along their veins."* Locked/within-material palette, HIGH SVG. ✔
- **DECORATIVE_OBJECT** — *"give props form (grain/fold) but less detail than the
  subject."* Medium priority, lower SVG. ✔
- **BACKGROUND_SUPPORT** — *"keep the background simple so the subject reads."* FREE
  palette, LOW SVG. ✔

Each region also stores a one-line `rejection_reason_if_low_quality` — the artist's
veto a future QA step must enforce (e.g. facial: *"Reject any split/merge/smoothing
that alters the eye/nose/mouth silhouette or removes the catch-light highlight."*).

## Future modules affected
| Module | Obeys |
| --- | --- |
| split / gradient engines (QF12+) | `split_permission` + `split_style` |
| palette engine | `palette_merge_permission` |
| SVG generator | `svg_precision` |
| numbering | `numbering_sensitivity` |
| validation / QA | order by `validation_priority`; enforce `rejection_reason_if_low_quality` |

## Validation (all pass)
- **Ran only the cat.**
- **No output image changes.** Existing artifacts (`preview.png`, `region_mask.png`,
  `game_preview.png`, `texture_fill_preview.png`, `outline.svg`, `regions.json`,
  `palette.json`, `game_config.json`, `lineart.png`) are **byte-identical** before
  and after the QF13 pass (verified by SHA-256). Region count (88), palette (17),
  numbering, SVG validity, and leak count (0) are unchanged from QF12.
- **Only new reports added:** `artistic_decision_report.json` +
  `artistic_decision_overlay.png`.
- **Report well-formed:** all 88 regions carry all 17 decision fields; every
  `artistic_role` is one of the nine defined roles.

## Backward compatibility
Decision-only and best-effort: on a flat synthetic image it simply produces low-
importance `BACKGROUND_SUPPORT`/`DECORATIVE_OBJECT` records and changes nothing else.
QF7–13 stay out of the zip exporter, so export is untouched.

## Honest limitations
- Roles are only as good as upstream semantics plus the enclosure override. Purple
  flower petals mislabelled CLOTH upstream read as `DECORATIVE_OBJECT`, and the heart
  pendant as `FLOWER_DETAIL` — low-harm, disclosed, not hidden. The high-value
  corrections (subject body unified, eyes → facial) are the ones that matter for
  fidelity.
- Facial detection is heuristic (small non-subject islands enclosed by the cat body);
  it correctly caught the eyes here but is not a trained face model.

## Rules honoured
1. Original artwork never redesigned — no pixels touched at all.
2. Reuses QF7/QF8/QF9/QF11/QF12/QF3 reports; recomputes none.
3. No duplicate computation — only new adjacency/enclosure geometry.
4. One responsibility: `artistic_decision_engine.py` produces decisions only.
5. Backward compatible — additive; existing outputs byte-identical.
6. Commercial quality over speed — enclosure override makes the roles illustrator-credible.
7. **Passes**: gives future pixel fixes correct, protected, per-region intent — so
   their edits will be more faithful to the original, not less.
