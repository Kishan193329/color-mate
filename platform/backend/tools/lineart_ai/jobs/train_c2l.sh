#!/usr/bin/env bash
# GPU job: build colour->line training pairs from our own line-art pages and train the c2l U-Net.
# Runs on the pod in /workspace with tools/lineart_ai/*.py beside it. Inputs via env:
#   PAGES_URL  presigned URL of a .tgz of line-art pages (prep_data.py output), staged by the workflow
#   REAL_URL   optional: .tgz of the owner's real pairs (NNNNN_c.png + NNNNN_l.png), oversampled x12
#   INIT_URL   optional: an earlier c2l.pt to fine-tune from (lr 1.5e-4 instead of 3e-4)
#   PAIRS      how many pairs to render (default 900)      STEPS  training steps (default 12000)
#   DISTRACT_P share of crops that get texture distractors (default 0.7)   LR  override
# Output: /workspace/out/c2l.pt, sample_*.jpg, test_out/* (test pictures in /workspace/test if any).
set -euo pipefail
cd /workspace
export HF_HUB_ENABLE_HF_TRANSFER=1 HF_HOME=/workspace/hf
pip install -q "diffusers==0.31.0" "transformers==4.46.3" "accelerate==1.1.1" safetensors opencv-python-headless hf_transfer > pip.log 2>&1
mkdir -p data out
curl -sL -o pages.tgz "$PAGES_URL" && tar xzf pages.tgz -C data
echo "pages: $(ls data/*.png | wc -l)"
if [ -n "${REAL_URL:-}" ]; then mkdir -p real && curl -sL -o real.tgz "$REAL_URL" && tar xzf real.tgz -C real && export REAL_DIR=/workspace/real; echo "real pairs: $(ls real/*_l.png 2>/dev/null | wc -l)"; fi
if [ -n "${INIT_URL:-}" ]; then curl -sL -o init.pt "$INIT_URL" && export INIT=/workspace/init.pt; fi
python make_pairs.py data pairs "${PAIRS:-900}"
python train_c2l.py pairs out "${STEPS:-12000}"
if ls test/* >/dev/null 2>&1; then python infer_c2l.py out/c2l.pt out/test_out test/*; fi
echo JOB_DONE
