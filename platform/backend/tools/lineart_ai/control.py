"""Control images for the line-art ControlNet: how much of the photo's texture reaches the model.

Canny on a raw photo copies every texture (fur, beads, foliage) and the model draws it all.
Flattening first (mean-shift + a few colours) keeps the shapes and drops the texture.
"""
import cv2
import numpy as np


def _fit(im, side=1024):
    h, w = im.shape[:2]
    s = side / max(h, w)
    W, H = int(round(w * s / 8) * 8), int(round(h * s / 8) * 8)
    return cv2.resize(im, (W, H), interpolation=cv2.INTER_AREA)


def flatten(im, sp=14, sr=28, colours=12):
    """Mean-shift on a half-size copy (texture goes, edges stay), then k-means to a few colours."""
    small = cv2.resize(im, (im.shape[1] // 2, im.shape[0] // 2), interpolation=cv2.INTER_AREA)
    ms = cv2.pyrMeanShiftFiltering(small, sp, sr)
    ms = cv2.resize(ms, (im.shape[1], im.shape[0]), interpolation=cv2.INTER_LINEAR)
    lab = cv2.cvtColor(ms, cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    cv2.setRNGSeed(7)
    _, lbl, cen = cv2.kmeans(lab, colours, None, (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0),
                             2, cv2.KMEANS_PP_CENTERS)
    q = cen[lbl.ravel()].reshape(ms.shape).astype(np.uint8)
    q = cv2.medianBlur(cv2.cvtColor(q, cv2.COLOR_LAB2BGR), 5)
    return q


def edges(im, lo, hi, min_len=0):
    e = cv2.Canny(cv2.cvtColor(im, cv2.COLOR_BGR2GRAY), lo, hi)
    if min_len:
        n, lab, st, _ = cv2.connectedComponentsWithStats(e, 8)
        keep = np.zeros(n, bool)
        keep[1:] = st[1:, cv2.CC_STAT_AREA] >= min_len
        e = np.where(keep[lab], 255, 0).astype(np.uint8)
    return e


VARIANTS = {
    "A_raw": lambda im: edges(cv2.bilateralFilter(im, 9, 60, 60), 70, 170),
    "B_flat": lambda im: edges(flatten(im, 14, 28, 14), 50, 150, 30),
    "C_flatter": lambda im: edges(flatten(im, 22, 40, 9), 50, 150, 60),
}


def sketch_edges(sketch_gray, size, ink_cut=70, min_len=25):
    """The server's line-art model output (white paper, dark strokes) as a control map: the
    strokes it drew, not the photo's texture."""
    s = cv2.resize(sketch_gray, size, interpolation=cv2.INTER_AREA)
    ink = ((255 - s.astype(np.int16)) > ink_cut).astype(np.uint8) * 255
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    keep = np.zeros(n, bool)
    keep[1:] = st[1:, cv2.CC_STAT_AREA] >= min_len
    return np.where(keep[lab], 255, 0).astype(np.uint8)


def control(path, variant):
    im = _fit(cv2.imread(path))
    if variant == "D_sketch":
        sk = cv2.imread(path.rsplit(".", 1)[0] + "_sketch.png", cv2.IMREAD_GRAYSCALE)
        e = sketch_edges(sk, (im.shape[1], im.shape[0]))
    else:
        e = VARIANTS[variant](im)
    return np.stack([e] * 3, axis=2), (im.shape[1], im.shape[0])
