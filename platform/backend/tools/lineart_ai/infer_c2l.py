"""Run the colour->line model (train_c2l.py) on pictures. CPU is fine.

    python infer_c2l.py c2l.pt OUT_DIR IMAGE [IMAGE...]
Writes OUT_DIR/<name>_prob.png (ink probability) and <name>_line.png (thresholded at 0.5).
"""
import os
import sys

import cv2
import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from train_c2l import UNet  # noqa: E402

MAX_SIDE = 1254


def predict(net, bgr, dev="cpu"):
    h, w = bgr.shape[:2]
    s = MAX_SIDE / max(h, w)
    if s < 1:
        bgr = cv2.resize(bgr, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
    h, w = bgr.shape[:2]
    ph, pw = (16 - h % 16) % 16, (16 - w % 16) % 16
    pad = cv2.copyMakeBorder(bgr, 0, ph, 0, pw, cv2.BORDER_REFLECT)
    x = torch.tensor(np.ascontiguousarray(pad[..., ::-1]).transpose(2, 0, 1)[None] / 255.0, dtype=torch.float32, device=dev)
    with torch.no_grad():
        p = torch.sigmoid(net(x))[0, 0].cpu().numpy()
    return p[:h, :w]


def main():
    weights, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    net = UNet().to(dev)
    net.load_state_dict(torch.load(weights, map_location=dev))
    net.eval()
    for p in sys.argv[3:]:
        name = os.path.splitext(os.path.basename(p))[0]
        prob = predict(net, cv2.imread(p), dev)
        cv2.imwrite(f"{out}/{name}_prob.png", (255 - prob * 255).astype(np.uint8))
        cv2.imwrite(f"{out}/{name}_line.png", np.where(prob > 0.5, 0, 255).astype(np.uint8))
        print(name, "ink %.1f%%" % ((prob > 0.5).mean() * 100), flush=True)


if __name__ == "__main__":
    main()
