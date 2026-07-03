# Border Engineering — Deep Analysis (Reverse Engineering)

Deep, multi-lens analysis of the 25 border observations
([`border_observations.csv`](border_observations.csv)). We are building
**scientific understanding**, not software. Every observation is examined through
four lenses:

- 🔬 **Computer Vision Researcher** — what is measurable in the pixels.
- 🎮 **Graphics / Game-Engine Engineer** — closure, tap accuracy, SVG, print.
- 🖌️ **Illustrator** — intent and the manual decision a professional makes.
- 📐 **Topology / Geometry** — the mathematical concept that fits.

**No implementation is proposed.** Mathematical models name *concepts* (Bezier,
Distance Transform, Graph Cut, Medial Axis, Morphology, Topology…), never code.
Governed by [`../../COLORFORGE_QUALITY_RULEBOOK.md`](../../COLORFORGE_QUALITY_RULEBOOK.md).

Each observation answers ten questions:
**1** Observation · **2** Visual Purpose (illustrator) · **3** Engineering Purpose
(CBN engine) · **4** If Ignored (defects) · **5** Human Decision · **6** Computer
Vision Decision · **7** Mathematical Model · **8** Acceptance Criteria
(measurable) · **9** Failure Examples · **10** Research Notes.

---

## Measurable properties glossary (reference)

| Property | Metric | Meaning |
| --- | --- | --- |
| Edge strength | `‖∇I‖` (Sobel); Canny hysteresis | sharpness of tonal change |
| Local contrast | Weber `ΔI/I`, Michelson, or window σ; chroma ΔE in Lab | is the edge visible |
| Contour continuity | #components, #endpoints (neighbour-count=1), gap `d` | how closed a boundary is |
| Curvature | Menger `κ=4·Area/(‖a‖‖b‖‖c‖)`, or `dθ/ds` | how sharply it bends |
| Corner angle | `θ=arccos(u·v/‖u‖‖v‖)` | true corner vs smooth arc |
| Border length | arc length `L=Σ‖pᵢ₊₁−pᵢ‖` | structural vs noise |
| Edge density | `ρ=#edgepx/area` | texture vs clean line |
| Orientation coherence | structure tensor `c=(λ₁−λ₂)/(λ₁+λ₂)` | flow/strands vs grain |
| Distance transform (DT) | dist to nearest boundary | inscribed radius, stroke width ≈2·DT |
| Skeleton / medial axis | thinning ridge; junctions = neighbour-count ≥3 | topology of strokes |
| Connected components | region/label counting | fragmentation, leaks |
| Shape complexity | compactness `P²/4πA`; #vertices | organic vs noise |
| Local entropy | `−Σp log p` in a window | busyness / texture |
| Closure-weight | Δ(#open faces) if edge removed | how much a region needs the edge |

---

## Per-observation deep analysis

### BORD-001 — Outer head/hair silhouette must be one closed loop (portrait)

1. **Observation:** The outermost boundary of the head + hair mass must read as a
   single continuous closed loop even though flyaway wisps repeatedly puncture it.
2. **Visual Purpose:** Illustrators commit to one confident silhouette to establish
   the subject as a solid, readable shape; individual wisps are *suggested* inside
   the mass, never drawn onto the contour.
3. **Engineering Purpose:** The outer loop is the seam between the two largest
   fills (subject vs background). It is the highest-priority closure in the image.
4. **If Ignored:** Background flood-fill leaks into the face → one giant merged
   region; wisp fragments cause region explosion; the merged region is a huge, low-
   precision tap target (poor tap accuracy); ugly SVG with a ragged outer path.
5. **Human Decision:** The illustrator mentally low-passes the hair, tracing the
   envelope and ignoring strands that cross it — an intuitive scale-space decision.
6. **Computer Vision Decision:** `findContours(RETR_EXTERNAL)` → max-enclosed-area
   contour; endpoints on the outer band signal breaks; a coarse-scale envelope
   (large-σ smoothing / morphological closing) recovers the silhouette; a wisp is a
   high-κ spur enclosing negligible area.
7. **Mathematical Model:** Morphology (closing), Topology (exactly one closed
   face separating subject/background), Curvature analysis (spur pruning), Graph
   (endpoint bridging), Level-set / scale-space envelope.
8. **Acceptance Criteria:** Exactly one closed outer loop per subject; a
   background-seeded flood never enters the subject on the reference set; wisp-spur
   count below a defined band.
9. **Failure Examples:** Backlit flyaway hair (dozens of thin spurs); dark hair on
   a dark background (silhouette contrast → 0); fur collar merging with hair;
   over-closing swallows a real gap (ear/hair negative space).
10. **Research Notes:** What silhouette-scale σ generalises across hair types
    (straight/curly/afro)? Could image matting / alpha give a cleaner envelope than
    gradients? Relationship between "wisp area threshold" and image resolution.

### BORD-002 — Low-contrast silhouette vs similar-tone background (cat)

1. **Observation:** The body outline nearly vanishes where similar-tone fur meets a
   similar-tone background; edge strength drops far below the rest of the contour.
2. **Visual Purpose:** A human illustrator still draws the full outline because they
   perceive the *object*, not the local contrast — closure is a cognitive act.
3. **Engineering Purpose:** Guarantees the silhouette closes on its weakest side,
   which is exactly where automated pipelines fail.
4. **If Ignored:** A hole appears at the lowest-contrast arc → background leak on
   the "hardest" side; the defect is systematic, not random.
5. **Human Decision:** The illustrator uses global object knowledge ("a cat body is
   continuous here") to bridge what the eye barely sees.
6. **Computer Vision Decision:** Locally-adaptive hysteresis (lower `t_lo` along the
   outer band); bridge the weak stretch by extrapolating tangents from the flanking
   strong edges; optionally a chroma/texture cue where luminance fails.
7. **Mathematical Model:** Adaptive thresholding, Graph (shortest-path edge linking
   / snakes-active-contours energy), Topology (force closure), gradient-vector-flow.
8. **Acceptance Criteria:** Silhouette closed even on the min-contrast side; **and**
   near-zero phantom edges in the flat background (background edge-density ≈ 0).
9. **Failure Examples:** Grey cat on grey couch; white animal on snow; black subject
   on black; over-correction hallucinates edges into flat backgrounds.
10. **Research Notes:** Can a learned "objectness"/saliency prior safely lower the
    threshold only along true silhouettes? Trade-off curve: closure gain vs phantom-
    edge risk as `t_lo` drops.

### BORD-003 — Object edge vs soft drop shadow (product)

1. **Observation:** A soft drop shadow forms a luminance ramp whose mid-slope can
   rival the true object edge, tempting the border onto the shadow.
2. **Visual Purpose:** Illustrators draw the object outline crisply and render the
   shadow as a separate, softer shape — never confusing the two.
3. **Engineering Purpose:** Keeps the region boundary on the object so fills and tap
   targets match the real silhouette.
4. **If Ignored:** The region bloats outward into the shadow → haloed object, wrong
   silhouette, mismatched tap area, muddy print.
5. **Human Decision:** The illustrator reads the *step* (hard object edge) vs the
   *gradient* (shadow) and places ink only on the step.
6. **Computer Vision Decision:** A true edge is a narrow high-peak gradient (step); a
   shadow is a wide low-plateau gradient (ramp). Separate by gradient-profile width
   and Laplacian zero-crossing steepness.
7. **Mathematical Model:** Edge-profile / scale-space analysis, Laplacian-of-Gaussian
   zero-crossings, Curvature of the intensity profile (step vs ramp classification).
8. **Acceptance Criteria:** Border lies on the object contour (within tolerance of
   the hand-marked silhouette); shadow is outside or a separate low-priority region;
   no measurable outward bloat.
9. **Failure Examples:** Long soft shadow on glossy surfaces; gradient reflections;
   contact shadows fused with the object base.
10. **Research Notes:** Is profile-width alone robust across lighting, or is a
    shadow/illumination prior needed? How to treat *contact* shadows that truly
    touch the object base.

### BORD-004 — Face→neck soft-shadow inner boundary (portrait)

1. **Observation:** The face/neck separation is a broad soft tonal-plane change
   (jaw occlusion shadow), not a drawn line; peak gradient is low and spread out.
2. **Visual Purpose:** Illustrators imply the jaw with a soft shadow edge to convey
   3-D form without a hard outline that would look cartoonish.
3. **Engineering Purpose:** Keeps neck and face as distinct paintable tones so the
   lower face reads as form, not a flat blob.
4. **If Ignored:** Neck merges into face → flat, formless output ("looks flat"
   quality-pass fail).
5. **Human Decision:** The illustrator decides "these are two tonal planes" and
   draws the minimal boundary needed to separate them.
6. **Computer Vision Decision:** Tonal-plane segmentation (local mean difference /
   bilateral / superpixel boundary — as an *edge cue only*) promoted to a border
   when it separates two large, differently-toned regions, then closed.
7. **Mathematical Model:** Graph Cut / region-boundary energy, Morphology (close),
   scale-space (soft-edge detection), tonal-plane (piecewise-constant Mumford–Shah).
8. **Acceptance Criteria:** A closed inner border separates neck from face **iff** a
   measurable tonal plane exists (mean ΔL above threshold); not invented under flat
   lighting.
9. **Failure Examples:** Diffuse/flat lighting (no shadow to find); double chin folds
   (ambiguous multiple planes); strong side-light creating false extra planes.
10. **Research Notes:** How to rank soft candidate borders by "form importance"?
    Should this rely on a face-landmark prior for the jaw line?

### BORD-005 — Handle-to-body T-junction (product / mug)

1. **Observation:** Where the handle meets the body, three boundary branches meet;
   an incomplete connecting stroke leaves the two shapes sharing an opening.
2. **Visual Purpose:** Illustrators deliberately close the junction so the handle
   reads as attached-but-separate.
3. **Engineering Purpose:** Both shapes must be independently closed and fillable;
   junctions are where "two regions" is decided.
4. **If Ignored:** Fill crosses the join → handle + body become one region; colour
   numbering and tap targets both break.
5. **Human Decision:** The illustrator instinctively completes the little bridging
   strokes that seal each side of the join.
6. **Computer Vision Decision:** Junctions = skeleton pixels with ≥3 neighbours;
   verify each incident face closes (planar-graph Euler check); complete the missing
   shortest branch.
7. **Mathematical Model:** Topology (planar subdivision / Euler characteristic),
   Graph (junction resolution), Skeleton/Medial-axis analysis, Morphology.
8. **Acceptance Criteria:** Both meeting regions are closed; a flood seeded in one
   does not cross the junction into the other.
9. **Failure Examples:** Thin low-contrast join; clustered handles (basket); the
   join is genuinely open in the source art.
10. **Research Notes:** Distinguishing a *should-close* junction from an intentional
    opening (e.g., a spout). Junction confidence scoring.

### BORD-006 — Ragged hairline fringe (portrait)

1. **Observation:** The hairline is a dense comb of short strands — a fractal fringe
   between two large regions (hair, forehead), not a clean boundary.
2. **Visual Purpose:** Illustrators render a *few* representative strands and a
   single implied hairline, not every strand — economy for readability.
3. **Engineering Purpose:** One clean hair/skin boundary instead of dozens of tiny
   leaking slivers keeps region count sane.
4. **If Ignored:** Speckle/region explosion along the fringe; several slivers open →
   leaks; cluttered numbers; noisy SVG.
5. **Human Decision:** The illustrator picks the dominant hairline curve and a
   handful of hero strands, discarding the rest.
6. **Computer Vision Decision:** A band of high edge-density + high shape-complexity
   between two smooth regions ⇒ collapse to its medial spine; keep strands only if
   `length > L_strand ∧ contrast > C_strand`.
7. **Mathematical Model:** Medial Axis / skeletonisation (fringe spine), Morphology,
   Local entropy (fringe detection), Curvature-scale-space for hero-strand selection.
8. **Acceptance Criteria:** A single smooth hair/skin boundary; fringe region count
   below a band; zero open fringe slivers; number of retained strands bounded.
9. **Failure Examples:** Curly/afro hair (very deep fringe); fine baby hair (fringe
   vanishes); wispy updos with detached locks.
10. **Research Notes:** Optimal "hero strand" count for perceived fidelity vs
    simplicity. Can entropy alone localise the fringe band reliably?

### BORD-007 — Fuzzy outer fur silhouette (dog)

1. **Observation:** Guard hairs cross the silhouette, making the outer contour
   jagged at hair scale; the perceived silhouette is the hair envelope.
2. **Visual Purpose:** Illustrators draw a clean fur outline with a few directional
   ticks to *imply* fuzziness, not a literal hair-by-hair edge.
3. **Engineering Purpose:** A clean closed outer boundary that still lets interior
   fur read as fur.
4. **If Ignored:** Non-closing jagged outer border → background leak; or global
   over-smoothing kills the fur character.
5. **Human Decision:** The illustrator separates "outline job" (smooth envelope)
   from "texture job" (interior ticks) and treats them differently.
6. **Computer Vision Decision:** Multi-scale — coarse-σ silhouette for the envelope,
   fine-scale strands kept only interior; smoothing decays with distance from the
   silhouette.
7. **Mathematical Model:** Scale-space, Distance Transform (distance-to-silhouette
   field), anisotropic diffusion, Morphology (envelope closing).
8. **Acceptance Criteria:** Outer boundary closed and smooth (low pixel-scale
   curvature variance) while interior strand density/coherence is preserved.
9. **Failure Examples:** Long-haired breeds (huge fuzzy band); wet spiky fur; fur
   overlapping another object edge.
10. **Research Notes:** How to auto-set the silhouette band width per subject? Does
    anisotropic (along-flow) smoothing beat isotropic for the envelope?

### BORD-008 — Tiger stripe internal borders (tiger)

1. **Observation:** Stripes are high-contrast, elongated, hard-edged internal bands
   that define the animal's identity.
2. **Visual Purpose:** Illustrators emphasise stripes as the subject's signature —
   they are drawn boldly, never softened away.
3. **Engineering Purpose:** Stripes must survive as separate regions; they are
   protected structure, not texture.
4. **If Ignored:** Stripes blur/merge into base fur → flat orange animal; identity
   destroyed ("looks flat" fail); print looks wrong.
5. **Human Decision:** The illustrator prioritises the pattern, keeping every
   salient band even while simplifying surrounding fur.
6. **Computer Vision Decision:** Elongated high-contrast bands with strong
   orientation coherence and clear DT thickness ⇒ protect from smoothing/merge.
7. **Mathematical Model:** Structure tensor (coherence/orientation), Distance
   Transform (band thickness), Morphology (band-preserving), Topology (keep as
   separate faces).
8. **Acceptance Criteria:** All salient stripes retained as separate closed regions;
   base fur not fragmented by stripe micro-texture; pattern recognisable in output.
9. **Failure Examples:** Faded/low-contrast stripes; motion-blurred stripes; stripe
   tips tapering below min paintable width.
10. **Research Notes:** A saliency measure for "identity-defining" pattern vs
    incidental marking. Handling stripe tips below min-area gracefully.

### BORD-009 — Nested eye rings + catchlight (portrait)

1. **Observation:** The eye is concentric closed shapes at shrinking scale
   (eyelid→sclera→iris→pupil→catchlight); inner rings are small and fragile.
2. **Visual Purpose:** Illustrators lavish care on the eye — nested rings + a
   highlight create life and gaze; it is the emotional focus.
3. **Engineering Purpose:** Each ring must be a distinct closed fill; the eye is the
   highest-value small feature in a portrait.
4. **If Ignored:** Rings merge → dead/blank eye; the single most identity-destroying
   defect; tiny catchlight lost.
5. **Human Decision:** The illustrator draws the eye largest-in-attention, ensuring
   each ring closes and placing the catchlight precisely.
6. **Computer Vision Decision:** Within the eye ROI, keep the ordered set of closed
   near-circular contours whose areas form a decreasing nest; catchlight = tiny
   luminance-maximum blob kept if above min-feature-area.
7. **Mathematical Model:** Topology (nested closed faces / contour hierarchy),
   Hough/circle-fitting priors, Distance Transform (nested radii), Connected
   components.
8. **Acceptance Criteria:** Each eye ring present and closed; catchlight retained
   when above min-feature-area; gaze preserved on eye-crop references.
9. **Failure Examples:** Half-closed eyes; glasses glare over the eye; very small
   eyes in group photos; heavy shadow filling the socket.
10. **Research Notes:** Should eyes get a dedicated feature-aware pass with a
    landmark prior? Minimum resolution at which nested rings are recoverable.

### BORD-010 — Eyelid crease fine line (portrait)

1. **Observation:** The upper-lid crease is a faint, thin, low-contrast line that
   easily breaks into segments with 1–2px gaps.
2. **Visual Purpose:** A single delicate crease line conveys the lid fold without
   heavy ink — restraint is the intent.
3. **Engineering Purpose:** Keeps the eye socket enclosed so skin fill doesn't flood
   the eye.
4. **If Ignored:** Skin bleeds across the broken crease into the eye region → merged
   eye/skin.
5. **Human Decision:** The illustrator draws one continuous confident crease even
   where the reference is faint.
6. **Computer Vision Decision:** Ridge detection (Frangi-like) for thin low-contrast
   lines; endpoint pairing on feature lines within a small bridge radius.
7. **Mathematical Model:** Ridge/vesselness filters, Graph (endpoint bridging),
   Curvature-continuity for tangent-aligned linking.
8. **Acceptance Criteria:** Crease continuous where a ridge exists; eye socket stays
   enclosed (no eye/skin merge).
9. **Failure Examples:** Monolid / no visible crease; heavy makeup creating false
   double creases; deep-set eyes in shadow.
10. **Research Notes:** Feature-scoped gap radius vs global — do facial lines need a
    larger bridge tolerance than generic strokes?

### BORD-011 — Animal pupil shape (cat)

1. **Observation:** The pupil (slit or round) is a strong high-contrast closed shape
   — easy to detect, catastrophic to lose.
2. **Visual Purpose:** Illustrators render the pupil precisely; its shape (slit vs
   round) is species-defining and expressive.
3. **Engineering Purpose:** A robust, must-keep feature region that anchors the face.
4. **If Ignored:** The eye reads as a flat disc; expression and species cue lost.
5. **Human Decision:** The illustrator keeps the exact pupil silhouette even while
   simplifying the iris.
6. **Computer Vision Decision:** Highest-contrast small closed shape inside the eye
   ROI; eccentricity distinguishes slit vs round; low κ-tolerance during
   simplification to preserve geometry.
7. **Mathematical Model:** Connected components (max-contrast blob), Shape
   descriptors (eccentricity/Hu moments), Topology (keep as a face).
8. **Acceptance Criteria:** Pupil present, closed, and shape-faithful (eccentricity
   within tolerance of source).
9. **Failure Examples:** Tapetum glare (reflective eye); pupil merged with a dark
   iris; blown-out highlights erasing the pupil.
10. **Research Notes:** Preserving slit-pupil sharpness vs generic curve smoothing —
    per-feature simplification tolerance.

### BORD-012 — Lip boundary is chroma, not luminance (portrait)

1. **Observation:** Lip vs skin is largely a *colour* (chroma) change with weak
   luminance gradient; luminance-only edge detection under-responds.
2. **Visual Purpose:** Illustrators define lips by colour shift and a soft edge, not
   a hard black outline, for a natural look.
3. **Engineering Purpose:** Lips must become their own region with faithful colour.
4. **If Ignored:** Lips merge into skin → the mouth disappears; colour fidelity fails.
5. **Human Decision:** The illustrator perceives the colour change and outlines the
   lip form (cupid's bow) accordingly.
6. **Computer Vision Decision:** Add chroma-gradient (edges in Lab a*/b* or hue) to
   the edge cue; composite `g=√(‖∇L‖²+w‖∇(a,b)‖²)`; detect the lip border on `g`,
   then close.
7. **Mathematical Model:** Colour-space gradients (CIELAB ΔE), multi-channel edge
   fusion, Graph (closure), Curvature (smooth cupid's bow).
8. **Acceptance Criteria:** Closed lip region present when chroma separation (ΔE)
   exceeds threshold; sampled colour matches source.
9. **Failure Examples:** Pale/nude lips (ΔE→0); strong lipstick giving a hard false
   line vs. the natural soft edge; lip-liner artifacts.
10. **Research Notes:** Best colour space for skin/lip separation (Lab vs YCbCr vs
    learned)? Weight `w` between luminance and chroma gradients.

### BORD-013 — Nostril openings (portrait)

1. **Observation:** Nostrils are small dark high-contrast blobs easily merged with
   the surrounding nose shadow.
2. **Visual Purpose:** Illustrators keep the two nostril accents to punctuate and
   ground the nose form.
3. **Engineering Purpose:** Small must-keep features that stop the nose reading flat.
4. **If Ignored:** Nostrils dissolve into nose shadow → flat, ungrounded nose.
5. **Human Decision:** The illustrator marks the nostrils distinctly even when the
   surrounding shadow is dark.
6. **Computer Vision Decision:** Small dark closed components with high local
   contrast to their immediate neighbourhood, kept above min-feature-area.
7. **Mathematical Model:** Connected components, Local contrast, Morphology (prevent
   merge with the larger shadow), Topology (separate faces).
8. **Acceptance Criteria:** Nostrils present and separate when visible and above
   min-feature-area.
9. **Failure Examples:** Profile view (one/occluded nostril); very dark skin (low
   nostril contrast); strong under-lighting flattening the distinction.
10. **Research Notes:** Feature-vs-shadow separation when both are dark — is a
    landmark/nose-tip prior warranted?

### BORD-014 — Nose-pad (leather) outline (dog)

1. **Observation:** The nose pad has a real closed outline whose lower rim can blend
   into dark muzzle fur (locally low contrast).
2. **Visual Purpose:** Illustrators outline the nose leather clearly as a distinct
   material from fur.
3. **Engineering Purpose:** Prevents muzzle fill from leaking into the nose pad;
   preserves a key feature.
4. **If Ignored:** Nose pad merges with muzzle → lost feature + leak.
5. **Human Decision:** The illustrator closes the pad outline confidently even where
   the rim is faint.
6. **Computer Vision Decision:** Feature-scoped adaptive hysteresis on the weak rim +
   endpoint bridging; treat as a priority outline exempt from noise removal.
7. **Mathematical Model:** Adaptive thresholding, Graph (closure), Topology
   (priority face), closure-weighted protection.
8. **Acceptance Criteria:** Nose-pad contour closed; no leak into muzzle; feature
   present.
9. **Failure Examples:** Black nose on black fur; wet-nose speculars fragmenting the
   pad; snout in shadow.
10. **Research Notes:** Generalising "feature priority" — a catalogue of must-close
    animal features (nose, eyes, mouth line).

### BORD-015 — Cheek/jaw smooth arcs, not polygonal (portrait)

1. **Observation:** Cheek and jaw are long smooth organic arcs; naive polygon
   simplification faceted them.
2. **Visual Purpose:** Faces are curved; illustrators use flowing lines to keep
   likeness and softness.
3. **Engineering Purpose:** Preserve likeness — angular faces are off-model and read
   as low quality.
4. **If Ignored:** Chiselled/polygonal jaw; "uncanny" face; visible corners in the
   SVG.
5. **Human Decision:** The illustrator draws one sweeping stroke, adding a corner
   only at a genuine feature (chin tip).
6. **Computer Vision Decision:** Long contour + low mean curvature + low curvature
   variance ⇒ organic arc; cap inserted corners; distinguish real corners by a
   sharp local κ maximum.
7. **Mathematical Model:** Bezier / Spline fitting (curvature-minimising `∫κ²ds`),
   RDP with a corner-angle constraint, Curvature-scale-space corner detection.
8. **Acceptance Criteria:** Curvature error along sampled facial arcs below
   threshold; no corners inserted on smooth spans; genuine corners preserved.
9. **Failure Examples:** Over-simplify (blocky jaw) vs under-simplify (wobble); a
   real sharp chin smoothed away; jawline occluded by hair.
10. **Research Notes:** Curve-fit vs corner-constrained RDP — which preserves
    likeness better perceptually? Landmark-guided arc segmentation.

### BORD-016 — Ear smooth sweep with defined tip (cat)

1. **Observation:** The ear is a smooth curve ending in a defined tip; thresholding
   leaves stair-steps and simplification can facet or blunt the tip.
2. **Visual Purpose:** A clean ear sweep + crisp tip is instantly recognisable and
   characterful.
3. **Engineering Purpose:** Clean, recognisable ear region with a preserved tip
   corner.
4. **If Ignored:** Jagged/faceted ear; blunted or over-spiked tip; poor print edge.
5. **Human Decision:** The illustrator smooths the sweep but deliberately keeps the
   single sharp tip.
6. **Computer Vision Decision:** Curvature-continuity along the contour with one
   protected curvature maximum (the tip) while smoothing the rest.
7. **Mathematical Model:** Spline fitting, Curvature analysis (single-max
   preservation), RDP with protected vertices.
8. **Acceptance Criteria:** Smooth ear contour; tip corner preserved (angle within
   tolerance); no stair-steps.
9. **Failure Examples:** Folded ears (Scottish-fold); tufted lynx tips (fringe on the
   tip); ears against a busy background.
10. **Research Notes:** Detecting "the one true tip" vs incidental κ spikes from
    noise.

### BORD-017 — Petal edges: curved and overlapping (flower)

1. **Observation:** Petals are smooth curves that overlap; the overlap edge must
   separate front petal from back petal (an occlusion boundary).
2. **Visual Purpose:** Illustrators keep each petal distinct and layered to convey
   depth and count.
3. **Engineering Purpose:** Each petal a clean, separate, curved region with resolved
   overlaps.
4. **If Ignored:** Petals merge into a blob; overlaps leak; the flower loses form and
   count.
5. **Human Decision:** The illustrator draws the front petal continuous and lets the
   back petal tuck behind it at the overlap.
6. **Computer Vision Decision:** Per-petal curve fitting + T-junction detection at
   overlaps (the continuous/occluding curve wins; the abutting one terminates).
7. **Mathematical Model:** Bezier/Spline fitting, Topology (occlusion T-junctions),
   Graph (junction resolution).
8. **Acceptance Criteria:** Each petal closed and separate; overlap boundaries
   resolved consistently; curves smooth.
9. **Failure Examples:** Dense multi-petal flowers (many overlaps); translucent
   petals with see-through edges; tightly furled buds.
10. **Research Notes:** Ordering petals front-to-back from occlusion cues; handling
    ambiguous mutual overlaps.

### BORD-018 — Near-miss 1–2px gaps (general, #1 leak source)

1. **Observation:** Anti-aliasing + thresholding drops the weakest pixel where two
   soft edges nearly meet, leaving hairline gaps.
2. **Visual Purpose:** An illustrator always closes such gaps instinctively —
   open regions are never intended.
3. **Engineering Purpose:** Guaranteed closure makes flood fill safe; this is the
   dominant real-world failure mode.
4. **If Ignored:** Colour bleeds between regions; an unpredictable giant merged
   region; the worst tap-accuracy and print defects.
5. **Human Decision:** The illustrator's pen naturally connects endpoints that are
   "obviously" meant to meet.
6. **Computer Vision Decision:** Endpoints via neighbour-count=1; pair within
   `GAP_RADIUS` with tangent alignment; equivalently morphological closing radius
   `r`; measure fragments-before vs after.
7. **Mathematical Model:** Morphology (closing), Graph (min-cost endpoint matching),
   Topology (reduce open faces), Distance Transform (nearest endpoint).
8. **Acceptance Criteria:** Zero flood-fill leaks on references; bridge count
   reasonable; no unintended fusions of separate regions.
9. **Failure Examples:** Over-bridging fuses intentionally-separate regions;
   under-bridging leaves leaks; large real gaps wrongly auto-closed.
10. **Research Notes:** Learning `GAP_RADIUS` per resolution/mode; distinguishing a
    "meant-to-close" gap from an intentional opening.

### BORD-019 — Thin structural loops drop out (glasses frame)

1. **Observation:** A frame thinner than the effective stroke width falls below the
   detection floor and vanishes, opening the lens region.
2. **Visual Purpose:** Illustrators keep thin structural loops (frames, wires) intact
   because they define enclosed shapes.
3. **Engineering Purpose:** Thin-but-structural loops must stay closed to enclose
   their region.
4. **If Ignored:** The lens region opens and merges with face/background; a whole
   feature disappears.
5. **Human Decision:** The illustrator draws the frame at a legible weight regardless
   of how thin it is in the photo.
6. **Computer Vision Decision:** Stroke-width map via DT (`width≈2·DT`); sub-threshold
   strokes that (nearly) form a loop are conditionally thickened to closure without
   altering the enclosed shape.
7. **Mathematical Model:** Distance Transform / Medial Axis (stroke width),
   Morphology (conditional dilation), Topology (loop closure).
8. **Acceptance Criteria:** Thin structural loops closed; enclosed shape unchanged
   (area within tolerance); neighbours not merged by the thickening.
9. **Failure Examples:** Rimless glasses (no frame); fine jewellery chains;
   thickening bleeding into adjacent detail.
10. **Research Notes:** Detecting "structural loop" vs incidental thin line; safe
    thickening that never distorts enclosed geometry.

### BORD-020 — Occlusion overlap borders (product / stacked objects)

1. **Observation:** Where A occludes B, the shared contour belongs to A (continues)
   and cuts B (terminates); mishandling merges A and B.
2. **Visual Purpose:** Illustrators use overlap to show depth order; the front object
   reads as in front.
3. **Engineering Purpose:** Foreground/background objects must stay separate regions.
4. **If Ignored:** Overlapping objects fuse into one region → wrong depth, wrong
   fills.
5. **Human Decision:** The illustrator draws the occluding edge through and stops the
   occluded edge at the junction.
6. **Computer Vision Decision:** T-junctions (degree-3); the near-colinear pair is the
   occluder's continuing edge, the third terminates (occluded).
7. **Mathematical Model:** Topology (T-junction / figure-ground), Graph (branch
   pairing by turning angle), Gestalt good-continuation.
8. **Acceptance Criteria:** Both objects remain closed independent regions; junctions
   resolved consistently across the reference set.
9. **Failure Examples:** Many mutual overlaps (object pile); transparent occluders;
   shadow bridging two objects.
10. **Research Notes:** Global depth-ordering from many local T-junctions; consistency
    when cues conflict.

### BORD-021 — Diagonal/curved stair-stepping (general aliasing)

1. **Observation:** Binary thresholding on a pixel grid quantises smooth edges into
   1px steps (aliasing); high-frequency zig-zag on an otherwise smooth contour.
2. **Visual Purpose:** Illustrators produce clean anti-aliased strokes; steps are an
   artifact no professional leaves.
3. **Engineering Purpose:** Smooth borders → professional look, clean vectorisation,
   crisp print.
4. **If Ignored:** Ratty edges; downstream smoothing/curve-fitting misled by the
   zig-zag; ugly SVG; jagged print at scale.
5. **Human Decision:** The illustrator draws the intended smooth line, never the
   pixel staircase.
6. **Computer Vision Decision:** High pixel-scale curvature variance / chain-code
   turn-frequency on a contour whose coarse curvature is low ⇒ aliasing; threshold on
   a supersampled/sub-pixel level set (marching squares).
7. **Mathematical Model:** Sub-pixel contouring (marching squares), Gaussian
   level-set smoothing, Curvature analysis, Bezier/Spline export.
8. **Acceptance Criteria:** No visible stair-steps at export scale; real corners
   preserved; SVG vertex count reasonable.
9. **Failure Examples:** Over-smoothing rounds real 90° corners; under-smoothing
   leaves steps in the SVG; thin features smoothed away.
10. **Research Notes:** Anti-alias before vs after vectorising — where in the pipeline
    is curvature best preserved?

### BORD-022 — Outer silhouette vs interior strokes (cat, spatially-varying)

1. **Observation:** One global smoothing setting cannot both flatten the fuzzy outer
   silhouette and keep crisp interior strands — they need opposite treatment.
2. **Visual Purpose:** "Clean outside, detailed inside" is the hallmark of good fur
   line art; illustrators consciously vary their mark-making.
3. **Engineering Purpose:** Location-dependent processing yields closed clean edges
   without flattening interior detail.
4. **If Ignored:** Global smooth erases strands (flat) OR global sharp leaves jagged
   outline (leaks + ugly).
5. **Human Decision:** The illustrator uses bold confident outer strokes and fine
   interior ticks — different tools for different regions.
6. **Computer Vision Decision:** Distance-to-silhouette field + orientation coherence
   drive smoothing: strong near silhouette / low coherence; preserve interior / high
   coherence; anisotropic across strokes.
7. **Mathematical Model:** Anisotropic diffusion, Distance Transform (control field),
   Structure tensor (coherence), scale-space.
8. **Acceptance Criteria:** Outer boundary smooth + closed; interior strand
   density/coherence preserved (measured separately in the two zones).
9. **Failure Examples:** Uniform fur (no silhouette/interior distinction); very thin
   subjects (silhouette band covers everything).
10. **Research Notes:** Auto-deriving the control field; does coherence-guided
    anisotropy generalise across fur types?

### BORD-023 — Skin pores/grain (portrait texture noise)

1. **Observation:** Skin micro-texture is high-density, low-contrast, short,
   non-closing edges — high `ρ`, low length, low contrast.
2. **Visual Purpose:** Illustrators render skin as smooth planes; pores are never
   drawn.
3. **Engineering Purpose:** A clean face free of speckle regions and false borders.
4. **If Ignored:** The face fragments into hundreds of tiny regions; number/colour
   clutter; noisy SVG; poor print.
5. **Human Decision:** The illustrator ignores grain entirely, seeing only tonal
   planes.
6. **Computer Vision Decision:** Edge-preserving denoise pre-pass (bilateral);
   suppress edges failing `length≥L_min ∧ contrast≥C_min` in high-edge-density skin;
   protect via closure-weight.
7. **Mathematical Model:** Bilateral / anisotropic diffusion, Local entropy (texture
   detection), thresholding on `length×contrast`, closure-weighted keep.
8. **Acceptance Criteria:** Skin regions smooth; region count in skin within band;
   real small features (moles) above thresholds retained.
9. **Failure Examples:** Freckles/moles (real small features mistaken for grain);
   macro skin shots; heavy texture makeup.
10. **Research Notes:** Separating meaningful skin marks from grain — is a size +
    isolation + contrast rule enough, or is learning needed?

### BORD-024 — Dense fur micro-texture (tiger)

1. **Observation:** Fur is a texture field of thousands of tiny edges; only
   flow-aligned, higher-contrast strokes carry structural meaning.
2. **Visual Purpose:** Illustrators imply fur with a manageable set of directional
   strokes, not every hair.
3. **Engineering Purpose:** Keep region count sane while retaining flow and stripes.
4. **If Ignored:** Region explosion; real structure buried under micro-fragments;
   unusable page.
5. **Human Decision:** The illustrator selects strokes that follow the fur's flow and
   drops the rest.
6. **Computer Vision Decision:** Orientation coherence (structure tensor) keeps
   flow-aligned strokes; drop isolated, incoherent, short flecks;
   `keep iff coherence·length·contrast > τ`.
7. **Mathematical Model:** Structure tensor / coherence, Local entropy, anisotropic
   (along-flow) smoothing, thresholding.
8. **Acceptance Criteria:** Region/fragment count within band; dominant flow and
   stripes retained; interior reads as fur, not noise.
9. **Failure Examples:** Chaotic fur (low coherence everywhere); fine down; wet
   clumped fur.
10. **Research Notes:** Ideal balance between flow fidelity and region budget; can a
    target region count drive an adaptive `τ`?

### BORD-025 — Faint but real structural edge (general, the dangerous case)

1. **Observation:** Some true region-closing borders are low-contrast and *look*
   like the noise we want to delete; contrast alone cannot separate them.
2. **Visual Purpose:** Illustrators keep a faint but structurally necessary line
   because they understand the shape it closes.
3. **Engineering Purpose:** Prevents noise removal from destroying real outlines —
   the dangerous false-negative that "clean-looking" line art hides.
4. **If Ignored:** Aggressive denoise deletes a faint true edge → a large region
   opens → leak, despite tidy-looking line art.
5. **Human Decision:** The illustrator asks "does this line close a shape?" — if yes,
   it stays regardless of faintness.
6. **Computer Vision Decision:** Closure-weight test — would removing this edge open a
   large region boundary? If yes, keep regardless of contrast.
7. **Mathematical Model:** Topology (planar-face count / closure-weight), Graph
   (cut/connectivity test), Persistent-homology-style importance of a boundary.
8. **Acceptance Criteria:** No structural (region-completing) edge removed by noise
   passes; measured as zero new leaks introduced by denoising.
9. **Failure Examples:** A faint edge that is *also* noise-adjacent (ambiguous);
   gradients where "the region" is ill-defined; two faint edges, only one structural.
10. **Research Notes:** Operationalising closure-weight efficiently; graceful behaviour
    when structure itself is genuinely ambiguous.

---

## Cross-observation synthesis

**Which measurements decide outcomes (frequency across BORD-001…025):**

| Measurement | Observations it drives |
| --- | --- |
| Contour continuity (endpoints/gaps/junctions) | 001,002,005,010,014,018,019,020 |
| Local contrast (luminance + chroma) | 002,003,004,008,011,012,013,014,023,025 |
| Curvature / corner angle | 001,015,016,017,021 |
| Distance transform / stroke width | 005,006,007,019,022 |
| Orientation coherence (structure tensor) | 006,008,022,024 |
| Edge density / local entropy | 006,023,024 |
| Border length | most (structural-vs-noise gate) |
| Closure-weight (topology) | 002,014,018,025 |
| Shape complexity / area | 006,009,011,013 |

**Three reverse-engineered truths about commercial-quality line art:**

1. **Closure is a topology property, not a pixel property.** Leaks, junctions, thin
   loops, and faint-real edges are all decided by whether the boundary graph's faces
   are closed — hence the recurring *continuity* + *closure-weight* measurements.
   Contrast alone is insufficient.
2. **Keep-vs-drop is multi-factor.** Every noise-vs-edge case needs
   `length × contrast × coherence` plus a topological closure override — never a
   single contrast threshold, which both deletes real faint edges and keeps texture.
3. **Treatment must be spatially varying.** Silhouette vs interior, envelope vs
   strand, organic arc vs sharp feature — one global setting cannot win; the deciding
   control fields are *distance-to-silhouette* and *orientation coherence*.

**The four lenses converge:** the illustrator's intuition ("draw the shape, not the
pixels"), the CV measurements, the game-engine's tap/print/SVG requirements, and the
topology all point to the same conclusion — **borders are shapes and graphs first,
gradients second.** This is the scientific basis for a future *Perfect Border and
Shape Engine*; no implementation is proposed here.
