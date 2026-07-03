# ColorForge AI — RunPod Worker

Optional AI helper service. It improves the **input** to ColorForge (image
cleanup, cleaner line art, semantic hints) but is **never** the region source —
the main backend always derives final closed regions locally from the line art.

## Endpoints

| Method | Path                | In            | Out                              |
| ------ | ------------------- | ------------- | -------------------------------- |
| GET    | `/health`           | –             | `{status, gpu, models, version}` |
| POST   | `/enhance-image`    | `{image}`     | `{enhanced}`                     |
| POST   | `/generate-lineart` | `{image}`     | `{lineart}`                      |
| POST   | `/detect-semantics` | `{image}`     | `{semantic_map, confidence}`     |

`image`, `enhanced`, `lineart` are base64-encoded PNG bytes.

## Run locally

```bash
cd runpod-worker
python -m venv .venv && . .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8100
curl http://localhost:8100/health
```

Then point the main backend at it:

```bash
export AI_MODE=runpod
export RUNPOD_API_URL=http://localhost:8100
# RUNPOD_API_KEY optional for local
```

## Deploy on RunPod

1. **Create a RunPod pod** (CPU is fine for the reference tasks; GPU when you add real models).
2. **Upload the worker** — push this folder, or build/push the Docker image (`docker build -t youruser/colorforge-worker .`).
3. **Start the worker** — `uvicorn app:app --host 0.0.0.0 --port 8100` (or the container `CMD`). Expose the port via RunPod's proxy.
4. **Test** — `curl https://<pod>-8100.proxy.runpod.net/health` should return `{"status":"ok",...}`.
5. **Set `RUNPOD_API_URL`** on the main backend to that URL (and `RUNPOD_API_KEY` if you put auth in front).
6. **Set `AI_MODE=runpod`** (or flip it from the in-app AI Settings panel).

## Swapping in real models

Replace the bodies of `enhance`, `lineart`, and `semantics` in `app.py`. The
API contract stays the same, so the main backend needs no changes. The reference
implementation uses OpenCV so the worker runs with zero model downloads.
