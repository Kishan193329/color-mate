"""ColorForge AI — RunPod AI helper worker.

A small, self-contained HTTP service that performs the *optional* heavy AI
helper tasks. It is deployed separately (e.g. on a RunPod GPU pod) and the main
backend calls it when AI_MODE=runpod.

IMPORTANT: this worker is a HELPER only. It never produces the final regions —
it returns an enhanced image, a cleaner line art, and semantic hints. The main
backend always derives the final closed regions locally from the line art.

Endpoints:
    GET  /health             -> readiness + capabilities
    POST /enhance-image      {image} -> {enhanced}
    POST /generate-lineart   {image} -> {lineart}
    POST /detect-semantics   {image} -> {semantic_map, confidence}

Images are base64-encoded PNG bytes in JSON.

The reference implementation here uses classical OpenCV so it runs anywhere with
no GPU or model download. Swap the bodies of `enhance`, `lineart`, and
`semantics` for real models (e.g. a diffusion cleanup, an informative-drawings
line-art net, or a segmentation/detector) without changing the API.

Run locally:
    cd runpod-worker
    pip install -r requirements.txt
    uvicorn app:app --host 0.0.0.0 --port 8100
"""
from __future__ import annotations

import base64

import cv2
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="ColorForge AI — RunPod Worker", version="1.0.0")

MAX_SIDE = 1600


class ImagePayload(BaseModel):
    image: str  # base64-encoded PNG/JPEG bytes


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #
def _decode(image_b64: str) -> np.ndarray:
    raw = base64.b64decode(image_b64)
    arr = np.frombuffer(raw, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("could not decode image")
    h, w = img.shape[:2]
    scale = MAX_SIDE / float(max(h, w))
    if scale < 1.0:
        img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return img


def _encode(img: np.ndarray) -> str:
    ok, buf = cv2.imencode(".png", img)
    if not ok:
        raise ValueError("could not encode image")
    return base64.b64encode(buf.tobytes()).decode("ascii")


# --------------------------------------------------------------------------- #
# tasks
# --------------------------------------------------------------------------- #
def enhance(img: np.ndarray) -> np.ndarray:
    """AI image cleanup (reference: edge-preserving denoise + unsharp)."""
    smooth = cv2.bilateralFilter(img, 9, 75, 75)
    blur = cv2.GaussianBlur(smooth, (0, 0), 3)
    return cv2.addWeighted(smooth, 1.5, blur, -0.5, 0)  # unsharp mask


def lineart(img: np.ndarray) -> np.ndarray:
    """Cleaner line art (reference: denoise + adaptive threshold + despeckle)."""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.medianBlur(gray, 5)
    edges = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 9, 7
    )
    lines = cv2.bitwise_not(edges)
    lines = cv2.morphologyEx(lines, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    return cv2.bitwise_not(lines)


def semantics(img: np.ndarray) -> dict:
    """Broad object/part hints (reference: coarse heuristics).

    A real model would return detected objects + masks here. We return a
    structured hint map + confidence so the main backend can use it as helper
    data alongside its local rules.
    """
    h, w = img.shape[:2]
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    sat = float(hsv[:, :, 1].mean())
    val = float(hsv[:, :, 2].mean())

    objects = [{"label": "background", "confidence": 0.8}]
    # Very rough skin presence check (helper hint only).
    ycrcb = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
    cr, cb = ycrcb[:, :, 1], ycrcb[:, :, 2]
    skin_ratio = float(((cr > 135) & (cr < 180) & (cb > 85) & (cb < 135)).mean())
    if skin_ratio > 0.06:
        objects.append({"label": "person", "confidence": round(min(0.95, 0.4 + skin_ratio), 2)})

    semantic_map = {
        "width": w,
        "height": h,
        "avg_saturation": round(sat, 1),
        "avg_value": round(val, 1),
        "skin_ratio": round(skin_ratio, 3),
        "objects": objects,
    }
    confidence = {
        "overall": round(0.5 + 0.4 * skin_ratio, 2),
        "objects": {o["label"]: o["confidence"] for o in objects},
    }
    return {"semantic_map": semantic_map, "confidence": confidence}


# --------------------------------------------------------------------------- #
# routes
# --------------------------------------------------------------------------- #
@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "gpu": False,
        "models": ["opencv-enhance", "opencv-lineart", "opencv-semantics"],
        "version": app.version,
    }


@app.post("/enhance-image")
def enhance_image(payload: ImagePayload) -> dict:
    return {"enhanced": _encode(enhance(_decode(payload.image)))}


@app.post("/generate-lineart")
def generate_lineart(payload: ImagePayload) -> dict:
    return {"lineart": _encode(lineart(_decode(payload.image)))}


@app.post("/detect-semantics")
def detect_semantics(payload: ImagePayload) -> dict:
    return semantics(_decode(payload.image))
