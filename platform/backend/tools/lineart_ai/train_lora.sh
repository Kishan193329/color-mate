#!/usr/bin/env bash
# Trains the "cmlineart" SDXL LoRA on a RunPod GPU (see prep_data.py for the data).
#   bash train_lora.sh            -> /workspace/lora/pytorch_lora_weights.safetensors
set -euo pipefail
cd /workspace
pip install -q "diffusers==0.31.0" "transformers==4.46.3" "accelerate==1.1.1" "peft==0.13.2" \
    datasets safetensors opencv-python-headless
[ -f train_sdxl_lora.py ] || curl -sL -o train_sdxl_lora.py \
  https://raw.githubusercontent.com/huggingface/diffusers/v0.31.0/examples/text_to_image/train_text_to_image_lora_sdxl.py

[ -f /workspace/data/metadata.jsonl ] || python prep_data.py /workspace/data 160

accelerate launch --mixed_precision fp16 train_sdxl_lora.py \
  --pretrained_model_name_or_path stabilityai/stable-diffusion-xl-base-1.0 \
  --pretrained_vae_model_name_or_path madebyollin/sdxl-vae-fp16-fix \
  --train_data_dir /workspace/data --caption_column text \
  --resolution 1024 --center_crop --random_flip \
  --train_batch_size 1 --gradient_accumulation_steps 4 --gradient_checkpointing \
  --max_train_steps 1200 --checkpointing_steps 400 \
  --learning_rate 1e-4 --lr_scheduler constant --lr_warmup_steps 0 \
  --rank 16 --mixed_precision fp16 --seed 7 \
  --output_dir /workspace/lora
echo TRAIN_DONE
