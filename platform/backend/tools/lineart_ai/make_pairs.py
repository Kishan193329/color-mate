"""Training pairs for the colour->line model, made from our own line-art pages.

Each page (prep_data.py output) is COLOURED by SDXL + canny ControlNet, which keeps the drawing in
place, so the page itself is the exact line art of the painted result. Several render styles and
seeds, so the model learns "painted / coloured-pencil / flat cartoon -> the same clean line".

    python make_pairs.py PAGES_DIR OUT_DIR [N]
Writes OUT_DIR/NNNNN_c.png (colour) + NNNNN_l.png (line), same size.
"""
import glob
import os
import random
import sys

import cv2
import numpy as np
import torch
from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetPipeline
from PIL import Image

STYLES = [
    "coloured pencil illustration, soft pencil texture, warm autumn colours, cosy, detailed",
    "digital painting, soft shading, vibrant colours, glossy highlights, detailed illustration",
    "flat cartoon colouring, clean cel shading, bright colours, children's book illustration",
    "watercolour painting, gentle washes, pastel colours, soft light",
    "fantasy illustration, rich saturated colours, dramatic light, intricate detail",
    # v2: a second pencil style — the owner's pictures are pencil, and grain is what v1 inked
    "coloured pencil drawing, visible pencil strokes, hatching shading, paper grain, hand drawn",
]
NEG = "black and white, line art, sketch, monochrome, grey, text, watermark, blurry, deformed"


def main():
    src, out = sys.argv[1], sys.argv[2]
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 400
    os.makedirs(out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(src, "*.png")))
    random.Random(7).shuffle(files)
    files = files[:n]

    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae, torch_dtype=torch.float16).to("cuda")
    pipe.set_progress_bar_config(disable=True)

    for i, p in enumerate(files):
        g = cv2.imread(p, cv2.IMREAD_GRAYSCALE)
        h, w = g.shape
        s = 1024 / max(h, w)
        W, H = int(round(w * s / 8) * 8), int(round(h * s / 8) * 8)
        g = cv2.resize(g, (W, H), interpolation=cv2.INTER_AREA)
        line = np.where(g < 128, 0, 255).astype(np.uint8)
        edge = 255 - line                                  # white lines on black
        style = STYLES[i % len(STYLES)]
        img = pipe(style, negative_prompt=NEG, image=Image.fromarray(np.stack([edge] * 3, 2)),
                   controlnet_conditioning_scale=1.0, width=W, height=H, num_inference_steps=25,
                   guidance_scale=7.0, generator=torch.Generator("cuda").manual_seed(1000 + i)).images[0]
        col = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
        # the painted picture must carry the line like the owner's pictures do: draw it on top,
        # in a dark brown/black at varying strength so the model sees both faint and bold outlines
        tone = random.Random(i).choice([(0, 0, 0), (20, 20, 40), (10, 30, 60), (30, 30, 30)])
        alpha = random.Random(i + 1).uniform(0.6, 1.0)
        drawn = (line < 128).astype(np.uint8)
        if random.Random(i + 2).random() < 0.3:        # v2: some pictures carry a bolder outline
            drawn = cv2.dilate(drawn, np.ones((2, 2), np.uint8))
        mask = drawn[..., None].astype(np.float32) * alpha
        col = (col * (1 - mask) + np.array(tone, np.float32) * mask).astype(np.uint8)
        cv2.imwrite(f"{out}/{i:05d}_c.png", col)
        cv2.imwrite(f"{out}/{i:05d}_l.png", line)
        if i % 20 == 0:
            print("pair", i, flush=True)
    print("PAIRS_DONE")


if __name__ == "__main__":
    main()
