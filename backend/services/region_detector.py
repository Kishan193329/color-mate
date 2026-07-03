"""Stage 4 — Detecting regions (Phase 5: profile-driven + tiny-region merge).

Regions are the closed white areas enclosed by the (gap-repaired) line art.
Connected components label them; `tiny_region_merger` then merges or drops
sub-floor regions. Geometry (area / bbox / centroid / mean color) is recomputed
vectorized after the merge so merged regions report correct stats.

No color quantization, no recoloring — colors are sampled from the original
working image inside each final region.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from config import DEFAULT_DIFFICULTY, DIFFICULTY_PROFILES
from services import tiny_region_merger

LINE_COLOR_RGB = (40, 40, 40)


def run(closed_lineart_path: Path, preprocessed_path: Path, profile: dict | None = None) -> dict:
    profile = profile or DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY]
    gray = cv2.imread(str(closed_lineart_path), cv2.IMREAD_GRAYSCALE)
    color_bgr = cv2.imread(str(preprocessed_path), cv2.IMREAD_COLOR)
    height, width = gray.shape[:2]

    _, bw = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    num_labels, labels, stats, centroids = cv2.connectedComponentsWithStats(
        bw, connectivity=4
    )

    keep_area = max(
        int(profile["min_area_abs"]),
        int(profile["min_area_frac"] * height * width),
    )

    # Merge / drop sub-floor components.
    remap, kept, merged_count, removed_count = tiny_region_merger.plan_merge(
        labels, num_labels, stats, keep_area, width, height
    )
    labels = remap[labels]

    # --- vectorized geometry recompute (handles merged areas) ---------------
    areas_orig = stats[:, cv2.CC_STAT_AREA].astype(np.int64)
    left = stats[:, cv2.CC_STAT_LEFT].astype(np.int64)
    top = stats[:, cv2.CC_STAT_TOP].astype(np.int64)
    right = left + stats[:, cv2.CC_STAT_WIDTH].astype(np.int64) - 1
    bottom = top + stats[:, cv2.CC_STAT_HEIGHT].astype(np.int64) - 1

    flat = labels.ravel()
    counts = np.bincount(flat, minlength=num_labels)
    sum_b = np.bincount(flat, weights=color_bgr[:, :, 0].astype(np.float64).ravel(), minlength=num_labels)
    sum_g = np.bincount(flat, weights=color_bgr[:, :, 1].astype(np.float64).ravel(), minlength=num_labels)
    sum_r = np.bincount(flat, weights=color_bgr[:, :, 2].astype(np.float64).ravel(), minlength=num_labels)

    # Bounding box + centroid accumulated from each label's members.
    bb = {i: [left[i], top[i], right[i], bottom[i]] for i in kept}
    acc_x = {i: areas_orig[i] * centroids[i][0] for i in kept}
    acc_y = {i: areas_orig[i] * centroids[i][1] for i in kept}
    acc_a = {i: float(areas_orig[i]) for i in kept}
    for i in range(1, num_labels):
        p = int(remap[i])
        if p == i or p == 0:
            continue
        box = bb[p]
        box[0] = min(box[0], left[i]); box[1] = min(box[1], top[i])
        box[2] = max(box[2], right[i]); box[3] = max(box[3], bottom[i])
        acc_x[p] += areas_orig[i] * centroids[i][0]
        acc_y[p] += areas_orig[i] * centroids[i][1]
        acc_a[p] += float(areas_orig[i])

    lut = np.zeros((num_labels, 3), dtype=np.uint8)  # RGB
    lut[0] = LINE_COLOR_RGB

    order = sorted(kept, key=lambda i: (bb[i][1], bb[i][0]))
    regions: list[dict] = []
    for number, i in enumerate(order, start=1):
        area = int(counts[i])
        if area == 0:
            continue
        r = int(round(sum_r[i] / area))
        g = int(round(sum_g[i] / area))
        b = int(round(sum_b[i] / area))
        lut[i] = (r, g, b)
        x0, y0, x1, y1 = bb[i]
        cx = int(round(acc_x[i] / acc_a[i]))
        cy = int(round(acc_y[i] / acc_a[i]))
        regions.append(
            {
                "id": number - 1,
                "label": int(i),
                "x": int(x0), "y": int(y0),
                "w": int(x1 - x0 + 1), "h": int(y1 - y0 + 1),
                "cx": cx, "cy": cy,
                "area": area,
                "color": "#{:02X}{:02X}{:02X}".format(r, g, b),
                "number": number,
            }
        )

    return {
        "version": 1,
        "width": int(width),
        "height": int(height),
        "regions": regions,
        "palette": [],
        "small_regions_removed": int(removed_count),
        "tiny_regions_merged": int(merged_count),
        # In-memory helpers (not serialized):
        "_label_map": labels,
        "_color_lut": lut,
        "_preprocessed_bgr": color_bgr,
        "_profile": profile,
    }


def build_from_labels(
    labels: np.ndarray, color_bgr: np.ndarray, profile: dict | None = None
) -> dict:
    """Rebuild region_data from an EDITED label map (Phase 10 region tools).

    Unlike `run`, this does not detect/merge — it takes the labels as given
    (after a manual merge/split/delete) and recomputes geometry + colors. Label
    0 is the line/background; any positive label is a region.
    """
    profile = profile or DIFFICULTY_PROFILES[DEFAULT_DIFFICULTY]
    labels = labels.astype(np.int32)
    height, width = labels.shape
    num = int(labels.max()) + 1 if labels.size else 1

    flat = labels.ravel()
    counts = np.bincount(flat, minlength=num)
    ys, xs = np.indices((height, width))
    fx, fy = xs.ravel().astype(np.int64), ys.ravel().astype(np.int64)
    sum_x = np.bincount(flat, weights=fx, minlength=num)
    sum_y = np.bincount(flat, weights=fy, minlength=num)
    sum_b = np.bincount(flat, weights=color_bgr[:, :, 0].astype(np.float64).ravel(), minlength=num)
    sum_g = np.bincount(flat, weights=color_bgr[:, :, 1].astype(np.float64).ravel(), minlength=num)
    sum_r = np.bincount(flat, weights=color_bgr[:, :, 2].astype(np.float64).ravel(), minlength=num)

    min_x = np.full(num, width, np.int64); max_x = np.full(num, -1, np.int64)
    min_y = np.full(num, height, np.int64); max_y = np.full(num, -1, np.int64)
    np.minimum.at(min_x, flat, fx); np.maximum.at(max_x, flat, fx)
    np.minimum.at(min_y, flat, fy); np.maximum.at(max_y, flat, fy)

    lut = np.zeros((num, 3), np.uint8)
    lut[0] = LINE_COLOR_RGB
    kept = [i for i in range(1, num) if counts[i] > 0]
    kept.sort(key=lambda i: (min_y[i], min_x[i]))

    regions: list[dict] = []
    for number, i in enumerate(kept, start=1):
        area = int(counts[i])
        r = int(round(sum_r[i] / area)); g = int(round(sum_g[i] / area)); b = int(round(sum_b[i] / area))
        lut[i] = (r, g, b)
        x0, y0 = int(min_x[i]), int(min_y[i])
        regions.append(
            {
                "id": number - 1, "label": int(i),
                "x": x0, "y": y0,
                "w": int(max_x[i] - min_x[i] + 1), "h": int(max_y[i] - min_y[i] + 1),
                "cx": int(round(sum_x[i] / area)), "cy": int(round(sum_y[i] / area)),
                "area": area,
                "color": "#{:02X}{:02X}{:02X}".format(r, g, b),
                "number": number,
            }
        )

    return {
        "version": 1, "width": int(width), "height": int(height),
        "regions": regions, "palette": [],
        "small_regions_removed": 0, "tiny_regions_merged": 0,
        "_label_map": labels, "_color_lut": lut,
        "_preprocessed_bgr": color_bgr, "_profile": profile,
    }
