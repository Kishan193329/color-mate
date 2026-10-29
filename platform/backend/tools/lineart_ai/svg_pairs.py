"""Training pairs for the colour->line model from the owner's OWN SVG pictures (CLAUDE.md §8.5cm).

Each SVG carries both halves exactly: the filled shapes are the colour picture and the ink shapes
(svg_builder._ink_flags) are the line art — bold outer strokes, thin inner detail, the look the
owner wants. Per SVG three colour variants are rendered (flat / shaded+hue jitter / pencil) so the
model does not just learn flat fills; the line target is the same for all three.

    python svg_pairs.py SVG_DIR OUT_DIR [LONG_SIDE=1100]
Writes OUT_DIR/NNNNN_c.png + NNNNN_l.png (black ink on white).
"""
import glob
import os
import random
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from services import svg_builder as S  # noqa: E402

SS = 2


def render(svg_path, long_side):
    w0, h0, shapes = S.parse(Path(svg_path).read_text(encoding="utf-8", errors="ignore"))
    s = long_side / max(w0, h0)
    W, H = int(round(w0 * s)), int(round(h0 * s))
    _, ink_flags = S._ink_flags(shapes, s, W, H)
    big = np.full((H * SS, W * SS, 3), 255, np.uint8)
    inkbig = np.zeros((H * SS, W * SS), np.uint8)
    for (rgb, op, polys), ink in zip(shapes, ink_flags):
        bgr = (rgb[2], rgb[1], rgb[0])
        if op >= 0.999:
            S._fill(big, polys, bgr, s * SS)
        else:
            m = np.zeros(big.shape[:2], np.uint8)
            S._fill(m, polys, 255, s * SS)
            sel = m > 0
            big[sel] = (big[sel] * (1 - op) + np.array(bgr) * op).astype(np.uint8)
        S._fill(inkbig, polys, 255 if ink else 0, s * SS)
    colour = cv2.resize(big, (W, H), interpolation=cv2.INTER_AREA)
    ink = cv2.resize(inkbig, (W, H), interpolation=cv2.INTER_AREA)
    # the colour picture WITHOUT its ink, so a variant can redraw the line at its own strength
    fills = colour.copy()
    hole = ink > 20
    if hole.any():
        fills = cv2.inpaint(fills, hole.astype(np.uint8) * 255, 3, cv2.INPAINT_TELEA)
    return fills, ink


def shade(fills, rng):
    h, w = fills.shape[:2]
    hsv = cv2.cvtColor(fills, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[..., 0] = (hsv[..., 0] + rng.uniform(-12, 12)) % 180
    hsv[..., 1] = np.clip(hsv[..., 1] * rng.uniform(0.7, 1.3), 0, 255)
    out = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR).astype(np.float32)
    g = np.random.default_rng(rng.randint(0, 10 ** 9)).random((h // 32 + 2, w // 32 + 2)).astype(np.float32)
    g = cv2.resize(g, (w, h), interpolation=cv2.INTER_CUBIC)
    out *= (0.75 + 0.5 * g)[..., None]                      # soft light/shadow blotches
    return np.clip(out, 0, 255).astype(np.uint8)


def pencil(fills, rng):
    h, w = fills.shape[:2]
    out = fills.astype(np.float32)
    n = np.random.default_rng(rng.randint(0, 10 ** 9)).random((h, w)).astype(np.float32)
    n = cv2.blur(n, rng.choice([(1, 7), (7, 1), (5, 5)]))
    out *= (0.82 + 0.36 * n)[..., None]                     # paper grain
    out = 255 - (255 - out) * rng.uniform(0.75, 0.95)       # pencil never reaches full saturation
    return np.clip(out, 0, 255).astype(np.uint8)


def draw_line(img, ink, rng, bold):
    a = ink.astype(np.float32) / 255.0
    if bold:
        a = cv2.dilate(a, np.ones((2, 2), np.uint8))
    a *= rng.uniform(0.65, 1.0)
    tone = np.array(rng.choice([(0, 0, 0), (25, 25, 25), (30, 45, 70), (20, 35, 60)]), np.float32)
    return (img * (1 - a[..., None]) + tone * a[..., None]).astype(np.uint8)


def main():
    src, out = sys.argv[1], sys.argv[2]
    long_side = int(sys.argv[3]) if len(sys.argv) > 3 else 1100
    os.makedirs(out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(src, "*.svg")))
    k = 0
    for i, p in enumerate(files):
        try:
            fills, ink = render(p, long_side)
        except Exception as e:  # noqa: BLE001
            print("skip", os.path.basename(p), e, flush=True)
            continue
        target = np.where(ink > 127, 0, 255).astype(np.uint8)
        rng = random.Random(i)
        for variant in (None, shade, pencil):
            col = variant(fills, rng) if variant else fills
            col = draw_line(col, ink, rng, bold=rng.random() < 0.3)
            if rng.random() < 0.5:
                col = cv2.GaussianBlur(col, (0, 0), rng.uniform(0.4, 0.9))
            cv2.imwrite(f"{out}/{k:05d}_c.png", col)
            cv2.imwrite(f"{out}/{k:05d}_l.png", target)
            k += 1
        print(f"{i+1}/{len(files)} {os.path.basename(p)} {fills.shape[1]}x{fills.shape[0]} ink {100*(ink>127).mean():.1f}%", flush=True)
    print("PAIRS_DONE", k)


if __name__ == "__main__":
    main()
