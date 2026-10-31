"""border.svg for the app: the ink as FILLED vector shapes (CLAUDE.md §8.5cu).

The reveal game draws `border.png` at fit and switches to `border.svg` once the player zooms past
1.3x (RevealView.VECTOR_BORDER_FROM) - but only when the file is the filled-ink export it knows:
`fill-rule="evenodd"` + `fill="#000"`, one or more <path> of M/L/Q/Z (absolute or relative),
scaled by the viewBox onto the picture's pixel grid (model/reveal/BorderVector.java). The July
engine's own border.svg is the OLD per-region outline file, which the app rejects, so c2l pictures
were a 1254 px bitmap at every zoom and went soft at 3x.

    write(mask, out_path, up) -> bytes written     mask: bool ink at `up` x the canvas size

Contours of the ink (outer + holes; evenodd keeps the holes open), simplified, smoothed by a
3-tap average capped at SMOOTH_MAX_PX so sharp junctions stay sharp (§8.5bp), and emitted as a
quadratic through each edge's midpoint with the vertex as control point - a traced circle is a
circle again. Coordinates are integers on the `up` grid (the viewBox is up x the picture), relative
commands, so the file stays small.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

EPS = 0.75            # approxPolyDP epsilon, px on the `up` grid
SMOOTH_MAX_PX = 0.5   # the 3-tap average may move a vertex at most this far (canvas px)
MIN_AREA = 12.0       # contours smaller than this (px² on the `up` grid) are dust


def _smooth(pts: np.ndarray, max_move: float) -> np.ndarray:
    if len(pts) < 4:
        return pts
    avg = (np.roll(pts, 1, 0) + pts + np.roll(pts, -1, 0)) / 3.0
    d = avg - pts
    n = np.linalg.norm(d, axis=1)
    k = np.minimum(1.0, max_move / np.maximum(n, 1e-6))[:, None]
    return pts + d * k


def _path(pts: np.ndarray) -> str:
    """Closed quadratic curve through the midpoints, vertices as control points. Relative."""
    pts = np.round(pts).astype(np.int64)
    n = len(pts)
    if n < 3:
        return ""
    mids = (pts + np.roll(pts, -1, 0)) / 2.0           # midpoint of edge i -> i+1
    mids = np.round(mids).astype(np.int64)
    out = [f"M{mids[0][0]} {mids[0][1]}"]
    pen = mids[0].copy()
    for i in range(n):
        ctrl = pts[(i + 1) % n]
        end = mids[(i + 1) % n]
        out.append(f"q{ctrl[0] - pen[0]} {ctrl[1] - pen[1]} {end[0] - pen[0]} {end[1] - pen[1]}")
        pen = end
    out.append("z")
    return "".join(out)


def write(mask: np.ndarray, out_path: Path, up: int) -> int:
    h, w = mask.shape
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    parts = []
    for c in contours:
        if abs(cv2.contourArea(c)) < MIN_AREA:
            continue
        c = cv2.approxPolyDP(c, EPS, True).reshape(-1, 2).astype(np.float64)
        if len(c) < 3:
            continue
        c = _smooth(c, SMOOTH_MAX_PX * up)
        d = _path(c)
        if d:
            parts.append(d)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w // up}" height="{h // up}" '
           f'viewBox="0 0 {w} {h}">\n<path fill="#000" fill-rule="evenodd" d="{"".join(parts)}"/>\n</svg>\n')
    data = svg.encode("utf-8")
    Path(out_path).write_bytes(data)
    return len(data)
