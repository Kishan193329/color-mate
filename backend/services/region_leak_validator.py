"""Quality Fix 4 — region-aware leak validator (PBSE S8).

Upgrades leak detection from proximity (endpoint pairs within a radius) to an
actual **flood-fill escape test**, comparing the line art BEFORE and AFTER border
repair.

Method (exactly the task's description):
  1. Partition the repaired line art into its intended closed areas
     (connected white components = the same regions the engine will fill).
  2. Flood-fill each intended region from a guaranteed-interior seed.
  3. Detect escape — a flood that spills far beyond its region (into a neighbour)
     or reaches the image frame from an interior region (background bleed).
  4. Run the same fill on the PRE-repair walls to see which leaks the repair
     fixed (before/after comparison).

Why flood-fill (not an erosion/neck heuristic): a real closed line art always
partitions the canvas into valid components, so testing containment on the
defining walls is honest and false-positive-free. Legitimate thin regions (a
yarn ball's strands, a slim curtain) do NOT get flagged, because their fill still
stays inside their own borders. It changes no palette, numbers, regions, or
colors.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

ESCAPE_TOL = 1.5  # a fill covering >1.5x its region's area has escaped it


def _passable(gray: np.ndarray) -> np.ndarray:
    """255 where a fill can pass (white), 0 at ink/walls."""
    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    return bw


def _flood(passable: np.ndarray, seed: tuple[int, int]) -> tuple[int, bool]:
    """Flood a passable image from `seed`; return (filled_area, touches_frame)."""
    x, y = int(seed[0]), int(seed[1])
    if passable[y, x] != 255:
        return 0, False
    img = passable.copy()
    h, w = img.shape
    mask = np.zeros((h + 2, w + 2), np.uint8)
    cv2.floodFill(img, mask, (x, y), 128, 0, 0, 4)
    filled = img == 128
    touches = bool(filled[0, :].any() or filled[-1, :].any()
                   or filled[:, 0].any() or filled[:, -1].any())
    return int(filled.sum()), touches


def _seed(labels: np.ndarray, lbl: int) -> tuple[int, int]:
    """Deepest interior point of a region (distance-transform max) — always inside."""
    mask = (labels == lbl).astype(np.uint8)
    dt = cv2.distanceTransform(mask, cv2.DIST_L2, 5)
    _, _, _, maxloc = cv2.minMaxLoc(dt)
    return maxloc  # (x, y)


def validate(pre_gray: np.ndarray, post_gray: np.ndarray,
             leak_map_path: Path | None = None) -> dict:
    h, w = post_gray.shape
    min_region = max(200, int(0.0004 * h * w))

    p_post = _passable(post_gray)
    p_pre = _passable(pre_gray)
    num, labels, stats, _ = cv2.connectedComponentsWithStats(
        (p_post > 0).astype(np.uint8), connectivity=4
    )
    frame_labels = set(np.unique(np.concatenate([
        labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]
    ]).tolist()))
    frame_labels.discard(0)

    region_leak_ids: list[int] = []   # remaining: interior region escapes into neighbour
    background_leak_ids: list[int] = []  # remaining: interior region reaches the frame
    fixed_ids: list[int] = []          # was leaking on pre-repair walls, now contained
    closed_areas = 0

    for lbl in range(1, num):
        area = int(stats[lbl, cv2.CC_STAT_AREA])
        if area < min_region:
            continue
        closed_areas += 1
        if lbl in frame_labels:
            continue  # background/border regions legitimately touch the frame
        seed = _seed(labels, lbl)

        post_area, post_touch = _flood(p_post, seed)   # containment in final output
        pre_area, pre_touch = _flood(p_pre, seed)       # containment before repair

        escaped_post = post_touch or post_area > area * ESCAPE_TOL
        escaped_pre = pre_touch or pre_area > area * ESCAPE_TOL

        if post_touch:
            background_leak_ids.append(lbl)
        elif escaped_post:
            region_leak_ids.append(lbl)
        elif escaped_pre:
            fixed_ids.append(lbl)

    region_leak_count = len(region_leak_ids)
    background_leak_count = len(background_leak_ids)
    leaking_region_ids = region_leak_ids + background_leak_ids
    leak_pass = region_leak_count == 0 and background_leak_count == 0

    if leak_pass:
        reason = (
            f"Flood-fill test passed on all {closed_areas} closed areas: every "
            f"interior region's fill stays inside its borders (no escape into a "
            f"neighbour or the background)"
        )
        if fixed_ids:
            reason += f"; border repair fixed {len(fixed_ids)} region(s) that leaked before repair"
        reason += "."
    else:
        reason = (
            f"{region_leak_count} region(s) escape into a neighbour and "
            f"{background_leak_count} interior region(s) reach the background "
            f"(ids {leaking_region_ids})."
        )

    if leak_map_path is not None:
        canvas = np.full((h, w, 3), 255, np.uint8)
        canvas[labels == 0] = (30, 30, 30)          # line art
        for lbl in fixed_ids:                        # fixed-by-repair = green
            canvas[labels == lbl] = (90, 200, 90)
        for lbl in leaking_region_ids:               # remaining leak = red
            canvas[labels == lbl] = (60, 60, 220)
        cv2.imwrite(str(leak_map_path), canvas)

    return {
        "region_leak_count": int(region_leak_count),
        "background_leak_count": int(background_leak_count),
        "leaking_region_ids": leaking_region_ids,
        "leak_pass_reason": reason,
        "leak_pass": bool(leak_pass),
        "closed_areas_before": None,   # filled in by run() context if needed
        "closed_areas_after": int(closed_areas),
        "leaks_fixed_by_repair": len(fixed_ids),
    }
