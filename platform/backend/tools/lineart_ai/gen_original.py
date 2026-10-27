"""An ORIGINAL picture for the game, line first, colour second.

1. SDXL + the cmlineart LoRA draws a colouring page from a text description (no source image).
2. The LoRA is unloaded and SDXL + ControlNet colours that exact line art, so the painting sits
   under the lines (the reveal game shows the painting through regions cut from the lines).

    python gen_original.py LORA_DIR OUT_DIR [N]
Writes NN_line.png, NN_colour.png and sheet.jpg (line | colour | colour with lines).
"""
import os
import sys

import cv2
import numpy as np
import torch
from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetPipeline, StableDiffusionXLPipeline
from PIL import Image

W, H = 832, 1216
SUBJECT = ("a cute fantasy girl with huge sparkling eyes and long eyelashes, fox ears, a crown of roses "
           "and daisies, an ornate golden filigree tiara with a blue gem, golden jewellery on her neck "
           "and shoulder, feathered shoulder, wild flowers around her, portrait")
LINE_PROMPT = ("cmlineart, coloring book page, " + SUBJECT + ", pure black and white line art, crisp black "
               "outlines on a plain white background, every area left white, closed shapes, smooth confident "
               "strokes, vector style")
# v1 came out with grey shading in hair and flowers, which the cleanup turned into black blotches
LINE_NEG = ("grey, gray, shading, shadows, gradient, tones, hatching, cross-hatching, stippling, filled black "
            "areas, colour, texture, photo, text, watermark, signature, logo, blurry, sketchy, messy lines")
COLOUR_PROMPT = (SUBJECT + ", vibrant saturated colours, crisp sharp details, glossy highlights, clean cel "
                 "shading, sunset sky purple pink orange, warm glowing light, high quality digital illustration")
COLOUR_NEG = ("washed out, faded, pastel haze, blurry, soft focus, muddy colours, monochrome, black outlines, "
              "line art, sketch, text, watermark, signature, logo, deformed")
# v1 used 1.0 for the whole run: colours followed the lines but came out washed. Following the lines
# only for the first part of the steps leaves the rest free to make the paint crisp.
CN_SCALE = 0.8
CN_END = 0.7


def main():
    lora, out = sys.argv[1], sys.argv[2]
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 6
    os.makedirs(out, exist_ok=True)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", vae=vae, torch_dtype=torch.float16).to("cuda")
    pipe.load_lora_weights(lora)
    lines = []
    for i in range(n):
        img = pipe(LINE_PROMPT, negative_prompt=LINE_NEG, width=W, height=H, num_inference_steps=35,
                   guidance_scale=7.0, generator=torch.Generator("cuda").manual_seed(100 + i)).images[0]
        g = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2GRAY)
        img.save(f"{out}/{i:02d}_line_raw.png")
        lines.append(g)
        print("line", i, flush=True)
    pipe.unload_lora_weights()

    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    cpipe = StableDiffusionXLControlNetPipeline(**pipe.components, controlnet=cn).to("cuda")
    rows = []
    for i, g in enumerate(lines):
        edge = np.where(g < 128, 255, 0).astype(np.uint8)          # white lines on black, like canny
        img = cpipe(COLOUR_PROMPT, negative_prompt=COLOUR_NEG, image=Image.fromarray(np.stack([edge] * 3, 2)),
                    controlnet_conditioning_scale=CN_SCALE, control_guidance_end=CN_END, width=W, height=H,
                    num_inference_steps=35, guidance_scale=7.5, generator=torch.Generator("cuda").manual_seed(200 + i)).images[0]
        img.save(f"{out}/{i:02d}_colour.png")
        col = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
        both = (col * (g[..., None] / 255.0)).astype(np.uint8)
        tw, th = 300, int(300 * H / W)
        rows.append(np.hstack([cv2.resize(x, (tw, th), interpolation=cv2.INTER_AREA) for x in
                               (cv2.cvtColor(g, cv2.COLOR_GRAY2BGR), col, both)]))
        print("colour", i, flush=True)
    cv2.imwrite(f"{out}/sheet.jpg", np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 85])
    print("EVAL_DONE")


if __name__ == "__main__":
    main()
