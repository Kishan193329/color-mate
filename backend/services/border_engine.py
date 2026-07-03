"""Perfect Border & Shape Engine (PBSE) — v1.

Implements the small, practical v1 subset of the PBSE architecture
(`research/06_algorithms/PERFECT_BORDER_ENGINE_ARCHITECTURE.md`):

    S1 Field extraction   — edge confidence, local contrast, edge density, endpoints
    S3/S5 Validation      — endpoints, fragments, suspected gaps, leak risk
    S5 Gap repair upgrade — bridge 1-2px gaps via endpoint pairing (no false merge)
    S6/S7 Shape refine    — reduce stair-step jaggedness, smooth curves, keep corners
    S8 Validation report  — border_quality_report.json fields

Operates ONLY on the border/line-art layer. It does not touch palette, numbers,
color, or regions — those are produced downstream from the refined line art.
Line art remains the source of truth (Rulebook I2).
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from services import region_leak_validator

# Bridge only genuinely small near-miss gaps. A 1-2px source gap shows up as a
# slightly larger endpoint separation once strokes are thinned (thinning recedes
# ragged stub ends), so the safe catch radius is a few px. Kept small so we never
# fuse two intended regions (endpoint bridging, not area dilation).
GAP_RADIUS = 6.0
BRIDGE_THICKNESS = 2

_NEIGH8 = np.array([[1, 1, 1], [1, 0, 1], [1, 1, 1]], np.uint8)


# --------------------------------------------------------------------------- #
# primitives
# --------------------------------------------------------------------------- #
def _to_lines(gray: np.ndarray) -> np.ndarray:
    """Return a binary {0,1} stroke map (1 = ink) from a black-on-white line art."""
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    return (cv2.bitwise_not(bw) > 0).astype(np.uint8)


def _fragments(binary: np.ndarray) -> int:
    n, _ = cv2.connectedComponents(binary, connectivity=8)
    return max(0, n - 1)


def _thin(binary: np.ndarray) -> np.ndarray:
    """Zhang-Suen thinning to a 1px skeleton (for endpoint/topology analysis only;
    the exported line art keeps its original thickness)."""
    img = (binary > 0).astype(np.uint8)
    img[0, :] = img[-1, :] = img[:, 0] = img[:, -1] = 0  # avoid roll wrap artifacts
    changed, guard = True, 0
    while changed and guard < 60:
        guard += 1
        changed = False
        for step in (0, 1):
            N = np.roll(img, 1, 0); S = np.roll(img, -1, 0)
            E = np.roll(img, -1, 1); W = np.roll(img, 1, 1)
            NE = np.roll(N, -1, 1); SE = np.roll(S, -1, 1)
            SW = np.roll(S, 1, 1); NW = np.roll(N, 1, 1)
            P = [N, NE, E, SE, S, SW, W, NW]  # P2..P9 clockwise
            B = sum(P)
            seq = P + [N]
            A = sum(((seq[k] == 0) & (seq[k + 1] == 1)).astype(np.uint8) for k in range(8))
            cond = (img == 1) & (B >= 2) & (B <= 6) & (A == 1)
            if step == 0:
                cond &= (N * E * S == 0) & (E * S * W == 0)
            else:
                cond &= (N * E * W == 0) & (N * S * W == 0)
            if cond.any():
                img[cond] = 0
                changed = True
    return img


def _endpoints(skel: np.ndarray) -> np.ndarray:
    """Endpoint coordinates (y,x) — skeleton pixels with exactly one 8-neighbour."""
    neigh = cv2.filter2D(skel, -1, _NEIGH8, borderType=cv2.BORDER_CONSTANT)
    ys, xs = np.where((skel == 1) & (neigh == 1))
    return np.stack([ys, xs], axis=1) if len(ys) else np.empty((0, 2), int)


def _gap_pairs(coords: np.ndarray, radius: float) -> list[tuple[int, int, float]]:
    """Endpoint pairs within `radius` (grid-bucketed). These are bridgeable gaps."""
    pairs: list[tuple[int, int, float]] = []
    if len(coords) < 2:
        return pairs
    cell = max(1, int(radius))
    buckets: dict[tuple[int, int], list[int]] = {}
    for idx, (y, x) in enumerate(coords):
        buckets.setdefault((x // cell, y // cell), []).append(idx)
    for idx, (y, x) in enumerate(coords):
        gx, gy = x // cell, y // cell
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for jdx in buckets.get((gx + dx, gy + dy), []):
                    if jdx <= idx:
                        continue
                    yy, xx = coords[jdx]
                    d = float(np.hypot(x - xx, y - yy))
                    if 0 < d <= radius:
                        pairs.append((idx, jdx, d))
    return pairs


# Jaggedness is measured on STRUCTURAL contours only (silhouette / large shape
# edges). Fine fur/texture contours are meant to stay detailed, so counting them
# would swamp the metric — BORD-021 is about smoothing structural stair-steps.
STRUCTURAL_MIN_LEN = 60


def _jaggedness(binary: np.ndarray) -> float:
    """Mean pixel-scale turning angle along STRUCTURAL stroke contours, in [0,1].
    Stair-steps produce many ~90 deg turns -> high score; smooth curves -> low."""
    contours, _ = cv2.findContours(binary, cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
    total_angle, total_pts = 0.0, 0
    for c in contours:
        p = c.reshape(-1, 2).astype(np.float64)
        if len(p) < STRUCTURAL_MIN_LEN:
            continue
        v = np.roll(p, -1, axis=0) - p
        n = np.linalg.norm(v, axis=1)
        n[n == 0] = 1.0
        v /= n[:, None]
        dots = np.clip((v * np.roll(v, -1, axis=0)).sum(axis=1), -1.0, 1.0)
        angles = np.arccos(dots)
        total_angle += float(angles.sum())
        total_pts += len(p)
    if total_pts == 0:
        return 0.0
    return round((total_angle / total_pts) / np.pi, 4)


def _refine_shape(gray: np.ndarray) -> np.ndarray:
    """Sub-pixel anti-alias: supersample -> blur -> threshold -> downsample, then a
    1px morphological close so resampling can never reopen a stroke (connectivity-
    safe, per architecture S6 'must not reopen a closed face'). Small sigma keeps
    real high-amplitude corners intact."""
    h, w = gray.shape
    up = cv2.resize(gray, (w * 2, h * 2), interpolation=cv2.INTER_LINEAR)
    up = cv2.GaussianBlur(up, (3, 3), 1.0)
    _, up = cv2.threshold(up, 127, 255, cv2.THRESH_BINARY)
    down = cv2.resize(up, (w, h), interpolation=cv2.INTER_AREA)
    _, out = cv2.threshold(down, 127, 255, cv2.THRESH_BINARY)
    # Heal any hairline breaks introduced by resampling (operate on the ink layer).
    ink = cv2.bitwise_not(out)
    ink = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    return cv2.bitwise_not(ink)


def _image_fields(preprocessed_path: Path | None, strokes: np.ndarray) -> tuple[float, float]:
    """Edge confidence (mean gradient magnitude under strokes, 0..1) and mean
    local contrast of the working image. Returns (edge_confidence, local_contrast)."""
    if preprocessed_path is None or not Path(preprocessed_path).exists():
        return 0.0, 0.0
    img = cv2.imread(str(preprocessed_path), cv2.IMREAD_GRAYSCALE).astype(np.float32)
    gx = cv2.Sobel(img, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(img, cv2.CV_32F, 0, 1, ksize=3)
    mag = np.sqrt(gx * gx + gy * gy)
    mag /= (mag.max() + 1e-6)
    mask = strokes > 0
    edge_conf = float(mag[mask].mean()) if mask.any() else 0.0
    mean = cv2.boxFilter(img, -1, (7, 7))
    sq = cv2.boxFilter(img * img, -1, (7, 7))
    local_std = np.sqrt(np.maximum(sq - mean * mean, 0.0))
    local_contrast = float(local_std.mean() / 255.0)
    return round(edge_conf, 4), round(local_contrast, 4)


# --------------------------------------------------------------------------- #
# public API
# --------------------------------------------------------------------------- #
def analyze(gray: np.ndarray) -> dict:
    """Border-graph validation metrics for a black-on-white line art."""
    strokes = _to_lines(gray)
    skel = _thin(strokes)
    coords = _endpoints(skel)
    gaps = _gap_pairs(coords, GAP_RADIUS)
    return {
        "endpoints": int(len(coords)),
        "fragments": _fragments(strokes),
        "suspected_gaps": len(gaps),
        "jaggedness": _jaggedness(strokes),
        "_coords": coords,
        "_gaps": gaps,
        "_strokes": strokes,
    }


def run(lineart_path: Path, out_path: Path, preprocessed_path: Path | None = None,
        profile: dict | None = None, leak_map_path: Path | None = None) -> dict:
    """Full PBSE v1 pass. Reads the line art, validates, repairs 1-2px gaps,
    refines shape, writes the refined line art to `out_path`, and returns the
    border quality report (incl. Quality Fix 4 region-aware leak validation)."""
    gray = cv2.imread(str(lineart_path), cv2.IMREAD_GRAYSCALE)

    # --- S1 fields + BEFORE validation ---
    before = analyze(gray)
    edge_conf, local_contrast = _image_fields(preprocessed_path, before["_strokes"])
    edge_density = float(before["_strokes"].mean())

    # --- S5 safe gap repair (endpoint bridging, each endpoint used once) ---
    art = gray.copy()
    coords = before["_coords"]
    used: set[int] = set()
    repaired = 0
    for i, j, _d in sorted(before["_gaps"], key=lambda t: t[2]):
        if i in used or j in used:
            continue
        (y1, x1), (y2, x2) = coords[i], coords[j]
        cv2.line(art, (int(x1), int(y1)), (int(x2), int(y2)), 0, BRIDGE_THICKNESS)
        used.add(i); used.add(j); repaired += 1

    # --- S6/S7 shape refinement ---
    refined = _refine_shape(art)
    cv2.imwrite(str(out_path), refined)

    # --- S8 AFTER validation ---
    after = analyze(refined)
    suspected_leaks = after["suspected_gaps"]  # proximity signal (kept for context)

    # Quality Fix 4 — region-aware leak validation (flood-fill / neck test),
    # comparing the line art before vs after border repair.
    leak = region_leak_validator.validate(gray, refined, leak_map_path)

    # Closure/shape are "improved and safe"; leak is now the region-aware verdict.
    closure_pass = bool(repaired > 0 and after["endpoints"] <= before["endpoints"])
    leak_pass = bool(leak["leak_pass"])
    shape_pass = bool(after["jaggedness"] <= before["jaggedness"] + 1e-6)
    final_pass = bool(closure_pass and leak_pass and shape_pass)

    return {
        "version": 2,
        "fields": {
            "edge_confidence": edge_conf,
            "local_contrast": local_contrast,
            "edge_density": round(edge_density, 5),
            "endpoints": before["endpoints"],
        },
        "endpoints_before": before["endpoints"],
        "endpoints_after": after["endpoints"],
        "fragments_before": before["fragments"],
        "fragments_after": after["fragments"],
        "suspected_gaps": before["suspected_gaps"],
        "repaired_gaps": int(repaired),
        "suspected_leaks": int(suspected_leaks),
        "jaggedness_score_before": before["jaggedness"],
        "jaggedness_score_after": after["jaggedness"],
        # Quality Fix 4 — region-aware leak fields
        "region_leak_count": leak["region_leak_count"],
        "background_leak_count": leak["background_leak_count"],
        "leaking_region_ids": leak["leaking_region_ids"],
        "leak_pass_reason": leak["leak_pass_reason"],
        "leaks_fixed_by_repair": leak["leaks_fixed_by_repair"],
        "closed_areas": leak["closed_areas_after"],
        "closure_pass": bool(closure_pass),
        "leak_pass": bool(leak_pass),
        "shape_pass": bool(shape_pass),
        "final_pass": final_pass,
    }
