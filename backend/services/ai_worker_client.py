"""Phase 8 — AI worker client.

High-level wrapper over `runpod_client`: runs the helper tasks for a job and
saves every AI output into the job folder (so nothing is lost and a job can be
inspected/reprocessed later):

    enhanced.png       (AI image cleanup)
    ai_lineart.png     (AI line-art — fed into the LOCAL region pipeline)
    semantic_map.json  (AI semantic helper data)
    confidence.json    (AI confidence scores)
"""
from __future__ import annotations

import json
from pathlib import Path

from services.runpod_client import RunPodClient, decode_image, encode_image


def enhance(client: RunPodClient, src_path: Path, out_path: Path) -> Path:
    enhanced_b64 = client.enhance_image(encode_image(src_path))
    out_path.write_bytes(decode_image(enhanced_b64))
    return out_path


def lineart(client: RunPodClient, src_path: Path, out_path: Path) -> Path:
    lineart_b64 = client.generate_lineart(encode_image(src_path))
    out_path.write_bytes(decode_image(lineart_b64))
    return out_path


def semantics(client: RunPodClient, src_path: Path, map_path: Path, conf_path: Path) -> dict:
    result = client.detect_semantics(encode_image(src_path))
    map_path.write_text(json.dumps(result.get("semantic_map", {}), indent=2), encoding="utf-8")
    conf_path.write_text(json.dumps(result.get("confidence", {}), indent=2), encoding="utf-8")
    return result
