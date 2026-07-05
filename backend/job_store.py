"""File-system backed job store.

Each job lives in `storage/jobs/<job_id>/` and its state is persisted to
`job.json` in that folder. This keeps Sprint 1 dependency-free (no database)
while leaving an obvious seam to swap in Redis/Postgres later.
"""
from __future__ import annotations

import json
import shutil
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from config import ARTIFACTS, JOBS_DIR, PROCESSING_STEPS

# Per-process lock. Job writes are small and infrequent for Sprint 1, so a
# single coarse lock is plenty and avoids partial reads.
_lock = threading.Lock()

JOB_FILE = "job.json"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def job_dir(job_id: str) -> Path:
    return JOBS_DIR / job_id


def _job_file(job_id: str) -> Path:
    return job_dir(job_id) / JOB_FILE


def create_job(job_id: str) -> dict:
    """Create a fresh job record on disk."""
    folder = job_dir(job_id)
    folder.mkdir(parents=True, exist_ok=True)
    record = {
        "job_id": job_id,
        "status": "created",
        "progress": 0,
        "current_step": None,
        "steps": [
            {"name": name, "status": "pending", "index": i}
            for i, name in enumerate(PROCESSING_STEPS)
        ],
        "error": None,
        "created_at": _now(),
        "updated_at": _now(),
        "artifacts": {},
    }
    _write(record)
    return record


def _write(record: dict) -> None:
    record["updated_at"] = _now()
    path = _job_file(record["job_id"])
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(record, indent=2), encoding="utf-8")
    tmp.replace(path)  # atomic on the same filesystem


def get_job(job_id: str) -> Optional[dict]:
    path = _job_file(job_id)
    if not path.exists():
        return None
    with _lock:
        return json.loads(path.read_text(encoding="utf-8"))


def update_job(job_id: str, **fields) -> Optional[dict]:
    with _lock:
        path = _job_file(job_id)
        if not path.exists():
            return None
        record = json.loads(path.read_text(encoding="utf-8"))
        record.update(fields)
        _write(record)
        return record


def set_step(job_id: str, step_index: int, progress: int) -> None:
    """Mark steps up to `step_index` done, the current one active."""
    with _lock:
        path = _job_file(job_id)
        if not path.exists():
            return
        record = json.loads(path.read_text(encoding="utf-8"))
        for step in record["steps"]:
            if step["index"] < step_index:
                step["status"] = "done"
            elif step["index"] == step_index:
                step["status"] = "active"
            else:
                step["status"] = "pending"
        record["current_step"] = PROCESSING_STEPS[step_index]
        record["progress"] = progress
        record["status"] = "processing"
        _write(record)


def finish_job(job_id: str) -> None:
    with _lock:
        path = _job_file(job_id)
        if not path.exists():
            return
        record = json.loads(path.read_text(encoding="utf-8"))
        for step in record["steps"]:
            step["status"] = "done"
        record["status"] = "done"
        record["progress"] = 100
        record["current_step"] = None
        record["artifacts"] = _discover_artifacts(job_id)
        _write(record)


def fail_job(job_id: str, message: str) -> None:
    update_job(job_id, status="error", error=message)


def _discover_artifacts(job_id: str) -> dict[str, str]:
    """Return logical-name -> download URL for artifacts present on disk."""
    folder = job_dir(job_id)
    out: dict[str, str] = {}
    for logical, filename in ARTIFACTS.items():
        if (folder / filename).exists():
            out[logical] = f"/api/download/{job_id}/{logical}"
    return out


BACKUP_DIR = "backup"

# Files snapshotted before a cleanup so "Undo Cleanup" can restore them.
BACKUP_FILES = [
    "lineart.png", "region_mask.png", "outline.svg",
    "regions.json", "game_config.json", "palette.json",
    "preview.png", "texture_fill_preview.png", "game_preview.png",
    "quality_report.json", "lineart_quality.json", "lineart_settings.json",
    "border_quality_report.json", "leak_map.png", "merged_region_map.png",
    "split_preview_map.png", "semantic_region_report.json", "semantic_overlay.png",
    "material_behavior_report.json", "material_behavior_overlay.png",
    "processing_policy_report.json", "processing_policy_overlay.png",
    "gradient_preservation_report.json", "gradient_preview_map.png",
    "flow_gradient_report.json", "flow_gradient_overlay.png", "progress.json",
]

# ---------------------------------------------------------------------------
# Phase 10 — edit versioning (undo/redo)
# ---------------------------------------------------------------------------

VERSIONS_DIRNAME = "versions"
VERSIONS_INDEX = "versions.json"
# Full snapshot of editable state. `labels.npy` is the persisted region label
# map that the region tools operate on.
SNAPSHOT_FILES = BACKUP_FILES + ["labels.npy"]


def versions_root(job_id: str) -> Path:
    return job_dir(job_id) / VERSIONS_DIRNAME


def _vindex_path(job_id: str) -> Path:
    return versions_root(job_id) / VERSIONS_INDEX


def _read_vindex(job_id: str) -> dict:
    path = _vindex_path(job_id)
    if path.exists():
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            pass
    return {"history": [], "current": -1, "seq": 0}


def _write_vindex(job_id: str, idx: dict) -> None:
    versions_root(job_id).mkdir(parents=True, exist_ok=True)
    _vindex_path(job_id).write_text(json.dumps(idx, indent=2), encoding="utf-8")


def has_versions(job_id: str) -> bool:
    return _read_vindex(job_id)["current"] >= 0


def snapshot(job_id: str, label: str) -> str:
    """Save current editable state as a new version; truncates any redo tail."""
    with _lock:
        idx = _read_vindex(job_id)
        # Drop redo history (and its folders) past the current pointer.
        for entry in idx["history"][idx["current"] + 1:]:
            shutil.rmtree(versions_root(job_id) / entry["id"], ignore_errors=True)
        idx["history"] = idx["history"][: idx["current"] + 1]

        idx["seq"] += 1
        vid = "version_%03d" % idx["seq"]
        vdir = versions_root(job_id) / vid
        vdir.mkdir(parents=True, exist_ok=True)
        src = job_dir(job_id)
        for name in SNAPSHOT_FILES:
            f = src / name
            if f.exists():
                shutil.copy2(f, vdir / name)

        idx["history"].append({"id": vid, "label": label})
        idx["current"] = len(idx["history"]) - 1
        _write_vindex(job_id, idx)
        return vid


def _restore_version_files(job_id: str, vid: str) -> None:
    vdir = versions_root(job_id) / vid
    dst = job_dir(job_id)
    for name in SNAPSHOT_FILES:
        f = vdir / name
        if f.exists():
            shutil.copy2(f, dst / name)


def undo(job_id: str) -> Optional[str]:
    with _lock:
        idx = _read_vindex(job_id)
        if idx["current"] <= 0:
            return None
        idx["current"] -= 1
        _write_vindex(job_id, idx)
        vid = idx["history"][idx["current"]]["id"]
    _restore_version_files(job_id, vid)
    return vid


def redo(job_id: str) -> Optional[str]:
    with _lock:
        idx = _read_vindex(job_id)
        if idx["current"] >= len(idx["history"]) - 1:
            return None
        idx["current"] += 1
        _write_vindex(job_id, idx)
        vid = idx["history"][idx["current"]]["id"]
    _restore_version_files(job_id, vid)
    return vid


def versions_state(job_id: str) -> dict:
    idx = _read_vindex(job_id)
    return {
        "versions": [e["id"] for e in idx["history"]],
        "labels": [e["label"] for e in idx["history"]],
        "current": idx["current"],
        "can_undo": idx["current"] > 0,
        "can_redo": idx["current"] < len(idx["history"]) - 1,
    }


LINEART_SETTINGS_FILE = "lineart_settings.json"


def lineart_settings_path(job_id: str) -> Path:
    return job_dir(job_id) / LINEART_SETTINGS_FILE


def read_lineart_settings(job_id: str) -> dict:
    path = lineart_settings_path(job_id)
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return {}


def write_lineart_settings(job_id: str, settings: dict) -> dict:
    """Merge non-None values over the existing settings and persist them."""
    current = read_lineart_settings(job_id)
    for key, value in settings.items():
        if value is not None:
            current[key] = value
    lineart_settings_path(job_id).write_text(
        json.dumps(current, indent=2), encoding="utf-8"
    )
    return current


def backup_dir(job_id: str) -> Path:
    return job_dir(job_id) / BACKUP_DIR


def make_backup(job_id: str) -> None:
    """Snapshot current generated artifacts (single-level undo)."""
    import shutil

    dest = backup_dir(job_id)
    dest.mkdir(parents=True, exist_ok=True)
    src = job_dir(job_id)
    for name in BACKUP_FILES:
        f = src / name
        if f.exists():
            shutil.copy2(f, dest / name)


def has_backup(job_id: str) -> bool:
    d = backup_dir(job_id)
    return d.exists() and any(d.iterdir())


def restore_backup(job_id: str) -> bool:
    """Restore the last snapshot. Returns False if there is nothing to undo."""
    import shutil

    d = backup_dir(job_id)
    if not d.exists() or not any(d.iterdir()):
        return False
    dst = job_dir(job_id)
    for name in BACKUP_FILES:
        f = d / name
        if f.exists():
            shutil.copy2(f, dst / name)
    return True


PROGRESS_FILE = "progress.json"


def progress_path(job_id: str) -> Path:
    return job_dir(job_id) / PROGRESS_FILE


def read_progress(job_id: str) -> dict:
    """Return the saved fill progress, or an empty record if none exists."""
    path = progress_path(job_id)
    if not path.exists():
        return {"filled": [], "updated_at": None}
    with _lock:
        data = json.loads(path.read_text(encoding="utf-8"))
    # Normalise: unique, sorted, ints.
    filled = sorted({int(x) for x in data.get("filled", [])})
    return {"filled": filled, "updated_at": data.get("updated_at")}


def write_progress(job_id: str, filled: list[int]) -> dict:
    """Persist the set of filled region ids for a job (atomic write)."""
    record = {
        "filled": sorted({int(x) for x in filled}),
        "updated_at": _now(),
    }
    with _lock:
        path = progress_path(job_id)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(record, indent=2), encoding="utf-8")
        tmp.replace(path)
    return record


def region_total(job_id: str) -> int:
    """Total fillable regions, read from the generated game_config.json."""
    path = job_dir(job_id) / "game_config.json"
    if not path.exists():
        return 0
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return len(data.get("regions", []))
    except (ValueError, OSError):
        return 0


def list_jobs() -> list[dict]:
    jobs: list[dict] = []
    if not JOBS_DIR.exists():
        return jobs
    for folder in sorted(JOBS_DIR.iterdir(), reverse=True):
        record_path = folder / JOB_FILE
        if record_path.exists():
            jobs.append(json.loads(record_path.read_text(encoding="utf-8")))
    return jobs
