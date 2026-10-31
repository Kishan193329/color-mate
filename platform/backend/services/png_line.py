"""The border IS the picture's own drawn line (CLAUDE.md §8.5cp).

Owner, on the c2l cottage: the border must have exactly the shape of the line drawn in the PNG,
smooth and rounded like his reference apps - not a line the model re-draws at one width
(services/c2l_lineart) and not restyled widths (services/ink_style). So the ink is taken from the
picture itself:

  dark      pixels that are dark (DARK_L) or clearly darker than their surroundings (REL_DARK) -
            a brown outline on an orange roof is not black, but it is the darkest thing nearby
  line-like dark structures no wider than MAX_W: a morphological opening with a MAX_W disc keeps
            only the wide dark FILLS (a shadowed wheel hub, dark hair), which are subtracted
  gated     kept only near the line model's strokes (GATE px): the model decides what is a drawn
            line and what is pencil grain / wood texture; its own stroke is added back where it
            runs over a dark pixel, so a line the gate thinned is never cut
then specks dropped and pinholes filled. apply() hands that mask to round_line as the WIDTH source
and the model's own line as the stroke structure (§8.5cr), and writes the result as ai_lineart.png
(anti-aliased, canvas size — the July engine builds the regions and border.png from it); the
model's raw line is kept as ai_lineart_raw.png.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

DARK_L = 100      # Lab L below this is ink outright
REL_DARK = 40     # ... or at least this much darker than the local (median) surroundings ...
REL_MAX_L = 150   # ... as long as it is not a light colour itself
BG_WIN = 21       # median window that stands for "the surroundings"
MAX_W = 16        # px: dark things wider than this are fills, not lines
GATE = 8          # px from a model stroke within which a dark line pixel counts
SPECK = 80        # px: smaller ink pieces are dirt
HOLE = 30         # px: smaller paper holes inside the ink are pinholes


def _disc(r: float):
    k = max(1, int(r))
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * k + 1, 2 * k + 1))


def extract(bgr: np.ndarray, model_ink: np.ndarray | None):
    """bgr picture + boolean model-ink mask (same size, or None) -> bool mask of the painting's line."""
    h, w = bgr.shape[:2]
    L = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)[:, :, 0]
    bg = cv2.medianBlur(L, BG_WIN)
    dark = (L < DARK_L) | (((bg.astype(np.int16) - L.astype(np.int16)) > REL_DARK) & (L < REL_MAX_L))
    dark8 = dark.astype(np.uint8)
    fills = cv2.morphologyEx(dark8, cv2.MORPH_OPEN, _disc(MAX_W // 2)) > 0
    lines = dark & ~fills
    if model_ink is not None:
        near = cv2.dilate(model_ink.astype(np.uint8), _disc(GATE)) > 0
        lines = (lines & near) | (model_ink & dark)
    n, lab, st, _ = cv2.connectedComponentsWithStats(lines.astype(np.uint8), 8)
    keep = st[:, cv2.CC_STAT_AREA] >= SPECK
    keep[0] = False
    lines = keep[lab]
    n, lab, st, _ = cv2.connectedComponentsWithStats((~lines).astype(np.uint8), 4)
    tiny = st[:, cv2.CC_STAT_AREA] < HOLE
    tiny[0] = False
    lines |= tiny[lab]
    return lines


def apply(folder: Path) -> dict:
    folder = Path(folder)
    bgr = cv2.imread(str(folder / "reveal_master.png"))
    if bgr is None:
        raise FileNotFoundError("reveal_master.png missing")
    src = folder / "ai_lineart.png"
    raw = folder / "ai_lineart_raw.png"
    model = cv2.imread(str(raw if raw.is_file() else src), cv2.IMREAD_GRAYSCALE)
    if model is not None and not raw.is_file():
        cv2.imwrite(str(raw), model)
    if model is not None and model.shape != bgr.shape[:2]:
        model = cv2.resize(model, (bgr.shape[1], bgr.shape[0]), interpolation=cv2.INTER_NEAREST)
    if model is None:
        raise FileNotFoundError("ai_lineart.png missing - run c2l_lineart.generate first")
    lines = extract(bgr, model < 128)
    from services import round_line
    # the MODEL's drawing is the stroke structure; the painting's own line gives each stroke its
    # width (§8.5cr) - clean round pen strokes, bold outer contours, thin details
    g2, st = round_line.strokes(model < 128, width_src=lines)
    g1 = cv2.resize(g2, (bgr.shape[1], bgr.shape[0]), interpolation=cv2.INTER_AREA)   # anti-aliased 1x
    cv2.imwrite(str(src), g1)
    return dict(ink=float(lines.mean()), ink_model=float((model < 128).mean()), **st)
