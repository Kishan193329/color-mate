# ColorForge Quality Rulebook — v1

The official rules for ColorForge image conversion. Every stage of the engine —
line art, region detection, color assignment, numbering, and export — must obey
these rules. When two rules conflict, resolve them using the **Fix Priority
Order** in Section 7.

---

## Core Principle

**The original image must NOT be redesigned.**

ColorForge converts existing artwork into closed, fillable color-by-number
regions while **preserving the original shape, shading, highlight, texture, and
visual identity**. The engine is a _translator_, not an _illustrator_:

- It may simplify, close, and smooth — never reinterpret or restyle.
- Line art is the source of truth for region boundaries.
- Regions come from closed line art, never from color quantization.
- A correct result is recognizably the same picture, just paint-by-number.

---

## 1. Border / Line Art Rules

The line art defines every region boundary, so it must be trustworthy.

1. **Closed borders.** Every outline that should enclose a fill area must form a
   continuous loop. No boundary may dead-end inside a fillable area.
2. **Smooth curves.** Curved edges must read as smooth arcs, not stair-stepped
   pixels. Straighten only what was straight in the original.
3. **No broken gaps.** Small breaks between strokes must be detected and closed
   before region detection. A single-pixel gap is a defect.
4. **No jagged edges.** Remove aliasing/jitter along strokes; the silhouette
   must be clean at the export resolution.
5. **Variable line thickness allowed.** Thickness may vary to reflect the
   original weighting (bold silhouette, finer interior). Uniformity is not
   required; legibility and closure are.
6. **Preserve important outlines.** Structural edges — silhouettes, feature
   boundaries, object separations — must survive cleanup at all costs.
7. **Ignore useless texture noise.** Sensor grain, compression artifacts, and
   fine surface micro-texture that do not represent a real edge must be
   suppressed so they never become strokes or regions.

**Balance rule:** when unsure whether an edge is "important outline" or "useless
noise," weigh it by _length + contrast + closure contribution_. Long,
high-contrast, region-closing edges are important; short, low-contrast, isolated
speckles are noise.

---

## 2. Region Rules

Regions are the fill areas a user paints. They must be clean and safe.

1. **Every fill area must be closed.** A region is valid only if its boundary is
   a closed loop (outer contour, plus holes as inner loops).
2. **No leaks.** Fill must never bleed from one region into another. If a fill
   would leak, the border is broken — fix the border, do not patch the fill.
3. **No random tiny fragments.** Sliver and speck regions created by noise or
   anti-aliasing must be removed or merged.
4. **Important shadows/highlights stay separate.** A meaningful shadow, highlight,
   or tonal plane that defines form must remain its own region, not be flattened
   into its neighbor.
5. **Unimportant noise merges.** Tiny, low-contrast fragments that carry no
   visual meaning merge into the nearest large region (never into an unrelated
   region across a real boundary).

**Separate-vs-merge test:** keep a region separate if it is (a) above the minimum
paintable area, AND (b) visually distinct from its neighbor (tone / color / role).
Otherwise merge it into the neighbor it shares the most border with.

---

## 3. Color Fidelity Rules

Color must stay true to the source. ColorForge samples color; it does not
recolor.

1. **Preserve original colors.** Each region's target color is sampled from the
   original image (median inside the region). Never invent or shift colors.
2. **Preserve gradients as stepped regions.** A smooth gradient becomes an
   ordered series of stepped color regions that follow the gradient direction —
   not a single flat blob.
3. **Do not flatten body / fur / skin.** Large organic areas keep their internal
   tonal variation as multiple regions. A face is not one skin color; fur is not
   one brown.
4. **Do not force a low color count.** The palette size follows the artwork.
   Never cap colors to hit a target number if that destroys fidelity.
5. **Merge only visually identical colors.** Group two regions' colors only when
   they are perceptually the same (small LAB/ΔE distance). Similar-but-distinct
   tones stay distinct.

**Fidelity guard:** grouping is a labeling convenience for numbering, never a
segmentation method. If grouping would visibly change the picture, don't group.

---

## 4. Fur / Hair Rules

Fur and hair are the hardest case: high-frequency detail that must read as flow,
not noise.

1. **Preserve main flow.** The dominant direction of strands/locks must be
   visible in the region layout.
2. **Preserve stripes / bands.** Distinct markings (tiger stripes, hair
   highlights, color bands) stay as separate regions.
3. **Remove micro noise.** Single-strand speckle and grain that don't define
   form are suppressed.
4. **Smooth the outer silhouette.** The outer edge of the fur/hair mass is a
   clean, closed, smooth boundary — even while the interior stays detailed.
5. **Keep meaningful internal strands.** Long, coherent internal strokes that
   convey volume and direction are preserved; short incoherent ones are not.

**Fur test:** a strand is meaningful if it is long, directional, and consistent
with the local flow. Isolated dots and 1–2px flecks are not.

---

## 5. Number Placement Rules

Numbers guide painting and must never fight the art.

1. **Number inside the region.** Placed at the region's largest inscribed point
   (distance-transform center), guaranteed within the fill area.
2. **Readable size.** Sized to the region; large enough to read, scaled down for
   small regions.
3. **No border overlap.** The number must not touch or cross a region boundary
   or sit on a stroke.
4. **Hide only if too small.** If a region cannot fit a legible number without
   overlap, hide the number (the region is still fillable via its color/id).

---

## 6. Quality Pass Rules

Every conversion is graded. The output **FAILS** the quality pass if any of the
following are true:

- ❌ **Borders open** — any fillable area has a broken/leaking boundary.
- ❌ **Important details lost** — a structural outline, feature, or meaningful
  shadow/highlight was removed.
- ❌ **Output looks flat** — gradients/body/fur/skin were flattened into single
  blobs; the picture lost its form.
- ❌ **Color fidelity low** — sampled colors drift from the original, or distinct
  tones were merged.

A pass requires: all regions closed, no leaks, important detail intact, gradients
stepped (not flattened), and colors faithful to the source.

---

## 7. Fix Priority Order

When correcting a conversion (automatic or manual), fix in this order. Do not
work on a lower item while a higher item is failing — later stages depend on
earlier ones.

1. **Border** — close gaps, remove noise strokes, ensure continuous loops.
2. **Shape** — smooth curves and silhouettes without distorting the form.
3. **Region Split** — separate meaningful shadows/highlights; merge noise.
4. **Color Fidelity** — sample true colors; step gradients; group only identical.
5. **Number Placement** — position, size, and visibility of numbers.
6. **SVG / Export** — closed paths, clean geometry, faithful final files.

Rationale: a perfect color pass is worthless on an open border; a perfect number
is worthless in a leaking region. Boundaries first, always.

---

## Next Implementation

The next implementation should be:

### **Quality Fix 3 — Perfect Border and Shape Engine**

Focus: guarantee **closed, smooth, leak-free borders and faithful shapes** before
any downstream color/number/export work — directly satisfying Fix Priority items
**1 (Border)** and **2 (Shape)**, which every other rule depends on.

_Rulebook version: v1_
