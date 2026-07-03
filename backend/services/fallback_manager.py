"""Phase 8 — fallback manager.

Attempts the AI helper tasks and guarantees a safe outcome: on ANY failure it
records a 'fallback' status and returns control to the local engine. It also
remembers the most recent source ('ai' | 'fallback' | 'local') so the UI can
show Ready / Offline / Fallback.

It never raises to the pipeline — the worst case is "use local".
"""
from __future__ import annotations

from pathlib import Path

from config import ARTIFACTS
from services import ai_worker_client
from services.runpod_client import RunPodClient

# Most recent AI outcome (process-wide; resets on restart).
_last: dict = {"source": None, "error": None}


def last() -> dict:
    return dict(_last)


def _set(source: str, error: str | None) -> None:
    _last["source"] = source
    _last["error"] = error


def set_local() -> None:
    _set("local", None)


def try_ai(client: RunPodClient, folder: Path) -> dict:
    """Run enhance → lineart → semantics, saving outputs. Returns a plan dict.

    On success: {source:'ai', enhanced, ai_lineart, semantic_map}.
    On failure: {source:'fallback', error}. Never raises.
    """
    src = folder / ARTIFACTS["preprocessed"]
    try:
        client.health()  # fail fast if the worker is unreachable
        enhanced = ai_worker_client.enhance(client, src, folder / ARTIFACTS["enhanced"])
        ai_line = ai_worker_client.lineart(client, enhanced, folder / ARTIFACTS["ai_lineart"])
        ai_worker_client.semantics(
            client, enhanced,
            folder / ARTIFACTS["semantic_map"], folder / ARTIFACTS["confidence"],
        )
        _set("ai", None)
        return {
            "source": "ai",
            "enhanced": str(enhanced),
            "ai_lineart": str(ai_line),
            "semantic_map": str(folder / ARTIFACTS["semantic_map"]),
        }
    except Exception as exc:  # noqa: BLE001 - any failure -> local fallback
        _set("fallback", str(exc))
        return {"source": "fallback", "error": str(exc)}
