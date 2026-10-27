#!/usr/bin/env bash
# GPU job: build colour->line training pairs from our own line-art pages and train the c2l U-Net.
# Runs on the pod in /workspace with tools/lineart_ai/*.py beside it. Inputs via env:
#   PAGES_URL  presigned URL of a .tgz of line-art pages (prep_data.py output), staged by the workflow
#   PAIRS      how many pairs to render (default 450)      STEPS  training steps (default 6000)
# Output: /workspace/out/c2l.pt, sample_*.jpg, test_out/* (test pictures in /workspace/test if any).
set -euo pipefail
cd /workspace
export HF_HUB_ENABLE_HF_TRANSFER=1 HF_HOME=/workspace/hf
pip install -q "diffusers==0.31.0" "transformers==4.46.3" "accelerate==1.1.1" safetensors opencv-python-headless hf_transfer > pip.log 2>&1
mkdir -p data out
curl -sL -o pages.tgz "$PAGES_URL" && tar xzf pages.tgz -C data
echo "pages: $(ls data/*.png | wc -l)"
python make_pairs.py data pairs "${PAIRS:-450}"
python train_c2l.py pairs out "${STEPS:-6000}"
if ls test/* >/dev/null 2>&1; then python infer_c2l.py out/c2l.pt out/test_out test/*; fi
echo JOB_DONE
