# Engineering Sprint — Router-Driven Defaults

**Date:** 2026-07-08 · **Validation:** all 27 batch images · **AI:** local
**Goal:** use `image_type_report.json` to pick a **safe default mode / start profile** per upload —
**without changing any algorithm.** The router now *decides the starting point*; every border, reveal,
region, palette and Quality-Fix engine is untouched.

**Result: 27 / 27 complete · 0 crashes · 0 leaks · reveal == original on all 27 · 27 PASS.**
Exactly **one** final sheet changed vs the prior batch — a Sketch simplified from `professional` → `hard`,
which is precisely the intended effect. All other 26 outputs are byte-identical.

---

## 1. What changed (defaults only)

The image-type router now emits a **safe default recommendation**, and the quality-retry engine uses it
as the **starting profile** for its existing Medium-first search. Nothing downstream of the starting
point changed — the escalate / step-down / keep-the-best-score logic is identical.

**New fields in `image_type_report.json`** (task 2):

| field | meaning |
|---|---|
| `recommended_mode` | primary product mode for this type — `reveal` (photographic / intricate) or `auto` (clean cartoons) |
| `recommended_profile` | retry **start** profile — `easy` (intricate/minimal) or `"auto"` (= explore from Medium) |
| `reason` | one-line human explanation |

**Recommendation table** (`image_type_router.recommend`, task 1):

| type | mode | start profile | rationale |
|---|---|---|---|
| Portrait | reveal | medium | photographic — reveal reproduces the original; medium keeps overhead modest |
| Landscape | reveal | medium | detailed scene — reveal reliable; medium avoids an over-complex sheet |
| Illustration | auto | *auto* → medium | clean cartoon — auto-profile picks best flat-fill fidelity |
| Anime | auto | *auto* → medium | flat cel art — auto-profile |
| Object | auto | *auto* → medium | single subject — auto-profile |
| **Mandala** | reveal | **easy** | intricate radial pattern — start simple to avoid an over-complex sheet |
| **Sketch** | reveal | **easy** | line drawing / few colours — simplest profile |
| **Logo** | reveal | **easy** | few colours, plain background — simplest profile avoids over-splitting |

`recommended_profile == "auto"` maps to a **Medium** start (`auto_profile_selector.recommended_start`),
i.e. the pre-sprint behaviour, unchanged. Only **Mandala / Sketch / Logo** get a genuinely different
(`easy`) start. Reveal remains the primary mode for photographic types (rules 5–7).

**Files changed**
- `backend/services/image_type_router.py` — `RECOMMENDATION` table + `recommend()`, folded into
  `classify()`; `image_type_report.json` version → 2.
- `backend/auto_profile_selector.py` — `recommended_start(report)` maps a router report to a valid start
  profile (`"auto"`/unknown → `medium`).
- `backend/quality_retry_engine.py` — `process_upload` routes the image up front, derives the start from
  the router (overridable via an explicit `start=`), and records the recommendation in
  `auto_profile_report.json` (version → 2: `image_type`, `recommended_mode/_profile`, `router_reason`,
  `start_profile`).

No border, reveal, region-generation, palette, or Quality-Fix code was touched (rules 1–4).

---

## 2. Effect on output — one image, simplified

Because the retry is capped at **3 attempts** and escalates "up" from its start, a simpler start makes
intricate/minimal types **settle on a simpler final sheet** instead of climbing all the way to
`professional`. Over the 27:

| start profile | n | of which final sheet changed vs prior batch |
|---|---|---|
| medium (Portrait/Landscape/Illustration/Anime — unchanged) | 25 | 0 |
| easy (Mandala #03, Sketch #06) | 2 | 1 |

- **Sketch #06:** start `easy` → attempts `easy → medium → hard` (all read TOO_FLAT) → keeps **`hard`**
  (best score 34.7). Prior batch started `medium` → `medium → hard → professional` → kept `professional`.
  Net: a **simpler** color-by-number sheet (`hard` < `professional`), which is exactly rule 7. The reveal
  completion is still the exact original (ΔE 0.0), so nothing is lost for the player.
- **Mandala #03:** start `easy` → chosen `easy` — the same winner the prior batch reached from a `medium`
  start, so its final output is **byte-identical**; only the path there is shorter.
- **Logo:** no Logo images in this batch, so the `easy` Logo default is defined but not exercised here.

Honest note: the Sketch's simplification comes from the **attempt cap** meeting an earlier start, not from
the quality signals preferring fewer colours (the sketch reads TOO_FLAT at every profile — it *wants* more
fidelity in flat-fill mode). The router deliberately biases such types to start simpler so bounded retries
land on a less-overwhelming sheet; reveal mode remains their recommended primary mode regardless.

---

## 3. No visual change on unchanged-start images (byte-identical proof)

For the 25 `medium`-start images the start profile is unchanged, so the deterministic pipeline
(`cv2.setRNGSeed(0)`) must produce identical output. Verified two ways:

1. **Same winner across all 25** — every `medium`-start image chose the **same profile** as the prior
   (zero-crash) batch (Illustration → professional/hard/easy, Portrait/Landscape → their prior winners).
2. **Byte-identical snapshot** — the cat (Illustration → `medium` start) run through the full
   `process_upload` on this sprint's code vs the **git-stashed pre-sprint code**: **all 17 product +
   reveal artifacts identical, 0 different** (regions, palette, labels, game_config, result.zip members,
   `reveal_blank/_completed`, `reveal_validation`, border/artistic reports).

⇒ the change only moves the **starting point** of the search; it alters no pixel of an unchanged-start
image, and no algorithm anywhere.

---

## 4. Validation — all 27 images

| Metric | Result |
|---|---|
| completed | **27 / 27** |
| crashes / errors | **0** |
| **PASS** (done · 0 leaks · reveal == original) | **27 / 27** |
| reveal completed == original | **all** (max ΔE **0.0**) |
| leaks | **0 on every image** |
| final sheet changed vs prior batch | **1** (Sketch #06, simplified) |
| avg time | 62 s / image |

Router type distribution (unchanged from the zero-crash batch — classification wasn't touched):
Illustration 14 · Landscape 6 · Portrait 4 · Mandala 1 · Sketch 1 · Anime 1.

Per-image data: `research/08_reports/router_defaults/batch_rd_summary.csv` (+ `batch_rd.jsonl`, `batch_rd.log`).
Samples: `sample_image_type_report_{mandala,sketch,illustration}.json` and the matching
`sample_auto_profile_report_*.json` (showing `recommended_*` / `start_profile` / `chosen_profile`).
Harness: `batch_rd.py`. PASS = the job completes, has zero leaks, and the reveal-mode completed canvas
equals the original — true for all 27.

---

## 5. Scope honoured

1. Border algorithms — **unchanged.** 2. Reveal mode — **unchanged** (reveal == original on all 27).
3. Region generation — **unchanged.** 4. Router **only** decides the default profile / mode.
5. Reveal stays primary for photos/portraits. 6. Illustration/anime/cartoon use auto-profile (Medium
start). 7. Mandala/sketch/logo start from a simpler profile — demonstrated by the Sketch settling on a
simpler final sheet.

## Recommended next
- Replace the heuristic classifier (or add face detection) so photoreal Portraits stop reading as flat
  Illustrations — the recommendation table already routes Portrait → reveal/medium, so better
  classification directly improves defaults with no further wiring.
- Consider surfacing `recommended_mode` in the UI as the pre-selected mode (reveal vs auto flat-fill),
  now that the report carries it.
