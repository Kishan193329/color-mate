# Cat Split Preview Report — Quality Fix 6

Turns the Quality Fix 5 split *recommendations* into a **visual advisory preview**:
a suggested split line per suspected-merged region, drawn over the original
image. **Advisory only — no region is split, no palette or numbers change.**

- Detector/preview: [`backend/services/region_split_detector.py`](backend/services/region_split_detector.py)
- Report: [`border_quality_report.json`](border_quality_report.json)
- Preview: [`research/07_validation/cat_split/split_preview_map.png`](research/07_validation/cat_split/split_preview_map.png)

## Scope compliance
✔ **No auto-split** — regions unchanged (66). ✔ Palette unchanged (12) ·
✔ Numbers unchanged (66/66) · ✔ UI unchanged (preview is a backend artifact,
auto-downloadable via the existing route).

---

## What the preview shows

`split_preview_map.png` layers, per the task:
1. **Original image** as the background (lightened for contrast).
2. **Line art** overlaid for context.
3. **Suspect region** highlighted (red tint).
4. **Suggested split line** drawn boldly in red, **clipped to its region**.

### Suggested split line per region

| Region | Split-line source | Endpoints (image px) |
| ---: | --- | --- |
| **7** (head/upper fur) | geometry-bisector | `[486, 1277] → [750, -485]` |
| **68** (body/chest fur) | geometry-bisector | `[1223, 590] → [25, 1132]` |
| **81** (front-legs fur) | geometry-bisector | `[902, 829] → [73, 1114]` |

The lines are drawn only where they cross their region, so the visible guide is
the in-region segment: a near-vertical bisector down the face (region 7) and
diagonal bisectors across the chest (68) and front legs (81).

### How a split line is chosen
- **Coherent missing border** — if a strong source edge crosses the region
  interior (a real missing divider), the line is **fit to that edge** (source
  `internal-edge`), pinpointing where to cut.
- **Geometry bisector** — otherwise (high internal colour variance but no single
  hard edge, as with the cat's shaded fur), the guide is a line **perpendicular to
  the region's major axis through its centroid** (source `geometry-bisector`) —
  an honest "consider splitting across here" hint.

All three cat suggestions are `geometry-bisector`: the fur regions vary by tone,
not by a hard undrawn line, so the preview offers a sensible bisecting guide
rather than pretending to know an exact border.

---

## `border_quality_report.json` (QF6 fields)

```json
{
  "split_preview_available": true,
  "split_preview_count": 3,
  "split_recommendations": [
    {
      "region_id": 7,
      "reasons": ["over-large region with high internal colour variance"],
      "split_line": [[486, 1277], [750, -485]],
      "split_line_source": "geometry-bisector"
    }
    /* + regions 68 and 81, each with a split_line */
  ]
}
```

(Retains all QF3 border metrics, QF4 leak fields, and QF5 merged-region fields.)

---

## Verification
- Full `pipeline.run` on the cat: `split_preview_available = true`,
  `split_preview_count = 3`, `split_preview_map.png` produced; **palette still 12,
  66/66 regions numbered** — everything downstream untouched.
- The preview was visually reviewed: three clear red split guides over the
  original cat, each inside its suspect region.
- The line generator's `internal-edge` mode is exercised by the QF5 synthetic
  (flat-colour merge), where the split line is fit to the actual colour boundary;
  the cat exercises the `geometry-bisector` fallback.

## Notes & next step
- **Purely advisory** — `split_preview_*` fields and the map do not affect
  `final_pass`, palette, numbers, or regions. Nothing is cut.
- A future Quality Fix could let a user **accept** a previewed split (applying it
  via the existing Phase-10 split tool at the shown line) — always behind explicit
  confirmation. QF6 stops at the preview.
