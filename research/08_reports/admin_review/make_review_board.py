"""Compose the eight review previews of one job into a labelled 4x2 board for the report."""
from __future__ import annotations
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
OUT = REPO / "research" / "08_reports" / "admin_review"
OUT.mkdir(parents=True, exist_ok=True)
ORDER = [("original", "1. Original"), ("border_preview", "2. Border Preview"),
         ("number_preview", "3. Number Preview"), ("artistic_border_preview", "4. Artistic Border"),
         ("reveal_preview", "5. Reveal"), ("region_heatmap", "6. Region Heatmap"),
         ("tiny_region_overlay", "7. Tiny Region Overlay"), ("border_density_map", "8. Border Density")]


def font(sz):
    for n in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            continue
    return ImageFont.load_default()


def board(job: str):
    folder = REPO / "storage" / "jobs" / job
    rev = json.loads((folder / "review.json").read_text())
    assets = rev["assets"]
    cell_w, bar = 460, 30
    tiles = []
    for key, label in ORDER:
        fn = assets.get(key)
        img = Image.open(folder / fn).convert("RGB") if fn and (folder / fn).exists() \
            else Image.new("RGB", (cell_w, cell_w), (40, 40, 46))
        r = cell_w / img.width
        img = img.resize((cell_w, max(1, int(img.height * r))))
        cell_h = img.height + bar
        tile = Image.new("RGB", (cell_w, cell_h), (24, 29, 36))
        tile.paste(img, (0, bar))
        d = ImageDraw.Draw(tile)
        d.rectangle([0, 0, cell_w, bar], fill=(30, 30, 35))
        d.text((8, bar // 2), label, fill=(235, 238, 245), font=font(16), anchor="lm")
        tiles.append(tile)
    row_h = max(t.height for t in tiles)
    cols, rows = 4, 2
    gap = 8
    W = cols * cell_w + (cols + 1) * gap
    H = rows * row_h + (rows + 1) * gap
    board = Image.new("RGB", (W, H), (15, 18, 22))
    for i, t in enumerate(tiles):
        r, c = divmod(i, cols)
        board.paste(t, (gap + c * (cell_w + gap), gap + r * (row_h + gap)))
    out = OUT / f"review_board_{job.split('_')[-1]}.png"
    board.save(out)
    s = rev["statistics"]
    print(f"{job}: {out.name}  regions={s['region_count']} palette={s['palette_count']} "
          f"complexity={s['complexity_score']} play={s['estimated_play_time_min']}m")


if __name__ == "__main__":
    for job in (sys.argv[1:] or ["br_14"]):
        board(job)
