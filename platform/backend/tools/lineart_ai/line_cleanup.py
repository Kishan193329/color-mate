"""Turns a redrawn colouring page (redraw_test.py's NN_line_raw.png) into a colour-by-number board:
smooth edges at 2048 px, big solid-black masses opened up so they can be coloured (small ones such
as pupils stay solid), cells too small to tap or number filled with ink, specks dropped.

    python line_cleanup.py IN_DIR OUT_DIR
Writes OUT_DIR/NN_clean.png and OUT_DIR/cleanup_sheet.jpg (before | after, with region counts).
All sizes are in pixels at SIDE = 2048.
"""
import glob
import os
import sys

import cv2
import numpy as np

SIDE = 2048
HOLLOW_CORE = 14.0    # ink thicker than 2x this is a solid mass, not a stroke...
CORE_MIN = 0.004      # ...and a mass whose thick core covers more than this share of the canvas
                      # (hair) is opened up; smaller ones (pupils, a nose) stay solid
HOLLOW_RIM = 6.0      # the outline left around an opened mass
CELL_R_INK = 3.5      # a cell thinner than this is a crumb between strokes -> ink. Larger small
                      # cells stay open: inking them turned beaded jewellery into black blotches;
                      # the game merges them into a neighbour instead.
CELL_R_MIN = 7.0      # below this a cell cannot hold a number (counted, not changed)
SPECK = 60            # ink pieces smaller than this are dirt


def _smooth(ink):
    g = cv2.GaussianBlur(ink.astype(np.float32), (0, 0), 1.2)
    return g > 0.5


def cleanup(gray):
    h, w = gray.shape
    s = SIDE / max(h, w)
    g = cv2.resize(gray, (round(w * s), round(h * s)), interpolation=cv2.INTER_CUBIC)
    ink = _smooth(g < 128)
    area = ink.size

    # 1. big solid masses -> outline. Judged by the thick core alone: a pupil joined to the eye's
    #    outline is part of a huge ink piece, but its own core is small.
    d = cv2.distanceTransform(ink.astype(np.uint8), cv2.DIST_L2, 5)
    core = (d > HOLLOW_CORE).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(core, 8)
    big = np.zeros(n, bool)
    big[1:] = st[1:, cv2.CC_STAT_AREA] > CORE_MIN * area
    core = big[lab]
    r = int(HOLLOW_CORE - HOLLOW_RIM + 1)
    grow = cv2.dilate(core.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1)))
    ink &= ~((grow > 0) & (d > HOLLOW_RIM))

    # 2. cells too small to tap or number -> ink (like the pupils and lashes in the reference apps)
    paper = (~ink).astype(np.uint8)
    n, lab = cv2.connectedComponents(paper, connectivity=4)
    dp = cv2.distanceTransform(paper, cv2.DIST_L2, 5)
    rmax = np.zeros(n, np.float32)
    np.maximum.at(rmax, lab.ravel(), dp.ravel())
    tiny = rmax < CELL_R_INK
    tiny[0] = False
    ink |= tiny[lab]

    # 3. specks
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink.astype(np.uint8), 8)
    keep = np.zeros(n, bool)
    keep[1:] = st[1:, cv2.CC_STAT_AREA] >= SPECK
    ink = keep[lab]
    return np.where(_smooth(ink), 0, 255).astype(np.uint8)


def stats(bw):
    paper = (bw >= 128).astype(np.uint8)
    n, lab = cv2.connectedComponents(paper, connectivity=4)
    d = cv2.distanceTransform(paper, cv2.DIST_L2, 5)
    rmax = np.zeros(n, np.float32)
    np.maximum.at(rmax, lab.ravel(), d.ravel())
    k = max(bw.shape) / SIDE
    return n - 1, int((rmax[1:] < CELL_R_MIN * k).sum()), (bw < 128).mean() * 100


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    rows = []
    for p in sorted(glob.glob(os.path.join(src, "[0-9][0-9]_line_raw.png"))):
        name = os.path.basename(p)[:2]
        gray = cv2.imread(p, cv2.IMREAD_GRAYSCALE)
        before = np.where(gray < 128, 0, 255).astype(np.uint8)
        after = cleanup(gray)
        cv2.imwrite(f"{out}/{name}_clean.png", after)
        b, a = stats(before), stats(after)
        print(f"{name}: regions {b[0]} -> {a[0]}, too small {b[1]} -> {a[1]}, ink {b[2]:.1f}% -> {a[2]:.1f}%")
        tw = 420
        th = int(tw * after.shape[0] / after.shape[1])
        rows.append(np.hstack([cv2.resize(x, (tw, th), interpolation=cv2.INTER_AREA) for x in (before, after)]))
    mw = max(r.shape[1] for r in rows)
    sheet = np.vstack([np.pad(r, ((0, 8), (0, mw - r.shape[1] + 8)), constant_values=150) for r in rows])
    cv2.imwrite(f"{out}/cleanup_sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 88])


if __name__ == "__main__":
    main()
