# ColorForge AI — Engine Architecture Audit (QF3 → QF14)

**Reviewers (composite):** Senior Computer Vision Engineer · Senior Graphics Engineer · Professional Illustrator · Software Architect · Commercial Product Reviewer
**Date:** 2026-07-06
**Scope:** Quality Fix 3 through Quality Fix 14 and the surrounding pipeline. Analysis only — no code was modified.

> **Note on the README/docs:** `README.md`, `docs/architecture.md`, and `docs/roadmap.md` still describe the "Sprint 1 mock" skeleton. They are **stale**. The real engine (QF3–QF14) is a working local computer-vision pipeline, not mock output. This review audits the *actual* code, not the docs.

---

## 1. Current Architecture Diagram

```
UPLOAD
  │
  ▼
┌──────────────────────────── pipeline._process ────────────────────────────┐
│ Step 1  Preprocess ................ image_preprocess                        │
│ Step 2  Line art .................. advanced_lineart_generator             │
│            (edge_cleaner, stroke_normalizer, line_thickener,               │
│             line_gap_detector, line_gap_repair)                            │
│            └─ lineart_quality_score  → lineart_quality.json                │
│                                                                            │
│ Step 3  Close gaps / borders ...... region_cleanup.remove_noise           │
│            border_engine  (QF3 PBSE) ─────────────► border_quality_report │
│              ├─ region_leak_validator  (QF4) ─────► leak_map.png          │
│              └─ region_split_detector  (QF5/QF6) ─► merged_region_map.png │
│                                                     split_preview_map.png │
│                                                                            │
│ Step 4  Regions ................... region_detector (+ tiny_region_merger) │
│            ├─ mask_generator → region_mask.png                             │
│            ├─ svg_generator  → outline.svg                                 │
│            ├─ color_sampler / palette_grouper                              │
│            └─ region_hierarchy.apply_all  (Phase-7 semantics, PARALLEL)    │
│                 (semantic_classifier + semantic_rules + object_detector)   │
│                                                                            │
│ Step 5  Numbers/preview ........... label_optimizer, json_generator,       │
│            preview_renderer, texture_preview_renderer, game_preview        │
│                                                                            │
│ Step 6  Export .................... quality_analyzer, json_generator,      │
│            zip_exporter                                                     │
│                                                                            │
│   ── ANALYSIS / BRAIN CASCADE (additive, best-effort, try/except) ──       │
│   QF7  semantic_region_analyzer ──► semantic_region_report.json           │
│   QF8  material_behavior_engine ──► material_behavior_report.json         │
│   QF9  processing_policy_engine ──► processing_policy_report.json         │
│   QF13 artistic_decision_engine ──► artistic_decision_report.json         │
└────────────────────────────────────────────────────────────────────────────┘
  │
  ▼  (pipeline.run, AFTER _process)
QF12 intelligent_split_engine  ── policy-gated pixel change ──► labels.npy
        └─ if changed → rebuild_from_labels()  → re-runs Step4-6 + QF7/8/9/13
  │
  ▼
QF14 contour_character_engine  ── rewrites contour geometry in
        outline.svg / regions.json / game_config.json
  │
  ▼
FINISH

  (QF10 gradient_preservation_engine + QF11 flow_gradient_engine:
   run() bodies are NEVER CALLED — imported only as helper libraries by QF12.)
```

**Report/data-flow dependency chain**

```
border_quality(QF3) ┐
semantic(QF7) ──► material(QF8) ──► policy(QF9) ──► artistic_decision(QF13) ──► QF14 contours
     │                   │              │                                       ▲
     └───────────────────┴──────────────┴──► QF12 splitter (reads QF7/8/9, NOT QF13)
```

---

## 2. Per-Quality-Fix Audit

### QF3 — Perfect Border & Shape Engine (`border_engine.py`)
- **Purpose:** Validate and repair the line-art border layer: measure edge fields, bridge 1–2 px endpoint gaps, anti-alias/refine shape, emit `border_quality_report.json`. Operates only on the line layer; line art stays the source of truth.
- **Strength:** Correct, conservative design. Endpoint-pairing bridge (each endpoint used once) avoids false merges. Zhang–Suen thinning + structural-only jaggedness metric is principled. The single genuine gatekeeper for downstream region quality.
- **Weakness:** Pure-Python thinning loop (`while changed`, up to 60 passes over the full frame) is the slowest CV step. `final_pass` requires `repaired > 0` for `closure_pass`, so a *perfectly closed* line art (nothing to repair) reports `closure_pass = False` — a semantically wrong verdict.
- **Future dependency:** Owns QF4 + QF5/QF6 as sub-calls; feeds `border_quality_report` to QF13/QF14 (baseline metrics only).
- **Simplify?** Replace hand-rolled thinning with `cv2.ximgproc.thinning` (opencv-contrib) or a distance-transform skeleton. Fix the `closure_pass` logic.
- **Duplicates?** No. Sole border authority.
- **Stay?** **Yes — core.**
- **Move?** No, but it should be renamed/reframed as the "line-layer engine" (it hosts QF4–QF6, which are not really "border" concerns).

### QF4 — Region-Aware Leak Validator (`region_leak_validator.py`)
- **Purpose:** Upgrade leak detection from proximity heuristics to a real flood-fill escape test (before vs after repair). Emits `leak_map.png` + leak fields.
- **Strength:** Honest and near false-positive-free — a truly closed line art partitions the canvas, so containment testing is sound. Distance-transform seeding guarantees interior seeds.
- **Weakness:** O(regions) `cv2.floodFill` calls, each on a full-frame copy — expensive on region-dense images. `ESCAPE_TOL = 1.5×` area is a magic constant; a legitimately concave region that shares a wall could exceed it. Purely diagnostic — nothing acts on `leaking_region_ids`.
- **Future dependency:** Consumed by QF3's `leak_pass`; leak counts surface in QF13's `border_baseline`.
- **Simplify?** The per-region flood loop could be replaced by a single connected-components pass comparing pre/post component counts for most cases.
- **Duplicates?** Partial conceptual overlap with QF5 (both hunt missed borders) but the mechanisms are complementary (escape vs internal evidence).
- **Stay?** Yes, as validation.
- **Move?** Fine inside QF3; logically a "validation" concern that could live in `quality_analyzer`.

### QF5 — Missing Split Detection (`region_split_detector.py`)
- **Purpose:** Find *fully-merged* regions a flood-fill cannot see (two intended areas with no dividing line), using multi-signal evidence from the original image (area outlier, Lab variance, internal edge span, compactness, contrast). Detection only.
- **Strength:** Good "require multiple signals to agree" gating reduces false positives. Produces actionable `split_recommendations` with a suggested split line.
- **Weakness:** Recomputes Canny/Lab/gray of the original that QF7 also computes minutes later — duplicated feature extraction. Six magic thresholds tuned to one image class (cats/animals). Detection is **never consumed** by an actuator — no engine reads `split_recommendations`. QF12 splits on *policy*, not on QF5's findings.
- **Future dependency:** None downstream — dead-ends into a report/overlay.
- **Simplify?** Share a single precomputed feature bundle with QF7.
- **Duplicates?** Feature extraction duplicates QF7; intent overlaps QF12 (both decide "this region should be split") but they never talk.
- **Stay?** Yes as advisory, **but wire it to QF12 or drop the overlap.**
- **Move?** Merge feature-extraction with QF7's understanding layer.

### QF6 — Advisory Split Preview (`region_split_detector.py`, same module)
- **Purpose:** Render `split_preview_map.png` — suspects tinted + a bold suggested split guide over the original.
- **Strength:** Cheap; good human-review artifact.
- **Weakness:** It is not a separate module — it's ~15 lines inside QF5's `detect()`. Calling it a distinct "Quality Fix" overstates it. Pure visualization.
- **Future dependency:** None.
- **Simplify?** It's already trivial. It's a rendering tail of QF5.
- **Duplicates?** It *is* QF5.
- **Stay?** As a debug overlay only.
- **Move?** Not a standalone concern; keep folded in QF5.

### QF7 — Semantic Region Analysis (`semantic_region_analyzer.py`)
- **Purpose:** Classify each region into 15 material/object classes and measure the visual properties (texture, gradient, edge-flow, structure tensor, orientation, color variance) every later decision depends on. The **understanding layer**. Analysis only.
- **Strength:** This is the keystone of the brain. Rich, reusable per-region feature vector computed once. Clean "no side effects" contract.
- **Weakness:** Classifier is a hand-tuned heuristic waterfall — brittle, demonstrably wrong on real content (the code itself documents "green cat eyes classed as LEAF," which QF13 then has to *undo* via adjacency). Confidence values are hardcoded guesses. Per-region Python loop with full-image `crop()` + boolean-mask indexing → O(R) with large constants.
- **Future dependency:** **Everything.** QF8, QF9, QF10-12, QF13, QF14 all key off `semantic_region_report.json`.
- **Simplify?** Precompute all per-pixel feature maps once (already mostly done) and vectorize the per-region reductions with `np.add.at`/`bincount` instead of a Python loop.
- **Duplicates?** **Yes — collides with the Phase-7 `region_hierarchy` / `semantic_classifier` stack**, which is a *second, independent* semantic classifier running in Step 4 with a different taxonomy. Two brains that disagree.
- **Stay?** **Yes — foundational.**
- **Move?** No, but it should absorb/replace the Phase-7 classifier so there is one semantic source of truth.

### QF8 — Material Behavior Engine (`material_behavior_engine.py`)
- **Purpose:** Turn "what it is" (QF7) into "how it should behave": per-class behavior tokens + preservation scores (flow/gradient/texture/noise) + future strategies. Reuses QF7, no recompute.
- **Strength:** Genuinely additive and cheap (pure JSON→JSON transform). Clean reuse contract. Well-factored `PROFILES` table.
- **Weakness:** Almost entirely a **static lookup table** keyed on class, lightly modulated by three QF7 numbers. Very little computation for a whole "engine"/report/overlay. The overlay adds cost with low value.
- **Future dependency:** Feeds QF9 and QF13.
- **Simplify?** **Yes — QF8 could be a function inside QF9**, or the `PROFILES` table could be data consumed directly by QF9. It doesn't earn a separate disk report.
- **Duplicates?** Strong overlap with QF9 (both map class→policy). The QF8→QF9 boundary is arbitrary.
- **Stay?** As logic yes; as a **separate report/overlay, no.**
- **Move?** **Merge into QF9.**

### QF9 — Processing Policy Engine (`processing_policy_engine.py`)
- **Purpose:** Convert QF7 + QF8 into concrete per-region policy: preserve flags, max simplification, split direction/strength, palette/SVG/numbering policies, validation priority. The **policy layer** and the actual gate for QF10-12.
- **Strength:** This is where the real decisions live — `future_split_strength`, `policy_class`, `SELECT_POLICY` drive every pixel change. Clear, tunable constant tables.
- **Weakness:** Depends on QF8's report existing (extra disk round-trip) when QF8 is mostly a table it could read directly. Split-strength formula is opaque (`cvar/25 * (0.5 + 0.5*max(flow,grad))`) with no validation that the resulting threshold matches perceptual need.
- **Future dependency:** The selection gate for QF10/11/12; feeds QF13.
- **Simplify?** Fold QF8 in; drop the intermediate report.
- **Duplicates?** Overlaps QF8 (class→behavior) and QF13 (policy→permission).
- **Stay?** **Yes — this is the operative policy engine.**
- **Move?** Absorb QF8.

### QF10 — Gradient Preservation Engine (`gradient_preservation_engine.py`)
- **Purpose:** First pixel-changing fix — split policy-selected regions into luminance bands to preserve shading.
- **Strength:** Introduced the reusable primitives now used by everything downstream: `_mean_fill`, `_delta_e`, `_boundaries`, `SELECT_POLICY`, band constants. Sound ΔE-improvement metric.
- **Weakness:** **Its `run()` is DEAD CODE — never called by the live pipeline.** Superseded by QF12. Luminance-only banding is isotropic/blobby (its own docstring admits this). It survives only as an imported constant/helper library.
- **Future dependency:** Only as a *helper module* for QF11/QF12.
- **Simplify?** Extract the shared helpers into a neutral `gradient_common.py`; delete the dead `run()`/preview.
- **Duplicates?** Fully superseded by QF12.
- **Stay?** **Helpers yes, engine no.** Misleading to leave a full "engine" whose entry point never runs.
- **Move?** Promote its helpers to a shared module; retire the rest.

### QF11 — Flow-Aware Gradient Engine (`flow_gradient_engine.py`)
- **Purpose:** Make QF10's bands follow material flow (fur/vein/fold) via oriented blur of band boundaries; adds `ssim` + `_oriented_blur`.
- **Strength:** The flow-alignment idea is the right instinct for fur/hair; `ssim` and `_oriented_blur` are genuinely reused by QF12.
- **Weakness:** **Same as QF10 — `run()` is DEAD CODE, never called.** Only its helpers are imported by QF12. Still luminance-based banding underneath.
- **Future dependency:** Helper source (`_oriented_blur`, `ssim`, `MIN_COHERENCE`, `BLUR_LEN`) for QF12.
- **Simplify?** Same treatment as QF10 — helpers to shared module, delete dead engine body.
- **Duplicates?** Superseded by QF12.
- **Stay?** **Helpers yes, engine no.**
- **Move?** Consolidate helpers.

### QF12 — Intelligent Region Split Engine (`intelligent_split_engine.py`)
- **Purpose:** The **only live pixel-changing splitter.** Splits policy-selected high-gradient regions by full-Lab k-means (separates stripe/shadow/highlight/base by real color), keeps coherent components, flow-aligns boundaries, protects small/fragile regions. Returns new labels; pipeline rebuilds from them.
- **Strength:** Correct core insight — k-means minimizes within-cluster variance, exactly what flat-fill ΔE measures, so colors get measurably closer. Good guardrails (`MIN_REGION_AREA`, `MIN_CLUSTER`, `NEW_LABELS_CAP`, material k-caps, `PROTECT_CLASSES`). Deterministic. Reuses QF10/11 helpers instead of copying.
- **Weakness:** **Does not read QF13** (`artistic_decision_report`) — so the "central artist brain's" `split_permission` / `split_style` are ignored by the actual splitter. It re-derives selection from QF9 policy directly. When it changes labels it triggers `rebuild_from_labels`, which **re-runs QF7/8/9/13 a second time** — expensive. k-means over region pixels + per-region full-frame `new_labels == lbl` scans are the heaviest runtime cost.
- **Future dependency:** Produces the final region geometry that QF13/QF14 then annotate/re-style.
- **Simplify?** Read QF13 as the selection authority (see §7). Avoid the double brain-cascade recompute.
- **Duplicates?** Obsoletes QF10 + QF11 engines (keeps their helpers).
- **Stay?** **Yes — this is the real fidelity engine.**
- **Move?** No. But it should sit *after* and *obey* QF13, not run before it.

### QF13 — Artistic Decision Engine (`artistic_decision_engine.py`)
- **Purpose:** The "central artist brain" — fuse QF7/8/9 (+QF11/12/3) into one per-region decision record: artistic role, visual importance, protect flags, and what future engines are *permitted* to do. Adds boundary adjacency + union-find "embedded-in-subject" detection to override upstream misclassification.
- **Strength:** The most sophisticated and genuinely valuable module. The adjacency/enclosure reasoning (cream chest mislabeled CLOTH, green eyes as LEAF → reclaimed as SUBJECT) is real illustrator-grade logic. Rich, auditable output (rejection reasons, commercial review).
- **Weakness:** **It is a brain whose orders are largely unheeded.** Its `split_permission`, `split_style`, `palette_merge_permission`, `svg_precision`, `numbering_sensitivity` are consumed by **no one except QF14** (and QF14 only reads role/importance/protect_shape). The splitter (QF12), palette grouper, SVG generator, and numbering **do not read it.** So ~60% of the report is decorative. Also the heaviest new computation (union-find over all region pairs, `_fill_lines` 8-iteration full-frame dilation, `_edge_lengths`) — and it runs **twice** when QF12 splits.
- **Future dependency:** Read only by QF14.
- **Simplify?** Either wire its permissions into QF12/palette/SVG/numbering (making it the true authority) or trim it to the fields QF14 actually uses.
- **Duplicates?** Overlaps QF9 (both emit palette/svg/numbering policy + validation priority). Two policy layers.
- **Stay?** **Yes — but it must be connected to the actuators to justify its cost.**
- **Move?** It should become the *single* policy authority QF12+ obey, replacing QF9's parallel policy fields.

### QF14 — Artistic Contour Character Engine (`contour_character_engine.py`)
- **Purpose:** Give each contour its own drawing style — organic Bézier silhouettes, crisp polyline eyes, tapered leaf/whisker tips — and re-emit `outline.svg` + region `path` strings. Runs last; touches only vector geometry (no labels/palette/numbers/color/export), so ΔE/SSIM are unchanged by construction.
- **Strength:** The most "illustrator-authentic" idea in the stack: variable contour treatment is exactly what separates a template from hand-drawn art. Catmull-Rom→Bézier with corner cusps is the right tool. Clean before/after jaggedness + IoU metrics. Idempotent, regenerates from labels.
- **Weakness:** Geometry is derived from **raster label masks**, so its fidelity is capped by region-detection quality — smoothing a jagged mask yields a smooth-but-wrong curve. Re-rasterizes every contour twice (before/after) for metrics — costly. Runs **twice** in the initial job (once inside `rebuild_from_labels` if QF12 split, once in `run()`). Only reads a thin slice of QF13.
- **Future dependency:** Terminal — nothing consumes its output but the frontend/SVG.
- **Simplify?** Skip the before/after rasterization metric in production (dev-only). De-duplicate the double invocation.
- **Duplicates?** Overlaps `svg_generator` + `contour_smoothing` (both emit contour paths). QF14 effectively replaces `svg_generator`'s output on the final pass.
- **Stay?** **Yes — high visual value.**
- **Move?** It should *be* the SVG generator (fold `svg_generator`/`contour_smoothing` into it) rather than overwriting their output afterward.

---

## 3. Architecture Review — Answers

**1. Is the current pipeline correct?**
Directionally yes, and impressively disciplined about "line art is the source of truth" and "analysis layers never block export." But it is **not clean**: two dead engines (QF10/QF11), two parallel semantic classifiers (QF7 vs Phase-7 `region_hierarchy`), a brain (QF13) that the actuators ignore, and a double execution of the whole QF7→QF13 cascade + QF14 whenever QF12 splits. It *works*, but it carries redundant passes and unwired decisions.

**2. Should any Quality Fix merge together?**
- **QF8 → QF9** (behavior is a table QF9 can own).
- **QF9 policy fields → QF13** (collapse two policy layers into one authority).
- **QF5 feature extraction → QF7** (stop recomputing Canny/Lab/gray).
- **QF10 + QF11 → a shared `gradient_common` helper module** (they are no longer engines).
- **QF14 + `svg_generator` + `contour_smoothing`** into one contour/SVG emitter.
- **QF7 + Phase-7 `region_hierarchy`** into one semantic source of truth.

**3. Should any Quality Fix split into multiple modules?**
No — the trend is over-fragmentation, not under. The only justified extraction is pulling the shared math helpers out of QF10 into a neutral module so QF11/QF12 don't import a "dead engine."

**4. Which reports are no longer necessary (in the shipped bundle)?**
All **overlays** are developer diagnostics, not product: `semantic_overlay`, `material_behavior_overlay`, `processing_policy_overlay`, `artistic_decision_overlay`, `flow_gradient_overlay`, `intelligent_split_overlay`, `contour_character_overlay`, `leak_map`, `merged_region_map`, `split_preview_map`, `gradient_preview_map`. `material_behavior_report` is redundant if QF8 merges into QF9.

**5. Which reports should become cache files (regenerated, not shipped)?**
The **intermediate brain reports**: `semantic_region_report`, `material_behavior_report`, `processing_policy_report`, `artistic_decision_report`, `flow_gradient_report`, `intelligent_split_report`, `border_quality_report`, `lineart_quality`. They are engine-to-engine plumbing; keep them in the job folder as cache but exclude from `result.zip`.

**6. Which reports should become permanent knowledge (product artifacts)?**
`regions.json`, `palette.json`, `game_config.json`, `outline.svg`, `region_mask.png`, `preview.png`, `result.zip`, and a single user-facing `quality_report.json` summary. These are the deliverables the game/print/editor actually consume.

**7. Is the Brain architecture correct?**
The **layering is correct** (understand → behavior → policy → decision → act), but the **wiring is broken**: QF13 is billed as the authority that "future engines obey," yet QF12 (the only actuator) never reads it, and neither do palette/SVG/numbering. Today the *real* brain is **QF9**, and QF13 is an elaborate annotation consumed almost entirely by QF14. Either promote QF13 to the true gate or demote it to QF14's pre-processor.

**8. Are responsibilities clean?**
Partially. Clean: QF7 (features), QF12 (split), QF14 (contours). Muddy: QF8/QF9/QF13 all emit palette/SVG/numbering policy (three-way overlap); QF4/QF5/QF6 are three "fixes" inside two functions of QF3's subtree; QF3 owns non-border concerns (leak, split detection).

**9. Any hidden technical debt?**
- **Dead engines:** QF10 and QF11 `run()` never execute.
- **Double compute:** QF7→QF13 cascade + QF14 re-run on every QF12 split and on every region edit (`rebuild_from_labels`).
- **Unconsumed outputs:** QF5 recommendations, most of QF13's permission fields.
- **Two semantic classifiers** with divergent taxonomies (QF7 vs `semantic_classifier`).
- **Legacy modules** likely superseded: `lineart_generator` (vs `advanced_lineart_generator`), `gap_closer`/`gap_repair` (vs `line_gap_*`).
- **Stale docs** describing a mock pipeline that no longer exists.
- **`closure_pass` logic bug** in QF3 (perfect line art fails the check).

**10. Any future performance bottlenecks?**
- Per-region **full-frame boolean scans** (`labels == lbl`, `new_labels == lbl`, `overlay[labels == lbl]`) repeated in QF7, QF8, QF9, QF12, QF13, QF14 → effectively **O(R · H · W)** per engine, ~6× over the stack.
- QF3 pure-Python Zhang–Suen thinning (up to 60 full-frame passes).
- QF4 per-region `floodFill` on full-frame copies.
- QF12 k-means per selected region.
- The whole cascade runs **twice** on any split/edit.
On a large, region-dense image these compound into multi-second CPU time with no GPU path.

**11. Any unnecessary computations?**
- QF5 recomputes Canny/Lab/gray that QF7 also computes.
- Every engine renders a debug overlay PNG even in production.
- QF14 rasterizes all contours twice for a metric that's dev-only.
- QF13 union-find + `_fill_lines` recomputed on the second cascade pass.
- QF8 is a near-static table dressed as a compute stage.

**12. Can the pipeline become shorter?**
Yes — from 12 "fixes" to ~7 real stages: **line layer (QF3+4+5)** → **features (QF7, one classifier)** → **policy/decision (QF8+QF9+QF13 fused)** → **split (QF12)** → **contours/SVG (QF14+svg_generator)** → **numbers/palette/export**. That removes 2 dead engines, 1 duplicate classifier, and 2 redundant report layers.

**13. Can memory usage improve?**
Yes. `labels.npy` is reloaded and full-copied by nearly every engine; pass the array in memory instead of round-tripping via disk + JSON. Overlays allocate a full HxWx3 canvas per engine. Precompute one shared feature bundle instead of per-engine gradient/Lab/edge maps.

**14. Can processing speed improve?**
Yes, substantially: (a) vectorize per-region reductions with `bincount`/`np.add.at` (already done in `region_detector` — apply the same pattern to QF7/8/9/13); (b) run the brain cascade **once** (compute QF12 selection first, split, then run QF7→QF13→QF14 a single time); (c) drop overlays and dev-only metrics in production; (d) use `cv2.ximgproc.thinning`; (e) keep intermediates in memory.

**15. Commercial readiness score**

| Dimension | Score /10 | Note |
|---|---|---|
| Output quality (color-by-number) | 7 | Real, coherent results; fidelity engine works |
| Illustrator authenticity | 6 | QF13/QF14 ideas are strong but under-wired |
| Architecture cleanliness | 5 | Dead code, double compute, unwired brain |
| Performance / scalability | 4 | CPU-only, O(R·H·W) loops, single-process FS store |
| Robustness | 7 | Best-effort try/except keeps export alive |
| Maintainability | 5 | 12 overlapping "fixes," stale docs |
| **Overall** | **≈ 5.5 / 10** | **Strong MVP engine; not yet production-hardened.** |

Ship-blocking before commercial launch: consolidate the brain wiring, remove dead engines, single-pass the cascade, add a multi-worker job store, exclude debug overlays from bundles, refresh docs.

---

## 4. Visual Quality — Biggest Remaining Differences vs. the Original Illustration

Ranked by how much each still separates the output from a hand-painted reference:

1. **Palette / color grouping** — flat mean-color fills + Lab grouping still flatten smooth gradients everywhere *except* the handful of QF12-selected regions. The single largest "it looks like paint-by-number, not painting" gap.
2. **Fur / flow texture** — QF12 k-means recovers major stripe/shadow bands, but fine directional fur strokes are averaged away; only policy-selected large regions get flow treatment.
3. **Face volume / soft shading** — cheeks, muzzle, and body rounding read flat; banding is coarse (≤5 clusters) and only where selected.
4. **Highlights / catch-lights** — small specular/eye highlights are below `MIN_CLUSTER`/`MIN_REGION_AREA` and get folded into the base; QF13 flags "protect_highlight" but no engine enforces it.
5. **Shadows / occlusion depth** — soft cast shadows survive only if they trip the split-strength gate; otherwise merged.
6. **Contour character (QF14)** — improves silhouette elegance, but curves derive from raster masks, so line weight is uniform and not variable-width like ink.
7. **SVG precision** — good adaptively, yet ultimately bounded by region-detection accuracy upstream.

*(Order will shift by subject: portraits push Face Volume/Highlights up; foliage pushes Leaf/Vein up. The palette gap is #1 across all subjects.)*

---

## 5. Strengths (Architecture-wide)

- **Disciplined invariant:** line art is the source of truth; analysis layers are strictly additive and wrapped in try/except so a failing "fix" never breaks the export.
- **Genuine reuse:** QF8→QF9→QF13 recompute nothing; QF12 imports QF10/11 helpers rather than copying.
- **Sound core algorithms:** flood-fill leak test (QF4), Lab k-means fidelity split (QF12), adaptive Bézier contours (QF14), enclosure-based subject reclamation (QF13) are all the *right* techniques.
- **Auditability:** every stage emits a structured report; overlays make the pipeline inspectable.
- **Deterministic & local:** no mandatory ML/cloud dependency; reproducible.

## 6. Weaknesses (Architecture-wide)

- Two dead engines (QF10/QF11), a duplicate semantic classifier, and an unwired brain (QF13).
- The full understanding→decision cascade and QF14 execute twice per split/edit.
- Six engines each perform O(R) full-frame boolean scans; no GPU path.
- Debug overlays and dev-only metrics run in production and ship in bundles.
- Responsibility overlap across QF8/QF9/QF13 (policy) and QF3/QF4/QF5/QF6 (line layer).
- Stale README/docs misrepresent the system as mock.

## 7. Risk Analysis

| Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|
| Performance collapse on large/region-dense images | High | High | Vectorize per-region reductions; single-pass cascade; in-memory labels |
| QF13 "brain" drift — its rules diverge from what QF12 actually does | High | High | Make QF12 read QF13 as selection authority |
| Two semantic classifiers give contradictory labels | Medium | High | Unify on QF7 |
| Single-process filesystem job store corrupts under concurrency | High | Medium | Move to Redis/DB + object storage (already on roadmap) |
| QF3 `closure_pass` false-negative misreports quality | Low | High | Fix the boolean condition |
| Dead QF10/QF11 mislead future maintainers | Medium | High | Extract helpers, delete dead `run()` |
| Heuristic classifier misfires on non-animal subjects | Medium | Medium | Confidence-gated fallback / eventual learned model |

## 8. Technical Debt Register

1. **Dead code:** `gradient_preservation_engine.run`, `flow_gradient_engine.run` never called.
2. **Double execution:** QF7→QF13 + QF14 re-run in `rebuild_from_labels` after QF12, then QF14 again in `run()`.
3. **Unwired outputs:** QF5 `split_recommendations`; QF13 `split_permission`/`split_style`/`palette_merge_permission`/`svg_precision`/`numbering_sensitivity`.
4. **Duplicate subsystems:** QF7 vs Phase-7 `region_hierarchy`/`semantic_classifier`; QF14 vs `svg_generator`/`contour_smoothing`; legacy `lineart_generator`/`gap_closer`/`gap_repair`.
5. **Disk-as-bus:** every engine reloads `labels.npy` + parses JSON reports instead of passing objects in memory.
6. **Production noise:** overlays + before/after rasterization run and ship in release builds.
7. **Logic bug:** QF3 `closure_pass` requires `repaired > 0`.
8. **Stale docs:** README/architecture/roadmap describe a mock pipeline.

## 9. Recommended Roadmap

**Phase A — Cleanup (no behavior change, low risk)**
- Delete dead `run()` bodies in QF10/QF11; move shared constants/helpers to `gradient_common.py`.
- Fix QF3 `closure_pass`.
- Exclude all `*_overlay.png`, `leak_map`, `*_map`, and intermediate reports from `result.zip`; keep them as job-folder cache.
- Refresh README/architecture/roadmap to describe the real QF3–QF14 engine.

**Phase B — Consolidation (medium risk)**
- Fold QF8 into QF9; collapse QF9's palette/SVG/numbering fields into QF13 so there is one policy authority.
- Unify semantics: retire the Phase-7 classifier, keep QF7 as the single source; feed `group_id` from QF7 output.
- Merge QF14 with `svg_generator`/`contour_smoothing` into one contour emitter.
- Share one precomputed feature bundle between QF5 and QF7.

**Phase C — Correct the brain wiring (higher value)**
- Make **QF12 read QF13** as its split authority (`split_permission`/`split_style`), and have palette/SVG/numbering honor QF13 too — so the "artist brain" is actually obeyed.
- Re-order to a **single-pass** cascade: compute selection → split (QF12) once → run QF7→QF13→QF14 once. Eliminate the double recompute in `rebuild_from_labels`.

**Phase D — Performance & scale**
- Vectorize all per-region reductions (`bincount`/`np.add.at`); pass `labels` in memory.
- Optional GPU/vectorized path for k-means + thinning.
- Multi-worker-safe job store (Redis/Postgres) + object storage (already roadmapped).

**Phase E — Fidelity (the visual gap)**
- Attack the **palette/gradient** gap first (rank #1): extend QF12 selection beyond the narrow policy gate, and enforce QF13's `protect_highlight` so catch-lights survive.
- Then fur-flow and face-volume shading.

---

*End of audit. No source files were modified; this document is the sole deliverable.*
