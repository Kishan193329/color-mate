"""Heatmap montage of four HVI perceptual scores painted onto the regions — the artist's eye."""
from __future__ import annotations
import json, sys
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
OUT = REPO / "research" / "08_reports" / "hvi"
OUT.mkdir(parents=True, exist_ok=True)
PANELS = [("recognition_weight", "Recognition Weight"), ("viewer_attention", "Viewer Attention"),
          ("fun_to_color_score", "Fun to Color"), ("visual_complexity", "Visual Complexity")]


def font(sz):
    for n in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            continue
    return ImageFont.load_default()


def panel(labels, score_by_label, title, cell_w=520):
    n = int(labels.max()) + 1
    lut = np.zeros(n, np.float64)
    for lbl, v in score_by_label.items():
        if 0 < lbl < n:
            lut[lbl] = v
    norm = np.uint8(np.clip(lut[labels] * 255, 0, 255))
    heat = cv2.applyColorMap(norm, cv2.COLORMAP_TURBO)
    heat[labels == 0] = (30, 30, 30)
    img = Image.fromarray(cv2.cvtColor(heat, cv2.COLOR_BGR2RGB))
    r = cell_w / img.width
    img = img.resize((cell_w, int(img.height * r)))
    bar = 30
    tile = Image.new("RGB", (cell_w, img.height + bar), (24, 29, 36))
    tile.paste(img, (0, bar))
    d = ImageDraw.Draw(tile)
    d.rectangle([0, 0, cell_w, bar], fill=(30, 30, 35))
    d.text((8, bar // 2), title, fill=(235, 238, 245), font=font(17), anchor="lm")
    return tile


def make(job):
    folder = REPO / "storage" / "jobs" / job
    labels = np.load(folder / "labels.npy").astype(np.int32)
    rep = json.loads((folder / "human_visual_report.json").read_text())
    tiles = []
    for key, title in PANELS:
        sbl = {r["label"]: r["scores"][key] for r in rep["regions"] if r.get("label") is not None}
        tiles.append(panel(labels, sbl, title))
    # also an original panel
    orig = cv2.cvtColor(cv2.imread(str(folder / "original.png")), cv2.COLOR_BGR2RGB)
    oi = Image.fromarray(orig).resize((520, int(orig.shape[0] * 520 / orig.shape[1])))
    ot = Image.new("RGB", (520, oi.height + 30), (24, 29, 36))
    ot.paste(oi, (0, 30))
    ImageDraw.Draw(ot).rectangle([0, 0, 520, 30], fill=(30, 30, 35))
    ImageDraw.Draw(ot).text((8, 15), "Original", fill=(235, 238, 245), font=font(17), anchor="lm")
    tiles = [ot] + tiles
    cols = 5
    gap = 8
    rowh = max(t.height for t in tiles)
    W = cols * 520 + (cols + 1) * gap
    H = rowh + 2 * gap
    board = Image.new("RGB", (W, H), (15, 18, 22))
    for i, t in enumerate(tiles):
        board.paste(t, (gap + i * (520 + gap), gap))
    board.save(OUT / f"hvi_scores_{job.split('_')[-1]}.png")
    print("wrote", OUT / f"hvi_scores_{job.split('_')[-1]}.png")


if __name__ == "__main__":
    for j in (sys.argv[1:] or ["br_14"]):
        make(j)
