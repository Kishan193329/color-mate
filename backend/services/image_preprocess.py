"""Stage 1 — Preparing image (real OpenCV implementation).

    original → resize (max 1600px) → edge-preserving denoise → save RGB working image

The original colors are preserved untouched; this is the working image that
later stages sample region colors from and that the preview is drawn over. No
recoloring or color quantization happens here.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

# Longest side of the working image. Caps cost of every downstream stage.
MAX_SIDE = 1600


def read_bgr(path: Path) -> np.ndarray:
    """Robustly load an image to a BGR numpy array.

    Uses PIL so we honour EXIF orientation and can read formats (e.g. WebP)
    that a given OpenCV build may not, then hands a clean BGR array to OpenCV.
    """
    with Image.open(path) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        rgb = np.array(im)
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)


def run(original_path: Path, out_path: Path) -> Path:
    img = read_bgr(original_path)

    h, w = img.shape[:2]
    scale = MAX_SIDE / float(max(h, w))
    if scale < 1.0:
        img = cv2.resize(
            img,
            (int(round(w * scale)), int(round(h * scale))),
            interpolation=cv2.INTER_AREA,
        )

    # Edge-preserving denoise: smooths flat areas (cleaner thresholding later)
    # while keeping the strong edges that become line art.
    img = cv2.bilateralFilter(img, d=7, sigmaColor=60, sigmaSpace=60)

    cv2.imwrite(str(out_path), img)
    return out_path
