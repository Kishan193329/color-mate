"""Ink styling for c2l pictures (CLAUDE.md §8.5co): the line model's one-weight ink redrawn with the
stroke weights, tapered ends and smooth edges of the owner's reference apps.

Measured on two reference screenshots (a rose, an eye; §8.5co): stroke widths run continuously over a
~7:1 range (p10 4 px .. p90 34 px at that zoom), the widest where the drawing meets the background
and the thinnest on open detail strokes; every free end thins to ~1/4 of the stroke's width over its
last ~3 widths; solid masses (pupils, lashes) stay solid; edges are anti-aliased curves.

Every stroke is classed by what it separates (tools/lineart_ai/line_style.py's method, ported here
with no scikit-image dependency - the skeleton is a numpy Zhang-Suen thinning):
  silhouette  edge of the background                  R_SILHOUETTE
  major       between two big regions                 R_MAJOR
  minor       any other region boundary               R_MINOR
  detail      open stroke inside one region           R_DETAIL, never wider than the model drew it
widths eased along the stroke (EASE) so a class change swells like a pen instead of stepping, then
thinned towards every free end (TAPER). Rendered at 2x and averaged down.

    apply(folder) -> keeps the model's line as ai_lineart_raw.png, writes the styled ai_lineart.png
                     (canvas size, anti-aliased) and ink2x.png (2x, what c2l_vector traces).
All constants are pixels at a REF-px long side and scale with the picture.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

REF = 2048.0
R_DETAIL = 1.2         # half-widths; at the 1254 canvas: detail 1.5 px, minor 3.2, major 7.3, silhouette 11.7
R_MINOR = 2.6
R_MAJOR = 6.0
R_SILHOUETTE = 9.6
MAJOR_AREA = 0.004     # both sides bigger than this share of the canvas -> major contour
SOLID_D = 9.0          # ink thicker than 2x this is a solid shape (pupil), kept as drawn
SIDE_WIN = 9           # how far across a stroke to look for the regions it separates
SPUR = 8               # skeleton branches shorter than this are bumps, not strokes
EASE = 4.0             # how gradually a stroke's width changes along its length (a 2-D blur over the
                       # skeleton: too wide and a thin stroke beside a thick one is pulled up with it)
TAPER = 3.0            # a free end thins over this many stroke WIDTHS ...
TAPER_MIN = 10.0       # ... and at least this many px
TAPER_TIP = 0.25       # the tip keeps this share of the width
UP = 2


def _disc(r: float):
    k = max(1, int(np.ceil(r)))
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * k + 1, 2 * k + 1))


def _ends(sk: np.ndarray) -> np.ndarray:
    nb = cv2.filter2D(sk.astype(np.uint8), -1, np.ones((3, 3), np.float32), borderType=cv2.BORDER_CONSTANT)
    return sk & (nb <= 2)


def _thin(mask: np.ndarray) -> np.ndarray:
    """Zhang-Suen thinning: a 1 px, 8-connected skeleton of a boolean mask. Pure numpy."""
    img = np.pad(mask.astype(np.uint8), 1)
    core = img[1:-1, 1:-1]
    while True:
        changed = False
        for step in (0, 1):
            p2, p3, p4, p5 = img[:-2, 1:-1], img[:-2, 2:], img[1:-1, 2:], img[2:, 2:]
            p6, p7, p8, p9 = img[2:, 1:-1], img[2:, :-2], img[1:-1, :-2], img[:-2, :-2]
            seq = (p2, p3, p4, p5, p6, p7, p8, p9)
            b = np.zeros(core.shape, np.uint8)
            a = np.zeros(core.shape, np.uint8)
            for i in range(8):
                b += seq[i]
                a += (seq[i] == 0) & (seq[(i + 1) % 8] == 1)
            if step == 0:
                c = ((p2 & p4 & p6) == 0) & ((p4 & p6 & p8) == 0)
            else:
                c = ((p2 & p4 & p8) == 0) & ((p2 & p6 & p8) == 0)
            rem = (core == 1) & (b >= 2) & (b <= 6) & (a == 1) & c
            if rem.any():
                core[rem] = 0
                changed = True
        if not changed:
            return core > 0


def _prune(sk: np.ndarray, k: int) -> np.ndarray:
    """Drops skeleton spurs shorter than k px (a thick stroke's bumpy edge grows them; redrawn they
    become thorns) while keeping the full length of real open strokes."""
    cut = sk.copy()
    for _ in range(k):
        cut &= ~_ends(cut)
    grow = _ends(cut)
    for _ in range(k):
        grow = (cv2.dilate(grow.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0) & sk & ~cut
        cut |= grow
    return cut


def _end_distance(sk: np.ndarray, limit: int) -> np.ndarray:
    """Path length along the skeleton from the nearest FREE end (inf beyond `limit` or none). Ends on
    the picture border are not free - a stroke running off the edge must not taper."""
    h, w = sk.shape
    border = np.zeros_like(sk)
    border[:3, :] = border[-3:, :] = border[:, :3] = border[:, -3:] = True
    dist = np.full(sk.shape, np.inf, np.float32)
    cut = sk.copy()
    for step in range(limit):
        cur = _ends(cut) & ~border
        if not cur.any():
            break
        dist[cur] = step
        cut &= ~cur
    return dist


def style(bw: np.ndarray):
    """bw: uint8 black (0) lines on white (255). Returns (grey at canvas size, grey at UP x, stats)."""
    ink = bw < 128
    h, w = ink.shape
    area = h * w
    k = max(h, w) / REF
    r_det, r_min, r_maj, r_sil = (x * k for x in (R_DETAIL, R_MINOR, R_MAJOR, R_SILHOUETTE))
    solid_d = SOLID_D * k
    win = max(3, int(round(SIDE_WIN * k)))
    spur = max(3, int(round(SPUR * k)))

    paper = (~ink).astype(np.uint8)
    n, reg, st, _ = cv2.connectedComponentsWithStats(paper, connectivity=4)
    sizes = st[:, cv2.CC_STAT_AREA].astype(np.float64) / area
    sizes[0] = 0
    edge = np.unique(np.concatenate([reg[0], reg[-1], reg[:, 0], reg[:, -1]]))
    edge = edge[edge > 0]
    background = edge[np.argmax(sizes[edge])] if len(edge) else -1

    # nearest region for every ink pixel; the two regions either side of a stroke are the extremes
    # of that map across it
    _, idx = cv2.distanceTransformWithLabels(1 - paper, cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    seeds = reg[paper > 0]                     # raster order == label order for DIST_LABEL_PIXEL
    near = np.where(ink, seeds[np.clip(idx - 1, 0, len(seeds) - 1)], reg).astype(np.float32)
    kk = np.ones((2 * win + 1,) * 2, np.uint8)
    lo = cv2.erode(near, kk).astype(np.int32)
    hi = cv2.dilate(near, kk).astype(np.int32)

    d = cv2.distanceTransform(ink.astype(np.uint8), cv2.DIST_L2, 5)
    solid_core = d > solid_d                   # pupils and other solid shapes are kept as drawn ...
    sk = _prune(_thin(ink & ~solid_core), spur)  # ... and left out of the stroke skeleton
    separates = sk & (lo != hi)
    silhouette = separates & ((lo == background) | (hi == background))
    major = separates & ~silhouette & (np.minimum(sizes[lo], sizes[hi]) > MAJOR_AREA)
    minor = separates & ~silhouette & ~major
    detail = sk & ~separates

    # target half-width per skeleton pixel, eased along the stroke, then tapered towards free ends
    r = np.zeros(ink.shape, np.float32)
    r[detail], r[minor], r[major], r[silhouette] = r_det, r_min, r_maj, r_sil
    num = cv2.GaussianBlur(r, (0, 0), EASE * k)
    den = cv2.GaussianBlur(sk.astype(np.float32), (0, 0), EASE * k)
    r = np.where(sk, num / np.maximum(den, 1e-6), 0).astype(np.float32)
    span = np.maximum(TAPER_MIN * k, TAPER * 2 * r)
    de = _end_distance(sk, int(np.ceil(span.max())) + 1)
    t = np.clip(de / np.maximum(span, 1e-6), 0, 1)
    r = np.where(sk, r * (TAPER_TIP + (1 - TAPER_TIP) * t), 0).astype(np.float32)

    # render at UP x: every pixel takes the half-width of its nearest skeleton pixel
    S = UP
    up = lambda m, mode=cv2.INTER_NEAREST: cv2.resize(m, (w * S, h * S), interpolation=mode)
    skS = up(sk.astype(np.uint8)) > 0
    dist, idx = cv2.distanceTransformWithLabels((~skS).astype(np.uint8), cv2.DIST_L2, 5,
                                                labelType=cv2.DIST_LABEL_PIXEL)
    rS = up(r)[skS]
    detS = up(detail.astype(np.uint8))[skS] > 0
    nearS = np.clip(idx - 1, 0, len(rS) - 1)
    # the skeleton block is S px thick at this scale and `dist` is measured from its edge, so the
    # true distance from the centreline is dist + (S-1)/2; `<=` would round every edge outward
    lines = dist < np.maximum(rS[nearS] * S - (S - 1) * 0.5, 0.5)
    drawn = up(cv2.GaussianBlur(ink.astype(np.float32), (0, 0), 0.8), cv2.INTER_LINEAR) > 0.5
    lines &= ~detS[nearS] | drawn              # detail never wider than the model drew it
    solid = (cv2.dilate(solid_core.astype(np.uint8), _disc(solid_d)) > 0) & ink
    lines |= up(cv2.GaussianBlur(solid.astype(np.float32), (0, 0), 1.0), cv2.INTER_LINEAR) > 0.5
    smooth = cv2.GaussianBlur(lines.astype(np.float32), (0, 0), 0.8 * S)
    g2 = np.clip((1 - smooth) * 255, 0, 255).astype(np.uint8)
    g1 = cv2.resize(g2, (w, h), interpolation=cv2.INTER_AREA)
    stats = dict(silhouette=int(silhouette.sum()), major=int(major.sum()), minor=int(minor.sum()),
                 detail=int(detail.sum()), ink_in=float(ink.mean()), ink_out=float((g1 < 128).mean()))
    return g1, g2, stats


def apply(folder: Path) -> dict:
    folder = Path(folder)
    src = folder / "ai_lineart.png"
    raw = folder / "ai_lineart_raw.png"
    bw = cv2.imread(str(raw if raw.is_file() else src), cv2.IMREAD_GRAYSCALE)
    if bw is None:
        raise FileNotFoundError("ai_lineart.png missing - run c2l_lineart.generate first")
    if not raw.is_file():
        cv2.imwrite(str(raw), bw)
    g1, g2, stats = style(bw)
    cv2.imwrite(str(src), g1)
    cv2.imwrite(str(folder / "ink2x.png"), g2)
    return stats
