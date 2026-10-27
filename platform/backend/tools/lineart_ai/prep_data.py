"""Training set for the line-art LoRA: the owner's own By Number line art (the old catalogue now
hosted on our server, legacy.py), composited on white, with captions.

    python prep_data.py OUT_DIR [per_category=160]

Writes OUT_DIR/*.png + metadata.jsonl ({"file_name", "text"}) — the imagefolder layout the
diffusers SDXL LoRA script reads.
"""
import concurrent.futures as cf
import json
import os
import random
import sys
import urllib.parse
import urllib.request

import cv2
import numpy as np

BASE = "https://srv1919471.hstgr.cloud/ColoringBook/"
TRIGGER = "cmlineart"
MAX_SIDE = 1024


def get(url, tries=3):
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except Exception:
            if i == tries - 1:
                raise


def on_white(data):
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_UNCHANGED)
    if img is None:
        return None
    if img.ndim == 3 and img.shape[2] == 4:
        a = img[:, :, 3:4].astype(np.float32) / 255.0
        img = (img[:, :, :3].astype(np.float32) * a + 255.0 * (1 - a)).astype(np.uint8)
    elif img.ndim == 2:
        img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    h, w = img.shape[:2]
    s = MAX_SIDE / max(h, w)
    if s < 1:
        img = cv2.resize(img, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
    # A page that is nearly all white or all ink teaches nothing.
    ink = (cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) < 128).mean()
    return img if 0.01 < ink < 0.45 else None


def main():
    out = sys.argv[1]
    per = int(sys.argv[2]) if len(sys.argv) > 2 else 160
    os.makedirs(out, exist_ok=True)
    cats = json.loads(get(BASE + "index.php/Number/category/"))["categories"]
    rng = random.Random(7)
    jobs = []
    for c in cats:
        rows = json.loads(get(BASE + "index.php/Number/gimg/c/" + urllib.parse.quote(c)))["data"]
        rng.shuffle(rows)
        jobs += [(c, r["fileName"]) for r in rows[:per]]
    print("pictures:", len(jobs), flush=True)

    def one(job):
        c, f = job
        try:
            img = on_white(get(f"{BASE}thumb/{urllib.parse.quote(c)}/{urllib.parse.quote(f)}.png"))
        except Exception:
            return None
        if img is None:
            return None
        name = f"{c}_{f}.png".replace(" ", "_")
        cv2.imwrite(os.path.join(out, name), img)
        subject = {"mandalas": "a mandala", "other": "a detailed scene", "daily": "a detailed scene",
                   "kids": "a simple cartoon for kids", "love": "a romantic scene"}.get(c, c)
        return {"file_name": name,
                "text": f"{TRIGGER}, coloring book page of {subject}, clean black outlines on white background, "
                        f"closed shapes, no shading, no colour"}

    with cf.ThreadPoolExecutor(16) as pool:
        rows = [r for r in pool.map(one, jobs) if r]
    with open(os.path.join(out, "metadata.jsonl"), "w") as fh:
        for r in rows:
            fh.write(json.dumps(r) + "\n")
    print("kept:", len(rows))


if __name__ == "__main__":
    main()
