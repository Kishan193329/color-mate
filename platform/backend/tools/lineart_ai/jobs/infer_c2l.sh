#!/usr/bin/env bash
# GPU job: run a trained colour->line model over the pictures in /workspace/test.
#   MODEL_URL  presigned URL of c2l.pt (staged from the VPS by the workflow)
set -euo pipefail
cd /workspace
pip install -q opencv-python-headless > pip.log 2>&1
mkdir -p out model
curl -sL -o model/c2l.pt "$MODEL_URL"
python infer_c2l.py model/c2l.pt out/test_out test/*
echo JOB_DONE
