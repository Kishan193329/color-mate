# ColorForge AI — Architecture

## Overview

ColorForge AI converts an uploaded image into a playable / printable
color-by-number artwork through a deterministic, staged pipeline:

```
Upload → Preprocess → Line Art → Gap Close → Region Detect
       → Mask + SVG → Numbers/JSON + Preview → Export bundle
```

The design goal of Sprint 1 is a **stable skeleton with real seams**: the data
contracts (artifacts + API) are final, while the intelligence inside each stage
is mock and swappable.

## Components

### Frontend (`frontend/`)

- **React + Vite + TypeScript**, styled with **Tailwind**, animated with
  **Framer Motion**.
- `api/client.ts` is the single typed gateway to the backend. No component talks
  to `fetch` directly except through it.
- Pages are thin; they poll or fetch via the client and render artifacts by
  their **download URLs** (`/api/download/{job}/{logical}`).

### Backend (`backend/`)

| Module          | Responsibility                                            |
| --------------- | --------------------------------------------------------- |
| `main.py`       | FastAPI routes, CORS, upload handling, downloads          |
| `pipeline.py`   | Orders the 6 stages, reports progress, handles failures   |
| `job_store.py`  | Persists job state to `storage/jobs/<id>/job.json`        |
| `config.py`     | Paths, artifact name map, step list, CORS origins         |
| `schemas.py`    | Pydantic request/response models                          |
| `services/*`    | One module per stage; pure functions over files/dicts     |

### Storage

File-system only for Sprint 1. Each job is a self-contained folder so it can be
zipped, copied, or later pushed to object storage without schema changes.

## Data contracts

### `regions.json`

```json
{
  "width": 1024,
  "height": 768,
  "region_count": 48,
  "regions": [
    { "id": 0, "x": 0, "y": 0, "w": 170, "h": 96,
      "cx": 85, "cy": 48, "color": "#AABBCC", "number": 1 }
  ]
}
```

### `palette.json`

```json
{ "color_count": 12, "colors": [{ "number": 1, "color": "#AABBCC" }] }
```

### `game_config.json`

```json
{
  "job_id": "abc123",
  "version": 1,
  "canvas": { "width": 1024, "height": 768 },
  "region_count": 48,
  "color_count": 12,
  "difficulty": "medium",
  "assets": { "lineart": "lineart.png", "...": "..." }
}
```

These shapes are stable; real region detection will fill `regions[]` with
organic shapes (paths/contours) instead of mock rectangles, but the keys stay.

## Concurrency model

- `/api/process` returns immediately and runs `pipeline.run` as a FastAPI
  background task.
- The frontend polls `/api/job/{id}` (~700ms) to drive the Processing UI.
- `job_store` uses a coarse in-process lock + atomic file replace for safe
  concurrent reads/writes within one server process. Multi-worker deployments
  should move state to Redis/DB (see roadmap).

## Extension points

To make a stage real, replace the body of the corresponding `services/*.run()`:

- `lineart_generator.run(preprocessed, out)` → real line-art model.
- `region_detector.run(closed, preprocessed)` → contour tracing / segmentation
  returning the same `region_data` dict.
- `gap_closer.run(lineart, out)` → morphological closing tuned for fill safety.

Nothing in `pipeline.py`, the API, or the frontend needs to change.
