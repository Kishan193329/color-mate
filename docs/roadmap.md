# ColorForge AI — Roadmap

## Sprint 1 — Foundation ✅ (this sprint)

- End-to-end flow: Upload → Process → Editor → Export.
- 6 backend APIs, file-system job store, 6-stage mock pipeline.
- All 9 service modules stubbed with real signatures and demo output.
- 6 frontend pages with premium Canva/Figma-style UI.

## Sprint 2 — Real line art & regions

- Replace `lineart_generator` with a real edge/line-art model.
- Replace `region_detector` with contour tracing producing closed organic
  regions (no color-quantization vectorization).
- Tune `gap_closer` for fill-safe boundaries.
- Editor: interactive region select, merge/split, recolor; zoom/pan canvas.

## Sprint 3 — Texture fill

- Per-region texture/painterly fills feeding the "Texture Fill" pipeline stage.
- Brush + fill tools, undo/redo, palette editing.

## Sprint 4 — SAM2 segmentation (optional path)

- Add SAM2-powered region proposals as an alternative `region_detector` backend.
- Toggle between heuristic and SAM2 detection per job.

## Sprint 5 — Scale & accounts

- Move job storage from local filesystem to object storage (S3/GCS).
- Job state to Redis/Postgres; multi-worker safe.
- User accounts, auth, sharing, and a real Dashboard backend.

## Known Sprint 1 simplifications

- Mock stages use PIL filters / grid sampling — outputs are illustrative.
- `STEP_DELAY_SECONDS` in `pipeline.py` adds artificial delay so the step
  animation is visible; remove once real (slower) stages land.
- Single-process job store; not safe across multiple uvicorn workers.
