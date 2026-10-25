"""Before/after for the trained LoRA: base SDXL vs base + cmlineart LoRA, same ControlNet input.

    python eval_lora.py IN_DIR LORA_DIR OUT_DIR
Writes OUT_DIR/eval_sheet.jpg: original | base | trained (both after the hollow post-process).
"""
import glob
import os
import sys

import cv2
import numpy as np
import torch
from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetPipeline

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from proto_sdxl import NEGATIVE, PROMPT, control_image  # noqa: E402

TRIGGER = "cmlineart, "


def hollow_arr(rgb, keep=3.0, speck=40):
    g = cv2.cvtColor(np.array(rgb), cv2.COLOR_RGB2GRAY)
    _, ink = cv2.threshold(g, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    d = cv2.distanceTransform(ink, cv2.DIST_L2, 5)
    ink[d > keep] = 0
    n, lab, st, _ = cv2.connectedComponentsWithStats(ink, 8)
    for i in range(1, n):
        if st[i, 4] < speck:
            ink[lab == i] = 0
    return 255 - ink


def main():
    src, lora, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae, torch_dtype=torch.float16).to("cuda")
    files = sorted(glob.glob(os.path.join(src, "*.jpg")))

    def run(prompt, tag):
        res = {}
        for p in files:
            ctrl, (W, H) = control_image(p)
            img = pipe(prompt, negative_prompt=NEGATIVE, image=ctrl, controlnet_conditioning_scale=0.6,
                       num_inference_steps=30, guidance_scale=7.0, width=W, height=H,
                       generator=torch.Generator("cuda").manual_seed(7)).images[0]
            name = os.path.splitext(os.path.basename(p))[0]
            img.save(f"{out}/{name}_{tag}.png")
            res[p] = hollow_arr(img)
            print(tag, name, flush=True)
        return res

    base = run(PROMPT, "base")
    pipe.load_lora_weights(lora)
    trained = run(TRIGGER + PROMPT, "lora")
    rows = []
    for p in files:
        src_img = cv2.imread(p)
        H, W = base[p].shape
        w = 340
        h = int(w * H / W)
        rows.append(np.hstack([cv2.resize(src_img, (w, h)),
                               cv2.cvtColor(cv2.resize(base[p], (w, h), interpolation=cv2.INTER_AREA), cv2.COLOR_GRAY2BGR),
                               cv2.cvtColor(cv2.resize(trained[p], (w, h), interpolation=cv2.INTER_AREA), cv2.COLOR_GRAY2BGR)]))
    mw = max(r.shape[1] for r in rows)
    sheet = np.vstack([np.pad(r, ((0, 6), (0, mw - r.shape[1]), (0, 0)), constant_values=255) for r in rows])
    cv2.imwrite(f"{out}/eval_sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])
    print("EVAL_DONE")


if __name__ == "__main__":
    main()
