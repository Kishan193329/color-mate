# Product Core Pivot — Original-Pixel Reveal Mode

**Date:** 2026-07-07 · **Validation:** cat.png + a fashion portrait + an anime image · **AI:** local
**What changed:** a new **reveal** product mode where completing a region uncovers the *original*
pixels inside that region — the finished picture is the original image itself, not a repaint.
**Additive only:** the flat-fill, palette, region and number systems are unchanged; profiles unaffected.

---

## The model

| Old (flat-fill) | New (reveal — main mode) |
|---|---|
| Fill a region → paint it a flat palette colour | Fill a region → **reveal the original pixels** in that region's mask |
| Completed = a posterised approximation | **Completed = the original image, exactly** |
| Colours *are* the output | Colours/numbers are **navigation hints only** |

The regions are used purely as **masks**; the palette/number system is kept and only tells the player
*which* region to uncover next. When every region is filled, the union of masks reveals the original.

### Core rules (all honoured)
1. **Immutable master** — the reveal source is the original image, resized (area) to the label grid to
   align with the masks. The *denoised* `preprocessed.png` is deliberately not used.
2. **Regions are masks only** — `region_reveal_mask(labels, label) = (labels == label)`.
3. **Fill reveals original pixels** — masked copy from the master; nothing generated.
4. **Palette = hint** — numbers/colours are navigation; they never fill.
5. **Completed = original under the union of masks** — proven ΔE 0 / 0 px diff (below).
6. **Borders & numbers are overlay layers** — never baked into the completed reveal.
7. **No generated colour replaces original pixels** — the master is original pixels, area-resized only.

---

## Preview states (task 3)

`services/original_pixel_reveal_renderer.py` renders four states per job:

1. **Blank coloring page** — white canvas + region outlines + numbers.
2. **Selected-number checker highlight** — highlights every region of a chosen number ("where does
   this number go").
3. **Partial filled preview** — the finished number-groups show **real original pixels**; the rest is
   the blank outlined page.
4. **Completed reveal** — every mask filled → the original image.

**Cat** (`research/08_reports/reveal_mode/cat_reveal_states.png`): blank → highlight (flowers) →
partial (fur/flowers/background uncovered as original pixels) → completed = original, indistinguishable.

**Fashion portrait** (`fashion_reveal_states.png`) — the decisive case: in flat-fill this image
posterised to a dark silhouette (see the 27-image batch); in **reveal mode the completed image is the
exact photoreal original**. Same for the anime image.

---

## Validation (task 4) — completed equals original under the masks

| Image | completed_reveal ΔE | pixel_diff | mask_coverage % | unrevealed % | matches original |
|---|---:|---:|---:|---:|:--:|
| cat | **0.0** | **0** | 89.63 | 10.37 | ✅ |
| fashion portrait | **0.0** | **0** | 92.92 | 7.08 | ✅ |
| anime | **0.0** | **0** | 77.90 | 22.10 | ✅ |

- **completed_reveal_delta_e = 0** and **completed_reveal_pixel_diff = 0** on every image: within the
  union of masks the reveal is *pixel-exact* to the master (rule 5).
- **mask_coverage_percent** = share of the canvas that is fillable region; **unrevealed_pixel_percent**
  = the thin line-art skeleton (label 0), which is the **overlay** layer, not part of the reveal
  (rules 5 & 6). Anime has more/thicker outlines → lower coverage, but the completed reveal still
  equals the original (the seamless completed preview fills the skeleton from the master too).

Because the completed picture is the original by construction, **reveal mode is image-type-agnostic**:
it produces a perfect result on the photoreal/detailed images that flat-fill flattened — directly
addressing the top finding of the 27-image batch test.

---

## Additive integration (tasks 5 & "do not break")

Verified by byte-for-byte snapshot diff (committed engine vs reveal, Medium, cat.png):

- **Every flat-fill artifact is identical** — `regions.json`, `palette.json`, `outline.svg`,
  `region_mask.png`, `preview.png`, `texture_fill_preview.png`, **`game_preview.png`**, `labels.npy`,
  `quality_report.json`, all QF7–QF14 reports, and `result.zip` (members identical modulo timing).
- **`game_config.json` changes are purely additive**: adds `default_mode: "reveal"`, `modes`
  (`primary: reveal`), a `reveal` manifest, and appends reveal assets. The pre-existing `regions`,
  `palette`, `fill_mode` (still `"palette"`), and `stats` are **identical**.
- **Profiles unaffected** — reveal is post-processing on `region_data`; it changes no region/palette/
  number logic, so all five output profiles behave exactly as before (flat-fill Medium byte-identical).

Reveal runs best-effort (wrapped in try/except) at the end of `_process` and of the region-edit
rebuild, so it never blocks the export and always reflects the final (post-split) regions.

---

## How the reveal master is built (rule 1 & 7)

`preprocessed.png` is the original **resized + bilateral-denoised**, so it is not the original's pixels.
The renderer instead loads the original (EXIF-correct) and **area-resizes it to the label grid** — no
denoise, no generated colour — and reveals from that. When the original is ≤ 1600 px it is used as-is.
The completed reveal therefore matches the uploaded image "as closely as technically possible" (exact
at the working resolution).

---

## Files changed
- `backend/services/original_pixel_reveal_renderer.py` (new) — reveal master, per-region masks, the 4
  preview states, and the validation metrics.
- `backend/pipeline.py` — `_apply_reveal()` renders the reveal artifacts and advertises reveal as the
  primary mode in `game_config` (additive); called at the end of `_process` and the rebuild.
- `backend/config.py` — six `reveal_*` artifact names registered (downloadable/discoverable).
- `research/08_reports/reveal_mode/` — cat & fashion state montages + validation JSONs.

*Flat-fill mode, palette/region/number systems, and all five output profiles are unchanged. Reveal is
the new primary product mode.*
