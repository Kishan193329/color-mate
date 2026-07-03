"""Phase 7 — semantic rules (heuristics).

Pure, dependency-light helpers that turn a region's sampled color + geometry
into a semantic label, a region type, and the merge/group rules. This is a
*helper layer*: the line-art closed regions remain the source of truth — these
rules only annotate and group them.
"""
from __future__ import annotations

import colorsys

# Semantic labels a region can receive.
SKIN = "skin"
HAIR = "hair"
FUR = "fur"
CLOTHING = "clothing"
BACKGROUND = "background"
DECORATION = "decoration"
FLOWER = "flower"
DETAIL = "detail"

# Region roles within a part.
RT_MAIN = "main"
RT_SHADOW = "shadow"
RT_HIGHLIGHT = "highlight"
RT_DETAIL = "detail"
RT_BACKGROUND = "background"

# Maps a fine label to the coarse filter category used by the UI.
FILTER_CATEGORY = {
    SKIN: "Skin",
    HAIR: "Hair",
    FUR: "Hair",
    CLOTHING: "Clothing",
    BACKGROUND: "Background",
    DECORATION: "Detail",
    FLOWER: "Detail",
    DETAIL: "Detail",
}


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = (value or "#808080").lstrip("#")
    if len(value) != 6:
        return (128, 128, 128)
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def get_rgb(region: dict) -> tuple[int, int, int]:
    if "_rgb" in region:
        r, g, b = region["_rgb"]
        return (int(r), int(g), int(b))
    return hex_to_rgb(region.get("sampled_color") or region.get("color"))


def get_bbox(region: dict) -> tuple[int, int, int, int]:
    if "bbox" in region and region["bbox"]:
        x, y, w, h = region["bbox"]
    else:
        x, y, w, h = region["x"], region["y"], region["w"], region["h"]
    return int(x), int(y), int(w), int(h)


def luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = rgb
    return 0.299 * r + 0.587 * g + 0.114 * b


def _hsv(rgb: tuple[int, int, int]) -> tuple[float, float, float]:
    r, g, b = (c / 255.0 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return h * 360.0, s * 255.0, v * 255.0


def is_skin(rgb: tuple[int, int, int]) -> bool:
    """YCrCb skin-tone test plus a basic channel-ordering sanity check."""
    r, g, b = rgb
    y = 0.299 * r + 0.587 * g + 0.114 * b
    cr = (r - y) * 0.713 + 128
    cb = (b - y) * 0.564 + 128
    return 135 <= cr <= 180 and 85 <= cb <= 135 and y >= 80 and r >= g >= b - 10


def classify_label(
    rgb: tuple[int, int, int],
    area: int,
    bbox: tuple[int, int, int, int],
    width: int,
    height: int,
) -> str:
    x, y, w, h = bbox
    _, s, v = _hsv(rgb)
    frac = area / float(max(1, width * height))
    touches_border = x <= 2 or y <= 2 or (x + w) >= width - 2 or (y + h) >= height - 2

    # Big uniform areas (or border-touching low-saturation areas) = background.
    if frac >= 0.18 or (touches_border and s < 60 and frac >= 0.04):
        return BACKGROUND
    if is_skin(rgb):
        return SKIN
    if luminance(rgb) < 70 and v < 110:  # dark mass -> hair / fur strokes
        return HAIR
    if s > 120 and (v > 90) and frac < 0.05:  # vivid small/medium -> flower/decor
        return FLOWER if s > 150 else DECORATION
    if frac >= 0.02 and s > 45:  # medium saturated mass -> clothing
        return CLOTHING
    if frac < 0.006:
        return DECORATION
    return DETAIL


def region_type(
    label: str,
    rgb: tuple[int, int, int],
    group_median_lum: float,
    area: int,
    group_median_area: float,
) -> str:
    if label == BACKGROUND:
        return RT_BACKGROUND
    if area < max(1.0, group_median_area * 0.25):
        return RT_DETAIL
    lum = luminance(rgb)
    if lum < group_median_lum - 25:
        return RT_SHADOW
    if lum > group_median_lum + 25:
        return RT_HIGHLIGHT
    return RT_MAIN
