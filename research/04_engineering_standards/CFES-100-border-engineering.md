# CFES-100 — Border Engineering Standard

**ColorForge Engineering Standard**
**Number:** CFES-100
**Area:** Borders / Line Art
**Status:** Draft (Day 1)
**Governs:** the line-art / border layer that all region detection depends on
**Source evidence:** [`../02_observations/border_observations.csv`](../02_observations/border_observations.csv),
[`../03_patterns/border_patterns.md`](../03_patterns/border_patterns.md)
**Parent rules:** [`../../COLORFORGE_QUALITY_RULEBOOK.md`](../../COLORFORGE_QUALITY_RULEBOOK.md) §1, §7

> This is a specification of **what the border layer must guarantee**, not an
> implementation. Algorithms that satisfy it are proposed later in `06_algorithms/`.

---

## 1. Scope & intent

The border layer converts an original image into a set of **closed, smooth,
faithful** line-art boundaries that region detection can flood-fill without
leaks — while preserving the original shape and identity. Borders are the source
of truth; nothing downstream can compensate for a bad border, so this standard is
Fix-Priority item #1.

## 2. Definitions

- **Silhouette / outer contour** — the single largest boundary loop enclosing the
  subject.
- **Inner border** — any boundary that separates two fill areas inside the
  silhouette.
- **Feature shape** — a small high-contrast closed shape (eye ring, pupil,
  catchlight, nostril, nose pad).
- **Closure** — a boundary forms a continuous loop with no gap.
- **Closure-weight** of an edge — how much a large region depends on that edge to
  stay closed (long, region-completing edges have high closure-weight).

## 3. Normative requirements

Keywords **MUST / SHOULD / MUST NOT** are normative.

### 3.1 Closure (P-B1, P-B4, P-B5)
- CFES-100-R1: Every fillable region **MUST** be bounded by a closed loop before
  flood fill.
- CFES-100-R2: The outermost contour **MUST** be identified and force-closed
  before region detection, and **MUST NOT** be deleted by any noise-removal step.
- CFES-100-R3: Gaps between stroke endpoints below the **gap radius**
  (see §4 `GAP_RADIUS`) **MUST** be bridged prior to flood fill.
- CFES-100-R4: Thin structural loops (e.g. glasses frames) that would drop out
  **MUST** be thickened enough to guarantee closure, **without** changing the
  shape they enclose.

### 3.2 Smoothness (P-B3, P-B6)
- CFES-100-R5: Curved and diagonal borders **MUST NOT** exhibit visible pixel
  stair-stepping at export resolution.
- CFES-100-R6: Long organic contours (faces, ears, petals) **MUST** preserve
  curvature continuity; simplification **MUST NOT** introduce corners above the
  **corner-angle threshold**.
- CFES-100-R7: Smoothing strength **MUST** be spatially varied — stronger on the
  outer silhouette than on interior strokes (fur/hair strands).

### 3.3 Fidelity / preservation (P-B2, P-B4, P-B8, Rulebook §1.6)
- CFES-100-R8: Structural outlines (silhouette, feature boundaries, object
  separations, stripes/bands) **MUST** survive cleanup.
- CFES-100-R9: Feature shapes above the **minimum feature area** **MUST** remain
  their own closed shapes (pupil, catchlight, nostril, nose pad).
- CFES-100-R10: Borders defined by tone/color change (not a drawn line)
  **SHOULD** be recovered via tonal-plane separation and then closed.
- CFES-100-R11: Occluding and junction contours **MUST** both close the front
  shape and cut the back shape so neither region merges.

### 3.4 Noise control (P-B7)
- CFES-100-R12: Low-contrast, short, non-closing edges (grain, micro-texture,
  compression blocks) **SHOULD** be suppressed before detection.
- CFES-100-R13: Noise removal **MUST** be closure-weighted: an edge that a large
  region depends on for closure **MUST NOT** be removed, even if faint.
- CFES-100-R14: Interior fur/hair micro-texture **SHOULD** be reduced to only
  long, directional, higher-contrast strokes; isolated flecks **SHOULD** be
  dropped.

## 4. Parameters (to be tuned; per line-art mode)

| Name | Meaning | Notes |
| --- | --- | --- |
| `GAP_RADIUS` | Max endpoint gap bridged during closure | scales with image size + mode |
| `MIN_FEATURE_AREA` | Smallest kept feature shape (eye/nostril/catchlight) | may differ from region min-area |
| `CORNER_ANGLE_THRESHOLD` | Max corner allowed when simplifying organic curves | guards P-B6 |
| `SILHOUETTE_SMOOTH` / `INTERIOR_SMOOTH` | Smoothing strengths (outer vs inner) | R7: outer > inner |
| `NOISE_MIN_LENGTH` / `NOISE_MIN_CONTRAST` | Below both ⇒ candidate noise | subject to closure-weight override (R13) |
| `THIN_LOOP_MIN_WIDTH` | Below ⇒ thicken to ensure closure | guards R4 |

Values are intentionally unset in Day 1; they belong to the algorithm + validation
stage. Each line-art mode (Simple Cartoon, Detailed Artwork, Mandala/Pattern,
Animal Fur, Portrait, Object/Product) provides its own values.

## 5. Acceptance criteria (validation hooks → `07_validation/`)

A border layer **passes** CFES-100 only if, for the reference set:
1. **Zero leaks** — flood fill never crosses between intended regions.
2. **Outer closed** — the silhouette is a single closed loop for every image.
3. **Features intact** — all feature shapes above `MIN_FEATURE_AREA` are present
   and closed (measured on eye/nose reference crops).
4. **No stair-stepping** — curvature error along sampled organic contours is
   below threshold.
5. **Noise controlled** — region/fragment count is within the expected band (no
   speckle explosion) **and** no required structural edge was deleted.

## 6. Non-goals

- Not defined here: region color sampling, palette grouping, number placement,
  SVG serialization details (covered by later CFES standards and Fix-Priority
  items 3–6).
- No implementation, no code changes. This document is spec-only.

## 7. Traceability

| Requirement | Backed by observations |
| --- | --- |
| R1–R4 (closure) | BORD-001,002,005,014,018,019 |
| R5–R7 (smoothness) | BORD-015,016,017,021,022,006,007 |
| R8–R11 (fidelity) | BORD-004,008,009,010,011,012,013,020 |
| R12–R14 (noise) | BORD-003,023,024,025 |

---

_Related next step (per Rulebook): **Quality Fix 3 — Perfect Border and Shape
Engine** will implement against CFES-100 R1–R7._
