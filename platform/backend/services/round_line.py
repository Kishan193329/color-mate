"""Clean round pen strokes (CLAUDE.md §8.5cq / §8.5cr).

The owner's references (his Majestic Lion SVG, the reference app's rose / eye / fox girl) are pen
drawings: every stroke one smooth curve of one confident width, rounded edges and ends, nothing
lumpy. Two inputs give that:
  topology  the LINE MODEL's drawing (services/c2l_lineart) - it draws lines, never fills, so a
            lantern is a lantern and a hinge a scroll; a mask cut from the painting (png_line) is a
            tangle of thousands of crumbs wherever the picture has dark metal or shadow
  width     the painting's own dark line (png_line.extract) - where the model's stroke runs inside
            it, the stroke takes that line's width, so outer contours come out bold and details thin
Each stroke: skeleton (numpy Zhang-Suen, spurs pruned) -> split at junctions into polylines ->
one width per stroke (PEN_PCT percentile of the painting's width along it, snapped to a NIB) ->
free ends tapered -> centre line Gaussian-smoothed with its ends pinned -> drawn at UP x with
cv2.line (round caps/joins) + a disc per junction -> small close -> anti-aliased edge.

    strokes(topology, width_src=None) -> (2x grey, stats)     both bool masks at canvas size
"""
from __future__ import annotations

import cv2
import numpy as np

SPUR = 6          # px: skeleton branches shorter than this are edge bumps, not strokes
SMOOTH_XY = 3.0   # sigma (px along the stroke) for the centre line
PEN_PCT = 60      # the stroke's width = this percentile of the painting's line width along it
NIBS = (2.5, 4.0, 6.0, 9.0)   # the pen set (px at canvas size) a stroke width snaps to
MIN_PIECE = 14    # px: an isolated stroke shorter than this is a crumb
TAPER = 2.5       # free ends thin over this many widths ...
TAPER_TIP = 0.35  # ... down to this share of the width
W_MAX = 18.0
UP = 2
CLOSE = 3         # px at UP x: closes the sliver left between two strokes of one band
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
            start = None
            for p in remaining:
                deg = sum((p[0] + dy, p[1] + dx) in remaining for dy, dx in _N8)
                if deg <= 1:
                    start = p
                    break
            if start is None:
                start = next(iter(remaining))        # a closed loop
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


def _on_junction(line, junction: np.ndarray, end: int) -> bool:
    y, x = line[0] if end == 0 else line[-1]
    return bool(junction[y, x])


def _smooth1d(v: np.ndarray, sigma: float) -> np.ndarray:
    if len(v) < 4 or sigma <= 0:
        return v
    return cv2.GaussianBlur(v.reshape(-1, 1).astype(np.float32), (0, 0), sigma,
                            borderType=cv2.BORDER_REPLICATE).ravel()


def strokes(topology: np.ndarray, width_src: np.ndarray | None = None):
    h, w = topology.shape
    sk = _prune(_thin(topology), SPUR)
    d = cv2.distanceTransform((width_src if width_src is not None else topology).astype(np.uint8), cv2.DIST_L2, 5)
    lines, junction = _polylines(sk)
    canvas = np.zeros((h * UP, w * UP), np.uint8)
    kept = 0
    for line in lines:
        p = np.array(line, np.float32)
        if len(p) < 2:
            continue
        free = [not _on_junction(line, junction, e) for e in (0, 1)]
        if len(p) < MIN_PIECE and all(free):
            continue                                            # an isolated crumb
        wv = 2.0 * d[p[:, 0].astype(int), p[:, 1].astype(int)]
        inside = wv > 0
        wc = float(np.percentile(wv[inside], PEN_PCT)) if inside.mean() > 0.5 else NIBS[0]
        wc = min(W_MAX, float(NIBS[int(np.argmin([abs(wc - n) for n in NIBS]))]))
        wv = np.full(len(p), wc, np.float32)
        for end in (0, 1):
            if free[end]:
                span = max(4, int(round(TAPER * wc)))
                idx = np.arange(len(wv)) if end == 0 else np.arange(len(wv))[::-1]
                wv = wv * (TAPER_TIP + (1 - TAPER_TIP) * np.minimum(idx, span) / span)
        y0, x0, y1, x1 = p[0, 0], p[0, 1], p[-1, 0], p[-1, 1]
        p[:, 0] = _smooth1d(p[:, 0], SMOOTH_XY)
        p[:, 1] = _smooth1d(p[:, 1], SMOOTH_XY)
        p[0] = (y0, x0)
        p[-1] = (y1, x1)                                        # ends stay where the pieces meet
        pts = np.round(p[:, ::-1] * UP).astype(np.int32)        # (x, y) at UP x
        for i in range(len(pts) - 1):
            t = max(1, int(round(float(wv[i] + wv[i + 1]) * 0.5 * UP)))
            cv2.line(canvas, tuple(pts[i]), tuple(pts[i + 1]), 255, t, lineType=cv2.LINE_8)
        kept += 1
    for y, x in zip(*np.nonzero(junction)):
        r = max(1, int(round(min(W_MAX, max(NIBS[0], 2.0 * d[y, x])) * 0.5 * UP)))
        cv2.circle(canvas, (int(x * UP), int(y * UP)), r, 255, -1)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * CLOSE + 1,) * 2)
    ink = cv2.morphologyEx((canvas > 127).astype(np.uint8), cv2.MORPH_CLOSE, k) > 0
    g = cv2.GaussianBlur(ink.astype(np.float32), (0, 0), EDGE)
    g2 = np.clip((1 - g) * 255, 0, 255).astype(np.uint8)
    return g2, dict(strokes=kept, ink_in=float(topology.mean()), ink_out=float((g2 < 128).mean()))
