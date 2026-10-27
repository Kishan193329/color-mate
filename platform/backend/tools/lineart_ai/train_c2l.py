"""Colour -> line art: a small fully-convolutional U-Net trained on make_pairs.py output.

Input: a colour picture (any size, trained on 512 crops). Output: ink probability per pixel.
Small enough to run on the VPS CPU at 1254 px (a few seconds), like the existing lineart model.

    python train_c2l.py PAIRS_DIR OUT_DIR [steps=6000]
Writes OUT_DIR/c2l.pt (weights, fp32) and a sample sheet every 1000 steps.
"""
import glob
import os
import random
import sys

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

CROP = 512
BATCH = 6


def block(i, o):
    return nn.Sequential(nn.Conv2d(i, o, 3, padding=1), nn.BatchNorm2d(o), nn.ReLU(inplace=True),
                         nn.Conv2d(o, o, 3, padding=1), nn.BatchNorm2d(o), nn.ReLU(inplace=True))


class UNet(nn.Module):
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
            s = skips.pop()
            x = d(torch.cat([x, s], 1))
        return self.out(x)


def load_pairs(d):
    cs = sorted(glob.glob(os.path.join(d, "*_c.png")))
    pairs = []
    for c in cs:
        l = c[:-6] + "_l.png"
        if os.path.exists(l):
            pairs.append((cv2.imread(c), cv2.imread(l, cv2.IMREAD_GRAYSCALE)))
    return pairs


def sample(pairs, rng):
    xs, ys = [], []
    for _ in range(BATCH):
        c, l = rng.choice(pairs)
        # scale jitter: the model must work on 1254 px uploads of any drawing size
        s = rng.uniform(0.6, 1.3)
        h, w = l.shape
        c2 = cv2.resize(c, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA if s < 1 else cv2.INTER_CUBIC)
        l2 = cv2.resize(l, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
        h, w = l2.shape
        if h < CROP or w < CROP:
            c2 = cv2.copyMakeBorder(c2, 0, max(0, CROP - h), 0, max(0, CROP - w), cv2.BORDER_CONSTANT, value=(255, 255, 255))
            l2 = cv2.copyMakeBorder(l2, 0, max(0, CROP - h), 0, max(0, CROP - w), cv2.BORDER_CONSTANT, value=255)
            h, w = l2.shape
        y0, x0 = rng.randint(0, h - CROP), rng.randint(0, w - CROP)
        c2, l2 = c2[y0:y0 + CROP, x0:x0 + CROP], l2[y0:y0 + CROP, x0:x0 + CROP]
        if rng.random() < 0.5:
            c2, l2 = c2[:, ::-1], l2[:, ::-1]
        # colour jitter + mild blur/noise: real uploads are JPEGs of varied brightness
        c2 = cv2.convertScaleAbs(c2, alpha=rng.uniform(0.85, 1.15), beta=rng.uniform(-15, 15))
        if rng.random() < 0.3:
            c2 = cv2.GaussianBlur(c2, (0, 0), rng.uniform(0.4, 1.0))
        xs.append(np.ascontiguousarray(c2[..., ::-1]).transpose(2, 0, 1) / 255.0)
        ys.append((l2 < 128).astype(np.float32)[None])
    return (torch.tensor(np.stack(xs), dtype=torch.float32), torch.tensor(np.stack(ys)))


def main():
    src, out = sys.argv[1], sys.argv[2]
    steps = int(sys.argv[3]) if len(sys.argv) > 3 else 6000
    os.makedirs(out, exist_ok=True)
    pairs = load_pairs(src)
    print("pairs", len(pairs), flush=True)
    dev = "cuda"
    net = UNet().to(dev)
    opt = torch.optim.AdamW(net.parameters(), lr=3e-4, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=3e-4, total_steps=steps, pct_start=0.05)
    rng = random.Random(7)
    scaler = torch.cuda.amp.GradScaler()
    pos_w = torch.tensor([3.0], device=dev)     # ink is ~15% of pixels; weight it up
    for step in range(1, steps + 1):
        x, y = sample(pairs, rng)
        x, y = x.to(dev), y.to(dev)
        with torch.autocast("cuda", dtype=torch.float16):
            logit = net(x)
            bce = F.binary_cross_entropy_with_logits(logit, y, pos_weight=pos_w)
            p = torch.sigmoid(logit)
            dice = 1 - (2 * (p * y).sum() + 1) / (p.sum() + y.sum() + 1)
            loss = bce + dice
        opt.zero_grad(set_to_none=True)
        scaler.scale(loss).backward()
        scaler.step(opt)
        scaler.update()
        sched.step()
        if step % 100 == 0:
            print(f"step {step} loss {loss.item():.4f} bce {bce.item():.4f} dice {dice.item():.4f}", flush=True)
        if step % 1000 == 0 or step == steps:
            torch.save(net.state_dict(), f"{out}/c2l.pt")
            net.eval()
            with torch.no_grad():
                pr = torch.sigmoid(net(x[:3])).cpu().numpy()
            net.train()
            tiles = []
            for i in range(3):
                a = (x[i].cpu().numpy().transpose(1, 2, 0)[..., ::-1] * 255).astype(np.uint8)
                b = cv2.cvtColor((255 - y[i, 0].cpu().numpy() * 255).astype(np.uint8), cv2.COLOR_GRAY2BGR)
                c = cv2.cvtColor((255 - pr[i, 0] * 255).astype(np.uint8), cv2.COLOR_GRAY2BGR)
                tiles.append(np.hstack([a, b, c]))
            cv2.imwrite(f"{out}/sample_{step}.jpg", np.vstack(tiles), [cv2.IMWRITE_JPEG_QUALITY, 80])
    print("TRAIN_DONE")


if __name__ == "__main__":
    main()
