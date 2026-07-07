# Engineering Sprint — Zero Crash & Image Router

**Date:** 2026-07-07 · **Validation:** all 27 batch images · **AI:** local
**Goal:** before adding any new AI, make the engine production-stable — fix every crash and add an
`ImageTypeRouter` foundation. **Stability only:** no visual algorithm, border, palette, or Quality-Fix
change (Medium output verified byte-identical).

**Result: 27 / 27 complete · 0 crashes · 27 PASS.**

---

## 1. Zero crash

The 27-image batch previously failed on 2 images (#08, #12) with
`Processing failed: 'split_preview_available'`.

- **Root cause:** `region_split_detector.detect()` (QF5) has an early-return for the degenerate case
  *"no interior regions ≥ min_region"* — which happens when the line art is a bare silhouette (a photo
  on a smooth background). That early-return omitted two keys (`split_preview_available`,
  `split_preview_count`) that its normal return provides and that `border_engine` reads
  (`border_engine.py:275`) → `KeyError`.
- **Fix (stability only):** the early-return now returns the **full contract** — the two missing keys as
  `False` / `0`, which is the correct value (a degenerate image has nothing to split). No detection
  logic, border, palette, or visual output changes; the normal path is untouched.

**Verification:** both formerly-crashing images now complete (`done`), and the full batch is
**27/27 done, 0 crashed, 0 errored**.

| | before | after |
|---|---|---|
| completed | 25 / 27 | **27 / 27** |
| crashes / errors | 2 | **0** |

## 2. ImageTypeRouter

New `services/image_type_router.py` classifies an upload into one of eight types —
**Illustration, Anime, Portrait, Landscape, Mandala, Object, Logo, Sketch** — from cheap global
statistics (fraction-based flatness/detail, saturation, edges, colour count, skin fraction, background
uniformity, centred rotational symmetry, sky), and writes **`image_type_report.json`**
(type, confidence, per-type scores, and all features). It is wrapped so it can never raise — on any
failure it returns a safe `Illustration` default.

Pipeline order (task 4): **Input → Router → Processing → Reveal.** The router runs right after
preprocessing and **changes nothing downstream** — it is reporting only. Sample
(`research/08_reports/zero_crash_batch/sample_image_type_report_cat.json`): the cat → `Illustration`.

Router type distribution over the 27:

| type | n |
|---|---|
| Illustration | 14 |
| Landscape | 6 |
| Portrait | 4 |
| Mandala | 1 |
| Sketch | 1 |
| Anime | 1 |

**Honest accuracy note.** This is a **v1 heuristic foundation**, not a trained model. It is reliable on
the structural signals — mandala (centred 90°/180°/mirror symmetry), landscape (sky + detail), sketch
(grayscale + white), logo (few colours + plain background) — and it correctly sends the flat cartoon
subjects (cat, kittens, horses, dolphins) to **Illustration**. It is **weak on photoreal portraits**:
women on smooth gradient backgrounds read as flat illustrations, and a warm-colour skin range cannot
tell orange fur from skin. Separating Portrait/Anime reliably needs face detection or a small learned
classifier — which is exactly what this router foundation is built to receive when AI is added. It
affects no output today, so its accuracy is not on the critical path for stability.

---

## Validation — all 27 images

| Metric | Result |
|---|---|
| completed | **27 / 27** |
| crashes | **0** |
| **PASS** (done · 0 leaks · reveal == original) | **27 / 27** |
| reveal completed == original | **all** (max ΔE **0.0**) |
| leaks | **0 on every image** |
| avg time | 51 s / image |

Per-image data: `research/08_reports/zero_crash_batch/batch_zc_summary.csv` (+ `batch_zc.jsonl`).
PASS here = the job completes, has zero leaks, and the reveal-mode completed canvas equals the original
(the product's success condition) — true for all 27.

### No visual change (byte-identical proof)
Snapshot diff, reveal-mode commit vs this sprint (Medium, cat.png): **every product and brain artifact
is byte-identical** (game_config / result.zip differ only by embedded timing). The crash fix touches
only the degenerate no-interior-regions path (a normal image is unaffected), and the router only adds
`image_type_report.json`. ⇒ **no visual / border / palette / Quality-Fix change.**

---

## Files changed
- `backend/services/region_split_detector.py` — early-return now returns the full contract (crash fix).
- `backend/services/image_type_router.py` (new) — 8-type classifier + `image_type_report.json`.
- `backend/pipeline.py` — router runs after preprocessing (Input → Router → Processing → Reveal).
- `backend/config.py` — `image_type_report` artifact registered.
- `research/08_reports/zero_crash_batch/` — batch results (CSV, JSONL, log) + a sample router report.

## Recommended next
- Replace the heuristic router with a small learned classifier (or add face detection) to fix the
  Portrait/Anime separation — the intended "add AI" step now that the engine is crash-free.
- Once the router is trusted, let it *drive* profile/mode selection (e.g. force reveal for portraits,
  raise fidelity for mandalas) — the routing wire is already in place.
