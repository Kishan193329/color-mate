"""Stage 5c — JSON artifacts (real, game-ready schema).

Emits three documents from the enriched `region_data`:

    regions.json      -> version + canvas size + every region (path, bbox, area,
                         label_pos, color_id, visible)
    palette.json      -> the de-duplicated color palette (id, number, color)
    game_config.json   -> a single game-ready bundle: regions + palette + stats
                         + difficulty + asset map

Region colors come from sampling the original image inside each region (done in
region_detector); palette entries are de-duplicated by exact color — there is no
color quantization and the original image is never recolored.
"""
from __future__ import annotations

import json
from pathlib import Path


def _palette(region_data: dict) -> list[dict]:
    """Build (and cache) the palette, assigning `color_id` to every region.

    Identical sampled colors collapse to one palette entry. Idempotent so all
    three writers can call it in any order.
    """
    if "_palette_v2" in region_data:
        return region_data["_palette_v2"]

    palette: list[dict] = []
    by_color: dict[str, int] = {}
    for region in region_data["regions"]:
        hex_color = region["color"]
        if hex_color not in by_color:
            pid = len(palette) + 1
            by_color[hex_color] = pid
            palette.append({"id": pid, "number": pid, "color": hex_color})
        region["color_id"] = by_color[hex_color]

    region_data["_palette_v2"] = palette
    return palette


def _region_public(region: dict) -> dict:
    """Project an internal region onto the public, serializable schema.

    `id` is the unique region id; `number`/`color_id` are the (shared) paint
    color group from the LAB palette grouper; `sampled_color` is the region's
    median color sampled from the original image.
    """
    return {
        "id": region["number"],
        "label": region.get("label"),  # internal label-map value (region tools)
        "path": region.get("path", ""),
        "area": int(region["area"]),
        "bbox": [region["x"], region["y"], region["w"], region["h"]],
        "label_pos": region.get("label_pos", {"x": region["cx"], "y": region["cy"]}),
        "sampled_color": region.get("sampled_color", region.get("color")),
        "color_id": region.get("color_id"),
        "number": region.get("color_number", region.get("color_id")),
        "visible": bool(region.get("visible", True)),
        # Phase 7 — semantic helper layer (None when disabled).
        "semantic_label": region.get("semantic_label"),
        "parent_object": region.get("parent_object"),
        "group_id": region.get("group_id"),
        "region_type": region.get("region_type"),
    }


def write_regions(region_data: dict, out_path: Path) -> Path:
    _palette(region_data)
    payload = {
        "version": 1,
        "width": region_data["width"],
        "height": region_data["height"],
        "regions": [_region_public(r) for r in region_data["regions"]],
    }
    out_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return out_path


def write_palette(region_data: dict, out_path: Path) -> Path:
    palette = _palette(region_data)
    payload = {"version": 1, "count": len(palette), "palette": palette}
    out_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return out_path


def write_game_config(
    job_id: str,
    region_data: dict,
    out_path: Path,
    stats: dict | None = None,
    fill_mode: str = "palette",
    semantic: dict | None = None,
) -> Path:
    palette = _palette(region_data)
    payload = {
        "version": 1,
        "job_id": job_id,
        "width": region_data["width"],
        "height": region_data["height"],
        "fill_mode": fill_mode,  # default fill style: "texture" | "palette"
        "semantic": semantic or {"mode": "rules", "hierarchy": None},
        "regions": [_region_public(r) for r in region_data["regions"]],
        "palette": palette,
        "stats": stats or {},
        "difficulty": _difficulty(len(palette)),
        "assets": {
            "original": "original.png",
            "lineart": "lineart.png",
            "region_mask": "region_mask.png",
            "outline": "outline.svg",
            "regions": "regions.json",
            "palette": "palette.json",
            "preview": "preview.png",
            "texture_fill_preview": "texture_fill_preview.png",
            "game_preview": "game_preview.png",
        },
    }
    out_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return out_path


def _difficulty(color_count: int) -> str:
    if color_count <= 8:
        return "easy"
    if color_count <= 20:
        return "medium"
    return "hard"
