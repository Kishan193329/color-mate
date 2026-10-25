"""Line first, colour second": a picture is REDRAWN as a colouring page (Qwen-Image-Edit), then the
line art is coloured from the original (SDXL ControlNet img2img), so the reveal image sits exactly
under the lines.

    python redraw_test.py IN_DIR OUT_DIR
Writes per input NN_line.png (clean black/white), NN_colour.png, and OUT_DIR/redraw_sheet.jpg:
original | line art | colour | colour with the lines on top.
"""
import gc
import glob
import os
import sys

import cv2
import numpy as np
import torch
from PIL import Image

LINE_PROMPT = ("Redraw this picture as a clean colouring-book page: crisp black outlines on a pure white "
               "background. Bold thick outer contours, thinner lines for inner details, every shape closed. "
               "Tiny details like eyelashes and pupils filled solid black. No colour, no grey, no shading, "
               "no hatching, no texture. Keep the same composition and subject.")
COLOUR_PROMPT = ("beautiful painted illustration, vibrant colours, soft shading, clean colour inside every "
                 "outline, high quality digital art")
COLOUR_NEGATIVE = "black outlines, line art, sketch, monochrome, blurry, noise, text, watermark"


def clean_line(img, speck=40):
    g = cv2.cvtColor(np.array(img.convert("RGB")), cv2.COLOR_RGB2GRAY)
    ink = (g < 128).astype(np.uint8) * 255
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    keep = np.zeros(n, bool)
    keep[1:] = st[1:, cv2.CC_STAT_AREA] >= speck
    return 255 - np.where(keep[lab], 255, 0).astype(np.uint8)


def redraw(files, out):
    from diffusers import QwenImageEditPipeline
    pipe = QwenImageEditPipeline.from_pretrained("Qwen/Qwen-Image-Edit", torch_dtype=torch.bfloat16).to("cuda")
    for p in files:
        name = os.path.splitext(os.path.basename(p))[0]
        img = pipe(image=Image.open(p).convert("RGB"), prompt=LINE_PROMPT, negative_prompt=" ",
                   true_cfg_scale=4.0, num_inference_steps=40,
                   generator=torch.Generator("cuda").manual_seed(7)).images[0]
        img.save(f"{out}/{name}_line_raw.png")
        cv2.imwrite(f"{out}/{name}_line.png", clean_line(img))
        print("line", name, img.size, flush=True)
    del pipe
    gc.collect()
    torch.cuda.empty_cache()


def colour(files, out):
    from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetImg2ImgPipeline
    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLControlNetImg2ImgPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae, torch_dtype=torch.float16).to("cuda")
    rows = []
    for p in files:
        name = os.path.splitext(os.path.basename(p))[0]
        line = cv2.imread(f"{out}/{name}_line.png", cv2.IMREAD_GRAYSCALE)
        h, w = line.shape
        W, H = int(round(w / 8) * 8), int(round(h / 8) * 8)
        line = cv2.resize(line, (W, H), interpolation=cv2.INTER_NEAREST)
        src = cv2.resize(cv2.imread(p), (W, H), interpolation=cv2.INTER_AREA)
        ctrl = Image.fromarray(np.stack([255 - line] * 3, axis=2))   # white lines on black, like a canny map
        img = pipe(COLOUR_PROMPT, negative_prompt=COLOUR_NEGATIVE,
                   image=Image.fromarray(cv2.cvtColor(src, cv2.COLOR_BGR2RGB)), control_image=ctrl,
                   strength=0.7, controlnet_conditioning_scale=0.9, num_inference_steps=30, guidance_scale=6.0,
                   width=W, height=H, generator=torch.Generator("cuda").manual_seed(7)).images[0]
        img.save(f"{out}/{name}_colour.png")
        col = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
        both = (col.astype(np.float32) * (line[..., None] / 255.0)).astype(np.uint8)
        tw = 300
        th = int(tw * H / W)
        rows.append(np.hstack([cv2.resize(x, (tw, th), interpolation=cv2.INTER_AREA) for x in
                               (src, cv2.cvtColor(line, cv2.COLOR_GRAY2BGR), col, both)]))
        print("colour", name, flush=True)
    mw = max(r.shape[1] for r in rows)
    sheet = np.vstack([np.pad(r, ((0, 6), (0, mw - r.shape[1]), (0, 0)), constant_values=255) for r in rows])
    cv2.imwrite(f"{out}/redraw_sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(src, "[0-9][0-9].jpg")))
    redraw(files, out)
    colour(files, out)
    print("EVAL_DONE")


if __name__ == "__main__":
    main()
