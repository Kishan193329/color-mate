"""c2l pictures as VECTOR art (CLAUDE.md §8.5cn).

The c2l line (services/c2l_lineart) used to go through the raster engine: a 1254 px border.png and
an id-map whose colour boundaries came from a 720 px k-means grid - blocky at any zoom. The app's
vector engine (svg_builder -> vector.json.gz) draws smooth shapes, so a c2l picture is now turned
into the same thing the owner's SVG pictures are: flat colour shapes + the ink, traced by vtracer.

write_source(folder) renders that flat picture at 2x (UP), smoothed, because vtracer traces whatever
pixel steps it is given (measured: the un-smoothed 1254 px source came back as angular polygons at
zoom, the Figma SVGs as curves):
  - colours: the July quantiser's centres (R._fine_color), but every FULL-RES pixel classified
    against them on a blurred copy, so boundaries follow the picture, not the 720 px grid;
  - regions: the line-art cells split by colour exactly as _game_data_color does, each painted its
    dominant centre colour, upscaled 2x and median-filtered (MEDIAN) so 1 px steps become curves;
  - ink: ai_lineart.png upscaled, blurred (INK_SIGMA) and re-thresholded - a smooth edge - in black.
Then svg_builder.vectorize(..., smooth=True, max_side=2*MAX_SIDE) + svg_builder.prepare() (which
scales the SVG back to the 1254 canvas) do the rest.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from services import lineart_region_builder as L
from services import reveal_game_builder as R

BLUR = 3.0          # sigma of the blur the full-res classification looks at (pencil grain must not split a fill)
UP = 3              # the source is rendered at this multiple of the canvas (= round_line.UP)
MEDIAN = 11         # median kernel on the 2x flat fills: rounds the 1 px staircase of the region map
INK_SIGMA = 3.0     # blur on the 2x ink mask before re-thresholding: smooth stroke edges
_CHUNK = 200_000    # pixels per distance block (the whole matrix would be ~600 MB)
MIN_FILL_LUM = 0.12 # no fill colour darker than this: the ink is pure black and svg_builder (strict for
                    # c2l items) separates the two by colour alone


def _fullres_idx(master: np.ndarray, k: int, blur: float):
    _, centers = R._fine_color(master, k, 1.0)
    src = cv2.GaussianBlur(master, (0, 0), blur) if blur > 0 else master
    z = src.reshape(-1, 3).astype(np.float32)
    c = centers.astype(np.float32)
    out = np.empty(z.shape[0], np.int32)
    for i in range(0, z.shape[0], _CHUNK):
        d = ((z[i:i + _CHUNK, None, :] - c[None, :, :]) ** 2).sum(2)
        out[i:i + _CHUNK] = d.argmin(1)
    return R._mode_filter(out.reshape(master.shape[:2]), k), centers


def write_source(folder: Path, blur: float = BLUR) -> Path:
    folder = Path(folder)
    master = cv2.imread(str(folder / "reveal_master.png"))
    if master is None:
        raise FileNotFoundError("reveal_master.png missing")
    h, w = master.shape[:2]
    line = R._load_lineart(folder, h, w)
    if line is None:
        raise FileNotFoundError("ai_lineart.png missing - run c2l_lineart.generate first")
    cv2.setRNGSeed(1234)                          # the July quantiser is otherwise random run to run
    color_idx, centers = _fullres_idx(master, R.COLOR_K, blur)
    # svg_builder tells ink from fills by colour; the ink is painted pure black below, so no fill
    # may come anywhere near black (a dark-brown k-means centre once came out at luminance 0.08
    # and the whole wheel hub traced as ink) - lift such fills just above black
    lum = centers[:, 2] * 0.299 + centers[:, 1] * 0.587 + centers[:, 0] * 0.114     # BGR
    low = lum < MIN_FILL_LUM * 255
    if low.any():
        centers = centers.astype(np.float32)
        centers[low] = np.maximum(centers[low], MIN_FILL_LUM * 255)
        centers = centers.astype(np.uint8)
    fl = L.build_regions(line, r_min=R.COLOR_LINE_RMIN)
    fl = L.intersect_color(fl, color_idx, r_min=R.COLOR_LINE_RMIN, centres=centers,
                           merge_de=R.COLOR_MERGE_DE, target=R.COLOR_TARGET)
    rmax = int(fl.max()) + 1
    key = (fl.astype(np.int64) * R.COLOR_K + color_idx).ravel()
    dom = np.bincount(key, minlength=rmax * R.COLOR_K).reshape(rmax, R.COLOR_K).argmax(1)
    lut = np.zeros((rmax, 3), np.uint8)
    for rid in (int(u) for u in np.unique(fl) if u > 0):
        lut[rid] = centers[int(dom[rid])]
    lut[0] = lut[1:].mean(0).astype(np.uint8) if rmax > 1 else 255   # label 0 (under the ink) takes a neutral fill
    fills = cv2.resize(lut[fl], (w * UP, h * UP), interpolation=cv2.INTER_NEAREST)
    fills = cv2.medianBlur(fills, MEDIAN)
    ink2 = cv2.imread(str(folder / "ink2x.png"), 0)            # ink_style's 2x render, already smooth
    if ink2 is not None and ink2.shape == (h * UP, w * UP):
        m = ink2 < 128
    else:
        ink = cv2.imread(str(folder / "ai_lineart.png"), 0)
        if ink.shape != (h, w):
            ink = cv2.resize(ink, (w, h), interpolation=cv2.INTER_NEAREST)
        m = cv2.resize((ink < 128).astype(np.float32), (w * UP, h * UP), interpolation=cv2.INTER_LINEAR)
        m = cv2.GaussianBlur(m, (0, 0), INK_SIGMA) > 0.5
    fills[m] = 0
    out = folder / "source.png"
    cv2.imwrite(str(out), fills)
    return out
