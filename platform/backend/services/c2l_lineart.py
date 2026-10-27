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
SPECK = 40            # ink pieces smaller than this (px) are dirt

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


def generate(image_path: Path, out_path: Path) -> Path:
    bgr = cv2.imread(str(image_path))
    if bgr is None:
        raise ValueError(f"unreadable {image_path}")
    ink = (predict(bgr) > THRESHOLD).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    keep = np.zeros(n, bool)
    keep[1:] = st[1:, cv2.CC_STAT_AREA] >= SPECK
    ink = keep[lab]
    cv2.imwrite(str(out_path), np.where(ink, 0, 255).astype(np.uint8))
    return out_path
