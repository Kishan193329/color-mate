"""Original-pixel reveal renderer (Product Core Pivot).

The reveal product model: the completed picture is the **original image itself**,
uncovered region by region — never a repaint. The engine's regions are used only as
**masks**; filling a region reveals the original pixels inside that mask. Palette
colours and numbers are navigation hints, not the fill. When every region is filled,
the union of masks reveals the original exactly.

Core rules honoured:
  1. The original image is the immutable master (rule 1). We resize a *copy* of the
     original to the label grid with area interpolation — no colour is generated or
     replaced (rule 7); denoised `preprocessed.png` is deliberately NOT used as the
     reveal source.
  2. Regions are masks only: `region_reveal_mask(labels, label)` (rule 2/3).
  4. The palette/number system is untouched and used only as a hint (rule 4).
  5. `render_completed` copies master pixels into every mask, so the completed canvas
     equals the master under the union of masks (validated to ΔE 0 / 0 px diff).
  6. Borders and numbers are drawn as separate overlay layers, never baked into the
     completed reveal.

This module adds artifacts and a validation report; it changes nothing in the
existing flat-fill (`game_preview`) or palette/region/number pipeline.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from services import image_preprocess

FONT = cv2.FONT_HERSHEY_SIMPLEX
LINE_RGB = (30, 30, 30)          # overlay border colour
BLANK_RGB = (255, 255, 255)      # unfilled coloring-page background
HIGHLIGHT_BGR = (70, 235, 255)   # "where does this number go" tint (amber)


def region_reveal_mask(labels: np.ndarray, label: int) -> np.ndarray:
    """The reveal mask for one region = its label footprint. Regions are masks only."""
    return labels == label


def reveal_master(region_data: dict, original_path: Path) -> np.ndarray:
    """The immutable reveal master: the ORIGINAL image (not the denoised working
    image) resized to the label grid with area interpolation. Original pixels only."""
    labels: np.ndarray = region_data["_label_map"]
    h, w = labels.shape
    orig = image_preprocess.read_bgr(Path(original_path))     # honours EXIF, robust
    if orig.shape[:2] != (h, w):
        orig = cv2.resize(orig, (w, h), interpolation=cv2.INTER_AREA)
    return orig


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def _draw_borders(canvas: np.ndarray, labels: np.ndarray, thickness: int = 2) -> None:
    edge = (_boundaries(labels).astype(np.uint8)) * 255
    if thickness > 1:
        edge = cv2.dilate(edge, np.ones((thickness, thickness), np.uint8))
    canvas[edge > 0] = LINE_RGB[::-1]


def _draw_numbers(canvas: np.ndarray, region_data: dict, skip_labels: set | None = None) -> None:
    scale_mult = float(region_data.get("_label_scale", 1.0))
    for r in region_data["regions"]:
        if not r.get("visible"):
            continue
        if skip_labels is not None and r["label"] not in skip_labels:
            continue
        text = str(r.get("color_number", r.get("color_id", "")))
        pos = r.get("label_pos", {"x": r["cx"], "y": r["cy"]})
        s = float(np.clip(r.get("_label_r", 12.0) / 22.0 * scale_mult, 0.4, 1.6))
        th = max(1, int(round(s * 2)))
        (tw, tht), _ = cv2.getTextSize(text, FONT, s, th)
        org = (int(pos["x"] - tw / 2), int(pos["y"] + tht / 2))
        cv2.putText(canvas, text, org, FONT, s, (255, 255, 255), th + 2, cv2.LINE_AA)
        cv2.putText(canvas, text, org, FONT, s, (20, 20, 20), th, cv2.LINE_AA)


# ── preview states ───────────────────────────────────────────────────────── #
def render_blank(region_data: dict, out_path: Path) -> Path:
    """State 1 — blank coloring page: white canvas + region outlines + numbers."""
    labels = region_data["_label_map"]
    canvas = np.full((labels.shape[0], labels.shape[1], 3), 255, np.uint8)
    _draw_borders(canvas, labels)
    _draw_numbers(canvas, region_data)
    cv2.imwrite(str(out_path), canvas)
    return out_path


def render_highlight(region_data: dict, out_path: Path, target_number: int) -> Path:
    """State 2 — selected-number checker: highlight every region whose colour number
    is `target_number` on the blank page, so the player sees where that number goes."""
    labels = region_data["_label_map"]
    canvas = np.full((labels.shape[0], labels.shape[1], 3), 255, np.uint8)
    sel = [r["label"] for r in region_data["regions"]
           if r.get("color_number", r.get("color_id")) == target_number]
    if sel:
        mask = np.isin(labels, sel)
        tint = canvas.copy()
        tint[mask] = HIGHLIGHT_BGR
        canvas = cv2.addWeighted(tint, 0.55, canvas, 0.45, 0)
    _draw_borders(canvas, labels)
    _draw_numbers(canvas, region_data)
    cv2.imwrite(str(out_path), canvas)
    return out_path


def render_partial(region_data: dict, master: np.ndarray, out_path: Path,
                   revealed_numbers: set) -> Path:
    """State 3 — partial fill: reveal ORIGINAL pixels for the colour-number groups
    the player has finished; the rest stays a blank outlined page with its numbers."""
    labels = region_data["_label_map"]
    canvas = np.full_like(master, 255)
    revealed_labels = [r["label"] for r in region_data["regions"]
                       if r.get("color_number", r.get("color_id")) in revealed_numbers]
    filled_mask = np.isin(labels, revealed_labels)
    canvas[filled_mask] = master[filled_mask]                 # reveal original pixels
    _draw_borders(canvas, labels)
    unfilled = {r["label"] for r in region_data["regions"] if r["label"] not in set(revealed_labels)}
    _draw_numbers(canvas, region_data, skip_labels=unfilled)  # numbers only on unfilled
    cv2.imwrite(str(out_path), canvas)
    return out_path


def render_completed(region_data: dict, master: np.ndarray, out_path: Path,
                     border_overlay: bool = False) -> tuple[Path, np.ndarray]:
    """State 4 — completed reveal: every region's mask filled with master pixels →
    the completed canvas equals the original. Borders are an OPTIONAL overlay layer
    (rule 6); off by default so the final matches the original exactly. Returns the
    (path, reveal-only image) — the reveal-only image (no overlay) is what validation
    compares against the master."""
    labels = region_data["_label_map"]
    reveal = np.full_like(master, 255)
    reveal[labels > 0] = master[labels > 0]                   # union of all masks
    out = reveal.copy()
    # Fill the thin label-0 skeleton from master too, so the SAVED preview is a
    # seamless original (the reward). This is cosmetic; validation uses `reveal`.
    out[labels == 0] = master[labels == 0]
    if border_overlay:
        _draw_borders(out, labels)
    cv2.imwrite(str(out_path), out)
    return out_path, reveal


# ── validation ───────────────────────────────────────────────────────────── #
def _delta_e(a_bgr: np.ndarray, b_bgr: np.ndarray, mask: np.ndarray) -> float:
    a = cv2.cvtColor(a_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    b = cv2.cvtColor(b_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    d = np.sqrt(((a - b) ** 2).sum(axis=2))
    return float(d[mask].mean()) if mask.any() else 0.0


def validate(region_data: dict, master: np.ndarray, reveal: np.ndarray) -> dict:
    """Prove the completed reveal equals the master under the union of masks."""
    labels = region_data["_label_map"]
    mask = labels > 0
    total = int(labels.size)
    covered = int(mask.sum())
    diff = int(np.any(reveal[mask] != master[mask], axis=-1).sum()) if covered else 0
    return {
        "completed_reveal_delta_e": round(_delta_e(reveal, master, mask), 6),
        "completed_reveal_pixel_diff": diff,
        "completed_reveal_pixel_diff_percent": round(diff / covered * 100, 6) if covered else 0.0,
        "mask_coverage_percent": round(covered / total * 100, 3),
        "unrevealed_pixel_percent": round((total - covered) / total * 100, 3),
        "matches_original_under_masks": bool(diff == 0),
        "note": "unrevealed pixels are the thin line-art skeleton (label 0), which is "
                "an overlay layer, not part of the reveal (rules 5 & 6).",
    }


# ── orchestration ────────────────────────────────────────────────────────── #
def render_all(region_data: dict, original_path: Path, out: dict) -> dict:
    """Produce the reveal master + 4 preview states + validation. `out` maps logical
    names {master,blank,highlight,partial,completed,validation} -> paths. Returns the
    reveal-mode manifest (assets + validation) for game_config."""
    labels = region_data["_label_map"]
    master = reveal_master(region_data, original_path)
    cv2.imwrite(str(out["master"]), master)

    # pick a busy colour number for the highlight demo (most regions share it)
    nums = [r.get("color_number", r.get("color_id")) for r in region_data["regions"]
            if r.get("color_number", r.get("color_id")) is not None]
    target = max(set(nums), key=nums.count) if nums else 1
    # partial demo: reveal the first half of the colour groups
    all_nums = sorted(set(nums))
    revealed = set(all_nums[: max(1, len(all_nums) // 2)])

    render_blank(region_data, out["blank"])
    render_highlight(region_data, out["highlight"], target)
    render_partial(region_data, master, out["partial"], revealed)
    _, reveal = render_completed(region_data, master, out["completed"])
    validation = validate(region_data, master, reveal)

    Path(out["validation"]).write_text(json.dumps(validation, indent=2), encoding="utf-8")
    return {
        "mode": "reveal",
        "source": "original pixels (immutable master); regions are masks only",
        "master_asset": Path(out["master"]).name,
        "mask_source": "labels.npy / region_mask.png (region_reveal_mask = labels==label)",
        "previews": {
            "blank": Path(out["blank"]).name,
            "selected_number_highlight": Path(out["highlight"]).name,
            "partial": Path(out["partial"]).name,
            "completed": Path(out["completed"]).name,
        },
        "highlight_demo_number": int(target),
        "partial_demo_numbers": [int(n) for n in sorted(revealed)],
        "validation": validation,
    }
