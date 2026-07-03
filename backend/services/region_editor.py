"""Phase 10 — region editor (manual correction tools).

Operates on the persisted label map (`labels.npy`) for structural edits
(merge / split / delete) and on the JSON outputs for attribute edits
(move/hide label, change color number). Structural edits rebuild every region
artifact via `pipeline.rebuild_from_labels`; attribute edits patch the JSON in
place. Line art remains the source of truth — these tools never invent regions
from color.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np

import pipeline
from job_store import job_dir


def _labels_path(job_id: str) -> Path:
    return job_dir(job_id) / "labels.npy"


def load_labels(job_id: str) -> np.ndarray:
    return np.load(_labels_path(job_id))


def save_labels(job_id: str, labels: np.ndarray) -> None:
    np.save(_labels_path(job_id), labels.astype(np.int32))


def _id_to_label(job_id: str) -> dict[int, int]:
    """Map public region id (regions.json) -> label-map value."""
    path = job_dir(job_id) / "regions.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    return {r["id"]: r["label"] for r in data.get("regions", []) if r.get("label") is not None}


# --- structural edits (label map) ------------------------------------------
def merge(job_id: str, region_ids: list[int]) -> None:
    if len(region_ids) < 2:
        raise ValueError("merge needs at least 2 regions")
    mapping = _id_to_label(job_id)
    targets = [mapping[i] for i in region_ids if i in mapping]
    if len(targets) < 2:
        raise ValueError("regions not found")
    labels = load_labels(job_id)
    keep = targets[0]
    for lbl in targets[1:]:
        labels[labels == lbl] = keep
    save_labels(job_id, labels)
    pipeline.rebuild_from_labels(job_id)


def delete(job_id: str, region_ids: list[int] | None = None, min_area: int | None = None) -> None:
    labels = load_labels(job_id)
    mapping = _id_to_label(job_id)
    to_remove: list[int] = []
    if region_ids:
        to_remove += [mapping[i] for i in region_ids if i in mapping]
    if min_area:
        data = json.loads((job_dir(job_id) / "regions.json").read_text(encoding="utf-8"))
        for r in data.get("regions", []):
            if r.get("area", 0) < min_area and r.get("label") is not None:
                to_remove.append(r["label"])
    if not to_remove:
        raise ValueError("nothing to delete")
    for lbl in to_remove:
        labels[labels == lbl] = 0  # back to line/background
    save_labels(job_id, labels)
    pipeline.rebuild_from_labels(job_id)


def split(job_id: str, region_id: int, points: list[list[float]]) -> None:
    """Split a region by cutting it along a poly-line, then relabelling halves."""
    import cv2

    mapping = _id_to_label(job_id)
    if region_id not in mapping:
        raise ValueError("region not found")
    lbl = mapping[region_id]
    labels = load_labels(job_id)

    pts = np.array([[int(round(x)), int(round(y))] for x, y in points], np.int32)
    if len(pts) < 2:
        raise ValueError("split needs a line of at least 2 points")
    cut = np.zeros(labels.shape, np.uint8)
    cv2.polylines(cut, [pts], False, 255, thickness=3)
    labels[(labels == lbl) & (cut > 0)] = 0  # carve the cut

    region_mask = (labels == lbl).astype(np.uint8)
    n, sub = cv2.connectedComponents(region_mask, connectivity=8)
    next_label = int(labels.max())
    for k in range(1, n):
        if k == 1:
            continue  # first piece keeps the original label
        next_label += 1
        labels[sub == k] = next_label
    save_labels(job_id, labels)
    pipeline.rebuild_from_labels(job_id)


# --- attribute edits (JSON patch, no geometry change) ----------------------
def _patch_region(job_id: str, region_id: int, patch: dict) -> None:
    folder = job_dir(job_id)
    for name in ("regions.json", "game_config.json"):
        path = folder / name
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for r in data.get("regions", []):
            if r.get("id") == region_id:
                r.update(patch)
        path.write_text(json.dumps(data, indent=2), encoding="utf-8")


def update_label(
    job_id: str, region_id: int,
    label_pos: dict | None = None, visible: bool | None = None,
) -> None:
    patch: dict = {}
    if label_pos is not None:
        patch["label_pos"] = {"x": int(label_pos["x"]), "y": int(label_pos["y"])}
    if visible is not None:
        patch["visible"] = bool(visible)
    if not patch:
        raise ValueError("nothing to update")
    _patch_region(job_id, region_id, patch)


def update_color(job_id: str, region_id: int, color_id: int) -> None:
    _patch_region(job_id, region_id, {"color_id": int(color_id), "number": int(color_id)})
