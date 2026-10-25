"""Which control map makes the cleanest page? Base SDXL + cmlineart LoRA, one ControlNet, three
control variants (control.py): A_raw canny, C_flatter canny, D_sketch (server line-art model).

    python eval_controls.py IN_DIR LORA_DIR OUT_DIR
Writes OUT_DIR/controls_sheet.jpg: original | A | C | D (after the hollow post-process).
"""
import glob
import os
import sys

import cv2
import numpy as np
import torch
from diffusers import AutoencoderKL, ControlNetModel, StableDiffusionXLControlNetPipeline
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from control import control  # noqa: E402
from eval_lora import TRIGGER, hollow_arr  # noqa: E402
from proto_sdxl import NEGATIVE, PROMPT  # noqa: E402

VARIANTS = ("A_raw", "C_flatter", "D_sketch")


def main():
    src, lora, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    cn = ControlNetModel.from_pretrained("xinsir/controlnet-canny-sdxl-1.0", torch_dtype=torch.float16)
    vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
    pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae, torch_dtype=torch.float16).to("cuda")
    pipe.load_lora_weights(lora)
    rows = []
    for p in sorted(glob.glob(os.path.join(src, "[0-9][0-9].jpg"))):
        name = os.path.splitext(os.path.basename(p))[0]
        cols = []
        for v in VARIANTS:
            ctrl, (W, H) = control(p, v)
            img = pipe(TRIGGER + PROMPT, negative_prompt=NEGATIVE, image=Image.fromarray(ctrl),
                       controlnet_conditioning_scale=0.6, num_inference_steps=30, guidance_scale=7.0,
                       width=W, height=H, generator=torch.Generator("cuda").manual_seed(7)).images[0]
            img.save(f"{out}/{name}_{v}.png")
            cols.append(hollow_arr(img))
            print(name, v, flush=True)
        w = 300
        h = int(w * H / W)
        rows.append(np.hstack([cv2.resize(cv2.imread(p), (w, h))] +
                              [cv2.cvtColor(cv2.resize(c, (w, h), interpolation=cv2.INTER_AREA), cv2.COLOR_GRAY2BGR)
                               for c in cols]))
    mw = max(r.shape[1] for r in rows)
    sheet = np.vstack([np.pad(r, ((0, 6), (0, mw - r.shape[1]), (0, 0)), constant_values=255) for r in rows])
    cv2.imwrite(f"{out}/controls_sheet.jpg", sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])
    print("EVAL_DONE")


if __name__ == "__main__":
    main()
