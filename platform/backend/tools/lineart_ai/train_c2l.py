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
DISTRACT_P = float(os.environ.get("DISTRACT_P", "0.7"))   # share of crops that get texture distractors
REAL_REPEAT = int(os.environ.get("REAL_REPEAT", "12"))   # the owner's real pairs count this many times


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


def _dark(rng):
    """a pencil / ink tone: black, dark grey or dark brown (BGR)"""
    return rng.choice([(0, 0, 0), (30, 30, 30), (40, 60, 90), (20, 40, 70), (60, 60, 60)])


def distract(c, rng):
    """Texture that must NOT become ink (v2, CLAUDE.md §8.5cm): wood grain, pencil hatching, specks
    and paper grain drawn INTO the colour picture only. The line target is untouched, so the model
    learns that thin repeated texture strokes are not drawing."""
    h, w = c.shape[:2]
    out = c.copy()
    for _ in range(rng.randint(1, 3)):
        kind = rng.choice(["grain", "grain", "hatch", "dots", "paper"])
        x0, y0 = rng.randint(0, w // 2), rng.randint(0, h // 2)
        x1, y1 = rng.randint(x0 + w // 4, w), rng.randint(y0 + h // 4, h)
        layer = np.zeros((h, w), np.float32)
        if kind in ("grain", "hatch"):
            ang = rng.uniform(0, np.pi)
            dx, dy = np.cos(ang), np.sin(ang)
            step = rng.randint(5, 18) if kind == "grain" else rng.randint(4, 9)
            length = rng.randint(80, 400) if kind == "grain" else rng.randint(8, 40)
            n = max(2, ((y1 - y0) + (x1 - x0)) // step)
            for i in range(n):
                cx, cy = rng.uniform(x0, x1), rng.uniform(y0, y1)
                pts = []
                for t in np.linspace(-length / 2, length / 2, 12):
                    wob = rng.uniform(-1.5, 1.5)
                    pts.append((cx + dx * t - dy * wob, cy + dy * t + dx * wob))
                pts = np.array(pts, np.int32).reshape(-1, 1, 2)
                cv2.polylines(layer, [pts], False, rng.uniform(0.35, 1.0), rng.choice([1, 1, 2]), cv2.LINE_AA)
        elif kind == "dots":
            for _ in range(rng.randint(15, 80)):
                cv2.circle(layer, (rng.randint(x0, x1), rng.randint(y0, y1)), rng.choice([1, 1, 2]), rng.uniform(0.4, 1.0), -1)
        else:   # paper grain: directional noise, like a pencil over rough paper
            noise = np.random.default_rng(rng.randint(0, 10 ** 9)).random((h, w)).astype(np.float32)
            k = rng.choice([(1, 5), (5, 1), (3, 3)])
            noise = cv2.blur(noise, k)
            layer = np.clip((noise - 0.5) * rng.uniform(0.6, 1.4), 0, 1) * rng.uniform(0.15, 0.4)
            m = np.zeros((h, w), np.float32); m[y0:y1, x0:x1] = 1
            layer *= cv2.GaussianBlur(m, (0, 0), 15)
        tone = np.array(_dark(rng), np.float32)
        a = layer[..., None]
        out = (out * (1 - a) + tone * a).astype(np.uint8)
    return out


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
        if rng.random() < DISTRACT_P:
            c2 = distract(np.ascontiguousarray(c2), rng)
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
    real = load_pairs(os.environ["REAL_DIR"]) if os.environ.get("REAL_DIR") and os.path.isdir(os.environ["REAL_DIR"]) else []
    print(f"synthetic pairs {len(pairs)}, real pairs {len(real)} x{REAL_REPEAT}, distract_p {DISTRACT_P}", flush=True)
    pairs = pairs + real * REAL_REPEAT
    print("pairs", len(pairs), flush=True)
    dev = "cuda"
    net = UNet().to(dev)
    lr = float(os.environ.get("LR", "3e-4"))
    if os.environ.get("INIT") and os.path.isfile(os.environ["INIT"]):   # fine-tune from an earlier model
        net.load_state_dict(torch.load(os.environ["INIT"], map_location=dev))
        lr = float(os.environ.get("LR", "1.5e-4"))
        print("init from", os.environ["INIT"], "lr", lr, flush=True)
    opt = torch.optim.AdamW(net.parameters(), lr=lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=steps, pct_start=0.05)
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
