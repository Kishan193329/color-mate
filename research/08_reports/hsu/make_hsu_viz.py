"""Scene-map overlay: paint each region by its HSU scene role, beside the original + a legend."""
from __future__ import annotations
import json, sys
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
OUT = REPO / "research" / "08_reports" / "hsu"
OUT.mkdir(parents=True, exist_ok=True)
# BGR
ROLE_COLOR = [("main_subject_regions", (60, 60, 235), "main subject"),
              ("secondary_subject_regions", (60, 200, 255), "secondary subject"),
              ("foreground_regions", (80, 200, 80), "foreground"),
              ("background_regions", (225, 150, 60), "background"),
              ("low_importance_regions", (150, 150, 150), "low importance")]


def font(sz):
    for n in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            continue
    return ImageFont.load_default()


def scene_map(job, cell_w=560):
    f = REPO / "storage" / "jobs" / job
    labels = np.load(f / "labels.npy").astype(np.int32)
    rep = json.loads((f / "human_scene_report.json").read_text())
    scene = rep["scene"]
    role_of = {}
    for field, _, _ in ROLE_COLOR:                      # priority = list order
        for lbl in scene[field]:
            role_of.setdefault(lbl, field)
    focal = set(scene["focal_regions"])
    n = int(labels.max()) + 1
    lut = np.full((n, 3), 235, np.uint8)                # neutral light grey
    cmap = {field: col for field, col, _ in ROLE_COLOR}
    for lbl, field in role_of.items():
        if 0 < lbl < n:
            lut[lbl] = cmap[field]
    canvas = lut[labels]
    canvas[labels == 0] = (40, 40, 40)
    # outline focal regions in bright red
    fo = np.isin(labels, np.array(sorted(focal) or [-1], dtype=labels.dtype))
    edge = cv2.dilate(fo.astype(np.uint8), np.ones((3, 3), np.uint8)) - fo.astype(np.uint8)
    canvas[edge.astype(bool)] = (0, 0, 255)

    orig = cv2.imread(str(f / "original.png"))
    orig = cv2.resize(orig, (labels.shape[1], labels.shape[0]))
    pair = cv2.cvtColor(np.hstack([orig, np.full((labels.shape[0], 8, 3), 255, np.uint8), canvas]),
                        cv2.COLOR_BGR2RGB)
    img = Image.fromarray(pair)
    r = (cell_w * 2 + 8) / img.width
    img = img.resize((int(img.width * r), int(img.height * r)))
    bar = 34
    legend = 30
    W, Hh = img.width, img.height + bar + legend
    out = Image.new("RGB", (W, Hh), (24, 29, 36))
    out.paste(img, (0, bar))
    d = ImageDraw.Draw(out)
    d.rectangle([0, 0, W, bar], fill=(30, 30, 35))
    d.text((10, bar // 2), f"{job}:  scene_type={scene['scene_type']}  -> {scene['recommended_scene_strategy']}"
           f"   (sep {scene['subject_background_separation_score']}, comp {scene['composition_score']}, "
           f"depth {scene['depth_layers']})", fill=(235, 238, 245), font=font(15), anchor="lm")
    x = 10
    for _, col, name in ROLE_COLOR + [("focal", (0, 0, 255), "focal outline")]:
        d.rectangle([x, Hh - legend + 8, x + 16, Hh - legend + 22], fill=(col[2], col[1], col[0]))
        d.text((x + 20, Hh - legend + 15), name, fill=(210, 214, 222), font=font(13), anchor="lm")
        x += 20 + int(len(name) * 7.5) + 18
    out.save(OUT / f"scene_map_{job.split('_')[-1]}.png")
    print("wrote", OUT / f"scene_map_{job.split('_')[-1]}.png", "|", scene["scene_type"], scene["recommended_scene_strategy"])


if __name__ == "__main__":
    for j in (sys.argv[1:] or ["br_14"]):
        scene_map(j)
