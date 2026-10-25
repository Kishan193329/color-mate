"""Flat art -> a colouring page drawn as smooth curves, the way a vector colouring page looks.

Pixel-traced lines wobble and swell; the reference apps' lines are smooth and even. So instead of
keeping traced pixels, the picture is cut into cells (drawn outlines + flat-colour patches), every
cell's edge is smoothed into a curve, and the curves are drawn anti-aliased at fixed widths:
edges that follow a drawn outline at W_OUTLINE, edges between two colours only at W_COLOUR.

    python cells_to_curves.py IMAGE OUT_DIR       -> OUT_DIR/curves.png (+ cells.png debug)
"""
import os
import sys

import cv2
import numpy as np
from scipy.ndimage import find_objects

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flat_to_lines import DARK_V, _fill_from_nearest, colour_labels  # noqa: E402

SIDE = 2048
CELL_MIN = 260        # cells smaller than this (px) fold into a neighbour
CELL_THICK = 5.0      # ...and so do cells thinner than this (inscribed radius)
SMOOTH = 2.5          # px at SIDE: how much each cell's edge is rounded
W_OUTLINE = 4.5       # px at SIDE, an edge on a drawn outline
W_COLOUR = 2.6        # px at SIDE, an edge between two colours only
S = 2                 # render scale for anti-aliasing


def cells(im):
    h, w = im.shape[:2]
    dark = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)[..., 2] < DARK_V
    lbl = colour_labels(im, dark)
    # a cell = a connected patch of one colour, cut by the drawn outline
    ids = np.zeros((h, w), np.int32)
    nxt = 1
    for c in np.unique(lbl):
        n, cc = cv2.connectedComponents(((lbl == c) & ~dark).astype(np.uint8), connectivity=4)
        m = cc > 0
        ids[m] = cc[m] + nxt - 1
        nxt += n - 1
    area = np.bincount(ids.ravel(), minlength=nxt)
    r = np.zeros(nxt, np.float32)
    np.maximum.at(r, ids.ravel(), cv2.distanceTransform((ids > 0).astype(np.uint8), cv2.DIST_L2, 5).ravel())
    bad = (area < CELL_MIN) | (r < CELL_THICK)
    bad[0] = True
    hole = bad[ids]
    _, idx = cv2.distanceTransformWithLabels(hole.astype(np.uint8), cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    keep = ids[~hole]
    ids = np.where(hole, keep[np.clip(idx - 1, 0, len(keep) - 1)], ids)
    return ids, dark


def smooth_cells(ids):
    """Smooths the cell MAP, not each outline: every cell's mask is blurred and each pixel goes to
    the strongest. Neighbours then share one smooth boundary (smoothing each contour on its own
    drew two slightly different curves along every shared edge)."""
    h, w = ids.shape
    big = cv2.resize(ids.astype(np.float32), (w * S, h * S), interpolation=cv2.INTER_NEAREST).astype(np.int32)
    best = np.zeros(big.shape, np.float32)
    arg = big.copy()
    sig = SMOOTH * S
    pad = int(3 * sig) + 2
    for v, sl in enumerate(find_objects(ids), start=1):
        if sl is None:
            continue
        y0, y1 = max(sl[0].start * S - pad, 0), min(sl[0].stop * S + pad, big.shape[0])
        x0, x1 = max(sl[1].start * S - pad, 0), min(sl[1].stop * S + pad, big.shape[1])
        b = cv2.GaussianBlur((big[y0:y1, x0:x1] == v).astype(np.float32), (0, 0), sig)
        sub = best[y0:y1, x0:x1]
        win = b > sub
        sub[win] = b[win]
        arg[y0:y1, x0:x1][win] = v
    return arg


def draw(ids, dark):
    h, w = ids.shape
    lab = smooth_cells(ids)
    edge = np.zeros(lab.shape, bool)
    edge[:, 1:] |= lab[:, 1:] != lab[:, :-1]
    edge[1:, :] |= lab[1:, :] != lab[:-1, :]
    dist = cv2.distanceTransform((~edge).astype(np.uint8), cv2.DIST_L2, 5)
    near_outline = cv2.resize(cv2.dilate(dark.astype(np.uint8), np.ones((7, 7), np.uint8)),
                              (w * S, h * S), interpolation=cv2.INTER_NEAREST) > 0
    # half-width per pixel from the edge it is nearest to, eased so widths blend at junctions
    half = cv2.GaussianBlur(np.where(near_outline, W_OUTLINE, W_COLOUR).astype(np.float32), (0, 0), 3 * S) * S / 2
    alpha = np.clip(half - dist + 0.5, 0, 1)          # anti-aliased coverage
    out = cv2.resize((1 - alpha) * 255, (w, h), interpolation=cv2.INTER_AREA)
    return out.astype(np.uint8)


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = cv2.imread(src)
    s = SIDE / max(im.shape[:2])
    im = cv2.resize(im, (round(im.shape[1] * s), round(im.shape[0] * s)), interpolation=cv2.INTER_CUBIC)
    ids, dark = cells(im)
    print("cells", len(np.unique(ids)))
    cv2.imwrite(os.path.join(out, "curves.png"), draw(ids, dark))


if __name__ == "__main__":
    main()
