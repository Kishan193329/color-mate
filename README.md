# ColorForge AI 🎨

An **AI Color-by-Number Studio** — turn any image into clean line art, detect
paintable regions, place numbers, and export a complete bundle. Built like a
premium Canva/Figma-style flow.

> **Sprint 1 status:** the full pipeline and UI are wired end-to-end. Every
> backend stage currently returns **mock/demo output**, but each service has the
> exact signature its real (ML) implementation will use — so you can swap in the
> real thing one stage at a time without touching the orchestration or UI.

The pipeline is intentionally:

```
Line Art → Closed Regions → Region Masks → Numbers → Texture Fill → Export
```

It does **not** use color-quantization vectorization, and it does **not** depend
on SAM2 yet — those are future phases.

---

## Project structure

```
colorforge-ai/
├── frontend/              # React + Vite + TypeScript + Tailwind + Framer Motion
│   └── src/
│       ├── pages/         # Landing, Upload, Processing, Editor, Export, Dashboard
│       ├── components/    # Layout, Stepper
│       └── api/           # typed backend client
├── backend/               # FastAPI + Python
│   ├── main.py            # API routes
│   ├── pipeline.py        # 6-stage orchestrator
│   ├── job_store.py       # file-system job state
│   ├── config.py          # paths, artifact names, steps
│   ├── schemas.py         # pydantic models
│   └── services/          # one module per pipeline stage (see below)
├── docs/                  # architecture & roadmap
└── storage/jobs/          # per-job artifacts (created at runtime)
```

---

## Install & run

### 1. Backend (FastAPI)

```bash
cd backend
python -m venv .venv
# Windows:  .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Backend runs at <http://localhost:8000> — interactive docs at `/docs`.

### 2. Frontend (Vite + React)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at <http://localhost:5173>. Vite proxies `/api` → the backend on
port 8000 (see `vite.config.ts`), so no CORS juggling in dev.

### 3. Use it

Open <http://localhost:5173>, click **Start Creating**, upload an image, and
watch the pipeline run. You land in the Editor, then Export.

---

## Architecture

### Flow

```
Upload Image → Generate Line Art → Detect Regions → Edit → Export
```

### Backend API

| Method | Endpoint                         | Purpose                                   |
| ------ | -------------------------------- | ----------------------------------------- |
| POST   | `/api/upload`                    | Create a job, save the original image     |
| POST   | `/api/process`                   | Kick off the processing pipeline (async)  |
| GET    | `/api/job/{job_id}`              | Job status, progress, steps, artifacts    |
| GET    | `/api/jobs`                      | List all jobs                             |
| GET    | `/api/download/{job_id}/{file}`  | Download an artifact (logical name)       |

`file` is a **logical name**, not a filename: `original`, `lineart`,
`region_mask`, `outline`, `regions`, `game_config`, `palette`, `preview`,
`result_zip`.

### Processing pipeline (6 stages)

`pipeline.py` runs these in order on a background task, updating job progress
after each. UI shows them live:

1. **Preparing image** — `services/image_preprocess.py`
2. **Creating clean line art** — `services/lineart_generator.py`
3. **Closing gaps** — `services/gap_closer.py`
4. **Detecting regions** — `services/region_detector.py` (+ `mask_generator`, `svg_generator`)
5. **Placing numbers** — `services/json_generator.py` (+ `preview_renderer`)
6. **Preparing export** — `services/zip_exporter.py`

### Generated artifacts (per job)

```
storage/jobs/<job_id>/
├── original.png        # the upload
├── preprocessed.png    # normalised working image
├── lineart.png         # clean line art
├── region_mask.png     # solved color map
├── outline.svg         # vector outline + numbers
├── regions.json        # region geometry / color / number
├── palette.json        # ordered color palette
├── game_config.json    # metadata for the playable game
├── preview.png         # numbered color-by-number sheet
├── result.zip          # everything bundled
└── job.json            # job state (status, progress, steps)
```

### Why mock services?

Each `services/*.py` module is a clean seam. The orchestrator (`pipeline.py`)
and the entire frontend only depend on the **artifacts** and **signatures**, not
the internals. To productionise a stage, replace the body of its `run()` — e.g.
drop a real line-art model into `lineart_generator.run()` — and nothing else
changes.

---

## Frontend pages

| Page         | Route                 | Purpose                                            |
| ------------ | --------------------- | -------------------------------------------------- |
| Landing      | `/`                   | Hero, flow overview, feature cards                 |
| Upload       | `/upload`             | Drag-and-drop upload → start processing            |
| Processing   | `/processing/:jobId`  | Live step-by-step progress (polls the job)         |
| Editor       | `/editor/:jobId`      | View toggles + palette/region/color panel + export |
| Export       | `/export/:jobId`      | Per-asset downloads + full bundle                  |
| Dashboard    | `/dashboard`          | All jobs with previews and quick actions           |

---

## Next phases

- **Sprint 2 — Real line art & regions:** swap mock services for real
  edge/line-art models and contour-based region detection; interactive region
  editing (merge/split, recolor) in the Editor.
- **Sprint 3 — Texture fill:** painterly/texture fills per region; brush and
  fill tools.
- **Sprint 4 — SAM2 segmentation:** optional SAM2-powered region proposals.
- **Sprint 5 — Cloud storage & accounts:** move job storage off the local
  filesystem; user accounts and sharing.

See [`docs/architecture.md`](docs/architecture.md) and
[`docs/roadmap.md`](docs/roadmap.md) for detail.

---

## Optional AI Assist (RunPod)

ColorForge runs fully on the local engine by default. You can optionally offload
the *heavy* AI helper tasks (image cleanup, cleaner line art, semantic hints) to
a RunPod worker. **RunPod is never the region source** — final closed regions
are always derived locally from the line art (`line art → gap close → flood-fill
→ SVG/JSON`), and any RunPod failure automatically falls back to local.

### Environment variables (main backend)

```bash
AI_MODE=local            # or "runpod"
RUNPOD_API_URL=          # e.g. https://<pod>-8100.proxy.runpod.net
RUNPOD_API_KEY=          # optional bearer token
RUNPOD_TIMEOUT_SECONDS=600
```

You can also flip Local ⇄ RunPod live from the in-app **AI** settings page
(`/ai`), which persists the choice and shows status **Ready / Offline /
Fallback**.

### RunPod setup

1. **Create a RunPod pod** (CPU is enough for the reference worker; GPU when you
   add real models).
2. **Upload the worker** — the [`runpod-worker/`](runpod-worker/) folder (or
   build & push its Docker image).
3. **Start the worker** — `uvicorn app:app --host 0.0.0.0 --port 8100`.
4. **Test `/health`** — `curl https://<pod>-8100.proxy.runpod.net/health`
   should return `{"status":"ok", ...}`.
5. **Set `RUNPOD_API_URL`** on the main backend to that URL.
6. **Set `AI_MODE=runpod`** (or switch it from the AI settings page).

The worker exposes `GET /health`, `POST /enhance-image`, `POST /generate-lineart`,
`POST /detect-semantics`, and produces `enhanced.png`, `ai_lineart.png`,
`semantic_map.json`, `confidence.json` — all saved into the job folder. See
[`runpod-worker/README.md`](runpod-worker/README.md). The reference worker uses
OpenCV so it runs with no model downloads; swap in real models without changing
the API.
