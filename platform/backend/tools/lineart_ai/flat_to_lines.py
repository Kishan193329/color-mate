"""Flat art that already has outlines (a finished colour-by-number picture, a vector-style
illustration) -> line art, without any AI pass.

Two sources of line: the drawn dark outlines, and every edge between two flat colours (petal
shading, water swirls, background stripes) — in such art those patches are separate parts too.
The result goes through line_cleanup + line_style like a redrawn page.

    python flat_to_lines.py IMAGE OUT_DIR
"""
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from line_cleanup import SIDE, cleanup  # noqa: E402
from line_style import style  # noqa: E402

DARK_V = 110        # HSV value below this is a drawn outline
COLOURS = 36        # flat colours to separate
MIN_PATCH = 220     # colour patches smaller than this (px at SIDE) are anti-aliasing, not parts
MIN_THICK = 6.0     # ...and so are patches whose inscribed radius is below this, however long


def _fill_from_nearest(lbl, hole):
    _, idx = cv2.distanceTransformWithLabels(hole.astype(np.uint8), cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    keep = lbl[~hole]                         # raster order == label order
    return np.where(hole, keep[np.clip(idx - 1, 0, len(keep) - 1)], lbl).astype(np.uint8)


def colour_edges(im, dark):
    """Edges between flat colours. The drawn outline itself is given to the colours on either side
    first, so where two fills meet across an outline the edge lands ON it, not as a second line
    beside it (which is what doubled every outline)."""
    lbl = colour_labels(im, dark)
    return cv2.dilate(lbl, np.ones((3, 3), np.uint8)) != cv2.erode(lbl, np.ones((3, 3), np.uint8))


def colour_labels(im, dark):
    """One flat-colour label per pixel, outline and slivers folded into the fills beside them."""
    h, w = im.shape[:2]
    lab = cv2.cvtColor(cv2.bilateralFilter(im, 7, 30, 7), cv2.COLOR_BGR2LAB).astype(np.float32)
    small = cv2.resize(lab, (512, round(512 * h / w)), interpolation=cv2.INTER_AREA).reshape(-1, 3)
    cv2.setRNGSeed(7)
    _, _, cen = cv2.kmeans(small, COLOURS, None, (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5),
                           3, cv2.KMEANS_PP_CENTERS)
    flat = lab.reshape(-1, 3)
    lbl = np.empty(len(flat), np.int32)
    for i in range(0, len(flat), 200_000):
        lbl[i:i + 200_000] = np.argmin(((flat[i:i + 200_000, None] - cen[None]) ** 2).sum(-1), 1)
    lbl = lbl.reshape(h, w).astype(np.uint8)
    # the outline and its anti-aliased fringe belong to the fills beside it
    fringe = cv2.dilate(dark.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
    lbl = _fill_from_nearest(lbl, fringe)
    lbl = cv2.medianBlur(lbl, 9)

    # fold slivers into the nearest real patch: tiny patches, and THIN ones — a strip of shading
    # running beside an outline is long (big area) but a few px wide, and drew a second line
    sliver = np.zeros((h, w), bool)
    for c in range(COLOURS):
        m = (lbl == c).astype(np.uint8)
        n, cc, st, _ = cv2.connectedComponentsWithStats(m, 4)
        thick = np.zeros(n, np.float32)
        np.maximum.at(thick, cc.ravel(), cv2.distanceTransform(m, cv2.DIST_L2, 5).ravel())
        bad = (st[:, cv2.CC_STAT_AREA] < MIN_PATCH) | (thick < MIN_THICK)
        bad[0] = False
        sliver |= bad[cc]
    return cv2.medianBlur(_fill_from_nearest(lbl, sliver), 9)


def to_lines(im):
    h, w = im.shape[:2]
    s = SIDE / max(h, w)
    im = cv2.resize(im, (round(w * s), round(h * s)), interpolation=cv2.INTER_CUBIC)
    dark = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)[..., 2] < DARK_V
    ink = dark | colour_edges(im, dark)
    return np.where(ink, 0, 255).astype(np.uint8)


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    raw = to_lines(cv2.imread(src))
    clean = cleanup(raw)
    styled, cls = style(clean)
    for name, img in (("raw", raw), ("clean", clean), ("styled", styled)):
        cv2.imwrite(os.path.join(out, f"{name}.png"), img)
    print(cls)


if __name__ == "__main__":
    main()
