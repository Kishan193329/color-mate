"""Busy-image simplifier.

Dark / photoreal / highly-detailed images make the line-art model ink every texture (rain, straw,
fabric, walls) into a sketchy mess. Mean-shift flattening collapses that texture into flat regions
BEFORE the line-art step, so the outline comes out clean (coloring-book), keeping the real edges
(faces, figures, objects).

Applied ONLY to the line-art SOURCE — the reveal/fill image stays the true original photo, so the
finished picture is still the real artwork. Clean flat illustrations should be left OFF (mean-shift
would soften their fine linework), so this is a manual per-upload choice, not automatic (edge-density
can't tell a flat line-drawing from a photo — the drawing often has MORE edges).

Text baked into an image (story captions) is NOT removed here — separating story-text from jewellery
/ fine detail needs a text-detection model; that lands with the Story category.
"""
from __future__ import annotations

import cv2
import numpy as np

# level -> (spatial radius, colour radius) for pyrMeanShiftFiltering
LEVELS = {
    "light":  (12, 30),
    "medium": (18, 45),
    "strong": (26, 60),
}


def simplify(img: np.ndarray, level: str = "medium") -> np.ndarray:
    """Flatten texture for a cleaner line-art. `level` in {light, medium, strong}."""
    sp, sr = LEVELS.get(level, LEVELS["medium"])
    out = cv2.pyrMeanShiftFiltering(img, sp, sr)
    if level == "strong":
        out = cv2.bilateralFilter(out, 9, 90, 90)      # extra smoothing at the strong setting
    return out
