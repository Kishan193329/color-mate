"""Admin Review System — a read-only review layer generated after processing.

For a completed job this builds the eight review previews, the ten headline statistics,
and the four approval actions into `review.json`. It is strictly READ-ONLY over the
pipeline's outputs: it reads `labels.npy`, `game_config.json`, `palette`, `outline.svg`
and the brain reports, and it WRITES only `review_*` assets + `review.json`. No rendering,
no regions, no palette, no numbers, no reveal are ever modified.

The eight previews:
  1. Original Image            -> original.png (as processed)
  2. Border Preview            -> review_border_preview.png  (plain region boundaries)
  3. Number Preview            -> game_preview.png           (the numbered colour-by-number)
  4. Artistic Border Preview   -> review_artistic_border.png (rasterised outline.svg)
  5. Reveal Preview            -> reveal_completed.png
  6. Region Heatmap            -> review_region_heatmap.png  (regions coloured by area)
  7. Tiny Region Overlay       -> review_tiny_overlay.png    (sub-threshold regions in red)
  8. Border Density Map        -> review_border_density.png  (local border busyness)

The ten statistics + a difficulty / play-time / complexity / border-quality / artistic
index are all derived from the pipeline outputs — this engine measures, it never re-runs
detection.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import cv2
import numpy as np

from services.contour_character_engine import _sample_d

# A region below this share of the frame (or the absolute floor) reads as "tiny" — the
# same order of magnitude as the region-detector keep floors, kept explicit here so the
# review's "tiny_regions" count is reproducible and independent of the profile.
TINY_AREA_FRAC = 0.0004
TINY_AREA_ABS = 64

# Play-time model (seconds): per region, a colourist locates the number then fills it.
FIND_SEC = 5.0
FILL_MIN_SEC = 2.0
FILL_MAX_SEC = 30.0
FILL_AREA_PER_SEC = 3000.0        # px filled per second once the number is found

# Complexity blend caps (a value at/above the cap maps to 100 on that component).
CX_REGION_CAP = 220               # regions
CX_DENSITY_CAP = 0.12             # border pixels / total pixels
CX_TINY_CAP = 0.40                # fraction of regions that are tiny


def _load(path: Path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _labels(folder: Path) -> np.ndarray:
    p = folder / "labels.npy"          # the canonical region map game_config numbers match
    return np.load(p).astype(np.int32)


def _clamp(x: float, lo: float = 0.0, hi: float = 100.0) -> float:
    return lo if x < lo else hi if x > hi else float(x)


# --------------------------------------------------------------------------- #
# asset generators — each writes a PNG into `folder` and returns its filename
# --------------------------------------------------------------------------- #
def _boundary_mask(labels: np.ndarray) -> np.ndarray:
    b = np.zeros(labels.shape, bool)
    b[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    b[:-1, :] |= labels[:-1, :] != labels[1:, :]
    b |= labels == 0
    return b


def _border_preview(labels: np.ndarray, folder: Path) -> str:
    canvas = np.full((*labels.shape, 3), 255, np.uint8)
    b = cv2.dilate(_boundary_mask(labels).astype(np.uint8), np.ones((2, 2), np.uint8)).astype(bool)
    canvas[b] = (20, 20, 20)
    cv2.imwrite(str(folder / "review_border_preview.png"), canvas)
    return "review_border_preview.png"


def _region_heatmap(labels: np.ndarray, area_by_label: dict, folder: Path) -> str:
    """Each region tinted by log(area): small regions cool, large regions warm."""
    n = int(labels.max()) + 1
    lut = np.zeros(n, np.float64)
    for lbl, area in area_by_label.items():
        if 0 < lbl < n:
            lut[lbl] = math.log1p(area)
    vals = lut[labels]
    hi = float(vals.max()) or 1.0
    norm = np.uint8(np.clip(vals / hi * 255.0, 0, 255))
    heat = cv2.applyColorMap(norm, cv2.COLORMAP_TURBO)
    heat[labels == 0] = (30, 30, 30)                      # line skeleton stays dark
    cv2.imwrite(str(folder / "review_region_heatmap.png"), heat)
    return "review_region_heatmap.png"


def _tiny_overlay(labels: np.ndarray, base_bgr: np.ndarray, tiny_labels: set, folder: Path) -> str:
    faded = (base_bgr.astype(np.float32) * 0.4 + 255 * 0.6).astype(np.uint8)
    if tiny_labels:
        mask = np.isin(labels, np.array(sorted(tiny_labels), dtype=labels.dtype))
        red = faded.copy()
        red[mask] = (40, 40, 235)
        faded = cv2.addWeighted(red, 0.75, faded, 0.25, 0)
        # outline each tiny region so single-pixel specks are still visible
        edge = cv2.dilate(mask.astype(np.uint8), np.ones((3, 3), np.uint8)) - mask.astype(np.uint8)
        faded[edge.astype(bool)] = (0, 0, 180)
    cv2.imwrite(str(folder / "review_tiny_overlay.png"), faded)
    return "review_tiny_overlay.png"


def _border_density(labels: np.ndarray, folder: Path) -> str:
    """Local border busyness: box-blur of the boundary map, colour-mapped."""
    h, w = labels.shape
    b = _boundary_mask(labels).astype(np.float32)
    k = max(15, (min(h, w) // 40) | 1)                    # odd kernel, scales with image
    dens = cv2.blur(b, (k, k))
    hi = float(dens.max()) or 1.0
    norm = np.uint8(np.clip(dens / hi * 255.0, 0, 255))
    heat = cv2.applyColorMap(norm, cv2.COLORMAP_INFERNO)
    cv2.imwrite(str(folder / "review_border_density.png"), heat)
    return "review_border_density.png", float(b.sum() / (h * w))


def _artistic_border_preview(folder: Path, h: int, w: int) -> str | None:
    """Rasterise the artistic outline.svg (widths + opacity honoured) so the review shows
    the actual rendered border — thickened silhouettes, emphasised features, removals."""
    svg = folder / "outline.svg"
    if not svg.exists():
        return None
    text = svg.read_text(encoding="utf-8")
    canvas = np.full((h, w, 3), 255, np.uint8)
    import re
    for m in re.finditer(r'<path d="([^"]+)"([^/]*)/>', text):
        d, attrs = m.group(1), m.group(2)
        sw = float((re.search(r'stroke-width="([\d.]+)"', attrs) or [None, "2"])[1])
        op = float((re.search(r'stroke-opacity="([\d.]+)"', attrs) or [None, "1"])[1])
        cm = re.search(r'stroke="#([0-9a-fA-F]{6})"', attrs)
        r_, g_, b_ = (int(cm.group(1)[0:2], 16), int(cm.group(1)[2:4], 16),
                      int(cm.group(1)[4:6], 16)) if cm else (17, 17, 17)
        col = (int(b_ * op + 255 * (1 - op)), int(g_ * op + 255 * (1 - op)), int(r_ * op + 255 * (1 - op)))
        t = max(1, int(round(sw)))
        for poly in _sample_d(d):
            cv2.polylines(canvas, [np.round(poly).astype(np.int32)], False, col, t, cv2.LINE_AA)
    cv2.imwrite(str(folder / "review_artistic_border.png"), canvas)
    return "review_artistic_border.png"


# --------------------------------------------------------------------------- #
# scoring — all indices are explainable admin-triage measures, 0..100
# --------------------------------------------------------------------------- #
def _play_time_seconds(areas: list) -> float:
    total = 0.0
    for a in areas:
        fill = min(FILL_MAX_SEC, max(FILL_MIN_SEC, a / FILL_AREA_PER_SEC))
        total += FIND_SEC + fill
    return total


def _complexity_score(region_count: int, border_density: float, tiny_frac: float) -> int:
    region_c = 100 * min(1.0, region_count / CX_REGION_CAP)
    density_c = 100 * min(1.0, border_density / CX_DENSITY_CAP)
    tiny_c = 100 * min(1.0, tiny_frac / CX_TINY_CAP)
    return int(round(_clamp(0.5 * region_c + 0.35 * density_c + 0.15 * tiny_c)))


def _border_quality_score(bq: dict | None) -> int | None:
    if not bq:
        return None
    s = 100.0
    leaks = int(bq.get("region_leak_count", 0) or 0)
    if leaks or bq.get("leak_pass") is False:
        s -= min(30.0, 10.0 + leaks * 5.0)
    if bq.get("shape_pass") is False:
        s -= 15.0
    if bq.get("closure_pass") is False:
        s -= 10.0
    s -= min(25.0, float(bq.get("jaggedness_score_after", 0.0) or 0.0) * 160.0)
    s -= min(10.0, int(bq.get("fragments_after", 0) or 0) * 0.2)
    return int(round(_clamp(s)))


def _artistic_score(abr: dict | None) -> int | None:
    if not abr:
        return None
    ms = abr.get("mean_scores") or {}
    keys = ["visual_importance", "colouring_enjoyment", "structural_importance",
            "artistic_importance", "playability"]
    vals = [float(ms[k]) for k in keys if k in ms]
    if not vals:
        return None
    return int(round(_clamp(100.0 * sum(vals) / len(vals))))


# --------------------------------------------------------------------------- #
# actions + status
# --------------------------------------------------------------------------- #
def _actions(job_id: str) -> list:
    base = f"/api/job/{job_id}/review"
    return [
        {"id": "approve", "label": "Approve", "method": "POST", "endpoint": f"{base}/approve"},
        {"id": "reject", "label": "Reject", "method": "POST", "endpoint": f"{base}/reject"},
        {"id": "regenerate", "label": "Regenerate", "method": "POST", "endpoint": f"{base}/regenerate"},
        {"id": "compare_previous", "label": "Compare Previous Version",
         "method": "GET", "endpoint": f"{base}/compare"},
    ]


def _compare(cur: dict, prev: dict | None) -> dict:
    if not prev:
        return {"available": False, "note": "No previous version to compare."}
    ps = prev.get("statistics", {})
    cs = cur
    fields = ["region_count", "palette_count", "tiny_regions", "average_region_size",
              "complexity_score", "border_quality_score", "artistic_score",
              "estimated_play_time_min"]
    deltas = {}
    for f in fields:
        a, b = cs.get(f), ps.get(f)
        if isinstance(a, (int, float)) and isinstance(b, (int, float)):
            deltas[f] = {"previous": b, "current": a, "delta": round(a - b, 3)}
    return {"available": True, "previous_status": prev.get("status"), "deltas": deltas}


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def build_review(folder: Path, job_id: str | None = None) -> dict:
    """Generate the review assets + review.json for a completed job. Read-only over the
    pipeline outputs. Returns the review dict (also written to review.json)."""
    folder = Path(folder)
    job_id = job_id or folder.name
    gc = _load(folder / "game_config.json") or {}
    regions = gc.get("regions", [])
    palette = gc.get("palette", [])
    h = int(gc.get("height") or 0)
    w = int(gc.get("width") or 0)

    labels = _labels(folder)
    if not h or not w:
        h, w = labels.shape
    area_by_label = {int(r.get("label")): int(r.get("area", 0)) for r in regions if r.get("label") is not None}
    areas = [int(r.get("area", 0)) for r in regions]

    tiny_thresh = max(TINY_AREA_ABS, int(TINY_AREA_FRAC * h * w))
    tiny_labels = {int(r["label"]) for r in regions
                   if r.get("label") is not None and int(r.get("area", 0)) < tiny_thresh}

    # base image for the tiny overlay
    orig = cv2.imread(str(folder / "original.png"))
    if orig is None:
        orig = np.full((h, w, 3), 255, np.uint8)
    elif orig.shape[:2] != (h, w):
        orig = cv2.resize(orig, (w, h), interpolation=cv2.INTER_AREA)

    density_name, border_density = _border_density(labels, folder)
    assets = {
        "original": "original.png",
        "border_preview": _border_preview(labels, folder),
        "number_preview": "game_preview.png" if (folder / "game_preview.png").exists() else None,
        "artistic_border_preview": _artistic_border_preview(folder, h, w),
        "reveal_preview": "reveal_completed.png" if (folder / "reveal_completed.png").exists()
        else ("reveal_partial.png" if (folder / "reveal_partial.png").exists() else None),
        "region_heatmap": _region_heatmap(labels, area_by_label, folder),
        "tiny_region_overlay": _tiny_overlay(labels, orig, tiny_labels, folder),
        "border_density_map": density_name,
    }

    total = h * w or 1
    largest = max(areas) if areas else 0
    tiny_frac = (len(tiny_labels) / len(regions)) if regions else 0.0
    play_sec = _play_time_seconds(areas)
    bq = _load(folder / "border_quality_report.json")
    abr = _load(folder / "artistic_border_report.json")

    statistics = {
        "region_count": len(regions),
        "palette_count": len(palette),
        "tiny_regions": len(tiny_labels),
        "tiny_threshold_px": tiny_thresh,
        "largest_region": {"area_px": largest, "area_pct": round(100.0 * largest / total, 2)},
        "average_region_size": int(round(sum(areas) / len(areas))) if areas else 0,
        "estimated_difficulty": gc.get("difficulty") or (gc.get("stats") or {}).get("difficulty"),
        "estimated_play_time_min": int(round(play_sec / 60.0)),
        "complexity_score": _complexity_score(len(regions), border_density, tiny_frac),
        "border_quality_score": _border_quality_score(bq),
        "artistic_score": _artistic_score(abr),
    }

    prev = _load(folder / "review_previous.json")
    existing = _load(folder / "review.json") or {}
    review = {
        "version": 1,
        "job_id": job_id,
        "status": existing.get("status", "pending"),
        "decision": existing.get("decision"),
        "note": "Admin review layer — read-only over the pipeline outputs. Eight previews, ten "
                "statistics, four one-click actions. Creates only review_* assets + review.json; "
                "no rendering, regions, palette, numbers or reveal are modified.",
        "assets": assets,
        "statistics": statistics,
        "actions": _actions(job_id),
        "comparison": _compare(statistics, prev),
        "readonly": True,
    }
    (folder / "review.json").write_text(json.dumps(review, indent=2), encoding="utf-8")
    return review


def set_status(folder: Path, status: str, reason: str | None = None) -> dict:
    """Approve / reject — update review.json status only (building it first if absent)."""
    folder = Path(folder)
    if not (folder / "review.json").exists():
        build_review(folder)
    review = _load(folder / "review.json") or {}
    review["status"] = status
    review["decision"] = {"status": status, "reason": reason}
    (folder / "review.json").write_text(json.dumps(review, indent=2), encoding="utf-8")
    return review


def archive_current(folder: Path) -> bool:
    """Snapshot the current review.json as review_previous.json (for Compare Previous
    Version after a Regenerate). Returns True if a snapshot was taken."""
    folder = Path(folder)
    cur = folder / "review.json"
    if cur.exists():
        (folder / "review_previous.json").write_text(cur.read_text(encoding="utf-8"), encoding="utf-8")
        return True
    return False


def compare_previous(folder: Path) -> dict:
    """Diff the current review statistics against the archived previous version."""
    folder = Path(folder)
    if not (folder / "review.json").exists():
        build_review(folder)
    cur = _load(folder / "review.json") or {}
    prev = _load(folder / "review_previous.json")
    return _compare(cur.get("statistics", {}), prev)
