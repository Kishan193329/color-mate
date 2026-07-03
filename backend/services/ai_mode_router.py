"""Phase 8 — AI mode router.

Single place that decides local vs RunPod and exposes status/test for the UI.
The effective mode is the UI override (persisted in ai_settings.json) if set,
otherwise the AI_MODE env var. Region detection is ALWAYS local — this only
governs the optional enhance/lineart/semantic helper step.
"""
from __future__ import annotations

import json
import time
from pathlib import Path

from config import (
    AI_MODE,
    AI_SETTINGS_FILE,
    RUNPOD_API_KEY,
    RUNPOD_API_URL,
    RUNPOD_TIMEOUT_SECONDS,
)
from services import fallback_manager
from services.runpod_client import RunPodClient, RunPodError


def get_mode() -> str:
    """Effective mode: UI override file wins over the env default."""
    try:
        if AI_SETTINGS_FILE.exists():
            m = json.loads(AI_SETTINGS_FILE.read_text(encoding="utf-8")).get("mode")
            if m in ("local", "runpod"):
                return m
    except (ValueError, OSError):
        pass
    return AI_MODE


def set_mode(mode: str) -> str:
    if mode not in ("local", "runpod"):
        raise ValueError("mode must be 'local' or 'runpod'")
    AI_SETTINGS_FILE.write_text(json.dumps({"mode": mode}, indent=2), encoding="utf-8")
    return mode


def _client() -> RunPodClient:
    return RunPodClient(RUNPOD_API_URL, RUNPOD_API_KEY, RUNPOD_TIMEOUT_SECONDS)


def maybe_enhance(job_id: str, folder: Path) -> dict:
    """Run the AI helper step if enabled; otherwise (or on failure) return a
    local plan. Region detection happens locally regardless of the result."""
    mode = get_mode()
    if mode != "runpod":
        fallback_manager.set_local()
        return {"source": "local"}
    if not RUNPOD_API_URL:
        fallback_manager._set("fallback", "RUNPOD_API_URL not configured")
        return {"source": "fallback", "error": "RUNPOD_API_URL not configured"}
    return fallback_manager.try_ai(_client(), folder)


def test_connection() -> dict:
    if not RUNPOD_API_URL:
        return {"reachable": False, "detail": "RUNPOD_API_URL not configured"}
    start = time.monotonic()
    try:
        info = _client().health()
        return {
            "reachable": True,
            "latency_ms": int((time.monotonic() - start) * 1000),
            "detail": info,
        }
    except RunPodError as exc:
        return {"reachable": False, "detail": str(exc)}


def status() -> dict:
    mode = get_mode()
    configured = bool(RUNPOD_API_URL)
    if mode != "runpod":
        health = "local"
    elif not configured:
        health = "offline"
    else:
        try:
            _client().health()
            health = "ready"
        except RunPodError:
            health = "offline"
    last = fallback_manager.last()
    return {
        "mode": mode,
        "env_mode": AI_MODE,
        "runpod_url_configured": configured,
        "runpod_url": RUNPOD_API_URL,
        "timeout_seconds": RUNPOD_TIMEOUT_SECONDS,
        "health": health,  # local | ready | offline
        "last_source": last["source"],  # ai | fallback | local | None
        "last_error": last["error"],
    }
