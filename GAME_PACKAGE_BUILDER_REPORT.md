# Game Package Builder — Report

**Goal.** Produce the exact package the Android app consumes after admin approval:
`game_package.json` (the Android-readable index) + `game_assets.zip` (the assets). It reads
existing pipeline outputs only — **no processing algorithm changes** — and **only approved (or
published) images can be packaged**.

---

## What the package contains

`game_assets.zip` bundles the manifest plus every asset under a flat `assets/` path:

| Package component | Asset(s) |
|-------------------|----------|
| original / reveal master | `assets/original.png`, `assets/reveal_master.png` |
| border SVG / PNG | `assets/border.svg` (artistic outline), `assets/border.png` (rasterised to match) |
| numbered preview | `assets/numbered_preview.png` |
| labels / masks | `assets/labels.npy`, `assets/region_mask.png`, `assets/reveal_{blank,partial,highlight,completed}.png` |
| palette / navigation colours | `assets/palette.json` (also inline in the manifest) |
| region metadata | `assets/regions.json`, `assets/game_config.json` |
| category metadata | in the manifest (`category`) |
| thumbnail | `assets/thumbnail.png` |
| difficulty / playtime / fun | in the manifest (`scores`) |
| review status | in the manifest (`review_status`) |

`game_package.json` is the single index the app reads:

```json
{
  "schema_version": 1,
  "job_id": "...", "image_id": "img_…",
  "review_status": "approved",
  "complete": true, "missing_assets": [],
  "category": { "name": "...", "title": "...", "tags": [...] },
  "dimensions": { "width": 1254, "height": 1254 },
  "counts": { "regions": 94, "palette": 22 },
  "scores": { "difficulty": "hard", "estimated_playtime_min": 16, "fun_score": 39,
              "complexity_score": 58, "border_quality_score": 79, "artistic_score": 48 },
  "palette": [ { "id": 1, "number": 1, "color": "#1B2325" }, ... ],
  "assets": { "original": "assets/original.png", "border_svg": "assets/border.svg", ... },
  "integrity_sha1": { "assets/original.png": "…", ... },
  "created_at": "..."
}
```

- **Android-readable paths** — every asset path is a relative, forward-slash path into the zip
  (`assets/…`); no absolute or Windows paths.
- **Integrity** — a sha1 per asset; the zip entries are verified against it.
- **Scores** — difficulty and playtime from the review layer; the **fun score** from the HVI
  mean fun-to-color (with a review-based fallback).
- **Approval gate** — `build_game_package` raises unless the publish status is `approved` or
  `published`. Publishing an image auto-builds its package.

---

## Validation — the cat image + all 27

`cat.png` was driven through a fresh pipeline run, then approved and packaged; the 27 finished
jobs were approved and packaged. (`research/08_reports/game_package/game_package_validation.json`)

| Check | Result |
|-------|--------|
| Packages built | **28** (cat + 27) |
| Crashes | **0** |
| Non-approved image refused | **yes** |
| Package complete (all required assets) | **28 / 28** |
| Android-readable asset paths | **28 / 28** |
| **Reveal completed-image ΔE = 0** | **28 / 28** |
| `game_package.json` + `game_assets.zip` present | **28 / 28** |
| Zip is a valid archive | **28 / 28** |
| Zip entries match the manifest sha1 | **28 / 28** |

Each zip is a complete, self-contained, ~8–12 MB bundle (manifest + 15 assets). The cat
package built end-to-end from a fresh pipeline run
(`research/08_reports/game_package/sample_game_package_cat.json`).

---

## API

| Method + endpoint | Purpose |
|-------------------|---------|
| `POST /api/job/{id}/package` | build the game package (refused unless approved → 409) |
| `GET /api/job/{id}/package` | the `game_package.json` manifest |
| `GET /api/job/{id}/package/download` | download `game_assets.zip` |

Publishing an image (`POST …/publish/publish`) also builds its package automatically.

## Files

- `backend/services/game_package_builder.py` — the builder (approval gate, asset collection,
  border-PNG rasterisation, scores, manifest + zip with integrity hashes).
- `backend/main.py` — package build / get / download endpoints; auto-build on publish.
- Validation: `research/08_reports/game_package/` (`game_package_validation.json`,
  `sample_game_package_cat.json`, `batch_game_package.py`, `smoke_gpb.py`).

## Verdict

Approved images now produce the exact Android bundle — original + reveal master, artistic
border SVG/PNG, numbered preview, labels/masks, palette/navigation colours, region metadata,
game_config, category, thumbnail, difficulty/playtime/fun scores and the review status — as a
single `game_package.json` index plus an integrity-checked `game_assets.zip`, with only-approved
gating, reveal ΔE = 0, and no processing algorithm change.
