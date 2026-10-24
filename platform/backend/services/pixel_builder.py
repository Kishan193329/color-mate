"""Any picture → pixel art in the format the app's "By Pixel" engine plays.

The engine (OpenGL/GroupTexture.java) reads a small PNG where every pixel is one cell, every
distinct ARGB value is one palette number, and value 0 (fully transparent) is an empty cell that
is never coloured. So the output is exactly that: a W×H PNG with at most `colours` distinct opaque
values plus transparency.

    build(image_bgr_or_bgra, size=64, colours=16, remove_bg=True) -> (rgba uint8 HxWx4, info)
"""
from __future__ import annotations

import cv2
import numpy as np

SIZES = (32, 48, 64, 80, 96, 128)
MAX_COLOURS = 48
RNG_SEED = 1234
WORK_SIDE = 512     # colours are measured on a copy about this big (see build)


def _to_bgra(img: np.ndarray) -> np.ndarray:
    if img.ndim == 2:
        return cv2.cvtColor(img, cv2.COLOR_GRAY2BGRA)
    if img.shape[2] == 3:
        return cv2.cvtColor(img, cv2.COLOR_BGR2BGRA)
    return img


def _background_mask(bgr: np.ndarray, tol: float = 18.0) -> np.ndarray:
    """Pixels connected to the border whose colour is close to the border's typical colour.

    A plain backdrop (white paper, a flat colour) becomes empty cells, so the picture reads as a
    sprite rather than a full rectangle to fill. Busy photos have no such backdrop: when the border
    is not uniform nothing is removed.
    """
    h, w = bgr.shape[:2]
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    border = np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]])
    ref = np.median(border, axis=0)
    if np.mean(np.linalg.norm(border - ref, axis=1) < tol) < 0.6:
        return np.zeros((h, w), bool)
    close = (np.linalg.norm(lab - ref, axis=2) < tol).astype(np.uint8)
    n, labels = cv2.connectedComponents(close, connectivity=4)
    edge = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]])))
    edge.discard(0)
    return np.isin(labels, list(edge)) & (close > 0)


def _despeckle(idx: np.ndarray, solid: np.ndarray, rounds: int = 2) -> np.ndarray:
    """A cell matching none of its 4 neighbours takes their most common colour — single odd
    pixels are tedious to find and read as noise."""
    idx = idx.copy()
    h, w = idx.shape
    for _ in range(rounds):
        changed = 0
        for y in range(h):
            for x in range(w):
                if not solid[y, x]:
                    continue
                nb = [idx[yy, xx] for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                      if 0 <= yy < h and 0 <= xx < w and solid[yy, xx]]
                if len(nb) >= 3 and idx[y, x] not in nb:
                    vals, counts = np.unique(nb, return_counts=True)
                    idx[y, x] = vals[np.argmax(counts)]
                    changed += 1
        if not changed:
            break
    return idx


MERGE_DIST = 14.0   # 8-bit Lab units; closer clusters read as one colour and become one number
MIN_SHARE = 0.015   # a colour on fewer cells than this is edge blending, not a colour of its own


def _merge_close(labels: np.ndarray, centres: np.ndarray, samples: np.ndarray) -> np.ndarray:
    """k-means always returns k clusters, so a two-colour drawing comes back with extra numbers
    for the blended edge shades. Fold every cluster into a bigger one it is visibly the same as,
    and fold a cluster so small it is only edge blending (MIN_SHARE of the cells) into its nearest
    bigger neighbour whatever the distance; the bigger cluster keeps its colour."""
    counts = np.bincount(labels, minlength=len(centres))
    tiny = max(3, MIN_SHARE * len(labels))
    target = np.arange(len(centres))
    for i in np.argsort(counts):                     # smallest first
        if counts[i] == 0:
            continue
        d = np.linalg.norm(centres - centres[i], axis=1)
        d[i] = np.inf
        d[counts <= counts[i]] = np.inf              # only fold into a bigger cluster
        j = int(np.argmin(d))
        if np.isfinite(d[j]) and (d[j] < MERGE_DIST or counts[i] < tiny):
            target[i] = j
            counts[j] += counts[i]
            counts[i] = 0
    for i in range(len(target)):                     # follow chains to the final cluster
        while target[target[i]] != target[i]:
            target[i] = target[target[i]]
    return target[labels]


def build(img: np.ndarray, size: int = 64, colours: int = 16, remove_bg: bool = True):
    size = int(size) if int(size) in SIZES else 64
    colours = max(2, min(int(colours), MAX_COLOURS))
    bgra = _to_bgra(img)
    h0, w0 = bgra.shape[:2]
    scale = size / max(h0, w0)
    w, h = max(1, round(w0 * scale)), max(1, round(h0 * scale))

    # Flatten transparency onto white before measuring colours.
    a = bgra[:, :, 3:4].astype(np.float32) / 255.0
    bgr_full = (bgra[:, :, :3].astype(np.float32) * a + 255.0 * (1 - a)).astype(np.uint8)

    # Colours are decided on a WORK copy SUB times larger than the grid, then each cell takes the
    # colour most of its SUB×SUB block has. Shrinking straight to the grid averaged neighbouring
    # colours into new in-between shades, so a two-colour drawing came back with six numbers.
    sub = max(1, min(8, WORK_SIDE // size))
    W, H = w * sub, h * sub
    work = cv2.resize(bgr_full, (W, H), interpolation=cv2.INTER_AREA)
    work_alpha = cv2.resize(bgra[:, :, 3], (W, H), interpolation=cv2.INTER_AREA)

    empty_w = work_alpha < 128
    if remove_bg:
        empty_w |= _background_mask(work)
    solid_w = ~empty_w
    if not solid_w.any():
        solid_w[:] = True

    lab = cv2.cvtColor(work, cv2.COLOR_BGR2LAB).astype(np.float32)
    samples = lab[solid_w]
    k = min(colours, len(np.unique(samples.round(), axis=0)))
    cv2.setRNGSeed(RNG_SEED)
    _, labels, centres = cv2.kmeans(samples, k, None,
                                    (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 40, 0.5),
                                    4, cv2.KMEANS_PP_CENTERS)
    labels = _merge_close(labels.ravel(), centres, samples)
    work_idx = np.full((H, W), len(centres), np.int32)          # extra id = empty
    work_idx[solid_w] = labels

    # Majority vote per cell; a cell is empty when most of its block is.
    blocks = work_idx.reshape(h, sub, w, sub).transpose(0, 2, 1, 3).reshape(h, w, sub * sub)
    counts = np.stack([(blocks == i).sum(axis=2) for i in range(len(centres) + 1)], axis=2)
    idx = counts.argmax(axis=2).astype(np.int32)
    solid = idx != len(centres)
    if not solid.any():
        solid[:] = True
        idx = counts[:, :, :len(centres)].argmax(axis=2).astype(np.int32)
    idx[~solid] = -1
    idx = _despeckle(idx, solid)

    centres_bgr = cv2.cvtColor(centres.reshape(-1, 1, 3).astype(np.uint8), cv2.COLOR_LAB2BGR).reshape(-1, 3)
    used = sorted(set(np.unique(idx[solid]).tolist()))
    out = np.zeros((h, w, 4), np.uint8)             # 0 = empty cell, exactly what the engine wants
    for i in used:
        m = idx == i
        out[m, :3] = centres_bgr[i]
        out[m, 3] = 255
    # Two clusters can land on the same 8-bit colour; the engine would merge them, which is fine.
    distinct = len({tuple(c) for c in out[solid][:, :3].tolist()})
    return out, {"w": w, "h": h, "colours": distinct, "cells": int(solid.sum())}


def preview(rgba: np.ndarray, cell: int = 8) -> np.ndarray:
    """A big nearest-neighbour copy on a light checkerboard, for the admin."""
    h, w = rgba.shape[:2]
    big = cv2.resize(rgba, (w * cell, h * cell), interpolation=cv2.INTER_NEAREST)
    yy, xx = np.mgrid[0:h * cell, 0:w * cell]
    checker = np.where(((yy // cell) + (xx // cell)) % 2 == 0, 245, 230).astype(np.uint8)
    a = big[:, :, 3:4].astype(np.float32) / 255.0
    bg = np.dstack([checker] * 3).astype(np.float32)
    rgb = big[:, :, :3].astype(np.float32) * a + bg * (1 - a)
    return rgb.astype(np.uint8)
