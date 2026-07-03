# Border Engineering Analysis — Reverse Engineering

Engineering-grade expansion of the 25 border observations
([`border_observations.csv`](border_observations.csv)). Each entry is analysed as
a graphics engineer would: **why the pixels behave this way**, what measurable
image properties are involved, and a candidate mathematical model.

**This is analysis, not implementation.** No algorithm is built and no engine
code is touched. Mathematical models are described to explain *why*
commercial-quality line art behaves as it does — they are hypotheses for the
later algorithm stage (`06_algorithms/`), not specifications.

Governed by [`../../COLORFORGE_QUALITY_RULEBOOK.md`](../../COLORFORGE_QUALITY_RULEBOOK.md).

---

## Measurable Properties Glossary

Definitions referenced throughout. `I` is the (grayscale) image, `∇I` its
gradient, `C` a traced contour as an ordered point list `p₀…p_{n-1}`.

| Property | Definition / metric | Intuition |
| --- | --- | --- |
| **Edge strength** | Gradient magnitude `‖∇I‖ = √(Iₓ² + I_y²)` (Sobel); Canny keeps edges via hysteresis (low `t_lo`, high `t_hi`). | How sharp a tonal change is. |
| **Local contrast** | Weber `ΔI/I`, Michelson `(I_max−I_min)/(I_max+I_min)`, or std-dev in a window `w`. | Whether an edge is visible vs flat. |
| **Contour continuity** | # connected components of the line layer; # endpoints (skeleton pixels with exactly one 8-neighbour, via a neighbour-count convolution); gap `d` = nearest-endpoint Euclidean distance. | How "closed" a boundary is. |
| **Border length** | Arc length `L = Σ‖p_{i+1}−p_i‖`. | Long edges are structural; short ones are usually noise. |
| **Curvature** | Menger `κ_i = 4·Area(p_{i−1},p_i,p_{i+1}) / (‖a‖‖b‖‖c‖)`; or turning-angle rate `dθ/ds`. | How sharply a boundary bends. |
| **Corner angle** | `θ = arccos( (u·v)/(‖u‖‖v‖) )` between incoming `u` and outgoing `v` segments at a vertex. | Distinguishes a true corner from a smooth arc. |
| **Edge density** | `ρ = (#edge px in window)/(window area)`. | Texture regions have high `ρ`; clean outlines low. |
| **Orientation coherence** | Structure tensor `J = Σ w·∇I∇Iᵀ`; coherence `c = (λ₁−λ₂)/(λ₁+λ₂)`, dominant orientation from eigenvector of `λ₁`. | Flow/strands score high `c`; random grain low. |
| **Distance transform (DT)** | `DT(p)=` distance to nearest boundary pixel. | Inscribed-circle radius, stroke width (`≈2·DT` on the medial axis), region "room". |
| **Shape complexity** | Compactness `P²/(4πA)` (1 = circle); or #vertices after simplification. | Faces/organics are moderate; noise fragments are extreme. |
| **Closure-weight** | Increase in # of *open* region boundaries if an edge is removed (test connectivity with/without it). | Measures how much a region depends on that edge. |

**Detection primitives** these compose into: Sobel/Canny gradients, connected
components + hierarchy (`findContours`), morphological close/open, thinning +
neighbour-count for endpoints/junctions, distance transform, structure tensor,
Douglas–Peucker / spline fitting, SLIC/superpixel boundaries (as an *edge cue*
only — never as the region source).

---

## Per-observation analysis

Each field: **WHY** it happens · **SOLVES** (visual problem) · **IGNORED**
(failure) · **DETECT** (auto) · **METRICS** · **MODEL** · **FAILURE EXAMPLES** ·
**ACCEPTANCE**.

### BORD-001 — Outer silhouette must be one closed loop (portrait, head/hair)
- **WHY:** Hair wisps and flyaways cross the silhouette; each wisp is a thin
  high-gradient spur that breaks the outer contour into arcs. The "true"
  silhouette is the low-frequency envelope; wisps are high-frequency excursions.
- **SOLVES:** Keeps the subject a single sealed shape so the background stays a
  separate fill.
- **IGNORED:** Background flood-fill leaks through a wisp gap into the face → one
  giant merged region; page unusable.
- **DETECT:** `findContours(RETR_EXTERNAL)` → take the max-enclosed-area contour;
  detect breaks as endpoints on the outer band; envelope via morphological
  closing at silhouette scale.
- **METRICS:** border length (longest loop), contour continuity (endpoints on the
  outer band), curvature (wisps = high-κ spikes), DT (wisp thinness).
- **MODEL:** Outer contour = `argmax_C Area(C)`. Wisp = contour segment with
  `κ > κ_hi` and enclosed area `< A_min`. Close with a structuring element of
  radius `r ≈ median hair-gap`.
- **FAILURE EXAMPLES:** Backlit flyaway hair (dozens of thin spurs); dark hair on
  dark background (silhouette contrast → 0).
- **ACCEPTANCE:** Exactly one closed outer loop; background fill confined to
  outside it on the reference set.

### BORD-002 — Low-contrast silhouette vs similar background (cat)
- **WHY:** When fur luminance ≈ background luminance, `‖∇I‖` along the silhouette
  falls below a global Canny `t_lo`, so those segments are simply never emitted.
- **SOLVES:** Preserves the body outline where the eye still perceives a boundary
  the gradient barely encodes.
- **IGNORED:** Silhouette has holes exactly where contrast is lowest → guaranteed
  leak on the "hardest" side of the animal.
- **DETECT:** Locally-adaptive hysteresis: lower `t_lo` along the outer band;
  bridge across the weak stretch using orientation continuity from neighbouring
  strong edges.
- **METRICS:** local contrast (Weber/Michelson along the contour), edge strength
  profile, contour continuity (gap length across weak stretch).
- **MODEL:** Adaptive threshold `t_lo(x) = μ_grad(x) − k·σ_grad(x)` in a band;
  connect endpoints whose tangents are colinear (angle diff `< α`) across gap
  `d < GAP_RADIUS`.
- **FAILURE EXAMPLES:** Grey cat on grey couch; snow animal on white; dark subject
  on dark. Over-correction invents edges in flat background.
- **ACCEPTANCE:** Silhouette closed even on the min-contrast side; no phantom
  edges into the flat background (edge-density in background ≈ 0).

### BORD-003 — Object edge vs soft drop shadow (product)
- **WHY:** A drop shadow is a smooth luminance ramp; its mid-gradient can rival
  the true edge, so naïve thresholding places the border on the shadow, not the
  object.
- **SOLVES:** Anchors the border to the object silhouette, not the shadow halo.
- **IGNORED:** Region grows into the shadow; object appears bloated / haloed.
- **DETECT:** The true edge is a *step* (narrow, high peak gradient); the shadow
  is a *ramp* (wide, low-plateau gradient). Distinguish by gradient-profile width
  / second-derivative zero-crossing sharpness.
- **METRICS:** edge strength (peak vs plateau), gradient-profile width, local
  contrast asymmetry across the edge.
- **MODEL:** Classify a candidate edge by `‖∇I‖_peak / profile_width`; keep step
  edges (high ratio), reject ramp edges. Equivalent to preferring Laplacian
  zero-crossings with steep slope.
- **FAILURE EXAMPLES:** Long soft shadow on glossy surface; gradient background
  reflections.
- **ACCEPTANCE:** Border sits on the object contour; shadow remains outside or a
  separate low-priority region; no outward bloat.

### BORD-004 — Face→neck soft-shadow inner boundary (portrait)
- **WHY:** The separation is a broad tonal plane change (occlusion shadow under
  the jaw), not a line; peak gradient is low and spread out.
- **SOLVES:** Keeps neck and face as distinct paintable tones (form reads).
- **IGNORED:** Neck merges into face → flat, formless lower face.
- **DETECT:** Tonal-plane segmentation (local mean difference / bilateral /
  superpixel boundary) promoted to a border when it separates two large,
  differently-toned regions; then closed.
- **METRICS:** local contrast (plane-to-plane mean ΔL), border length (long,
  smooth), curvature (low), edge density (low — it's a lone soft edge).
- **MODEL:** Edge candidate score `= ΔL̄ · length · (1/edge_density)`; promote if
  above threshold even when peak `‖∇I‖` is low. Close via endpoint bridging.
- **FAILURE EXAMPLES:** Diffuse/flat lighting (no shadow to find); double chin
  soft folds create ambiguous multiple planes.
- **ACCEPTANCE:** A closed inner border separates neck from face when a tonal
  plane exists; not invented under flat lighting.

### BORD-005 — Handle-to-body T-junction (product/mug)
- **WHY:** At the join, three boundary branches meet; if the connecting stroke is
  incomplete the two shapes share an opening.
- **SOLVES:** Handle and body remain independently fillable.
- **IGNORED:** Fill crosses the join → handle+body become one region; also breaks
  correct color numbering.
- **DETECT:** Junction points = skeleton pixels with ≥3 neighbours; verify each
  incident region boundary closes; complete the missing branch.
- **METRICS:** contour continuity (junction degree), DT (throat width at join),
  border length of each incident arc.
- **MODEL:** Detect T/Y-junctions via neighbour-count = 3 on the thinned line;
  require all incident faces closed (Euler check on the planar boundary graph);
  bridge the shortest missing arc.
- **FAILURE EXAMPLES:** Thin low-contrast join; multiple close handles (basket).
- **ACCEPTANCE:** Both meeting regions closed; flood fill does not cross the
  junction.

### BORD-006 — Ragged hairline fringe (portrait)
- **WHY:** The hairline is a dense comb of short high-gradient strands; each is a
  legitimate edge but collectively they form a fractal fringe, not a boundary.
- **SOLVES:** One clean hair/skin boundary instead of dozens of leaking slivers.
- **IGNORED:** Speckle explosion: many tiny fringe regions, several open → leaks;
  region count balloons.
- **DETECT:** High edge-density + high shape-complexity band between two large
  smooth regions (hair, forehead) ⇒ collapse to the band's low-frequency spine.
- **METRICS:** edge density (very high in the fringe), shape complexity /
  compactness (extreme), border length per fragment (short), orientation
  coherence (moderate, roughly parallel strands).
- **MODEL:** Replace the fringe with its medial spine (DT ridge of the band) or
  the smoothed envelope; keep individual strands only if
  `length > L_strand` AND `contrast > C_strand`.
- **FAILURE EXAMPLES:** Curly/afro hair (very deep fringe); baby fine hair (low
  contrast fringe vanishes entirely).
- **ACCEPTANCE:** Single smooth hair/skin boundary; fringe region count below a
  band; no open fringe slivers.

### BORD-007 — Fuzzy outer fur silhouette (dog)
- **WHY:** Individual guard hairs cross the silhouette, so the outer contour is
  jagged at hair scale; the perceived silhouette is the hair envelope.
- **SOLVES:** Clean closed outer boundary while fur still *reads* as fur inside.
- **IGNORED:** Non-closing jagged outer border → background leak; or over-smooth
  loses the fuzzy character.
- **DETECT:** Multi-scale: silhouette at coarse scale (Gaussian σ large) for the
  envelope; strands at fine scale kept only inside.
- **METRICS:** curvature spectrum (hair spikes = high-frequency κ), DT (hair
  thinness), distance-to-silhouette (band width).
- **MODEL:** Outer boundary = level set of a strongly-smoothed silhouette field;
  smoothing strength `σ(x)` decays with distance from the silhouette (see P-B3).
- **FAILURE EXAMPLES:** Long-haired breeds (huge fuzzy band); wet/spiky fur.
- **ACCEPTANCE:** Outer boundary closed and smooth; interior strand strokes
  retained; no background leak.

### BORD-008 — Tiger stripe internal borders (tiger)
- **WHY:** Stripes are high-contrast hard-edged bands; they are *the* identity of
  the subject and must not be smoothed into the base fur.
- **SOLVES:** Preserves pattern identity (recognisably a tiger).
- **IGNORED:** Stripes blur/merge → flat orange animal; identity destroyed
  (Rulebook "looks flat" fail).
- **DETECT:** High-contrast, elongated, closed or semi-closed bands with strong
  orientation coherence and clear DT-thickness; protect from smoothing/merge.
- **METRICS:** local contrast (high), orientation coherence (high), border length
  (long), DT (band thickness), curvature (moderate, banded).
- **MODEL:** Protect edges with `contrast·length·coherence > τ_pattern` from any
  merge/smoothing pass; treat each band as a keep-region.
- **FAILURE EXAMPLES:** Faded/low-contrast stripes; motion-blurred stripes; stripe
  tips that taper below min width.
- **ACCEPTANCE:** All salient stripes retained as separate regions; base fur not
  fragmented by stripe micro-texture.

### BORD-009 — Nested eye rings + catchlight (portrait)
- **WHY:** The eye is concentric closed shapes at rapidly shrinking scale
  (eyelid→sclera→iris→pupil→catchlight); small radius + soft lids make inner
  rings fragile.
- **SOLVES:** Preserves gaze and life; each ring a distinct fill.
- **IGNORED:** Rings merge → dead/blank eye; the single most identity-destroying
  failure in portraits.
- **DETECT:** Concentric near-circular contours by area/compactness ordering
  inside the eye ROI; catchlight = tiny bright blob (local maximum) inside the
  pupil.
- **METRICS:** compactness (≈circular), DT (nested radii), local contrast
  (pupil/sclera very high), area (feature vs min-feature-area).
- **MODEL:** Within the eye ROI, keep the ordered set of closed contours whose
  areas form a decreasing nest; keep catchlight if
  `area > MIN_FEATURE_AREA` and it is a local luminance maximum.
- **FAILURE EXAMPLES:** Half-closed eyes; glasses glare over the eye; very small
  eyes in group photos.
- **ACCEPTANCE:** Each eye ring closed and present; catchlight retained when large
  enough; gaze preserved on the eye-crop reference set.

### BORD-010 — Eyelid crease fine line (portrait)
- **WHY:** The crease is a faint, thin, low-contrast line; sub-pixel and easily
  broken by thresholding into segments with 1–2px gaps.
- **SOLVES:** Keeps the eye socket enclosed and the lid readable.
- **IGNORED:** Skin fill floods across the broken crease into the eye.
- **DETECT:** Endpoint pairing on feature lines with a small bridge radius; ridge
  detection (Frangi-like) for thin low-contrast lines.
- **METRICS:** contour continuity (endpoints/gaps), edge strength (low), border
  length (short-medium), curvature (gentle).
- **MODEL:** Bridge endpoints where `gap < GAP_RADIUS_FEATURE` and tangents align;
  detect the line as a gradient *ridge*, not a step.
- **FAILURE EXAMPLES:** Monolid / no visible crease; heavy makeup creating false
  double creases.
- **ACCEPTANCE:** Crease continuous where present; eye socket stays enclosed.

### BORD-011 — Animal pupil shape (cat)
- **WHY:** Pupil is a strong, high-contrast, well-defined closed shape (slit or
  round) — robust to detect, catastrophic to lose.
- **SOLVES:** Retains a defining facial feature.
- **IGNORED:** Eye reads as a flat disc; expression lost.
- **DETECT:** Highest-contrast small closed shape inside the eye ROI; shape
  descriptor (elongation/eccentricity) distinguishes slit vs round.
- **METRICS:** local contrast (max in ROI), compactness/eccentricity, area.
- **MODEL:** Keep `argmax_contrast` closed contour with `area>MIN_FEATURE_AREA`
  inside each eye; preserve its geometry through simplification (low κ tolerance).
- **FAILURE EXAMPLES:** Reflective tapetum glare; pupil merged with dark iris.
- **ACCEPTANCE:** Pupil present, closed, shape-faithful.

### BORD-012 — Lip boundary is tonal/colour, not drawn (portrait)
- **WHY:** Lip vs skin is largely a *chroma* change with weak luminance gradient;
  a luminance-only edge detector under-responds.
- **SOLVES:** Lips become their own region with faithful colour.
- **IGNORED:** Lips merge into skin; mouth disappears.
- **DETECT:** Add chroma-gradient (edges in a*/b* of CIELAB, or hue) to the edge
  cue; promote the lip/skin tonal-plane boundary and close it.
- **METRICS:** local contrast in chroma (ΔE in Lab), border length, curvature
  (smooth cupid's-bow), edge strength in luminance (low — that's the point).
- **MODEL:** Composite edge strength `g = √(‖∇L‖² + w·‖∇(a,b)‖²)`; detect lip
  border on `g`, then close.
- **FAILURE EXAMPLES:** Pale/nude lips (ΔE→0); strong lipstick creating a hard
  false line vs. natural soft edge.
- **ACCEPTANCE:** Closed lip region present when chroma separation exists; colour
  sampled faithfully.

### BORD-013 — Nostril openings (portrait)
- **WHY:** Small dark high-contrast blobs; easy to detect but easy to merge with
  the surrounding nose shadow.
- **SOLVES:** Nose keeps its form (openings punctuate it).
- **IGNORED:** Nostrils dissolve into nose shadow → flat nose.
- **DETECT:** Small dark closed components with high contrast to the immediate
  neighbourhood; keep above min-feature-area.
- **METRICS:** local contrast (dark vs skin), area, compactness.
- **MODEL:** Keep small dark closed shapes where `contrast>τ` and
  `area>MIN_FEATURE_AREA`; do not let them merge into the larger nose-shadow
  region.
- **FAILURE EXAMPLES:** Profile view (single/occluded nostril); very dark skin
  (low nostril contrast).
- **ACCEPTANCE:** Nostrils present and separate when visible + large enough.

### BORD-014 — Nose-pad (leather) outline (dog)
- **WHY:** The nose pad has a genuine closed outline but its lower rim can blend
  into dark muzzle fur (low contrast there).
- **SOLVES:** Prevents muzzle fill from leaking into the nose pad.
- **IGNORED:** Nose pad merges with muzzle → lost feature + leak.
- **DETECT:** Priority-close the nose-pad contour (adaptive threshold on the weak
  rim), similar to BORD-002 but on a feature.
- **METRICS:** local contrast profile around the rim, contour continuity (gap on
  the weak arc), DT (pad size).
- **MODEL:** Feature-scoped adaptive hysteresis + endpoint bridging on the pad
  contour; treat as priority outline (never dropped by noise removal).
- **FAILURE EXAMPLES:** Black nose on black fur; wet-nose specular highlights
  fragmenting the pad.
- **ACCEPTANCE:** Nose-pad contour closed; no leak into muzzle.

### BORD-015 — Cheek/jaw smooth arcs, not polygonal (portrait)
- **WHY:** Douglas–Peucker and aggressive polygon simplification insert corners on
  long low-curvature arcs, faceting the face.
- **SOLVES:** Keeps likeness — faces are curved, not chiselled.
- **IGNORED:** Angular, off-model face; "uncanny" polygon jaw.
- **DETECT:** Long contour, low mean curvature, low curvature variance ⇒ treat as
  organic arc; cap corner insertion.
- **METRICS:** curvature (low mean, low variance), corner angle (should stay near
  180°), border length (long), shape complexity (moderate).
- **MODEL:** Constrained simplification: minimise vertex count subject to max
  perpendicular error `ε` AND max corner deviation `Δθ < θ_max`; or fit a
  curvature-minimising spline (`min ∫κ² ds`).
- **FAILURE EXAMPLES:** Over-simplify (blocky jaw) vs under-simplify (noisy
  wobble). Sharp real features (chin tip) wrongly smoothed.
- **ACCEPTANCE:** Curvature error along sampled facial arcs below threshold; no
  inserted corners on smooth spans; genuine corners preserved.

### BORD-016 — Ear smooth sweep (cat)
- **WHY:** Ear is a smooth curve ending in a defined tip; thresholding leaves
  stair-steps, simplification risks faceting or blunting the tip.
- **SOLVES:** Clean, recognisable ear.
- **IGNORED:** Jagged/faceted ear; blunted or spiked tip.
- **DETECT:** Curvature-continuity check along the ear contour; treat the tip as a
  single high-κ point to preserve while smoothing the sweep.
- **METRICS:** curvature continuity, corner angle at tip (real corner), border
  length.
- **MODEL:** Preserve the single dominant curvature maximum (tip); smooth the rest
  with curvature-aware filtering.
- **FAILURE EXAMPLES:** Folded/Scottish-fold ears; tufted lynx tips (fringe on the
  tip).
- **ACCEPTANCE:** Smooth ear contour, tip preserved, no stair-steps.

### BORD-017 — Petal edges: curved + overlapping (flower)
- **WHY:** Petals are smooth curves that *overlap*; the overlap edge must separate
  front petal from back petal (an occlusion boundary).
- **SOLVES:** Each petal a clean, separate, curved region.
- **IGNORED:** Petals merge into a blob; overlaps leak.
- **DETECT:** Smooth-curve fitting per petal + T-junction detection at overlap
  points (see BORD-005/020).
- **METRICS:** curvature (smooth), corner angle at overlap junctions, contour
  continuity.
- **MODEL:** Curve-fit petal contours; at overlaps, the continuous (occluding)
  curve wins, the abutting (occluded) curve terminates.
- **FAILURE EXAMPLES:** Dense multi-petal flowers (many overlaps); translucent
  petals with see-through edges.
- **ACCEPTANCE:** Each petal closed and separate; overlap boundaries resolved;
  curves smooth.

### BORD-018 — Near-miss 1–2px gaps (general)
- **WHY:** Anti-aliasing + thresholding drops the weakest pixel where two soft
  edges nearly meet, leaving a hairline gap — the #1 leak source.
- **SOLVES:** Guarantees closure so flood fill is safe.
- **IGNORED:** Colour bleeds between regions; unpredictable giant merged region.
- **DETECT:** Endpoint detection (neighbour-count=1) + pair endpoints within
  `GAP_RADIUS`; equivalently morphological closing with radius `r`.
- **METRICS:** contour continuity (endpoint count, gap length), DT between
  endpoints.
- **MODEL:** Build an endpoint graph; connect pairs minimising added length with
  `d < GAP_RADIUS` and tangent alignment; or `close = erode(dilate(·, r), r)`.
  Report fragments-before vs after as the effectiveness metric.
- **FAILURE EXAMPLES:** Over-bridging fuses intentionally-separate regions;
  under-bridging leaves leaks. Large real gaps (should NOT be auto-bridged).
- **ACCEPTANCE:** Zero flood-fill leaks; bridge count reasonable; no unintended
  fusions.

### BORD-019 — Thin structural loops drop out (glasses frame)
- **WHY:** A frame thinner than the effective stroke width falls below the
  detection/threshold floor and vanishes, opening the lens region.
- **SOLVES:** Keeps thin but structural loops closed.
- **IGNORED:** Lens region opens and merges with face/background.
- **DETECT:** Stroke-width map via DT; strokes with width `< THIN_LOOP_MIN` that
  form (or nearly form) a loop are thickened.
- **METRICS:** DT/stroke width, contour continuity (loop closure), border length.
- **MODEL:** `width(p) ≈ 2·DT(p)` on the medial axis; conditional dilation of
  sub-threshold loop strokes until closure, without altering enclosed shape.
- **FAILURE EXAMPLES:** Rimless glasses (no frame to keep); very fine jewellery
  chains; thickening bleeds into adjacent detail.
- **ACCEPTANCE:** Thin structural loops closed; enclosed shape unchanged; no
  merging of neighbours.

### BORD-020 — Occlusion overlap borders (product)
- **WHY:** Where object A occludes B, the shared contour belongs to A (continues)
  and cuts B (terminates); getting this wrong merges A and B.
- **SOLVES:** Foreground/background objects stay separate.
- **IGNORED:** Overlapping objects fuse into one region.
- **DETECT:** T-junctions where three branches meet; the near-colinear pair is the
  occluder's continuing edge, the third is the occluded's terminating edge.
- **METRICS:** junction degree, corner/turning angles at the junction (colinearity
  test), contour continuity.
- **MODEL:** At each T-junction, pair the two branches with smallest turning angle
  as the occluding contour; the remaining branch stops. Enforce both faces
  closed.
- **FAILURE EXAMPLES:** Many mutual overlaps (pile of objects); transparent
  occluders.
- **ACCEPTANCE:** Both objects remain closed, independent regions; junctions
  resolved consistently.

### BORD-021 — Diagonal/curved stair-stepping (general)
- **WHY:** Binary thresholding of a continuous edge on a pixel grid quantises it
  into 1px steps (aliasing); high-frequency zig-zag along an otherwise smooth
  contour.
- **SOLVES:** Professional-looking smooth borders; better SVG.
- **IGNORED:** Ratty edges; downstream smoothing/curve-fitting misled by the
  zig-zag; ugly export.
- **DETECT:** Pixel-scale curvature variance / chain-code direction-change rate is
  high on a contour whose coarse curvature is low ⇒ aliasing.
- **METRICS:** curvature at pixel scale (variance), chain-code turn frequency,
  contrast (real edge underneath).
- **MODEL:** Threshold on a supersampled / Gaussian-smoothed level set (sub-pixel
  marching squares), or smooth the contour where
  `κ_pixel_variance` is high but `κ_coarse` is low. Anti-alias before, not after,
  vectorising.
- **FAILURE EXAMPLES:** Over-smoothing rounds real 90° corners; under-smoothing
  leaves steps in the SVG.
- **ACCEPTANCE:** No visible stair-steps at export scale; real corners preserved.

### BORD-022 — Outer silhouette vs interior strokes (cat, spatially-varying)
- **WHY:** One global smoothing setting cannot both flatten the outer fuzzy
  silhouette and keep crisp interior strands — they need opposite treatment.
- **SOLVES:** Clean outside, detailed inside — the hallmark of good fur line art.
- **IGNORED:** Global smooth erases strands (flat) OR global sharp leaves jagged
  outline (leaks/ugly).
- **DETECT:** Distance-to-silhouette field + orientation coherence: smooth where
  near silhouette / low coherence; preserve where interior / high coherence.
- **METRICS:** distance transform (to silhouette), orientation coherence, edge
  density.
- **MODEL:** Spatially-varying smoothing `σ(x) = σ_max·f(dist_to_silhouette) ·
  (1−coherence)`; anisotropic (smooth across strokes, not along them).
- **FAILURE EXAMPLES:** Uniform fur (no silhouette/interior distinction); very
  thin subjects (silhouette band covers everything).
- **ACCEPTANCE:** Outer boundary smooth+closed; interior strand count/coherence
  preserved.

### BORD-023 — Skin pores/grain (portrait)
- **WHY:** Sensor/skin micro-texture is high-density, low-contrast, short,
  non-closing edges — high `ρ`, low length, low contrast.
- **SOLVES:** Clean face free of speckle regions/false borders.
- **IGNORED:** Face fragments into hundreds of tiny regions; number/colour clutter.
- **DETECT:** Suppress edges failing `length ≥ L_min` AND `contrast ≥ C_min` in
  high-edge-density skin areas; edge-preserving denoise (bilateral) pre-pass.
- **METRICS:** edge density (high), border length (short), local contrast (low),
  closure-weight (≈0 — removing grain opens nothing).
- **MODEL:** Keep edge iff `(length ≥ L_min ∧ contrast ≥ C_min) ∨ closure_weight
  > 0`. Grain fails both → dropped.
- **FAILURE EXAMPLES:** Freckles/moles (real small features vs grain); heavy skin
  texture in macro shots.
- **ACCEPTANCE:** Skin regions smooth; region count in skin within band; real
  features (moles) above thresholds retained.

### BORD-024 — Dense fur micro-texture (tiger)
- **WHY:** Fur is a texture field; thousands of tiny edges where only
  flow-aligned, higher-contrast strokes carry structural meaning.
- **SOLVES:** Region count stays sane; stripes/structure visible.
- **IGNORED:** Region explosion; real structure buried under micro-fragments.
- **DETECT:** Orientation coherence (structure tensor) keeps flow-aligned strokes;
  drop isolated, incoherent, short flecks.
- **METRICS:** orientation coherence (keep high), edge density, border length,
  local contrast.
- **MODEL:** Keep stroke iff `coherence·length·contrast > τ`; suppress the rest.
  Coherence-guided (anisotropic) smoothing along the flow.
- **FAILURE EXAMPLES:** Chaotic fur (low coherence everywhere); fine down.
- **ACCEPTANCE:** Region/fragment count within band; dominant flow + stripes
  retained.

### BORD-025 — Faint but real structural edge (general)
- **WHY:** Some true region-closing borders are low-contrast and *look* like the
  noise we want to delete — contrast alone cannot separate them.
- **SOLVES:** Prevents noise removal from destroying real outlines (the dangerous
  false-negative).
- **IGNORED:** Aggressive denoise deletes a faint true edge → a large region opens
  → leak, despite "clean-looking" line art.
- **DETECT:** Closure-weight test: would removing this edge open a large region
  boundary? If yes, keep regardless of contrast.
- **METRICS:** closure-weight (high), border length (long), local contrast (low —
  the trap), edge continuity contribution.
- **MODEL:** Protected set `= { e : closure_weight(e) > 0 ∧ length(e) > L_struct }`
  exempt from contrast-based removal. Formally: an edge is protected if the line
  graph's number of closed faces drops when `e` is removed.
- **FAILURE EXAMPLES:** Faint edge that is *also* noise-adjacent (ambiguous);
  gradients where "the region" is ill-defined.
- **ACCEPTANCE:** No structural (region-completing) edge removed by noise passes;
  measured as zero new leaks introduced by denoising.

---

## Cross-observation engineering summary

**Recurring metrics** (how often they decide the outcome across BORD-001…025):

| Metric | Drives observations |
| --- | --- |
| Contour continuity (endpoints / gaps / junctions) | 001,002,005,010,014,018,019,020 |
| Local contrast (luminance + chroma) | 002,003,004,008,011,012,013,014,023,025 |
| Curvature / corner angle | 001,015,016,017,021 |
| Distance transform / stroke width | 005,006,007,019,022 |
| Orientation coherence (structure tensor) | 006,008,022,024 |
| Edge density | 006,023,024 |
| Border length | many (structural vs noise gate) |
| Closure-weight | 002,014,018,025 (the "keep faint real edges" test) |
| Shape complexity / area | 006,009,011,013 |

**Three engineering truths reverse-engineered from commercial-quality line art:**

1. **Closure is a graph property, not a pixel property.** Leaks, junctions, thin
   loops, and faint-real edges are all decided by whether the boundary graph's
   faces are closed — hence the recurring `contour continuity` + `closure-weight`
   metrics. Contrast alone is insufficient.

2. **Keep-vs-drop is multi-factor, never contrast-thresholding.** Every noise vs
   real-edge case (023,024,025) needs `length × contrast × coherence` plus a
   closure override. A single contrast threshold both deletes real faint edges and
   keeps texture.

3. **Treatment must be spatially varying.** Silhouette vs interior (022), envelope
   vs strand (006,007), organic arc vs sharp feature (015,016) — one global
   smoothing/threshold cannot win. The deciding fields are distance-to-silhouette
   and orientation coherence.

These three truths are the questions for the algorithm stage (`06_algorithms/`)
and the rationale behind the eventual **Quality Fix 3 — Perfect Border and Shape
Engine**. No implementation is proposed here.
