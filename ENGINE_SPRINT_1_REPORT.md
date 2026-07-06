# Engineering Sprint 1 — Brain Integration & Pipeline Consolidation

**Type:** Engineering consolidation sprint (architecture only)
**Date:** 2026-07-06
**Basis:** `ENGINE_REVIEW_QF3_QF14.md` (architecture audit)
**Scope:** Wire the QF13 Artistic Decision Engine as the single decision authority and
consolidate duplicated responsibility. **No new visual features. No new Quality Fix.
No new reports.**

---

## 0. Goal

The audit found that the "central artist brain" (QF13, `artistic_decision_engine`) was
**not the real authority**. The only live pixel-changing engine — QF12
(`intelligent_split_engine`) — read **QF9 processing policy** directly and ignored QF13.
QF13's `split_permission` / `split_style` / palette / SVG / numbering decisions were
consumed by almost no one. The *real* brain was QF9; QF13 was an elaborate annotation.

This sprint fixes the wiring so there is **one authority (QF13)** that the actuators
obey, without changing any output.

### Engineering rules honoured

| # | Rule | How it was met |
|---|------|----------------|
| 1 | Do not reduce visual quality | Every product artifact is **byte-identical** to the pre-sprint baseline (see §8). |
| 2 | Do not redesign algorithms | k-means split, Lab palette grouping, contour Bézier, semantic classifier — all untouched. |
| 3 | Keep backward compatibility | New engine params are optional; QF10/QF11 re-export their old names; QF12 falls back to QF9 if QF13 is absent. |
| 4 | Reuse all existing reports | No report removed. Two brain reports were **enriched additively**; no product/export report changed. |
| 5 | Remove duplicate responsibility | Selection logic centralised in one predicate (`core.gradient_common.legacy_split_selected`) owned by QF13. |
| 6 | One authority only | QF13 publishes the authoritative `split_plan`; QF12 obeys it instead of re-deriving from QF9. |

---

## 1. Architecture — BEFORE

```
_process(): … QF7 → QF8 → QF9 → QF13 (writes artistic_decision_report.json)
                                    │
run(): _apply_gradient_preservation (QF12)
        QF12 reads  QF9 processing_policy_report.json  ◄── the REAL authority
        QF12 IGNORES QF13 artistic_decision_report.json
          └─ splits → rebuild_from_labels → re-runs QF7/8/9/13
       _apply_contour_character (QF14) reads QF13 (role/importance/protect only)

Shared gradient math imported engine-to-engine:
   QF12 ── import ──► QF10 gradient_preservation_engine (_mean_fill, _delta_e, SELECT_POLICY …)
   QF12 ── import ──► QF11 flow_gradient_engine (_oriented_blur, ssim …)
   (QF10/QF11 run() = DEAD CODE, never called)

Decision authority for the one live actuator:  QF9   (not QF13)
```

**Report/data-flow (before):**

```
semantic(QF7) → material(QF8) → policy(QF9) → decision(QF13) → QF14 contours
                                     │                          ▲
                                     └──► QF12 splitter (reads QF9, NOT QF13)
```

---

## 2. Architecture — AFTER

```
_process(): … QF7 → QF8 → QF9 → QF13 (writes artistic_decision_report.json
                                        + authoritative split_plan)
                                    │
run(): _apply_gradient_preservation (QF12)
        QF12 reads  QF13 split_plan  ◄── THE AUTHORITY (ordered selection + style + protection)
        QF12 reads  QF7 semantic_report  (geometry: label, flow direction, coherence)
        QF12 fallback → QF9 only if the QF13 report is missing (robustness)
          └─ splits → rebuild_from_labels → re-runs QF7/8/9/13
       _apply_contour_character (QF14) reads QF13 (unchanged)

Shared gradient math now lives in ONE neutral module:
   core/gradient_common.py  ◄── QF12 imports directly (no engine-to-engine math import)
   QF10 / QF11 re-export from core (backward compatible, helper libraries only)

Actuators PREPARED to obey QF13 (read path wired, inert this sprint):
   palette_grouper · svg_generator · label_optimizer
     └─ read core/artistic_decisions.py (bridge to artistic_decision_report.json)
        behind an OBEY_QF13 flag (False in Sprint 1 → zero behaviour change)

Decision authority:  QF13  (single)
```

**Report/data-flow (after):**

```
semantic(QF7) → material(QF8) → policy(QF9) → decision(QF13, AUTHORITY) ─┬─► QF12 splitter (obeys split_plan)
   (geometry authority)                                                  └─► QF14 contours
                                                                          └─► palette/svg/numbering (prepared)
```

---

## 3. Brain Wiring (how QF13 became the authority)

### 3.1 One selection predicate, one owner
The historical QF12 selection gate was:

```python
policy_class in {GRADIENT_PRESERVE, FLOW_PROTECT}
    and preserve_gradient
    and future_split_strength >= 0.5
    and semantic_class not in {FLOWER, GLASS, EYE, SKY}
```

It is now a single named function — `core.gradient_common.legacy_split_selected(...)` —
**evaluated once, inside QF13**. QF13 stamps every region with `split_selected` and emits
an ordered `split_plan`. QF12 no longer contains selection logic; it consumes the plan.

### 3.2 The `split_plan` — authoritative and order-preserving
QF13 publishes, in `artistic_decision_report.json`:

```json
"split_selected_count": 9,
"split_selected_region_ids": [7, 22, 23, 27, 28, 41, 44, 45, 50],
"split_plan": [ { "region_id": 7, "label": 7, "semantic_class": "FUR",
                  "split_style": "LAB_CLUSTER_ALONG_FLOW",
                  "protect_shape": …, "protect_highlight": …,
                  "validation_priority": "HIGH" }, … ]
```

**Why the output is bit-identical.** QF12 assigns new sub-region label integers in the
order it processes selected regions, and those integers persist into `regions.json`
(`region_detector.build_from_labels` keeps the label value). The plan is therefore emitted
in **exactly** the order QF12 used before — sorted by QF9 `processing_priority`, stable over
semantic order — so the split-label assignment sequence, `labels.npy`, and every derived
artifact are unchanged. (Proof: the 9 split regions, their labels, cluster counts and
sub-region counts are identical before/after — see §8.)

### 3.3 QF12 obeys, QF7 still owns geometry
QF12 now reads:
- **QF13** `split_plan` → *which* regions to split, in order, + style/protection (the decision).
- **QF7** `semantic_region_report.json` → *where/how* (label, `dominant_gradient_direction`,
  `edge_flow_score`). QF7 remains the geometry/semantic authority (Task 8).

QF12 **no longer reads QF9** on the primary path. A QF9 fallback remains only for the case
where the QF13 report is unavailable, so a QF13 failure never disables the fidelity pass.

### 3.4 Actuators prepared (no behaviour change)
`palette_grouper`, `svg_generator`, `label_optimizer` each gained:
- an optional `decisions: ArtisticDecisions | None` parameter,
- a read via the new `core/artistic_decisions.py` bridge, and
- a documented `OBEY_QF13` seam mapping the relevant QF13 field to its effect:

| Engine | QF13 field it will obey | Sprint-1 state |
|--------|-------------------------|----------------|
| palette_grouper | `palette_merge_permission` (LOCKED / WITHIN_MATERIAL / FREE) | wired, `OBEY_QF13=False` |
| svg_generator | `svg_precision` (HIGH / MEDIUM / LOW) | wired, `OBEY_QF13=False` |
| label_optimizer | `numbering_sensitivity` (HIGH / MEDIUM / LOW) | wired, `OBEY_QF13=False` |

The bridge is unit-verified against the real report (e.g. a `FACIAL_FEATURE` region →
`svg_precision=HIGH`, `numbering_sensitivity=HIGH`, `palette_merge_permission=LOCKED`,
`protect_shape=True`). A future sprint flips `OBEY_QF13` and reorders QF13 ahead of the
actuators to activate them.

---

## 4. Dependency Graph

**Before**
```
QF12 ─reads→ QF9 (authority)          QF12 ─imports→ QF10, QF11  (math)
QF14 ─reads→ QF13                     QF14 ─imports→ QF13        (adjacency)
QF8/9/12/13/14 ─read→ QF7 (semantics)
Phase-7 region_hierarchy ─writes→ regions.json (2nd semantic taxonomy)
```

**After**
```
QF12 ─reads→ QF13 split_plan (authority)   QF12 ─imports→ core.gradient_common  (math)
QF12 ─reads→ QF7 (geometry)                QF12 ─fallback→ QF9 (only if QF13 absent)
QF13 ─imports→ core.gradient_common (legacy_split_selected)
palette/svg/numbering ─read→ core.artistic_decisions → QF13   (prepared, inert)
QF14 ─reads→ QF13 ; QF14 ─imports→ QF13 (adjacency, unchanged — see debt)
QF8/9/12/13/14 ─read→ QF7 (semantics)      Phase-7 unchanged (see §7 / debt)
```

New module: **`backend/core/`**
- `gradient_common.py` — shared gradient/split math + `legacy_split_selected` (single source).
- `artistic_decisions.py` — read-only bridge to `artistic_decision_report.json`, keyed by label/region_id.

---

## 5. Per-Task Results

| Task | Requirement | Result |
|------|-------------|--------|
| 1 | QF13 = only decision authority; future engines read `artistic_decision_report.json` | ✅ QF13 emits `split_selected` + ordered `split_plan`; `core/artistic_decisions.py` is the read bridge. |
| 2 | QF12 consumes QF13 (split permission/style/protection/priority) not QF9 | ✅ QF12 iterates `split_plan`; QF9 is fallback-only. Splits reproduced exactly. |
| 3 | Palette engine prepared to obey QF13 (no behaviour change) | ✅ `palette_merge_permission` seam wired behind `OBEY_QF13=False`. |
| 4 | SVG engine prepared to obey QF13 (no behaviour change) | ✅ `svg_precision` seam wired behind `OBEY_QF13=False`. |
| 5 | Number engine prepared to obey QF13 (no behaviour change) | ✅ `numbering_sensitivity` seam wired behind `OBEY_QF13=False`. |
| 6 | Identify duplicated policy fields QF8/QF9/QF13; migration plan | ✅ See §6. |
| 7 | QF10/QF11 helper-only; move reusable fns to `core/gradient_common.py` | ✅ Helpers centralised; QF12 imports core directly; QF10/QF11 re-export (dead `run()` retained per Rule 3). |
| 8 | QF7 = only semantic authority; document Phase-7 dependency | ✅ QF7 verified independent (classifies from pixels); Phase-7 still annotates product JSON (see §7). |

---

## 6. Duplicated Policy Fields (QF8 · QF9 · QF13) + Migration Plan

Field sets extracted from the live reports (cat.png run):

**Verbatim duplicate**
- `validation_priority` — emitted **independently by both QF9 and QF13**.
- `semantic_class`, `material_behavior`, `region_id` — context restated in all three.

**Three-hop restatement of the same decision** (QF8 strategy → QF9 policy → QF13 permission):

| Decision | QF8 field | QF9 field | QF13 field |
|----------|-----------|-----------|------------|
| Split | `future_split_strategy` | `future_split_direction`, `future_split_strength` | `split_permission`, `split_selected`, `split_style` |
| Palette | `future_palette_strategy` | `palette_merge_policy` | `palette_merge_permission` |
| SVG | `future_svg_strategy` | `svg_smoothing_policy` | `svg_precision` |
| Numbering | `future_number_strategy` | `numbering_policy` | `numbering_sensitivity` |
| Preservation | `*_preservation_score` (4) | `preserve_gradient/texture/flow/stripes` | `protect_shape/highlight/shadow/texture/flow` |

**Migration plan (future sprints — NOT this one):**
1. **Fold QF8 into QF9.** QF8 is a near-static lookup (`PROFILES`) lightly modulated by three
   QF7 numbers. Make it a function/table QF9 imports; drop `material_behavior_report.json` as a
   separate disk artifact (keep as in-memory struct).
2. **Collapse QF9's actuator fields into QF13.** `palette_merge_policy` / `svg_smoothing_policy`
   / `numbering_policy` / `validation_priority` should be emitted **only** by QF13. QF9 keeps the
   raw primitives QF13 needs (`preserve_*`, `future_split_strength`), or QF13 absorbs those too.
3. **Single `validation_priority` owner = QF13.** Remove QF9's copy.
4. **Sequencing prerequisite:** move QF13 *before* palette/SVG/numbering in the pass (or have them
   read the prior pass's decisions) so their `OBEY_QF13` seams can be switched on.

Target end-state: **one decision record per region (QF13)** with provenance; QF8 = internal table;
QF9 = raw policy primitives only.

---

## 7. QF7 Semantic Authority & Phase-7 Dependency (Task 8)

- **QF7 is the sole semantic authority for the decision cascade.** `semantic_region_analyzer`
  computes `semantic_class` purely from pixel measurements (RGB/HSV/texture/flow/gradient). It does
  **not** read any Phase-7 annotation. QF8, QF9, QF12, QF13, QF14 all key off
  `semantic_region_report.json` and nothing else for semantics.
- **Remaining Phase-7 dependency (documented, deliberately NOT removed):**
  `region_hierarchy.apply_all` (+ `semantic_classifier`, `object_detector`, `semantic_rules`) still
  runs in Step 4 / `_emit_from_region_data` and writes `semantic_label`, `parent_object`, `group_id`,
  `region_type` into the **shipped** `regions.json` / `game_config.json` (via
  `json_generator._region_public`) and `game_config.semantic`. This is a **second, divergent
  semantic taxonomy** consumed by the frontend/game grouping — not by the brain.
- It is **kept as-is** this sprint: removing it would change the product JSON (a region/export
  change), violating Rule 1. It is registered as tech debt for a future unification sprint (feed
  QF7's class into the Phase-7 group builder, or retire `semantic_classifier` in favour of QF7).

---

## 8. Validation — "Run only cat"

Full initial pipeline (`pipeline.run`) executed on `cat.png` in an isolated job dir, snapshotting
every product + brain artifact (raw SHA + timing-normalised SHA + `result.zip` member SHAs).

**Determinism check** (two independent baseline runs): every artifact byte-identical except
`game_config.json` / `result.zip` (embedded `job_id` + `processing_time_seconds` only) — so
byte-comparison is a valid gate.

**Baseline vs After (all 8 tasks):**

| Artifact | Result |
|----------|--------|
| `regions.json` | **byte-identical** |
| `palette.json` | **byte-identical** |
| `outline.svg` | **byte-identical** |
| `region_mask.png` | **byte-identical** |
| `preview.png`, `texture_fill_preview.png`, `game_preview.png` | **byte-identical** |
| `quality_report.json` | **byte-identical** |
| `labels.npy` | **byte-identical** |
| `lineart.png` | **byte-identical** |
| `semantic/material/processing_policy/border/contour_character` reports | **byte-identical** |
| `game_config.json` | identical except embedded `job_id`/timing |
| `result.zip` | members identical except embedded `job_id`/timing |
| `artistic_decision_report.json` | **changed — additive only** (`split_selected`, `split_plan`, `label`; 0 pre-existing fields altered across 88 regions) |
| `intelligent_split_report.json` | **changed — additive only** (`selection_authority`, `selection_source`, per-detail `split_style`; the 9 splits / labels / clusters / sub-regions are identical) |

⇒ **No region, SVG, palette, numbering, colour, or export change. Architecture only.**
The only two artifacts that changed are engine-internal brain reports (neither is in `result.zip`),
and both changed **purely additively**.

---

## 9. Duplicate Responsibilities Removed

- **Selection logic** de-duplicated: QF12's inline selection filter deleted; the predicate lives once
  in `legacy_split_selected`, owned by QF13. QF12 is now a pure actuator.
- **Engine-to-engine math imports** removed for QF12: it imports `core.gradient_common` directly
  instead of borrowing from the QF10/QF11 engine modules.
- **Split-decision authority** de-duplicated: was split between QF9 (real) and QF13 (nominal); now
  QF13 only.
- **`PROTECT_CLASSES`** consolidated into `core.gradient_common.SPLIT_PROTECT_CLASSES` (QF12 keeps a
  backward-compatible alias).

---

## 10. Remaining Technical Debt

1. **Dead engine bodies retained.** `gradient_preservation_engine.run` / `flow_gradient_engine.run`
   are still never called (kept for Rule 3 / reference). Delete in a future cleanup once no external
   caller relies on the modules.
2. **Two `validation_priority` producers** (QF9 + QF13) and the three-hop policy restatement (§6)
   remain — the field-level merge is deferred to the QF8→QF9→QF13 consolidation.
3. **Actuator seams inert.** palette/SVG/numbering read QF13 but `OBEY_QF13=False`. Activating them
   needs QF13 sequenced before them (see §6.4).
4. **QF14 imports `_fill_lines`/`_edge_lengths` from QF13** (engine-to-engine adjacency import). Move
   these to a shared `core` module in a future pass (out of this sprint's QF10/QF11 scope).
5. **Second semantic taxonomy (Phase-7)** still annotates the product JSON (§7).
6. **Double cascade.** QF7→QF13 (+QF14) still re-run inside `rebuild_from_labels` after a QF12 split.
   The single-pass reorder is a later performance sprint (audit Phase C/D).

---

## 11. Future Sprint Recommendations

- **Sprint 2 — Policy consolidation.** Execute §6: fold QF8 into QF9; move all actuator/validation
  fields into QF13; single `validation_priority`. Then reorder QF13 ahead of palette/SVG/numbering
  and flip `OBEY_QF13=True` so the brain is fully obeyed end-to-end.
- **Sprint 3 — Semantic unification.** Retire the Phase-7 classifier; feed QF7's `semantic_class`
  (and a QF7-derived `group_id`) into the product JSON so there is one semantic source.
- **Sprint 4 — Single-pass + performance.** Compute split selection → split once → run QF7→QF13→QF14
  once (kill the double cascade); vectorise per-region reductions; pass `labels` in memory; drop dev
  overlays from release bundles.
- **Sprint 5 — Fidelity.** With the brain fully wired, enforce `protect_highlight` and widen QF12
  selection beyond the narrow gate to close the #1 visual gap (palette/gradient flattening).

---

## 12. Files Changed

**New — `backend/core/`**
- `__init__.py`, `gradient_common.py` (shared math + `legacy_split_selected`),
  `artistic_decisions.py` (QF13 read bridge).

**Modified**
- `backend/services/artistic_decision_engine.py` (QF13) — publishes `split_selected`, `label`, and
  the ordered `split_plan`; documented as the single authority.
- `backend/services/intelligent_split_engine.py` (QF12) — consumes QF13 `split_plan`; imports math
  from `core`; QF9 fallback.
- `backend/services/gradient_preservation_engine.py` (QF10), `flow_gradient_engine.py` (QF11) —
  helper-only; re-export from `core.gradient_common`.
- `backend/services/palette_grouper.py`, `svg_generator.py`, `label_optimizer.py` — QF13 read seam
  (`OBEY_QF13=False`, inert).
- `backend/pipeline.py` — passes the QF13 report path to QF12; docstrings updated.

---

*End of Sprint 1 report. Architecture consolidated; QF13 is the single decision authority; every
product and export artifact is byte-identical to the pre-sprint baseline.*
