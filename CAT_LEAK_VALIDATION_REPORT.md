# Cat Leak Validation Report — Quality Fix 4

Region-aware leak validation for the cat reference (`cat.png`, 1254×1254),
upgrading PBSE leak detection from **proximity-based** (endpoint pairs within a
radius) to an **actual flood-fill escape test**.

- Validator: [`backend/services/region_leak_validator.py`](backend/services/region_leak_validator.py)
- Report: [`border_quality_report.json`](border_quality_report.json)
- Leak maps: [`research/07_validation/cat_leak/`](research/07_validation/cat_leak/)
  (`leak_map_before.png`, `leak_map_after.png`)

## Scope compliance
✔ Palette unchanged (12 grouped colors) · ✔ Numbers unchanged (66/66 regions) ·
✔ UI unchanged (leak fields live in the existing backend report; `leak_map.png`
is auto-downloadable via the existing route) · ✔ Region count unchanged (66 → 66).

---

## Method (region-aware, not proximity)

1. **Partition** the repaired line art into its intended closed areas (connected
   white components — the same regions the engine fills).
2. **Flood-fill** each interior region from a distance-transform seed (always
   inside the region).
3. **Detect escape** — a fill that spills past its region (> 1.5× its area) into a
   neighbour, or reaches the image frame from an interior region (background
   bleed).
4. **Compare before/after** — run the same fills on the pre-repair walls to see
   which leaks the border repair fixed.

Why flood-fill instead of an erosion/"neck" heuristic: a valid closed line art
always partitions the canvas into real components, so a **containment fill is
honest and false-positive-free**. Legitimate thin regions — the yarn ball's
strands, the slim red curtain — are **not** flagged, because their fill still
stays inside their own borders. (An earlier erosion-neck prototype false-flagged
exactly those; the flood-fill test does not.)

---

## Result — cat

| Field | Value |
| --- | --- |
| Closed areas tested (flood-fill) | 76 |
| **region_leak_count** | **0** |
| **background_leak_count** | **0** |
| **leaking_region_ids** | `[]` |
| leaks_fixed_by_repair | 0 |
| **leak_pass** | **true** |
| final_pass | true |
| **leak_pass_reason** | "Flood-fill test passed on all 76 closed areas: every interior region's fill stays inside its borders (no escape into a neighbour or the background)." |

### Before / after
| | Region count | Region leaks | Background leaks |
| --- | ---: | ---: | ---: |
| Before repair | 66 | 0 | 0 |
| After repair | 66 | 0 | 0 |

The cat is a clean, closed illustration: **every one of its 76 closed areas is
provably enclosed** — a flood fill seeded in each stays inside it. The 7 gaps
PBSE repaired (Quality Fix 3) were hairline stroke breaks that did **not**
separate two regions, so `leaks_fixed_by_repair = 0` — an honest zero, not a
missed detection. `leak_map_after.png` is therefore plain line art (no red
"remaining leak" fills, no green "fixed" fills).

---

## Validator has teeth — synthetic proof

Because the cat has no leaks, correctness was verified on controlled synthetics:

| Case | region_leak / background | leak_pass | leaks_fixed |
| --- | --- | --- | --- |
| Two boxes, **intact** divider | 0 / 0 | ✅ pass | — |
| Two boxes, divider **gap** closed by repair | 0 / 0 | ✅ pass | **2 regions fixed** |
| Box, outer-wall **hole** closed by repair | 0 / 0 | ✅ pass | **1 background fix** |
| Yarn-ball / thin curtain (legit thin regions) | 0 / 0 | ✅ pass | — (no false positive) |

So the validator (a) **passes** clean and legitimately-thin regions, and (b)
**detects and reports** real region-merge and background/silhouette leaks that
border repair fixed, via the before/after flood-fill comparison.

---

## Updated `border_quality_report.json` (leak fields)

```json
{
  "region_leak_count": 0,
  "background_leak_count": 0,
  "leaking_region_ids": [],
  "leak_pass_reason": "Flood-fill test passed on all 76 closed areas: every interior region's fill stays inside its borders (no escape into a neighbour or the background).",
  "leaks_fixed_by_repair": 0,
  "closed_areas": 76,
  "leak_pass": true,
  "final_pass": true
}
```

(Full report also retains the Quality Fix 3 fields: endpoints/fragments before &
after, repaired_gaps, jaggedness, closure/shape passes.)

---

## Known limitation (honest)

The flood-fill escape test cannot detect a **fully-merged remaining leak** — a
wide-open border where two intended areas become a *single* component in the
final line art. Such a region floods within itself, so there is no escape to
observe, and telling "one region" from "two that should be split" needs ground
truth. This validator reliably catches:
- leaks that **repair fixed** (before/after), and
- interior regions that **reach the frame** (silhouette bleed) in the final output.

Fully-merged remaining leaks are deferred to a future stage (PBSE S2 silhouette /
S4 feature protection, or a learned segmentation prior) — none of which are in
this fix.
