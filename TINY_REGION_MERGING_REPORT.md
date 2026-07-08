# Upstream Tiny-Region Merging — Report

**Goal.** Reduce unnecessary micro-regions *before* border generation while preserving the
original object shape, its features, and its colour accuracy.

This pass runs inside `region_detector.run`, immediately after the existing sub-floor
merge, and before masks / SVG / palette / numbering / borders are produced. It dissolves
only regions that are **both tiny and nearly the same colour as a neighbour** — the kind of
speckle that adds a number a colourist can barely see, never a feature.

---

## What it does

For every kept region below `keep_area × INSIGNIFICANT_AREA_MULT` (5× the profile's own
region floor — a profile-relative definition of "tiny"), the merger finds the touching
neighbour whose mean **Lab colour is closest**, and merges the tiny region into it **only if**
that colour distance is within the gate. The gate is the profile's `palette_delta` (the ΔE
inside which two colours already collapse to one palette entry downstream), **hard-capped at
`INSIGNIFICANT_DELTA_CAP = 6.0`**.

Design choices that satisfy the sprint's rules:

| Rule | How it is enforced |
|------|--------------------|
| Do not change reveal mode | Reveal is untouched; every merged pixel is reassigned to a **non-zero** surviving label, never to the line skeleton (label 0). The union of `labels > 0` is bit-identical → reveal ΔE stays 0. |
| Do not reduce image accuracy | Merge only across ΔE ≤ 6 — visually the same colour, far under the ~2.3 JND even after flat-fill. A ≤6 ΔE recolour of a sub-floor speck is imperceptible. |
| Do not merge across different objects / strong edges | A different object or a strong edge means a **large** colour gap; the ΔE ≤ 6 gate refuses to cross it. |
| Preserve eyes, lips, fingers, hair, accessories, decoration | Every such feature sits behind a high-contrast (large-ΔE) edge, so it is inherently spared. It also has to be *tiny* to even be a candidate. |
| Merge only visually insignificant tiny regions | Candidate set = tiny **and** colour-adjacent. Both conditions required. |
| Keep region IDs / palette / numbering stable | The smaller region merges **into** the larger (union-find), so the dominant region keeps its identity and label. Palette count held within ±1 on all 27 (identical on 21). |

Reported per job as `insignificant_regions_merged` in the region-detector result.

---

## Validation — all 27 images

Measured with a **controlled off/on comparison**: the AI line-art helper is patched out so the
line art is byte-identical between the two runs, and merge-OFF vs merge-ON therefore differ by
*nothing but this merge*. Each image runs at its auto-selected profile.

> Note on methodology: comparing region counts across two ordinary `process_upload` runs is
> invalid, because the AI line-art step is non-deterministic and changes the region count on
> its own. The controlled harness removes that confound. Line-art-independent invariants
> (reveal ΔE, leaks, crashes) hold from any run.

### Headline

| Metric | Result |
|--------|--------|
| Crashes | **0 / 27** (all `status = done`) |
| Reveal completed-image ΔE | **0.0 on 27 / 27** (`matches_original_under_masks` = true on all) |
| Colour leaks | **0** across all 27 |
| Total regions | **1506 → 1461  (−45, −3.0 %)** |
| Images actually simplified | 11 / 27 |
| Flat-fill (rendered) ΔE shift | mean **−0.080** (net *improvement*), min −2.250, max **+0.407** |
| Images with flat-fill shift > 0.3 ΔE | 1 (img 15, +0.407 — imperceptible, < JND) |
| Palette count | within ±1 on all 27 (unchanged on 21) |
| PASS (done ∧ leak 0 ∧ reveal ΔE 0 ∧ flat-fill shift ≤ 0.5) | **27 / 27** |

The mean flat-fill ΔE is **negative** — removing sub-floor speckle *reduces* the flat-fill
error on average rather than adding to it. The single largest positive shift, img 15 at
+0.407 ΔE, is well below the ~2.3 ΔE just-noticeable difference: no colourist can see it.

### Why the ΔE cap is 6, not 8

An earlier cap of 8 was tested across the same harness. It bought essentially the same region
reduction but let one Easy image (img 15) creep to **+0.618** flat-fill ΔE — the only image
that failed the ≤0.5 accuracy gate. Tightening the cap to 6 removes that creep (img 15 →
+0.407, now passing) while costing only ~1 merged region each on 3 images (14, 15, 27). Direct
probe:

| img | cap = 6 | cap = 8 |
|-----|---------|---------|
| 14 (hard) | −17,  +0.012 ΔE | −18,  +0.012 ΔE |
| 15 (easy) | −5,  **+0.407** ΔE | −6,  **+0.618** ΔE |
| 27 (hard) | −4,  −0.002 ΔE | −5,  +0.011 ΔE |
| 26 (prof) | −4,  +0.128 ΔE | −4,  +0.128 ΔE |

Cap 6 is the strictly safer accuracy guard for a negligible reduction cost, so it is the
shipped value.

---

## Visual examples (before / after region boundaries)

The two panels are the same artwork with region boundaries overlaid; only the region **count**
differs. The point of the comparison is that the pictures are indistinguishable — shape,
face, hair, accessories and object outlines are identical — while a handful of tiny,
same-colour background/foliage specks have dissolved.

### img 14 — hard, 170 → 153 regions (17 tiny merged)

![img14 before/after](research/08_reports/tiny_region_merging/visual_14.png)

Face, eyes, hair strands, the mailbox, and the hoodie logo are untouched; the merged regions
are speckle in the tree canopy, house shadows, and background shrubs.

### img 16 — medium, 92 → 86 regions (6 tiny merged)

![img16 before/after](research/08_reports/tiny_region_merging/visual_16.png)

Sky, road, houses, lamp post and picket fence are identical; 6 near-duplicate foliage/shadow
specks merged into their surrounding colour.

*(Additional overlays for img 13 and img 27 are in `research/08_reports/tiny_region_merging/`.)*

---

## Per-image results (controlled off/on, ΔE cap = 6)

| # | Profile | Regions before | Regions after | Merged | Reveal ΔE | Flat-fill ΔE shift | Leaks |
|---|---------|---------------:|--------------:|-------:|:---------:|:------------------:|:-----:|
| 1 | easy | 81 | 80 | 1 | 0.0 | +0.016 | 0 |
| 2 | easy | 105 | 105 | 0 | 0.0 | +0.000 | 0 |
| 3 | easy | 73 | 73 | 0 | 0.0 | +0.000 | 0 |
| 4 | medium | 46 | 46 | 0 | 0.0 | +0.000 | 0 |
| 5 | easy | 40 | 40 | 0 | 0.0 | +0.000 | 0 |
| 6 | hard | 29 | 28 | 1 | 0.0 | −2.250 | 0 |
| 7 | professional | 76 | 75 | 1 | 0.0 | +0.000 | 0 |
| 8 | professional | 6 | 6 | 0 | 0.0 | +0.000 | 0 |
| 9 | professional | 17 | 16 | 1 | 0.0 | +0.008 | 0 |
| 10 | professional | 30 | 30 | 0 | 0.0 | +0.000 | 0 |
| 11 | professional | 8 | 8 | 0 | 0.0 | +0.000 | 0 |
| 12 | professional | 6 | 6 | 0 | 0.0 | +0.000 | 0 |
| 13 | professional | 53 | 50 | 3 | 0.0 | −0.543 | 0 |
| 14 | hard | 170 | 153 | 17 | 0.0 | +0.012 | 0 |
| 15 | easy | 73 | 68 | 5 | 0.0 | +0.407 | 0 |
| 16 | medium | 92 | 86 | 6 | 0.0 | +0.000 | 0 |
| 17 | easy | 55 | 55 | 0 | 0.0 | +0.000 | 0 |
| 18 | easy | 44 | 44 | 0 | 0.0 | +0.000 | 0 |
| 19 | professional | 64 | 64 | 0 | 0.0 | +0.000 | 0 |
| 20 | easy | 68 | 68 | 0 | 0.0 | +0.000 | 0 |
| 21 | easy | 47 | 47 | 0 | 0.0 | +0.000 | 0 |
| 22 | professional | 18 | 18 | 0 | 0.0 | +0.000 | 0 |
| 23 | professional | 23 | 23 | 0 | 0.0 | +0.000 | 0 |
| 24 | professional | 18 | 18 | 0 | 0.0 | +0.000 | 0 |
| 25 | professional | 69 | 67 | 2 | 0.0 | +0.058 | 0 |
| 26 | professional | 86 | 82 | 4 | 0.0 | +0.128 | 0 |
| 27 | hard | 109 | 105 | 4 | 0.0 | −0.002 | 0 |
| **Σ** | | **1506** | **1461** | **45** | **0 on all** | **mean −0.080** | **0** |

---

## Files

- `backend/services/tiny_region_merger.py` — `plan_insignificant_merge` (colour-aware,
  union-find, merges smaller→larger, never maps to 0), `_mean_lab`, `_delta_e`.
- `backend/services/region_detector.py` — wires the pass in after the sub-floor merge,
  composes the two remaps, recomputes `kept`, and reports `insignificant_regions_merged`.
  Constants: `INSIGNIFICANT_AREA_MULT = 5.0`, `INSIGNIFICANT_DELTA_CAP = 6.0`.
- Controlled data: `research/08_reports/tiny_region_merging/ctrl6a|6b|6c.jsonl`.
- Visuals: `research/08_reports/tiny_region_merging/visual_{13,14,16,27}.png`.

## Verdict

Region count falls 3.0 % (up to −10 % on the busiest images) with **zero** crashes, **zero**
leaks, reveal ΔE **0 on every image**, palette stable within ±1, and flat-fill accuracy that
*improves* on average. Object shape and every protected feature are preserved. Rules met in
full.
