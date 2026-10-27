#!/usr/bin/env bash
# GPU job: original pictures from text (gen_original.py) — line art by the cmlineart LoRA, then colour.
#   LORA_URL  presigned URL of cmlineart_v1.safetensors (staged from the VPS)   N  how many (default 6)
set -euo pipefail
cd /workspace
export HF_HUB_ENABLE_HF_TRANSFER=1 HF_HOME=/workspace/hf
pip install -q "diffusers==0.31.0" "transformers==4.46.3" "accelerate==1.1.1" "peft==0.13.2" safetensors opencv-python-headless hf_transfer > pip.log 2>&1
mkdir -p lora out
curl -sL -o lora/pytorch_lora_weights.safetensors "$LORA_URL"
python gen_original.py lora out "${N:-6}"
echo JOB_DONE
