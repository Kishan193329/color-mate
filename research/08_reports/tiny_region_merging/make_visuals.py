"""Side-by-side region-boundary overlays (merge OFF vs ON) for the tiny-region-merging
report. Reads ctl_off_XX / ctl_on_XX job dirs left by batch_ctrl.py, draws region
boundaries (from labels.npy) over a faded original, annotates each panel with the
game_config region count, and writes visual_XX.png. Args: index [index ...]."""
from __future__ import annotations
import json, sys
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
JOBS = REPO / "storage" / "jobs"
OUT = REPO / "research" / "08_reports" / "tiny_region_merging"
OUT.mkdir(parents=True, exist_ok=True)


def _font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def boundaries(labels):
    """True where a pixel's region label differs from its right or bottom neighbour."""
    b = np.zeros(labels.shape, dtype=bool)
    b[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    b[:-1, :] |= labels[:-1, :] != labels[1:, :]
    b |= labels == 0  # line-art skeleton
    return b


def region_count(folder):
    gc = folder / "game_config.json"
    if gc.exists():
        return len(json.loads(gc.read_text()).get("regions", []))
    return None


def panel(folder, title):
    orig = cv2.cvtColor(cv2.imread(str(folder / "original.png")), cv2.COLOR_BGR2RGB)
    labels = np.load(folder / "labels.npy")
    if labels.shape != orig.shape[:2]:
        orig = cv2.resize(orig, (labels.shape[1], labels.shape[0]), interpolation=cv2.INTER_AREA)
    faded = (orig.astype(np.float32) * 0.45 + 255 * 0.55).astype(np.uint8)
    b = boundaries(labels)
    # thicken boundaries 1px for visibility
    b = cv2.dilate(b.astype(np.uint8), np.ones((2, 2), np.uint8)).astype(bool)
    faded[b] = (20, 20, 20)
    img = Image.fromarray(faded)
    bar_h = max(34, img.height // 16)
    canvas = Image.new("RGB", (img.width, img.height + bar_h), (255, 255, 255))
    canvas.paste(img, (0, bar_h))
    d = ImageDraw.Draw(canvas)
    d.rectangle([0, 0, img.width, bar_h], fill=(30, 30, 35))
    d.text((10, bar_h // 2), title, fill=(255, 255, 255), font=_font(int(bar_h * 0.5)), anchor="lm")
    return canvas


def make(i):
    off_d, on_d = JOBS / f"ctl_off_{i:02d}", JOBS / f"ctl_on_{i:02d}"
    if not (off_d / "labels.npy").exists() or not (on_d / "labels.npy").exists():
        print(f"img {i:02d}: missing labels, skipped")
        return
    n_off, n_on = region_count(off_d), region_count(on_d)
    left = panel(off_d, f"BEFORE  -  {n_off} regions")
    right = panel(on_d, f"AFTER  -  {n_on} regions  (merged {n_off - n_on} tiny)")
    gap = 12
    W = left.width + right.width + gap
    H = max(left.height, right.height)
    strip = Image.new("RGB", (W, H), (255, 255, 255))
    strip.paste(left, (0, 0))
    strip.paste(right, (left.width + gap, 0))
    out = OUT / f"visual_{i:02d}.png"
    strip.save(out)
    print(f"img {i:02d}: {n_off} -> {n_on} (-{n_off - n_on})  {out.name}")


if __name__ == "__main__":
    idx = [int(a) for a in sys.argv[1:]] or [14, 16, 27]
    for i in idx:
        make(i)
