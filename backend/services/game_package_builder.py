"""Game Package Builder — the exact package the Android app consumes after admin approval.

Bundles an APPROVED image's outputs into `game_package.json` (the Android-readable index) and
`game_assets.zip` (the assets). It reads existing pipeline outputs only — no processing
algorithm changes. **Only approved (or published) images can be packaged.**

The package includes the original / reveal master, the border SVG + PNG, the numbered preview,
labels / masks, palette / navigation colours, region metadata, game_config, category metadata,
a thumbnail, the difficulty / playtime / fun scores, and the review status. Every asset path in
the manifest is a relative, forward-slash path into the zip (Android-readable), with a sha1 for
integrity.
"""
from __future__ import annotations

import hashlib
import json
import re
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np

from services import publishing
from services.contour_character_engine import _sample_d

SCHEMA_VERSION = 1
PACKAGE_JSON = "game_package.json"
PACKAGE_ZIP = "game_assets.zip"

# logical name -> (source filename or None if generated, zip path, required)
ASSETS = [
    ("original", "original.png", "assets/original.png", True),
    ("reveal_master", "reveal_master.png", "assets/reveal_master.png", True),
    ("border_svg", "outline.svg", "assets/border.svg", True),
    ("border_png", None, "assets/border.png", True),                 # generated from outline.svg
    ("numbered_preview", "game_preview.png", "assets/numbered_preview.png", True),
    ("labels", "labels.npy", "assets/labels.npy", True),
    ("region_mask", "region_mask.png", "assets/region_mask.png", True),
    ("reveal_blank", "reveal_blank.png", "assets/reveal_blank.png", False),
    ("reveal_partial", "reveal_partial.png", "assets/reveal_partial.png", False),
    ("reveal_highlight", "reveal_highlight.png", "assets/reveal_highlight.png", False),
    ("reveal_completed", "reveal_completed.png", "assets/reveal_completed.png", True),
    ("palette", "palette.json", "assets/palette.json", True),
    ("regions", "regions.json", "assets/regions.json", True),
    ("game_config", "game_config.json", "assets/game_config.json", True),
    ("thumbnail", "publish_thumbnail.png", "assets/thumbnail.png", True),
]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _load(p: Path):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _sha1(b: bytes) -> str:
    return hashlib.sha1(b).hexdigest()


def _border_png_bytes(folder: Path, w: int, h: int) -> bytes | None:
    """Rasterise the artistic outline.svg to a border PNG (matches the vector border)."""
    svg = folder / "outline.svg"
    if not svg.exists() or w <= 0 or h <= 0:
        return None
    text = svg.read_text(encoding="utf-8")
    canvas = np.full((h, w, 3), 255, np.uint8)
    for m in re.finditer(r'<path d="([^"]+)"([^/]*)/>', text):
        d, attrs = m.group(1), m.group(2)
        sw = float((re.search(r'stroke-width="([\d.]+)"', attrs) or [None, "2"])[1])
        op = float((re.search(r'stroke-opacity="([\d.]+)"', attrs) or [None, "1"])[1])
        cm = re.search(r'stroke="#([0-9a-fA-F]{6})"', attrs)
        r_, g_, b_ = (int(cm.group(1)[0:2], 16), int(cm.group(1)[2:4], 16),
                      int(cm.group(1)[4:6], 16)) if cm else (17, 17, 17)
        col = (int(b_ * op + 255 * (1 - op)), int(g_ * op + 255 * (1 - op)), int(r_ * op + 255 * (1 - op)))
        for poly in _sample_d(d):
            cv2.polylines(canvas, [np.round(poly).astype(np.int32)], False, col, max(1, int(round(sw))), cv2.LINE_AA)
    ok, buf = cv2.imencode(".png", canvas)
    return buf.tobytes() if ok else None


def _fun_score(hvi: dict | None, stats: dict) -> int:
    means = (hvi or {}).get("means", {}) or {}
    if isinstance(means.get("fun_to_color_score"), (int, float)):
        return int(round(100 * means["fun_to_color_score"]))
    art = stats.get("artistic_score") or 0
    cx = stats.get("complexity_score")
    cx = cx if isinstance(cx, (int, float)) else 50
    return int(round(0.6 * art + 0.4 * (100 - cx)))


def is_packageable(folder: Path) -> bool:
    return publishing.status(folder) in ("approved", "published")


def build_game_package(folder: Path) -> dict:
    """Assemble game_package.json + game_assets.zip for an APPROVED image. Raises ValueError
    if the image is not approved/published."""
    folder = Path(folder)
    status = publishing.status(folder)
    if status not in ("approved", "published"):
        raise ValueError(f"only approved images can be packaged (status={status})")

    publishing.ensure_thumbnail(folder)                       # thumbnail/grid preview
    gc = _load(folder / "game_config.json") or {}
    w, h = int(gc.get("width") or 0), int(gc.get("height") or 0)
    palette = gc.get("palette", [])
    regions = gc.get("regions", [])
    review = _load(folder / "review.json") or {}
    stats = review.get("statistics", {}) or {}
    hvi = _load(folder / "human_visual_report.json")
    pub = publishing.load(folder)

    # collect the asset bytes (generating border.png), compute integrity + completeness
    entries: list[tuple[str, bytes]] = []
    assets_map: dict = {}
    integrity: dict = {}
    missing: list = []
    for name, src, zpath, required in ASSETS:
        data = None
        if name == "border_png":
            data = _border_png_bytes(folder, w, h)
        elif src is not None and (folder / src).exists():
            data = (folder / src).read_bytes()
        if data is None:
            if required:
                missing.append(name)
            continue
        entries.append((zpath, data))
        assets_map[name] = zpath
        integrity[zpath] = _sha1(data)

    complete = not missing

    manifest = {
        "schema_version": SCHEMA_VERSION,
        "note": "Game package for the Android app. Only approved images are packaged. All asset "
                "paths are relative forward-slash paths inside game_assets.zip. Built from "
                "existing pipeline outputs — no processing algorithm change.",
        "job_id": pub.get("job_id", folder.name),
        "image_id": _image_id(folder),
        "review_status": status,
        "complete": complete,
        "missing_assets": missing,
        "category": pub.get("category"),
        "dimensions": {"width": w, "height": h},
        "counts": {"regions": len(regions), "palette": len(palette)},
        "scores": {
            "difficulty": gc.get("difficulty") or stats.get("estimated_difficulty"),
            "estimated_playtime_min": stats.get("estimated_play_time_min"),
            "fun_score": _fun_score(hvi, stats),
            "complexity_score": stats.get("complexity_score"),
            "border_quality_score": stats.get("border_quality_score"),
            "artistic_score": stats.get("artistic_score"),
        },
        "palette": palette,                                   # navigation colours, inline
        "assets": assets_map,                                 # logical -> zip path
        "integrity_sha1": integrity,
        "created_at": _now(),
    }
    manifest_bytes = json.dumps(manifest, indent=2).encode("utf-8")

    # write game_package.json (standalone) + game_assets.zip (assets + manifest inside)
    (folder / PACKAGE_JSON).write_bytes(manifest_bytes)
    with zipfile.ZipFile(folder / PACKAGE_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(PACKAGE_JSON, manifest_bytes)
        for zpath, data in entries:
            z.writestr(zpath, data)
    return manifest


def _image_id(folder: Path) -> str:
    p = folder / "original.png"
    try:
        return "img_" + hashlib.sha1(p.read_bytes()).hexdigest()[:12]
    except OSError:
        return "job_" + Path(folder).name


def load_package(folder: Path) -> dict | None:
    return _load(Path(folder) / PACKAGE_JSON)
