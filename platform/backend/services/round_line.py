"""Clean round pen strokes from a line mask (CLAUDE.md §8.5cq).

The owner's references (his Majestic Lion SVG, the reference app's rose / eye / fox girl) are pen
drawings: every stroke a smooth curve with rounded edges and ends, nothing lumpy. A mask cut out of
a painted picture (services/png_line) has the right SHAPE - where the line runs, how wide it is -
but a ragged, lumpy edge. So each stroke is redrawn:

  skeleton   Zhang-Suen thinning of the mask, spurs < SPUR px pruned
  strokes    the skeleton split at junctions into polylines, each traced in order
  smooth     x/y of each polyline Gaussian-smoothed along its length (SMOOTH_XY), ends pinned
  width      2 x the mask's distance transform along the polyline, smoothed (SMOOTH_W), lumps
             capped at LUMP x the stroke's median, clamped to [W_MIN, W_MAX]
  render     every segment drawn at UP x with cv2.line (round caps/joins), a disc at each junction,
             anti-aliased edge

    strokes(mask) -> (2x grey, stats)        mask: bool, canvas size
"""
from __future__ import annotations

import cv2
import numpy as np

SPUR = 6          # px: skeleton branches shorter than this are edge bumps, not strokes
SMOOTH_XY = 3.0   # sigma (px along the stroke) for the centre line
SMOOTH_W = 8.0    # sigma for the width along the stroke
LUMP = 1.6        # a width over LUMP x the stroke's median is a lump, capped
W_MIN = 2.0       # px at canvas size
W_MAX = 18.0
UP = 2
CORE_ERODE = 2    # px at canvas size: the mask minus its ragged edge, unioned so wide bands stay solid
CLOSE = 3         # px at UP x: closes the sliver left between two parallel strokes of one band
EDGE = 0.9        # sigma of the final anti-alias blur at UP x
_N8 = ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1))


def _thin(mask: np.ndarray) -> np.ndarray:
    """Zhang-Suen thinning: a 1 px, 8-connected skeleton. Pure numpy."""
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


def _nb(sk: np.ndarray) -> np.ndarray:
    """Number of 8-neighbours on the skeleton, per pixel."""
    return cv2.filter2D(sk.astype(np.uint8), -1, np.ones((3, 3), np.float32), borderType=cv2.BORDER_CONSTANT).astype(np.int32) - sk.astype(np.int32)


def _prune(sk: np.ndarray, k: int) -> np.ndarray:
    cut = sk.copy()
    for _ in range(k):
        cut &= ~(cut & (_nb(cut) <= 1))
    grow = cut & (_nb(cut) <= 1)
    for _ in range(k):
        grow = (cv2.dilate(grow.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0) & sk & ~cut
        cut |= grow
    return cut


def _polylines(sk: np.ndarray):
    """Split the skeleton at junctions and trace every piece into an ordered list of (y, x).
    Returns (polylines, junction pixels). Each polyline ends on the junction pixel it touched, so
    the pieces of a drawing meet again when drawn."""
    nb = _nb(sk)
    junction = sk & (nb >= 3)
    seg = sk & ~junction
    n, lab = cv2.connectedComponents(seg.astype(np.uint8), connectivity=8)
    jset = set(zip(*np.nonzero(junction)))
    ys, xs = np.nonzero(seg)
    by_lab = {}
    for y, x, l in zip(ys.tolist(), xs.tolist(), lab[ys, xs].tolist()):
        by_lab.setdefault(l, set()).add((y, x))
    out = []
    for pts in by_lab.values():
        remaining = set(pts)
        while remaining:
            # start at a free end if the piece has one, else anywhere (a closed loop)
            start = None
            for p in remaining:
                deg = sum((p[0] + dy, p[1] + dx) in remaining for dy, dx in _N8)
                if deg <= 1:
                    start = p
                    break
            if start is None:
                start = next(iter(remaining))
            line = [start]
            remaining.discard(start)
            cur = start
            while True:
                nxt = None
                for dy, dx in ((-1, 0), (0, -1), (0, 1), (1, 0), (-1, -1), (-1, 1), (1, -1), (1, 1)):
                    q = (cur[0] + dy, cur[1] + dx)
                    if q in remaining:
                        nxt = q
                        break
                if nxt is None:
                    break
                line.append(nxt)
                remaining.discard(nxt)
                cur = nxt
            # pin each end to the junction it touched
            for end, insert_front in ((line[0], True), (line[-1], False)):
                for dy, dx in _N8:
                    j = (end[0] + dy, end[1] + dx)
                    if j in jset:
                        if insert_front:
                            line.insert(0, j)
                        else:
                            line.append(j)
                        break
            out.append(line)
    return out, junction


def _smooth1d(v: np.ndarray, sigma: float) -> np.ndarray:
    if len(v) < 4 or sigma <= 0:
        return v
    return cv2.GaussianBlur(v.reshape(-1, 1).astype(np.float32), (0, 0), sigma,
                            borderType=cv2.BORDER_REPLICATE).ravel()


def strokes(mask: np.ndarray):
    h, w = mask.shape
    sk = _prune(_thin(mask), SPUR)
    d = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 5)
    lines, junction = _polylines(sk)
    canvas = np.zeros((h * UP, w * UP), np.uint8)
    n_seg = 0
    for line in lines:
        p = np.array(line, np.float32)
        if len(p) < 2:
            continue
        wv = 2.0 * d[p[:, 0].astype(int), p[:, 1].astype(int)]
        wv = np.minimum(wv, LUMP * max(float(np.median(wv)), W_MIN))
        wv = np.clip(_smooth1d(wv, SMOOTH_W), W_MIN, W_MAX)
        y0, x0, y1, x1 = p[0, 0], p[0, 1], p[-1, 0], p[-1, 1]
        p[:, 0] = _smooth1d(p[:, 0], SMOOTH_XY)
        p[:, 1] = _smooth1d(p[:, 1], SMOOTH_XY)
        p[0] = (y0, x0)
        p[-1] = (y1, x1)                               # ends stay where the pieces meet
        pts = np.round(p[:, ::-1] * UP).astype(np.int32)   # (x, y) at UP x
        for i in range(len(pts) - 1):
            t = max(1, int(round(float(wv[i] + wv[i + 1]) * 0.5 * UP)))
            cv2.line(canvas, tuple(pts[i]), tuple(pts[i + 1]), 255, t, lineType=cv2.LINE_8)
            n_seg += 1
    for y, x in zip(*np.nonzero(junction)):
        r = max(1, int(round(float(d[y, x]) * UP)))
        cv2.circle(canvas, (int(x * UP), int(y * UP)), r, 255, -1)
    # a wide lumpy band thins into two parallel skeleton branches with a sliver of paper between
    # them: the mask's core (its ragged edge eroded away) fills the band, the strokes keep the edge
    core = cv2.erode(mask.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * CORE_ERODE + 1,) * 2))
    core = cv2.resize(core.astype(np.float32), (w * UP, h * UP), interpolation=cv2.INTER_LINEAR)
    ink = (canvas > 127) | (cv2.GaussianBlur(core, (0, 0), 1.5) > 0.5)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * CLOSE + 1,) * 2)
    ink = cv2.morphologyEx(ink.astype(np.uint8), cv2.MORPH_CLOSE, k) > 0
    g = cv2.GaussianBlur(ink.astype(np.float32), (0, 0), EDGE)
    g2 = np.clip((1 - g) * 255, 0, 255).astype(np.uint8)
    return g2, dict(strokes=len(lines), segments=n_seg, ink_in=float(mask.mean()), ink_out=float((g2 < 128).mean()))
