"""Prototype: picture -> clean colouring-page line art (SDXL + ControlNet canny).

    python gen.py IN_DIR OUT_DIR
Writes, per input: NN_s{scale}.png (raw) and NN_s{scale}_bw.png (thresholded), plus sheet.jpg.
"""
import glob
import os
import sys
import time

import cv2
import numpy as np
import torch
from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetPipeline
from PIL import Image

PROMPT = ("coloring book page, clean bold black outlines on a pure white background, simple line art, "
          "closed shapes, smooth confident strokes, no shading, no colour, no texture, children's colouring book")
NEGATIVE = ("color, colour, grey shading, gradient, hatching, cross-hatching, texture, noise, photo, realistic, "
            "text, watermark, signature, blurry, sketchy, messy lines, dots")
SCALES = (0.6, 0.85)


def control_image(path):
    im = cv2.imread(path)
    h, w = im.shape[:2]
    s = 1024 / max(h, w)
    W, H = int(round(w * s / 8) * 8), int(round(h * s / 8) * 8)
    im = cv2.resize(im, (W, H), interpolation=cv2.INTER_AREA)
    smooth = cv2.bilateralFilter(im, 9, 60, 60)
    edges = cv2.Canny(cv2.cvtColor(smooth, cv2.COLOR_BGR2GRAY), 70, 170)
    return Image.fromarray(np.stack([edges] * 3, axis=2)), (W, H)


def clean(img):
    g = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2GRAY)
    _, bw = cv2.threshold(g, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return bw


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae,
        torch_dtype=torch.float16, variant="fp16").to("cuda")
    tiles = []
    for p in sorted(glob.glob(os.path.join(src, "*.jpg"))):
        name = os.path.splitext(os.path.basename(p))[0]
        ctrl, (W, H) = control_image(p)
        row = [cv2.resize(cv2.imread(p), (W, H))]
        for sc in SCALES:
            t = time.time()
            img = pipe(PROMPT, negative_prompt=NEGATIVE, image=ctrl, controlnet_conditioning_scale=sc,
                       num_inference_steps=30, guidance_scale=7.0, width=W, height=H,
                       generator=torch.Generator("cuda").manual_seed(7)).images[0]
            img.save(f"{out}/{name}_s{sc}.png")
            bw = clean(img)
            cv2.imwrite(f"{out}/{name}_s{sc}_bw.png", bw)
            row.append(cv2.cvtColor(bw, cv2.COLOR_GRAY2BGR))
            print(name, sc, f"{time.time() - t:.1f}s", flush=True)
        r = np.hstack([cv2.resize(x, (360, int(360 * H / W))) for x in row])
        tiles.append(r)
    w = max(t.shape[1] for t in tiles)
    sheet = np.vstack([np.pad(t, ((0, 8), (0, w - t.shape[1]), (0, 0)), constant_values=255) for t in tiles])
    cv2.imwrite(f"{out}/sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])
    print("DONE")


if __name__ == "__main__":
    main()


def hollow(path, keep=3.0, speck=40):
    """Post-process: SDXL paints some areas solid black; keep only a `keep`-px rim of every ink
    area so the result is outlines, and drop specks. Big improvement on the 2026-10-25 prototype."""
    g = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    _, ink = cv2.threshold(g, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    d = cv2.distanceTransform(ink, cv2.DIST_L2, 5)
    ink[d > keep] = 0
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    for i in range(1, n):
        if st[i, 4] < speck:
            ink[lab == i] = 0
    return 255 - ink
