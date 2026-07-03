# Cat Missing-Split Report — Quality Fix 5

Detection (only) of **fully-merged leaks** — where a missing border made two
intended areas a single connected region. This is exactly the case Quality Fix 4
could not see: a flood fill stays inside a merged region, so there is no escape to
observe. QF5 instead reads evidence in the **original colour image** that the line
art failed to capture.

- Detector: [`backend/services/region_split_detector.py`](backend/services/region_split_detector.py)
- Report: [`border_quality_report.json`](border_quality_report.json) (v3)
- Map: [`research/07_validation/cat_split/merged_region_map.png`](research/07_validation/cat_split/merged_region_map.png)

## Scope compliance
✔ **Nothing is split** — detection only. ✔ Palette unchanged (12 colors) ·
✔ Numbers unchanged (66/66) · ✔ UI unchanged · ✔ Region count unchanged (66).

---

## Method — merged evidence, multiple signals

For every interior region, measured on the untouched working image:

| Signal | What it catches |
| --- | --- |
| **area outlier** | region far larger than its peers (a merge swallows area) |
| **internal colour variance** (Lab std) | one region spanning clearly different colours |
| **internal edge evidence** | a coherent source edge crossing the region interior that was never drawn as a border (a missing divider) |
| **shape complexity** (compactness) | dumbbell / concave shape from two joined blobs |
| **local contrast** | tonal spread inside the region |

A region is flagged only when **multiple signals agree** — a lone soft edge or a
little texture is not enough. The strongest signal, *internal edge span* (a
coherent line crossing the interior), pinpoints where a border is missing.

---

## Result — cat

| Field | Value |
| --- | --- |
| **suspected_merged_region_count** | **3** |
| **suspected_merged_region_ids** | `[7, 68, 81]` |
| split_recommendations | 3 (below) |

### Split recommendations

| Region | Area | area_ratio | color_std (Lab) | edge_span | compactness | Reason |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| **7** (head/upper fur) | 227,562 | 77.4× | 20.8 | 0.17 | 8.2 | over-large region with high internal colour variance |
| **68** (body/chest fur) | 101,975 | 34.7× | 20.3 | 0.09 | 5.9 | over-large region with high internal colour variance |
| **81** (front-legs fur) | 38,744 | 13.2× | 23.5 | 0.12 | 2.9 | over-large region with high internal colour variance |

`merged_region_map.png` tints the three suspects red and overlays the **internal
edge evidence in orange** — the tabby markings, whisker shading, and fur tone
transitions inside each region that were not captured as borders.

### Interpretation (honest)
- All three flags are the cat's **large fur regions**, which contain real internal
  colour variation (shading + tabby markings) with **no hard dividing line**
  (`edge_span` is low, 0.09–0.17). They are genuine **split candidates** — a
  richer color-by-number could separate lighter chest / shaded flanks — but they
  are *not* hard merged leaks.
- **No hard merged leak** was found on the cat: the "coherent missing border"
  rule (high `edge_span` + colour change) did **not** fire, which is correct — the
  cat is a clean illustration whose regions are separated by drawn lines; the only
  internal variation is gradient/textured fur.
- The two eye regions were **not** flagged (an earlier prototype did; the rule now
  ignores small, below-median regions so detailed features aren't mistaken for
  merges).

---

## Detector has teeth — synthetic proof

Since the cat has no hard merge, correctness was verified on controlled cases:

| Case | suspected | detail |
| --- | ---: | --- |
| Box with **two flat colours** and a **missing divider** | **1** | reason: "coherent source edge crosses the region interior with a colour change (missing border)"; `edge_span 0.47`; **split located** at the divider (`suggested_split_near_bbox ≈ [299,85,1,231]`) |
| Same box, **single flat colour** (no leak) | **0** | no false positive |
| Small detailed features (eyes) | **0** | ignored (below-median area) |

So the detector reliably (a) **finds and locates** a true missing-border merge
between flat colours, (b) does **not** false-positive on a single-colour region or
small detailed features, and (c) surfaces large high-variance regions as
split candidates with evidence.

---

## `border_quality_report.json` (QF5 fields)

```json
{
  "suspected_merged_region_count": 3,
  "suspected_merged_region_ids": [7, 68, 81],
  "split_recommendations": [
    {
      "region_id": 7, "area": 227562, "bbox": [308, 73, 659, 602],
      "reasons": ["over-large region with high internal colour variance"],
      "evidence": {"area_ratio": 77.4, "color_std": 20.78, "internal_edge_span": 0.168,
                   "internal_edge_density": 0.0202, "compactness": 8.16, "contrast": 0.121},
      "suggested_split_near_bbox": [650, 504, 141, 50]
    }
    /* + regions 68 and 81 */
  ]
}
```

(The report also retains QF3 border metrics and QF4 leak fields.)

---

## Notes & next step
- **Detection only** — no region was split; the recommendations are advisory and
  do not affect `final_pass`, palette, or numbers.
- Region ids are the border-graph's internal component labels (same convention as
  QF4's `leaking_region_ids`), used for diagnosis.
- A future Quality Fix could act on these recommendations (assisted or manual
  split at the suggested location), reusing the existing Phase-10 split tool —
  still gated behind human confirmation.
