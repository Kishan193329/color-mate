"""Auto profile selection (Product Sprint 2).

Reads a finished job's quality signals — leak report (QF3), palette / region /
tiny-region counts, a rendered-ΔE estimate, a flatness score, and protected-
highlight loss (from QF13's artistic_decision_report) — and decides whether the
chosen output profile is right, too flat (under-detailed) or too complex, plus a
single scalar `score()` used to pick the best output across attempts.

Pure functions only — no pipeline dependency, no side effects. It recomputes no
engine analysis; it only summarises artifacts the pipeline already wrote.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from config import OUTPUT_PROFILES
from core.gradient_common import _delta_e, _mean_fill

# Profiles ordered from simplest (fewest colours/regions) to richest.
PROFILE_ORDER = ["easy", "medium", "hard", "professional", "maximum_fidelity"]

# ── Quality targets for a good color-by-number ──────────────────────────────
FAITHFUL_DELTAE = 10.0    # rendered ΔE above this reads as "too flat" (under-detailed)
PALETTE_MIN = 14          # below this the result is boringly simple
PALETTE_MAX = 34          # above this it is overwhelming (too complex)
REGION_MAX = 170          # region count above this is too busy
TINY_MAX = 8              # sub-floor "clutter" regions tolerated


def _hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def _rendered_fill(folder: Path, labels: np.ndarray, regions: list, palette: list) -> np.ndarray:
    """Reconstruct the as-painted grouped-palette fill (what the game shows)."""
    pal = {p["id"]: p["color"] for p in palette}
    num = int(labels.max()) + 1
    lut = np.zeros((num, 3), np.uint8)
    for r in regions:
        lut[r["label"]] = _hex_to_rgb(pal.get(r.get("color_id"), "#C8C8C8"))
    fill = cv2.cvtColor(lut[labels], cv2.COLOR_RGB2BGR)
    fill[labels == 0] = (35, 35, 35)
    return fill


def _load(folder: Path, name: str) -> dict | list | None:
    p = folder / name
    if not p.exists():
        return None
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return None


def _highlight_loss(folder: Path, regions: list) -> int:
    """Count protected highlight/facial regions that got merged into a material
    colour (a lost catch-light). 0 under the CO1 category-lock."""
    art = _load(folder, "artistic_decision_report.json")
    if not art:
        return 0
    # label -> True if this region is a facial feature / explicit highlight
    prot = {}
    for a in art.get("regions", []):
        lbl = a.get("label")
        if lbl is None:
            continue
        prot[lbl] = (a.get("artistic_role") == "FACIAL_FEATURE")
    color_prot: dict = {}   # color_id -> set of {True, False} membership
    for r in regions:
        cid = r.get("color_id")
        color_prot.setdefault(cid, set()).add(bool(prot.get(r.get("label"), False)))
    # a colour shared by a protected AND an unprotected region = a merged highlight
    return sum(1 for cid, kinds in color_prot.items() if True in kinds and False in kinds)


def assess(folder: Path, profile_name: str) -> dict:
    """Summarise a finished job into the quality signals the selector reasons over."""
    folder = Path(folder)
    regions_doc = _load(folder, "regions.json") or {"regions": []}
    regions = regions_doc.get("regions", [])
    palette_doc = _load(folder, "palette.json") or {"palette": [], "count": 0}
    palette = palette_doc.get("palette", [])
    quality = _load(folder, "quality_report.json") or {}
    border = _load(folder, "border_quality_report.json") or {}

    rendered_de = meanfill_de = None
    labels_path = folder / "labels.npy"
    pre_path = folder / "preprocessed.png"
    if labels_path.exists() and pre_path.exists() and regions:
        labels = np.load(labels_path)
        bgr = cv2.imread(str(pre_path), cv2.IMREAD_COLOR)
        non_line = labels > 0
        fill = _rendered_fill(folder, labels, regions, palette)
        rendered_de = round(_delta_e(fill, bgr, non_line), 3)
        meanfill_de = round(_delta_e(_mean_fill(labels, bgr), bgr, non_line), 3)

    leaks = int(border.get("region_leak_count", 0) or 0) + \
        int(border.get("background_leak_count", 0) or 0)
    tiny = int(quality.get("small_regions_remaining", 0) or 0)
    return {
        "profile": profile_name,
        "leak_count": leaks,
        "region_count": len(regions),
        "palette_count": int(palette_doc.get("count", len(palette))),
        "tiny_region_count": tiny,
        "rendered_deltaE": rendered_de,          # as-painted fidelity (lower = better)
        "meanfill_deltaE": meanfill_de,          # per-region floor
        "flatness_score": meanfill_de,           # higher = flatter / more lost detail
        "protected_highlight_loss": _highlight_loss(folder, regions),
        "visual_score": int(quality.get("score", 0) or 0),
    }


def classify(m: dict) -> tuple[str, str]:
    """Return (verdict, direction). direction ∈ {"up","down","none"} — which way to
    move along PROFILE_ORDER to improve. Priority: leaks > too-complex > too-flat >
    too-simple > ok."""
    if m["leak_count"] > 0:
        # Leaks come from unclosed line art; a simpler profile closes gaps harder.
        return "LEAKS", "down"
    if m["palette_count"] > PALETTE_MAX or m["region_count"] > REGION_MAX \
            or m["tiny_region_count"] > TINY_MAX:
        return "TOO_COMPLEX", "down"
    de = m["rendered_deltaE"]
    if de is not None and de > FAITHFUL_DELTAE and m["palette_count"] < PALETTE_MAX:
        return "TOO_FLAT", "up"          # under-detailed, and room to add colour
    if m["palette_count"] < PALETTE_MIN:
        return "TOO_SIMPLE", "up"
    return "OK", "none"


def next_profile(current: str, direction: str, tried: set) -> str | None:
    """Step one profile in `direction`, skipping any already attempted."""
    if direction == "none" or current not in PROFILE_ORDER:
        return None
    i = PROFILE_ORDER.index(current)
    step = 1 if direction == "up" else -1
    j = i + step
    while 0 <= j < len(PROFILE_ORDER):
        if PROFILE_ORDER[j] not in tried:
            return PROFILE_ORDER[j]
        j += step
    return None


def score(m: dict) -> float:
    """Single 'is this a good color-by-number' score used to pick the winner.
    Balances fidelity (rendered ΔE) against usable palette/region size and
    cleanliness. Leaks are disqualifying."""
    if m["leak_count"] > 0:
        return -1000.0 - m["leak_count"]
    s = 100.0
    de = m["rendered_deltaE"]
    if de is not None:
        s -= de * 3.0                                   # fidelity is the main driver
    if m["palette_count"] > PALETTE_MAX:
        s -= (m["palette_count"] - PALETTE_MAX) * 2.0   # too many colours
    elif m["palette_count"] < PALETTE_MIN:
        s -= (PALETTE_MIN - m["palette_count"]) * 2.0   # too few colours
    if m["region_count"] > REGION_MAX:
        s -= (m["region_count"] - REGION_MAX) * 0.3
    s -= max(0, m["tiny_region_count"] - 3) * 1.5       # clutter beyond baseline
    s -= m["protected_highlight_loss"] * 5.0
    s += (m["visual_score"] - 99) * 1.0
    return round(s, 3)


def rank(assessments: list[dict]) -> list[dict]:
    """Assessments sorted best-first, each annotated with its score."""
    scored = [dict(a, score=score(a)) for a in assessments]
    return sorted(scored, key=lambda a: -a["score"])
