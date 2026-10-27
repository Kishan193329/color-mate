"""Gives cleaned line art (line_cleanup.py) the line weights of a printed colour-by-number page.

Measured on the owner's reference apps at 1272 px wide: detail strokes ~2 px, region lines ~4 px,
major contours ~9 px, silhouette up to ~20 px, anti-aliased edges, and thousands of open detail
strokes that separate nothing. Our redrawn art had one weight everywhere (6-10 px), a marker look.

Each stroke is classed by what it separates, then redrawn at that class's width:
  silhouette - the edge of the background                      -> R_SILHOUETTE
  major      - between two big regions                         -> R_MAJOR
  minor      - any other region boundary                       -> R_MINOR
  detail     - open strokes inside one region (veins, strands) -> thinned to R_DETAIL, keeps its taper
Solid blobs (pupils) stay as drawn. Output is rendered 2x and averaged down, so edges are
anti-aliased like the reference.

    python line_style.py IN_DIR OUT_DIR       (reads NN_clean.png, writes NN_styled.png + sheet)
All sizes are pixels at SIDE = 2048.
"""
import glob
import os
import sys

import cv2
import numpy as np
from skimage.morphology import skeletonize

SIDE = 2048
R_DETAIL = 1.6
R_MINOR = 3.2
R_MAJOR = 6.0
R_SILHOUETTE = 9.0
MAJOR_AREA = 0.004    # both sides bigger than this share of the canvas -> major contour
SOLID_D = 9.0         # ink thicker than 2x this is a solid shape (pupil), kept as drawn
SIDE_WIN = 9          # how far across a stroke to look for the regions it separates
SPUR = 8              # skeleton branches shorter than this are bumps, not strokes
EASE = 6.0           # how gradually a stroke's width changes along its length


def _disc(r):
    k = int(np.ceil(r))
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * k + 1, 2 * k + 1))


def _grow(mask, r, scale):
    return cv2.dilate(mask.astype(np.uint8), _disc(r * scale)) > 0


def _ends(sk):
    nb = cv2.filter2D(sk.astype(np.uint8), -1, np.ones((3, 3), np.float32), borderType=cv2.BORDER_CONSTANT)
    return sk & (nb <= 2)


def _prune(sk, k):
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


def style(bw):
    """bw: uint8 black (0) lines on white (255), long side SIDE. Returns anti-aliased grey image."""
    ink = bw < 128
    paper = (~ink).astype(np.uint8)
    h, w = ink.shape
    area = h * w

    n, reg, st, _ = cv2.connectedComponentsWithStats(paper, connectivity=4)
    sizes = st[:, cv2.CC_STAT_AREA].astype(np.float64) / area
    sizes[0] = 0
    edge = np.unique(np.concatenate([reg[0], reg[-1], reg[:, 0], reg[:, -1]]))
    edge = edge[edge > 0]
    background = edge[np.argmax(sizes[edge])] if len(edge) else -1

    # nearest region for every ink pixel
    _, idx = cv2.distanceTransformWithLabels(1 - paper, cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    seeds = reg[paper > 0]                     # raster order == label order for DIST_LABEL_PIXEL
    near = np.where(ink, seeds[np.clip(idx - 1, 0, len(seeds) - 1)], reg).astype(np.float32)

    # the two regions on either side of a stroke = the extremes of `near` across it
    k = np.ones((2 * SIDE_WIN + 1,) * 2, np.uint8)
    lo = cv2.erode(near, k).astype(np.int32)
    hi = cv2.dilate(near, k).astype(np.int32)

    sk = _prune(skeletonize(ink), SPUR)
    d = cv2.distanceTransform(ink.astype(np.uint8), cv2.DIST_L2, 5)
    separates = sk & (lo != hi)
    silhouette = separates & ((lo == background) | (hi == background))
    major = separates & ~silhouette & (np.minimum(sizes[lo], sizes[hi]) > MAJOR_AREA)
    minor = separates & ~silhouette & ~major
    detail = sk & ~separates

    # target half-width per skeleton pixel, eased along the stroke so a line swells and thins
    # like a pen instead of stepping (a step showed as a knot where the class changed)
    r = np.zeros(ink.shape, np.float32)
    r[detail], r[minor], r[major], r[silhouette] = R_DETAIL, R_MINOR, R_MAJOR, R_SILHOUETTE
    skf = sk.astype(np.float32)
    num = cv2.GaussianBlur(r, (0, 0), EASE)
    den = cv2.GaussianBlur(skf, (0, 0), EASE)
    r = np.where(sk, num / np.maximum(den, 1e-6), 0).astype(np.float32)

    # render at S x: every pixel takes the half-width of its nearest skeleton pixel
    S = 2
    up = lambda m, mode=cv2.INTER_NEAREST: cv2.resize(m, (w * S, h * S), interpolation=mode)
    skS = up(sk.astype(np.uint8)) > 0
    dist, idx = cv2.distanceTransformWithLabels((~skS).astype(np.uint8), cv2.DIST_L2, 5,
                                                labelType=cv2.DIST_LABEL_PIXEL)
    rS = up(r)[skS]                                    # raster order == label order
    detS = up(detail.astype(np.uint8))[skS] > 0
    near = np.clip(idx - 1, 0, len(rS) - 1)
    lines = dist <= rS[near] * S
    drawn = up(cv2.GaussianBlur(ink.astype(np.float32), (0, 0), 0.8), cv2.INTER_LINEAR) > 0.5
    lines &= ~detS[near] | drawn                       # detail keeps its own taper
    solid = _grow(d > SOLID_D, SOLID_D, 1) & ink       # pupils and other solid shapes, as drawn
    lines |= up(cv2.GaussianBlur(solid.astype(np.float32), (0, 0), 1.0), cv2.INTER_LINEAR) > 0.5
    smooth = cv2.GaussianBlur(lines.astype(np.float32), (0, 0), 0.8 * S)
    out = cv2.resize((1 - smooth) * 255, (w, h), interpolation=cv2.INTER_AREA)
    return np.clip(out, 0, 255).astype(np.uint8), dict(
        silhouette=int(silhouette.sum()), major=int(major.sum()), minor=int(minor.sum()), detail=int(detail.sum()))


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    rows = []
    for p in sorted(glob.glob(os.path.join(src, "[0-9][0-9]_clean.png"))):
        name = os.path.basename(p)[:2]
        bw = cv2.imread(p, cv2.IMREAD_GRAYSCALE)
        styled, cls = style(bw)
        cv2.imwrite(f"{out}/{name}_styled.png", styled)
        print(name, cls, flush=True)
        tw = 420
        th = int(tw * bw.shape[0] / bw.shape[1])
        rows.append(np.hstack([cv2.resize(x, (tw, th), interpolation=cv2.INTER_AREA) for x in (bw, styled)]))
    mw = max(r.shape[1] for r in rows)
    sheet = np.vstack([np.pad(r, ((0, 8), (0, mw - r.shape[1] + 8)), constant_values=150) for r in rows])
    cv2.imwrite(f"{out}/style_sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 88])


if __name__ == "__main__":
    main()
