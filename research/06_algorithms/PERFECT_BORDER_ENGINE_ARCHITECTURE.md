# Perfect Border & Shape Engine (PBSE) — Architecture

**Quality Fix 3 — architecture only.** This document defines the *shape* of the
engine: its pipeline stages, each stage's responsibility, its input/output
contracts, how it is validated, how it fails, what it depends on, and which
research observations it satisfies.

**No implementation. No pseudo-code. No algorithms.** Where a stage names a
measurement (edge confidence, coherence, distance transform, closure-weight),
that is a **data contract**, not an algorithm choice — the method that produces
it is decided later in the algorithm-selection phase.

**Sources of truth**
- [`../../COLORFORGE_QUALITY_RULEBOOK.md`](../../COLORFORGE_QUALITY_RULEBOOK.md) — §1 Border rules, §7 Fix Priority.
- [`../02_observations/border_engineering_analysis.md`](../02_observations/border_engineering_analysis.md) — BORD-001…025 deep analysis.
- [`../03_patterns/border_patterns.md`](../03_patterns/border_patterns.md) — patterns P-B1…P-B8.
- [`../04_engineering_standards/CFES-100-border-engineering.md`](../04_engineering_standards/CFES-100-border-engineering.md) — normative border standard.

**Scope.** PBSE owns Fix-Priority items **1 (Border)** and **2 (Shape)** only. It
produces a **closed, smooth, faithful border layer** and hands it to the existing
region-detection stage. It does **not** do color sampling, palette grouping,
number placement, or region SVG export (Fix-Priority 3–6, downstream).

---

## 1. Design principles (from the research)

The three reverse-engineered truths drive the whole architecture:

1. **Closure is topology, not pixels.** The central artifact is a **Border Graph**
   (nodes = endpoints/junctions, edges = strokes, faces = regions). Correctness is
   defined on faces, not on gradients.
2. **Keep-vs-drop is multi-factor.** No single contrast threshold. Every edge
   carries attributes (length, contrast, coherence, closure-weight, feature-tag);
   selection is a scored decision with a topological override.
3. **Treatment is spatially varying.** Smoothing and selection are modulated by
   control fields (distance-to-silhouette, orientation coherence), never one global
   setting.

**Standing invariants** (must hold at every stage):
- **I1 — Original is immutable.** The original upload is never edited; PBSE works on
  a derived working raster. (Rulebook Core Principle.)
- **I2 — Line art is the source of truth.** Regions are never derived from color
  quantization; PBSE only shapes the border layer.
- **I3 — Structure is protected.** A stroke tagged structural/feature or with
  positive closure-weight can never be deleted by a noise/simplification stage.
- **I4 — Every stage is measurable.** Each stage emits metrics into a Validation
  Report; the final gate is objective.

---

## 2. Data artifacts (contracts between stages)

| Artifact | Description |
| --- | --- |
| **Working Image** | Immutable original + derived working raster (bounded size). |
| **Field Stack** | Per-pixel fields: edge-confidence, luminance+chroma contrast, orientation+coherence, distance-to-silhouette, local entropy. |
| **Silhouette Model** | The outer closed boundary, figure/ground assignment, and occlusion (T-junction) relationships. |
| **Candidate Border Set** | Traced edge segments, each with attributes: length, contrast, coherence, closure-weight, feature-tag, stroke-width. |
| **Protected Feature Set** | Must-keep closed shapes (eyes, pupil, nostrils, nose-pad, thin structural loops, pattern bands). |
| **Border Graph** | Planar subdivision: nodes (endpoints/junctions), edges (strokes), faces (regions). The topological core. |
| **Refined Border Layer** | Smoothed, anti-aliased, curvature-faithful border — raster + vector-ready contours. |
| **Validation Report** | Per-stage + final metrics: closure proof, leak test, feature-intact, curvature error, region-count band. |
| **Control / Mode Profile** | Parameters supplied by the active line-art mode (Cartoon / Detailed / Mandala / Fur / Portrait / Product). |

---

## 3. Pipeline overview

```
Working Image
   │
   ▼
[S1] Field Extraction ─────────────► Field Stack
   │
   ▼
[S2] Silhouette & Figure-Ground ──► Silhouette Model
   │
   ▼
[S3] Structural Edge Selection ───► Candidate Border Set   (keep-vs-drop)
   │
   ▼
[S4] Feature Protection ──────────► Protected Feature Set
   │
   ▼
[S5] Topological Closure ─────────► Border Graph (closed faces)
   │
   ▼
[S6] Spatially-Varying Refinement ► Refined Border Layer (raster)
   │
   ▼
[S7] Curvature-Preserving Geometry ► Refined Border Layer (vector-ready)
   │
   ▼
[S8] Validation & Handoff ────────► Validated border → region detection
```

Cross-cutting: **Control/Mode Layer** feeds every stage; **Telemetry** collects
metrics for the Validation Report and the existing line-art quality score.

---

## 4. Stages

Each stage below specifies: **Purpose · Input · Output · Validation · Failure
conditions · Dependencies · Observations solved.**

### S1 — Field Extraction (measurement substrate)

- **Purpose:** Build the shared per-pixel measurement fields every later decision
  reads. This is the substrate that makes "multi-factor, spatially-varying"
  possible.
- **Input:** Working Image (+ Control/Mode Profile).
- **Output:** Field Stack — edge-confidence, luminance & chroma contrast,
  orientation + coherence, distance-to-silhouette (seeded later, refined here),
  local entropy.
- **Validation:** Fields are dense, finite, and normalized; edge-confidence
  correlates with hand-marked edges on the reference set; chroma field responds
  where luminance does not (lip/skin crops).
- **Failure conditions:** Field collapses to noise on low-contrast inputs; chroma
  channel unavailable (grayscale) with no fallback; fields disagree wildly with
  perceived structure.
- **Dependencies:** Working Image; Invariant I1.
- **Observations solved (enables):** foundational for all; directly underpins
  **BORD-002, BORD-003, BORD-012, BORD-023, BORD-024, BORD-025** (contrast/chroma/
  entropy/edge-confidence) and **BORD-006, BORD-008, BORD-022, BORD-024**
  (coherence).

### S2 — Silhouette & Figure-Ground Resolution

- **Purpose:** Identify the single outer boundary of each subject, resolve
  figure/ground and occlusion order, and distinguish the true object edge from soft
  shadow. Establishes the highest-priority closure and the distance-to-silhouette
  control field.
- **Input:** Field Stack.
- **Output:** Silhouette Model — outer closed boundary, figure/ground labels,
  occlusion (T-junction) relationships; finalized distance-to-silhouette field.
- **Validation:** Exactly one outer loop per subject; background-seeded fill stays
  outside; object edge sits on the step (not the shadow ramp); occlusion order
  self-consistent.
- **Failure conditions:** Multiple competing outer loops; silhouette placed on
  shadow halo; low-contrast side left open; conflicting occlusion cues unresolved.
- **Dependencies:** S1.
- **Observations solved:** **BORD-001** (outer loop), **BORD-002** (low-contrast
  silhouette), **BORD-003** (edge vs shadow), **BORD-007** (fur envelope silhouette),
  **BORD-020** (occlusion figure-ground).

### S3 — Structural Edge Selection (keep-vs-drop)

- **Purpose:** Decide which interior edges are structural borders and which are
  texture noise, using the multi-factor rule with a topological override. Promotes
  tonal/chroma borders that pure edges miss; collapses fringe/texture fields.
- **Input:** Field Stack; Silhouette Model.
- **Output:** Candidate Border Set — surviving edge segments with attributes
  (length, contrast, coherence, closure-weight, feature-tag, stroke-width).
- **Validation:** Region/fragment count within the mode's expected band; no
  structural (region-completing) edge dropped (closure-weight audit); tonal/chroma
  borders present where measurable planes exist.
- **Failure conditions:** Texture survives → region explosion; a faint structural
  edge deleted → latent leak (I3 violation); soft real border under-promoted → flat
  output.
- **Dependencies:** S1, S2.
- **Observations solved:** **BORD-004** (soft tonal border), **BORD-006** (fringe
  collapse), **BORD-012** (chroma lip border), **BORD-023** (skin grain),
  **BORD-024** (fur micro-texture), **BORD-025** (faint-real-edge / closure override).

### S4 — Feature Protection

- **Purpose:** Detect and lock small high-value closed shapes and thin structural
  loops so no later stage can smooth, merge, or delete them. Encodes "identity-
  defining" structure.
- **Input:** Field Stack; Silhouette Model; Candidate Border Set.
- **Output:** Protected Feature Set — eyes/rings, pupil, nostrils, nose-pad, thin
  loops (frames), pattern bands (stripes); each tagged must-keep.
- **Validation:** All features above min-feature-area present and tagged; nested eye
  rings ordered; pattern bands retained; thin loops flagged for closure in S5.
- **Failure conditions:** Feature missed (merged into neighbor); false feature
  (noise promoted); feature tag lost downstream (I3 breach).
- **Dependencies:** S1, S2, S3.
- **Observations solved:** **BORD-008** (stripes), **BORD-009** (eye rings +
  catchlight), **BORD-010** (crease as feature line), **BORD-011** (pupil),
  **BORD-013** (nostrils), **BORD-014** (nose-pad), **BORD-019** (thin structural
  loops — detection).

### S5 — Topological Closure

- **Purpose:** Guarantee every intended region is a closed face of the Border Graph:
  bridge near-miss gaps, resolve T-junctions (continue occluder / cut occluded),
  thicken sub-threshold structural loops to closure, seal the silhouette and feature
  contours. This is where "closure is topology" becomes concrete.
- **Input:** Candidate Border Set; Protected Feature Set; Silhouette Model.
- **Output:** Border Graph with all intended faces closed.
- **Validation:** Zero flood-fill leaks on references; endpoint/open-face count → 0
  for intended regions; no unintended fusions; enclosed shapes unchanged by
  thickening; fragments-before/after recorded.
- **Failure conditions:** Under-closure (residual leak); over-closure (separate
  regions fused, or a genuine opening wrongly sealed); junction mis-resolution
  (occluder/occluded swapped); thickening distorts enclosed geometry.
- **Dependencies:** S2, S3, S4.
- **Observations solved:** **BORD-001** (seal silhouette), **BORD-002** (bridge weak
  stretch), **BORD-005** (T-junction join), **BORD-010** (crease closure),
  **BORD-014** (nose-pad closure), **BORD-018** (near-miss gaps), **BORD-019**
  (thin-loop thickening), **BORD-020** (occlusion continue/cut).

### S6 — Spatially-Varying Shape Refinement

- **Purpose:** Clean the border's raster form without flattening meaning: strong
  smoothing on the outer silhouette, gentle on high-coherence interior strands;
  remove aliasing/stair-stepping. Uses the control fields from S1/S2.
- **Input:** Border Graph; Field Stack (distance-to-silhouette, coherence).
- **Output:** Refined Border Layer (raster) — closure preserved.
- **Validation:** Outer boundary low pixel-scale curvature variance; interior strand
  density/coherence preserved (measured separately in the two zones); no new gaps
  introduced (closure re-checked).
- **Failure conditions:** Global over-smoothing erases strands (flat); under-
  smoothing leaves jagged outline; smoothing reopens a closed face; anti-alias
  blurs a real sharp feature.
- **Dependencies:** S5 (must operate on a closed graph); S1.
- **Observations solved:** **BORD-006** (fringe smoothing), **BORD-007** (envelope
  vs strands), **BORD-021** (anti-alias stair-stepping), **BORD-022** (silhouette vs
  interior).

### S7 — Curvature-Preserving Geometry (vector-ready)

- **Purpose:** Convert cleaned borders into faithful geometry: keep organic arcs
  curved, preserve genuine corners/tips, resolve petal-style overlaps as smooth
  curves — producing vector-ready contours without faceting.
- **Input:** Refined Border Layer (raster); Protected Feature Set (corner/tip tags).
- **Output:** Refined Border Layer (vector-ready contours) with curvature metadata.
- **Validation:** Curvature error along sampled organic arcs below threshold; no
  corners inserted on smooth spans; genuine corners/tips preserved (angle within
  tolerance); feature geometry (pupil, tip) faithful.
- **Failure conditions:** Over-simplification (polygonal face/jaw); under-
  simplification (wobble/noise retained); a real corner smoothed or a smooth arc
  cornered; overlap junction mis-ordered.
- **Dependencies:** S6; S4 (feature/corner tags).
- **Observations solved:** **BORD-011** (preserve pupil geometry), **BORD-015**
  (cheek/jaw arcs), **BORD-016** (ear sweep + tip), **BORD-017** (petal curves +
  overlap), **BORD-021** (smooth curves for export).

### S8 — Validation & Handoff (quality gate)

- **Purpose:** Prove the border layer meets CFES-100 before anything downstream runs;
  emit metrics; block or flag on failure. The objective gate for Fix-Priority 1–2.
- **Input:** Border Graph; Refined Border Layer; per-stage telemetry.
- **Output:** Validated border layer + Validation Report → region detection; pass/
  fail/needs-correction verdict.
- **Validation (acceptance for the whole engine):**
  1. **Closure** — every intended region is a closed face; zero leaks.
  2. **Silhouette** — exactly one outer loop per subject.
  3. **Features intact** — all protected features present and closed.
  4. **Shape fidelity** — curvature error and stair-stepping below thresholds.
  5. **Noise control** — region/fragment count within the mode's band, with no
     structural edge deleted.
- **Failure conditions:** Any acceptance check fails → the layer is not handed off
  clean; the report names the failing check and (later) routes to manual correction
  tools. A silent pass with a hidden leak is the worst failure and is explicitly
  guarded by the closure test.
- **Dependencies:** S1–S7 (consumes all).
- **Observations solved (verifies):** acceptance for **all BORD-001…025**; directly
  gates **BORD-001/005/018** (leaks), **BORD-009/011/013/014** (features),
  **BORD-021** (stair-steps), **BORD-006/023/024** (region-count band).

---

## 5. Cross-cutting concerns

### Control / Mode Layer
Each line-art mode (Simple Cartoon, Detailed Artwork, Mandala/Pattern, Animal Fur,
Portrait, Object/Product) supplies a **Control/Mode Profile** that tunes stage
behavior (gap radius, min-feature-area, silhouette-vs-interior smoothing balance,
noise gates, curvature tolerance). Stages read parameters; they do not hard-code
them. This satisfies the "spatially varying / mode-aware" truth at the config level.

### Telemetry & Quality Score
Every stage writes metrics (fragments before/after, leak count, feature count,
curvature error, region-count) into the Validation Report, which also feeds the
existing line-art quality score so the engine's health is observable per job.

### Original-Preservation & Correction Integration
Invariant I1 holds throughout; the immutable original is retained for downstream
color sampling. When S8 flags a failure, the output is compatible with the existing
**manual correction tools** (draw/erase line, close gap, merge/split) and the
**version/undo** system — i.e., PBSE degrades to semi-automatic, per the Rulebook's
"commercial output will need semi-automatic cleanup."

---

## 6. Ordering rationale (why this sequence)

The stage order is the Rulebook Fix-Priority Order specialized to Border+Shape:

- **Measure (S1) → locate the master boundary (S2) → decide what is real (S3) →
  protect what matters (S4) → guarantee closure (S5) → clean the form (S6) → make
  it geometry (S7) → prove it (S8).**

Dependencies are strictly forward: closure (S5) must precede shape refinement (S6)
so smoothing can never reopen a face; feature protection (S4) precedes closure and
refinement so features are locked before any destructive step; geometry (S7) is last
because it assumes a clean, closed raster. Nothing downstream (color, number, export)
begins until S8 passes — a perfect color pass is worthless on an open border.

---

## 7. Observation → Stage traceability

| Observation | Primary stage(s) | Verified by |
| --- | --- | --- |
| BORD-001 outer loop | S2, S5 | S8 (leak) |
| BORD-002 low-contrast silhouette | S2, S5 | S8 (leak) |
| BORD-003 edge vs shadow | S2 | S8 (silhouette) |
| BORD-004 soft tonal border | S3 | S8 (noise/flat) |
| BORD-005 T-junction join | S5 | S8 (leak) |
| BORD-006 fringe | S3, S6 | S8 (region band) |
| BORD-007 fur envelope | S2, S6 | S8 (leak/shape) |
| BORD-008 stripes | S4 | S8 (features) |
| BORD-009 eye rings | S4 | S8 (features) |
| BORD-010 eyelid crease | S4, S5 | S8 (features/leak) |
| BORD-011 pupil | S4, S7 | S8 (features/shape) |
| BORD-012 lip chroma border | S3 | S8 (noise/flat) |
| BORD-013 nostrils | S4 | S8 (features) |
| BORD-014 nose-pad | S4, S5 | S8 (features/leak) |
| BORD-015 cheek/jaw arcs | S7 | S8 (shape) |
| BORD-016 ear sweep + tip | S7 | S8 (shape) |
| BORD-017 petal curves + overlap | S7 | S8 (shape) |
| BORD-018 near-miss gaps | S5 | S8 (leak) |
| BORD-019 thin loops | S4, S5 | S8 (features/leak) |
| BORD-020 occlusion | S2, S5 | S8 (leak) |
| BORD-021 stair-stepping | S6, S7 | S8 (shape) |
| BORD-022 silhouette vs interior | S6 | S8 (shape) |
| BORD-023 skin grain | S3 | S8 (region band) |
| BORD-024 fur micro-texture | S3 | S8 (region band) |
| BORD-025 faint real edge | S3 | S8 (leak/closure) |

**Coverage:** all 25 observations map to ≥1 stage and are verified at S8.

---

## 8. Non-goals (explicit)

- No color sampling, palette grouping, number placement, or region SVG export —
  those are Fix-Priority 3–6 and downstream engines.
- No algorithm/method selection, no parameter values, no code — those belong to the
  algorithm-design phase that follows this architecture.
- No dependence on RunPod/SAM2 for regions; PBSE is local and deterministic, and AI
  may only *improve inputs* to it (per existing AI-assist rules), never create
  regions.

---

_Next research step: for each stage S1–S8, evaluate candidate algorithms against its
I/O contract and CFES-100 acceptance criteria (in `06_algorithms/`), still without
touching engine code._
