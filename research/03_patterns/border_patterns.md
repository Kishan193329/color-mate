# Border Patterns — Day 1

Patterns distilled from the first 25 border observations
([`../02_observations/border_observations.csv`](../02_observations/border_observations.csv)).
Each pattern clusters multiple observations into one recurring, engine-actionable
truth. These feed the border engineering standard
([`../04_engineering_standards/CFES-100-border-engineering.md`](../04_engineering_standards/CFES-100-border-engineering.md)).

---

## P-B1 — The outermost loop must never open

**From:** BORD-001, BORD-002, BORD-003
**Pattern:** The single largest boundary — the subject silhouette — is the most
important border on the page, yet it is the one most likely to fail (hair wisps,
low-contrast fur-on-background, soft shadow mistaken for the edge).
**Consequence if ignored:** background floods the subject; the whole conversion
is unusable.
**Engine implication:** identify the outermost contour explicitly, protect it
from deletion, and force-close it before anything else.

## P-B2 — Many borders are tonal, not drawn

**From:** BORD-004, BORD-012, BORD-002
**Pattern:** Critical separations (face/neck, lip/skin, fur/background) are
defined by soft tone or color change rather than a hard line, so pure edge
detection under-detects them.
**Engine implication:** borders must be recoverable from **tonal-plane
separation**, not edges alone, then promoted to closed strokes.

## P-B3 — Fringe and fur edges must be simplified outside, preserved inside

**From:** BORD-006, BORD-007, BORD-022, BORD-008
**Pattern:** Hair/fur has two different edge jobs at once — a **fuzzy outer
silhouette** that must become one smooth closed curve, and **meaningful interior
strands/stripes** that must stay sharp and separate.
**Engine implication:** smoothing strength must be **spatially varied** — strong
on the outer silhouette, gentle on the interior.

## P-B4 — Eyes and small features are nested closed rings

**From:** BORD-009, BORD-010, BORD-011, BORD-013
**Pattern:** Eyes, nostrils, and catchlights are small, high-value nested closed
shapes whose closure is fragile but whose loss destroys the face.
**Engine implication:** protect small high-contrast closed shapes; bridge tiny
feature-line gaps; keep catchlight/nostril/pupil as their own regions when above
the minimum paintable area.

## P-B5 — Near-miss gaps are the primary leak source

**From:** BORD-018, BORD-005, BORD-014, BORD-019
**Pattern:** Most leaks come from borders that _almost_ close — a 1–2px gap, a
thin frame that drops out, an unfinished T-junction or occlusion cut.
**Engine implication:** a dedicated **gap-close / endpoint-bridge / thin-loop-
thicken** pass must run before flood fill and must be measured (fragments before
vs after).

## P-B6 — Organic curves must stay curved

**From:** BORD-015, BORD-016, BORD-017, BORD-021
**Pattern:** Faces, ears, and petals are smooth arcs; naive polygon
simplification makes them angular and off-model, and raw thresholding leaves
stair-stepping.
**Engine implication:** contour simplification must preserve curvature (curve
fitting / limited corner insertion), and edges must be anti-aliased/sub-pixel
before smoothing.

## P-B7 — Texture is noise until proven a border (closure-weighted)

**From:** BORD-023, BORD-024, BORD-025, BORD-003
**Pattern:** Skin grain, fur micro-texture, and JPEG blocks are non-borders and
must be suppressed — but some **real** borders are faint and look like noise.
The deciding factor is not contrast alone; it is whether the edge is **long,
directional, and helps close a large region**.
**Engine implication:** noise removal must be **closure-weighted** — never delete
an edge that a large region depends on, even if faint.

## P-B8 — Occlusion and junctions define separation

**From:** BORD-020, BORD-005
**Pattern:** Where shapes overlap or meet (object-over-object, handle-to-body),
the border must both **close the front shape** and **cut the back shape** at the
junction.
**Engine implication:** occluding/junction contours get border priority so
neither meeting region merges.

---

## Cross-cutting themes

1. **Closure over everything.** Every pattern ultimately protects a closed loop.
2. **Importance is not just contrast.** Length, direction, and closure
   contribution decide keep-vs-drop.
3. **One global setting can't win.** Smoothing and noise thresholds must vary by
   location (outer silhouette vs interior detail).
4. **Small high-value shapes are fragile and precious.** Eyes, nostrils,
   catchlights need explicit protection.

## Open questions for algorithm stage (`06_algorithms/`)

- How to robustly identify "the outermost contour" on busy/complex subjects?
- What metric operationalizes "closure-weighted keep" for faint edges?
- How to vary smoothing strength between silhouette and interior automatically?
- Minimum paintable area vs. keeping catchlights/nostrils — one threshold or
  per-feature?
