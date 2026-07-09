"""Montage of the six admin-review previews the Admin Publishing Flow shows, for the report."""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
OUT = REPO / "research" / "08_reports" / "admin_publishing"
JOB = REPO / "storage" / "jobs" / "cat_publish_test"
ORDER = [("publish_thumbnail.png", "1. Category Grid Preview"), ("original.png", "2. Original"),
         ("review_border_preview.png", "3. Border-only"), ("game_preview.png", "4. Numbered"),
         ("reveal_partial.png", "5. Reveal / Fill Simulation"), ("reveal_completed.png", "6. Final Completed")]


def font(sz):
    for n in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            continue
    return ImageFont.load_default()


cell_w, bar = 460, 30
tiles = []
for fn, label in ORDER:
    p = JOB / fn
    img = Image.open(p).convert("RGB") if p.exists() else Image.new("RGB", (cell_w, cell_w), (40, 40, 46))
    r = cell_w / img.width
    img = img.resize((cell_w, max(1, int(img.height * r))))
    tile = Image.new("RGB", (cell_w, img.height + bar), (24, 29, 36))
    tile.paste(img, (0, bar))
    d = ImageDraw.Draw(tile)
    d.rectangle([0, 0, cell_w, bar], fill=(30, 30, 35))
    d.text((8, bar // 2), label, fill=(235, 238, 245), font=font(16), anchor="lm")
    tiles.append(tile)
row_h = max(t.height for t in tiles)
cols, rows, gap = 3, 2, 8
W = cols * cell_w + (cols + 1) * gap
H = rows * row_h + (rows + 1) * gap
board = Image.new("RGB", (W, H), (15, 18, 22))
for i, t in enumerate(tiles):
    rr, cc = divmod(i, cols)
    board.paste(t, (gap + cc * (cell_w + gap), gap + rr * (row_h + gap)))
board.save(OUT / "publishing_previews_cat.png")
print("wrote", OUT / "publishing_previews_cat.png")
