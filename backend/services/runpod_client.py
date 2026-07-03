"""Phase 8 — low-level RunPod worker HTTP client.

Talks to a RunPod (or any HTTP) worker exposing:
    GET  /health
    POST /enhance-image     {image} -> {enhanced}
    POST /generate-lineart  {image} -> {lineart}
    POST /detect-semantics  {image} -> {semantic_map, confidence}

Images are base64-encoded PNG bytes in JSON. Uses only the standard library so
the backend gains no new runtime dependency. Raises on transport / HTTP errors;
callers (fallback_manager) decide what to do.
"""
from __future__ import annotations

import base64
import json
import urllib.error
import urllib.request


class RunPodError(RuntimeError):
    pass


class RunPodClient:
    def __init__(self, base_url: str, api_key: str = "", timeout: int = 600):
        if not base_url:
            raise RunPodError("RUNPOD_API_URL is not configured")
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout

    # -- transport -----------------------------------------------------------
    def _headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    def _request(self, method: str, path: str, payload: dict | None, timeout: int | None = None):
        url = f"{self.base_url}{path}"
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(url, data=data, headers=self._headers(), method=method)
        try:
            with urllib.request.urlopen(req, timeout=timeout or self.timeout) as resp:
                body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
        except urllib.error.URLError as exc:
            raise RunPodError(f"RunPod request failed: {exc}") from exc
        except json.JSONDecodeError as exc:
            raise RunPodError(f"RunPod returned invalid JSON: {exc}") from exc

    # -- endpoints -----------------------------------------------------------
    def health(self, timeout: int = 10) -> dict:
        return self._request("GET", "/health", None, timeout=timeout)

    def enhance_image(self, image_b64: str) -> str:
        out = self._request("POST", "/enhance-image", {"image": image_b64})
        if "enhanced" not in out:
            raise RunPodError("enhance-image response missing 'enhanced'")
        return out["enhanced"]

    def generate_lineart(self, image_b64: str) -> str:
        out = self._request("POST", "/generate-lineart", {"image": image_b64})
        if "lineart" not in out:
            raise RunPodError("generate-lineart response missing 'lineart'")
        return out["lineart"]

    def detect_semantics(self, image_b64: str) -> dict:
        out = self._request("POST", "/detect-semantics", {"image": image_b64})
        return {
            "semantic_map": out.get("semantic_map", {}),
            "confidence": out.get("confidence", {}),
        }


def encode_image(path) -> str:
    from pathlib import Path

    return base64.b64encode(Path(path).read_bytes()).decode("ascii")


def decode_image(b64: str) -> bytes:
    return base64.b64decode(b64)
