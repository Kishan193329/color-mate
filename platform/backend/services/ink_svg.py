"""border.svg for the app: the ink as FILLED vector shapes (CLAUDE.md §8.5cu).

The reveal game draws `border.png` at fit and switches to `border.svg` once the player zooms past
1.3x (RevealView.VECTOR_BORDER_FROM) - but only when the file is the filled-ink export it knows:
`fill-rule="evenodd"` + `fill="#000"`, one or more <path> of M/L/Q/Z (absolute or relative),
scaled by the viewBox onto the picture's pixel grid (model/reveal/BorderVector.java). The July
engine's own border.svg is the OLD per-region outline file, which the app rejects, so c2l pictures
were a 1254 px bitmap at every zoom and went soft at 3x.

    write(mask, out_path, up) -> bytes written     mask: bool ink at `up` x the canvas size

Dense contours of the ink (outer + holes; evenodd keeps the holes open), Gaussian-smoothed along
their length (SIGMA - the raster's staircase and corner nicks go, everything turns round), then
simplified and emitted as a quadratic through each edge's midpoint with the vertex as control
point - a traced circle is a circle again. Coordinates are integers on the `up` grid (the viewBox is up x the picture), relative
commands, so the file stays small.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

EPS = 1.2             # approxPolyDP epsilon, px on the `up` grid (after smoothing)
SIGMA = 4.0           # Gaussian along the DENSE contour, px on the `up` grid (~1.3 canvas px):
                      # takes the raster staircase and the stroke edges' pixel bumps out, rounds
                      # corners - the owner wants round (§8.5cv); 2.5 still showed steps at 6x,
                      # 4 is smooth and a 7 px (2.5 nib x3) stroke keeps its shape
MIN_AREA = 12.0       # contours smaller than this (px² on the `up` grid) are dust


def _smooth(pts: np.ndarray, sigma: float) -> np.ndarray:
    """Circular Gaussian smoothing of a closed dense contour."""
    if len(pts) < 8 or sigma <= 0:
        return pts
    k = int(3 * sigma) * 2 + 1
    wgt = cv2.getGaussianKernel(k, sigma).ravel()
    r = k // 2
    ext = np.concatenate([pts[-r:], pts, pts[:r]])
    return np.stack([np.convolve(ext[:, i], wgt, mode="valid") for i in range(2)], 1)


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
        c = _smooth(c.reshape(-1, 2).astype(np.float64), SIGMA)
        c = cv2.approxPolyDP(c.astype(np.float32).reshape(-1, 1, 2), EPS, True).reshape(-1, 2).astype(np.float64)
        if len(c) < 3:
            continue
        d = _path(c)
        if d:
            parts.append(d)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w // up}" height="{h // up}" '
           f'viewBox="0 0 {w} {h}">\n<path fill="#000" fill-rule="evenodd" d="{"".join(parts)}"/>\n</svg>\n')
    data = svg.encode("utf-8")
    Path(out_path).write_bytes(data)
    return len(data)
