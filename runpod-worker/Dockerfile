# ColorForge AI — RunPod worker image.
# Swap the base for a CUDA image (e.g. nvidia/cuda:12.4.1-runtime-ubuntu22.04)
# and add your model weights when you move from the reference OpenCV tasks to
# real GPU models.
FROM python:3.11-slim

# OpenCV headless still needs a couple of shared libs.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .

EXPOSE 8100
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8100"]
