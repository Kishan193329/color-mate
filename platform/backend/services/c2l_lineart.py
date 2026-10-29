"""Our own colour -> line-art model (trained with tools/lineart_ai/train_c2l.py, CLAUDE.md §8.5ck).

For pictures that already carry a drawn outline (coloured-pencil / cartoon art) it returns the
clean coloring-page line, close to a hand-made one. It is NOT for photographs — those keep the
AI line-art route. Runs on the CPU: a small fully-convolutional U-Net at the engine's 1254 px.

    generate(image_path, out_path) -> out_path   (ai_lineart.png: black strokes on white)

Weights: $C2L_MODEL, else <backend>/models/c2l/c2l.pt (25 MB, not in git).
"""
from __future__ import annotations

import os
from pathlib import Path

import cv2
import numpy as np

MODEL_PATH = Path(os.environ.get("C2L_MODEL") or Path(__file__).resolve().parent.parent / "models" / "c2l" / "c2l.pt")
MAX_SIDE = 1254
THRESHOLD = 0.5
# Dirt filter (measured on the owner's three pencil cottages, CLAUDE.md §8.5cl): the model emits
# 1,000-2,400 ink pieces per picture of which only 17-46 are drawing — the rest are pencil-grain
# dashes and dots, every one under MIN_DIM px with a weak peak confidence (~0.8 vs 1.00).
MIN_DIM = 35          # a piece narrower than this in both directions is a dot/dash ...
MIN_HOLE = 12         # ... unless it encloses a hole this big: a wheel hub, a flower centre, a bolt
STRONG = 0.95         # and every kept piece must contain at least one confident pixel

_net = None


def available() -> bool:
    return MODEL_PATH.is_file()


def _build():
    import torch
    import torch.nn as nn
    import torch.nn.functional as F

    def block(i, o):
        return nn.Sequential(nn.Conv2d(i, o, 3, padding=1), nn.BatchNorm2d(o), nn.ReLU(inplace=True),
                             nn.Conv2d(o, o, 3, padding=1), nn.BatchNorm2d(o), nn.ReLU(inplace=True))

    class UNet(nn.Module):      # must match train_c2l.UNet exactly (same state-dict keys)
        def __init__(self, ch=(32, 64, 128, 256, 384)):
            super().__init__()
            self.enc = nn.ModuleList()
            c = 3
            for o in ch:
                self.enc.append(block(c, o))
                c = o
            self.dec = nn.ModuleList()
            self.up = nn.ModuleList()
            for o in reversed(ch[:-1]):
                self.up.append(nn.ConvTranspose2d(c, o, 2, stride=2))
                self.dec.append(block(o * 2, o))
                c = o
            self.out = nn.Conv2d(c, 1, 1)

        def forward(self, x):
            skips = []
            for i, e in enumerate(self.enc):
                x = e(x)
                if i < len(self.enc) - 1:
                    skips.append(x)
                    x = F.max_pool2d(x, 2)
            for up, d in zip(self.up, self.dec):
                x = up(x)
                x = d(torch.cat([x, skips.pop()], 1))
            return self.out(x)

    net = UNet()
    net.load_state_dict(torch.load(MODEL_PATH, map_location="cpu"))
    net.eval()
    return net


def _model():
    global _net
    if _net is None:
        if not available():
            raise FileNotFoundError(f"c2l model missing: {MODEL_PATH}")
        _net = _build()
    return _net


def predict(bgr: np.ndarray) -> np.ndarray:
    """Ink probability per pixel, at the (capped) input size."""
    import torch
    h, w = bgr.shape[:2]
    s = MAX_SIDE / max(h, w)
    if s < 1:
        bgr = cv2.resize(bgr, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
    h, w = bgr.shape[:2]
    ph, pw = (16 - h % 16) % 16, (16 - w % 16) % 16
    pad = cv2.copyMakeBorder(bgr, 0, ph, 0, pw, cv2.BORDER_REFLECT)
    x = torch.tensor(np.ascontiguousarray(pad[..., ::-1]).transpose(2, 0, 1)[None] / 255.0, dtype=torch.float32)
    with torch.no_grad():
        p = torch.sigmoid(_model()(x))[0, 0].numpy()
    return p[:h, :w]


def _hole_owner(ink: np.ndarray, lab: np.ndarray, n: int) -> np.ndarray:
    """Per ink component, the area of the biggest hole it encloses (0 = none).

    A hole is a background component that does not touch the image border; its owner is the ink
    component touching it along the most pixels (a hole wedged between a dash and a wall belongs to
    the wall, not the dash)."""
    bg = (ink == 0).astype(np.uint8)
    nb, bl, bst, _ = cv2.connectedComponentsWithStats(bg, 4)
    big = bst[:, cv2.CC_STAT_AREA] >= MIN_HOLE
    big[np.unique(np.concatenate([bl[0], bl[-1], bl[:, 0], bl[:, -1]]))] = False   # touches the border
    big[0] = False
    hole = np.zeros(n, np.int64)
    if not big.any():
        return hole
    pairs = []
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):   # ink pixel next to a hole pixel -> (hole, owner)
        a = lab[max(dy, 0):lab.shape[0] + min(dy, 0), max(dx, 0):lab.shape[1] + min(dx, 0)]
        b = bl[max(-dy, 0):bl.shape[0] + min(-dy, 0), max(-dx, 0):bl.shape[1] + min(-dx, 0)]
        m = (a > 0) & big[b]
        pairs.append(b[m].astype(np.int64) * n + a[m])
    pairs = np.concatenate(pairs)
    if pairs.size == 0:
        return hole
    key, cnt = np.unique(pairs, return_counts=True)
    order = np.lexsort((-cnt, key // n))               # per hole, most-touching owner first
    key, hb = key[order], (key[order] // n)
    first = np.r_[True, hb[1:] != hb[:-1]]
    owner = key[first] % n
    np.maximum.at(hole, owner, bst[hb[first], cv2.CC_STAT_AREA])
    return hole


def clean(prob: np.ndarray) -> np.ndarray:
    """Ink mask from the model's probabilities with pencil dirt removed (see the constants)."""
    ink = (prob > THRESHOLD).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    dim = np.maximum(st[:, cv2.CC_STAT_WIDTH], st[:, cv2.CC_STAT_HEIGHT])
    peak = np.zeros(n, np.float32)
    np.maximum.at(peak, lab.ravel(), prob.ravel().astype(np.float32))
    keep = ((dim >= MIN_DIM) | (_hole_owner(ink, lab, n) > 0)) & (peak >= STRONG)
    keep[0] = False
    return keep[lab]


def generate(image_path: Path, out_path: Path) -> Path:
    bgr = cv2.imread(str(image_path))
    if bgr is None:
        raise ValueError(f"unreadable {image_path}")
    ink = clean(predict(bgr))
    cv2.imwrite(str(out_path), np.where(ink, 0, 255).astype(np.uint8))
    return out_path
