"""Image type router (Engineering Sprint — Zero Crash & Image Router).

A lightweight, dependency-free classifier that labels an upload as one of eight
types — Illustration, Anime, Portrait, Landscape, Mandala, Object, Logo, Sketch —
from cheap global image statistics (saturation, edges, flatness, colour count,
skin fraction, background uniformity, radial symmetry, sky). It emits
`image_type_report.json`.

This is the routing FOUNDATION only: it classifies and reports. It changes no
visual algorithm, border, palette, or Quality Fix — nothing downstream reads the
type yet. It is wrapped so it can never crash the pipeline (returns a safe default).
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from services import image_preprocess

TYPES = ["Illustration", "Anime", "Portrait", "Landscape",
         "Mandala", "Object", "Logo", "Sketch"]


def _corr(a: np.ndarray, b: np.ndarray) -> float:
    a = a.astype(np.float32).ravel() - float(a.mean())
    b = b.astype(np.float32).ravel() - float(b.mean())
    d = float(np.sqrt((a * a).sum() * (b * b).sum())) + 1e-6
    return float((a * b).sum() / d)


def features(bgr: np.ndarray) -> dict:
    """Cheap global descriptors, all normalised to ~[0,1] (except aspect)."""
    h0, w0 = bgr.shape[:2]
    s = 512.0 / max(h0, w0)
    if s < 1.0:
        bgr = cv2.resize(bgr, (int(round(w0 * s)), int(round(h0 * s))), interpolation=cv2.INTER_AREA)
    h, w = bgr.shape[:2]
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    sat, val, hue = hsv[:, :, 1], hsv[:, :, 2], hsv[:, :, 0]
    edges = cv2.Canny(gray, 60, 150)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    gmag = np.sqrt(gx * gx + gy * gy)
    # Fraction-based texture (the mean saturates and is useless): a photo is texture-
    # rich (high detail_frac); a flat cartoon has large flat fills (high flat_frac).
    flat_frac = float((gmag < 12).mean())
    detail_frac = float((gmag > 50).mean())

    q = (bgr >> 4).astype(np.int32)                        # 4 bits/channel
    uniq = int(len(np.unique(q.reshape(-1, 3), axis=0)))

    ycc = cv2.cvtColor(bgr, cv2.COLOR_BGR2YCrCb)
    Y, Cr, Cb = ycc[:, :, 0], ycc[:, :, 1], ycc[:, :, 2]
    skin = (Cr >= 135) & (Cr <= 180) & (Cb >= 85) & (Cb <= 135) & (Y >= 60)
    cy0, cy1, cx0, cx1 = int(h * 0.2), int(h * 0.8), int(w * 0.25), int(w * 0.75)

    border = np.concatenate([bgr[0, :], bgr[-1, :], bgr[:, 0], bgr[:, -1]]).reshape(-1, 3)
    bmean = border.mean(0)
    bgdist = np.sqrt(((bgr.astype(np.float32) - bmean) ** 2).sum(2))

    top = hsv[: max(1, int(h * 0.33))]
    sky = (top[:, :, 0] >= 90) & (top[:, :, 0] <= 130) & (top[:, :, 2] > 150)

    # Rotational symmetry on a centred square crop. A mandala is symmetric under
    # 90°, 180° AND mirror (take the MIN); a centred portrait passes the mirror but
    # fails 90°, so it scores low here.
    side = min(h, w)
    cym, cxm, half = h // 2, w // 2, side // 2
    crop = gray[cym - half:cym + half, cxm - half:cxm + half]
    if crop.size:
        crop = cv2.resize(crop, (128, 128))
        mandala_sym = max(0.0, min(_corr(crop, cv2.rotate(crop, cv2.ROTATE_90_CLOCKWISE)),
                                   _corr(crop, cv2.rotate(crop, cv2.ROTATE_180)),
                                   _corr(crop, cv2.flip(crop, 1))))
    else:
        mandala_sym = 0.0

    f = {
        "aspect": round(w / h, 3),
        "sat_mean": round(float(sat.mean()) / 255, 4),
        "grayscale": round(1 - float(sat.mean()) / 255, 4),
        "value_mean": round(float(val.mean()) / 255, 4),
        "edge_density": round(float((edges > 0).mean()), 4),
        "detail": round(detail_frac, 4),        # fraction texture-rich (photo/detail)
        "flatness": round(flat_frac, 4),         # fraction flat (cartoon fills)
        "unique_colors": uniq,
        "color_simplicity": round(float(max(0.0, 1 - uniq / 400.0)), 4),
        "white_frac": round(float((gray > 235).mean()), 4),
        "blackline_frac": round(float((gray < 60).mean()), 4),
        "skin_frac": round(float(skin.mean()), 4),
        "center_skin": round(float(skin[cy0:cy1, cx0:cx1].mean()) if cy1 > cy0 and cx1 > cx0 else 0.0, 4),
        "bg_uniform": round(float((bgdist < 25).mean()), 4),
        "mandala_sym": round(float(mandala_sym), 4),
        "top_sky": round(float(sky.mean()), 4),
    }
    return f


def scores(f: dict) -> dict:
    """Weighted heuristic score per type (higher = more likely). Hand-tuned; a v1
    routing signal, not a trained model."""
    photo = f["detail"]          # texture-rich fraction = photographic / fine detail
    s = {
        "Mandala": 4.0 * f["mandala_sym"] + 0.4 * f["edge_density"]
        - 1.2 * f["skin_frac"] - 0.6 * f["top_sky"],
        "Sketch": 2.2 * f["grayscale"] + 1.2 * f["white_frac"]
        - 1.6 * f["sat_mean"] - 1.2 * f["skin_frac"] - 0.5 * photo,
        "Logo": 1.8 * f["color_simplicity"] + 1.7 * f["bg_uniform"] + 0.6 * f["white_frac"]
        - 1.2 * f["edge_density"] - 1.0 * photo - 0.6 * f["skin_frac"],
        "Landscape": 1.7 * f["top_sky"] + 1.4 * photo + 0.7 * max(0.0, f["aspect"] - 1.0)
        - 1.6 * f["center_skin"] - 1.1 * f["bg_uniform"] - 1.0 * f["mandala_sym"],
        "Portrait": 1.8 * f["center_skin"] + 0.8 * f["skin_frac"] + 0.8 * photo + 0.6 * f["grayscale"]
        - 1.2 * f["flatness"] - 1.2 * f["mandala_sym"] - 1.3 * f["top_sky"],
        "Object": 1.6 * f["bg_uniform"] + 0.6 * f["flatness"]
        - 1.4 * f["center_skin"] - 1.1 * f["mandala_sym"] - 0.8 * f["top_sky"] - 0.6 * photo,
        "Anime": 1.3 * f["flatness"] + 1.0 * f["sat_mean"] + 0.8 * f["center_skin"]
        - 1.2 * f["mandala_sym"] - 1.3 * photo - 0.7 * f["top_sky"] - 0.4 * f["edge_density"],
        "Illustration": 1.4 * f["flatness"] + 1.0 * f["sat_mean"] + 0.9 * f["edge_density"]
        + 0.5 * f["top_sky"] - 1.2 * f["mandala_sym"] - 1.0 * photo - 0.5 * f["center_skin"],
    }
    return {k: round(v, 4) for k, v in s.items()}


def classify(bgr: np.ndarray) -> dict:
    f = features(bgr)
    sc = scores(f)
    ranked = sorted(sc.items(), key=lambda kv: -kv[1])
    top, second = ranked[0], ranked[1]
    # confidence = separation of the top score over the runner-up, squashed to [0,1]
    conf = round(float(1 / (1 + np.exp(-(top[1] - second[1]) * 2.5))), 3)
    return {
        "image_type": top[0],
        "confidence": conf,
        "runner_up": second[0],
        "scores": sc,
        "features": f,
    }


def route(original_path: Path, report_path: Path) -> dict:
    """Classify the (immutable) input and write image_type_report.json. Never raises;
    on any failure returns a safe Illustration default so the pipeline continues."""
    try:
        bgr = image_preprocess.read_bgr(Path(original_path))
        result = classify(bgr)
    except Exception as exc:  # noqa: BLE001 - routing must never break processing
        result = {"image_type": "Illustration", "confidence": 0.0,
                  "runner_up": None, "scores": {}, "features": {},
                  "error": f"{type(exc).__name__}: {exc}"}
    result["version"] = 1
    result["note"] = ("Routing foundation only — classification is reported but does not "
                      "change processing yet (no visual/border/palette/QF change).")
    try:
        Path(report_path).write_text(json.dumps(result, indent=2), encoding="utf-8")
    except OSError:
        pass
    return result
