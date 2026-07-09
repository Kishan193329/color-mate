"""Static montage mimicking the admin gallery cards (5 previews + stats) for the report."""
from __future__ import annotations
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa
OUT = REPO / "research" / "08_reports" / "local_admin_27"
OUT.mkdir(parents=True, exist_ok=True)
PREV = [("original.png", "Original"), ("review_border_preview.png", "Border"),
        ("game_preview.png", "Numbered"), ("reveal_partial.png", "Reveal"),
        ("reveal_completed.png", "Completed")]
VCOL = {"PASS": (46, 204, 113), "REVIEW": (245, 166, 35), "FAIL": (255, 93, 93)}


def font(sz):
    for n in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            continue
    return ImageFont.load_default()


def card(item, cell=300):
    f = REPO / "storage" / "jobs" / item["job_id"]
    bar, meta = 30, 30
    tiles = []
    for fn, lbl in PREV:
        p = f / fn
        img = Image.open(p).convert("RGB") if p.exists() else Image.new("RGB", (cell, cell), (40, 40, 46))
        img = img.resize((cell, cell))
        d = ImageDraw.Draw(img)
        d.rectangle([0, cell - 18, cell, cell], fill=(0, 0, 0))
        d.text((6, cell - 9), lbl, fill=(223, 227, 234), font=font(13), anchor="lm")
        tiles.append(img)
    W = cell * 5
    c = Image.new("RGB", (W, cell + bar + meta), (24, 29, 36))
    for i, t in enumerate(tiles):
        c.paste(t, (i * cell, bar))
    d = ImageDraw.Draw(c)
    d.rectangle([0, 0, W, bar], fill=(30, 30, 35))
    d.text((8, bar // 2), item["job_id"], fill=(235, 238, 245), font=font(16), anchor="lm")
    vc = VCOL.get(item["verdict"], (150, 150, 150))
    d.rectangle([90, 5, 90 + 78, bar - 5], fill=vc)
    d.text((90 + 39, bar // 2), item["verdict"], fill=(15, 18, 22), font=font(13), anchor="mm")
    d.text((185, bar // 2), f"status: {item['review_status']}", fill=(154, 164, 178), font=font(13), anchor="lm")
    meta_txt = (f"type {item['image_type']}   profile {item['profile']}   "
                f"{item['region_count']} regions   {item['palette_count']} colours   "
                f"{item['play_time_min']} min   reveal ΔE {item['reveal_deltaE']}")
    d.text((8, cell + bar + meta // 2), meta_txt, fill=(200, 205, 214), font=font(15), anchor="lm")
    return c


jobs = sys.argv[1:] or ["br_14", "br_16", "br_22"]
data = {i["job_id"]: i for i in main.admin_gallery()["items"]}
cards = [card(data[j]) for j in jobs if j in data]
gap = 10
W = max(c.width for c in cards)
H = sum(c.height for c in cards) + gap * (len(cards) + 1)
board = Image.new("RGB", (W, H), (15, 18, 22))
y = gap
for c in cards:
    board.paste(c, (0, y))
    y += c.height + gap
board.save(OUT / "gallery_cards.png")
print("wrote", OUT / "gallery_cards.png", "| jobs:", jobs)
