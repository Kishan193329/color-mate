# Clean Coloring-Book Line-Art — Local AI Engine

**Complaint (valid).** ColorForge's coloring page looked like garbage: a jagged region-boundary
map, not a real coloring-book line-art like the target reference.

## Diagnosis — two separate problems

1. **The coloring page used region boundaries, not a line-art.** The reveal game's border was
   drawn from the color-region label edges — a *segmentation map* (jagged, ugly), not drawing lines.
2. **Even the line-art itself was bad.** ColorForge's `lineart.png` is **Canny edge-detection**
   (`advanced_lineart_generator`). Canny is gradient-based, so it **over-inks texture** (trees →
   solid black blobs) and **under-inks smooth areas** (the subject's face/clothes left blank), with
   thick inconsistent weight. Classical edge detection **cannot** understand the drawing.

The target look is produced by an **AI line-art model** (subject-aware, clean, consistent).

## Fix — a local AI line-art model (CPU, no API, no GPU, no cost)

`backend/services/ai_lineart_local.py` runs a pretrained line-art model **locally**:

- **`lineart`** (default) — *Informative-Drawings* (17 MB, ~2-4 s/image on CPU). Clean thin ink
  outlines: the exact coloring-book look. **This matches the target.**
- **`anime`** — *Anime2Sketch* (217 MB, ~1 s). A more detailed pencil-sketch alternative.

Weights auto-download once from HuggingFace `lllyasviel/Annotators` into `backend/models/`
(git-ignored). If `torch` or the weights are unavailable, the engine returns `None` and the game
falls back to the region-boundary border — nothing breaks.

## What changed (minimal, safe)

- **`ai_lineart_local.py`** (new) — vendored generator matching the pretrained weights + a simple
  `generate(image) → clean line-art` (auto-download, lazy-load, graceful fallback, ink-crisp
  post-process).
- **`reveal_game_builder.py`** — the coloring-page border is now the **AI line-art** (alpha-blended
  black ink on top), falling back to region boundaries only if the model is unavailable. *Fixes
  Problem 1.*
- **`pipeline.py`** — a best-effort `_apply_ai_lineart` pass writes `ai_lineart.png` on every run so
  new uploads get the clean line-art automatically.

**Crucially, this is DISPLAY-ONLY.** It does **not** touch the classical `lineart.png` that region
detection depends on, so regions, numbers, reveal masks and the pixel-identity guarantee are all
unchanged (verified: final content == original, 0-pixel diff).

## The full flow (any uploaded image → clean coloring page)

1. Upload `original.png`.
2. Pipeline processes it (regions, numbers, reveal masks — as before).
3. **`_apply_ai_lineart`** runs the local model → `ai_lineart.png` (clean coloring-book outline).
4. `/api/job/{id}/play` builds the game: **AI line-art = the border**, color regions = the numbered
   cells, `reveal_master` = the reveal source.
5. Player taps a region → its original pixels reveal under the clean line-art → completed = original.

## Results

**Before → after** (`research/08_reports/ai_lineart/`):

- `compare_br14.png` — current Canny (heavy blobs, blank subject) vs Anime2Sketch vs
  **Informative-Drawings** (clean, matches target).
- `reveal_ai_border_14.png` — the game with the new border: clean coloring page → partial reveal →
  final original + ink.
- `coloring_page_16.png` — a landscape scene, proving it generalizes across image types.

| Check | Result |
|-------|--------|
| Border source | **`ai_lineart`** (clean line-art) on portrait / landscape / figure |
| Line-art quality | subject-aware, thin, consistent, no black blobs, no empty subject |
| Ink coverage | ~3.5–6% (clean) vs Canny ~21% (heavy) |
| Speed (CPU) | ~2–4 s / image |
| **Final content == original (0-pixel diff)** | **preserved** |
| Fallback when model unavailable | region-boundary border (no crash) |
| Region / number / reveal logic | **unchanged** |

## Notes

- `torch` (CPU) is now installed and is an **optional** runtime dependency — the code degrades
  gracefully without it.
- Model weights (`backend/models/`, ~234 MB) are git-ignored and auto-download on first use.
- Play it: **`http://127.0.0.1:8000/api/job/br_14/play`** (or the ▶ play link in the admin gallery).
