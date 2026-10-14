"""Invariants of the colour-fill engine.

Every check here exists because the matching bug shipped once. They are deliberately fast and
synthetic — no model, no torch, no fixture image — so there is no excuse not to run them:

    python tests/test_engine.py

The one thing they cannot cover is whether a picture LOOKS right; that still needs eyes.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services import lineart_region_builder as L          # noqa: E402
from services import reveal_game_builder as R             # noqa: E402


# ── the app's own SVG reader, in miniature ────────────────────────────────────
# A faithful port of BorderVector.appendPath: M/L/Q plus their relative forms and Z. If the
# exporter ever emits something else, this fails here rather than on a player's phone.
def parse_ink_svg(svg: str):
    vb = re.search(r'viewBox="\s*([-\d.]+)\s+([-\d.]+)\s+([\d.]+)\s+([\d.]+)', svg)
    assert vb, "no viewBox"
    assert 'fill-rule="evenodd"' in svg and 'fill="#000"' in svg, \
        "the app rejects any border.svg without this signature (§8.5bf)"
    m = re.search(r'<path[^>]*\bd="([^"]*)"', svg)
    if not m:
        return float(vb.group(3)), float(vb.group(4)), []
    d = m.group(1)
    i, n = 0, len(d)
    pen = start = (0.0, 0.0)
    polys, cur = [], []

    def num(i):
        while i < n and d[i] in " ,":
            i += 1
        s = i
        while i < n and (d[i].isdigit() or d[i] in "-+.eE"):
            i += 1
        assert i > s, "bad number at %d" % i
        return float(d[s:i]), i

    while i < n:
        c = d[i]
        if c in " ,\n\r\t":
            i += 1
            continue
        if c in "Zz":
            if cur:
                cur.append(start)
                polys.append(cur)
                cur = []
            pen = start
            i += 1
            continue
        u = c.upper()
        assert u in "MLQ", "the app cannot parse %r" % c
        rel = c != u
        i += 1
        x, i = num(i)
        y, i = num(i)
        ax, ay = (pen[0] + x, pen[1] + y) if rel else (x, y)
        if u == "Q":
            x2, i = num(i)
            y2, i = num(i)
            ex, ey = (pen[0] + x2, pen[1] + y2) if rel else (x2, y2)
            p0 = pen
            for t in np.linspace(0, 1, 9)[1:]:
                a, b, cc = (1 - t) ** 2, 2 * (1 - t) * t, t * t
                cur.append((a * p0[0] + b * ax + cc * ex, a * p0[1] + b * ay + cc * ey))
            pen = (ex, ey)
        elif u == "M":
            if cur:
                polys.append(cur)
            cur = [(ax, ay)]
            pen = start = (ax, ay)
        else:
            cur.append((ax, ay))
            pen = (ax, ay)
    if cur:
        polys.append(cur)
    return float(vb.group(3)), float(vb.group(4)), polys


def svg_raster(svg: str, w: int, h: int, ss: int = 4) -> np.ndarray:
    vw, vh, polys = parse_ink_svg(svg)
    big = np.zeros((h * ss, w * ss), np.uint8)
    k = w * ss / vw
    if polys:
        cv2.fillPoly(big, [np.rint(np.array(p) * k).astype(np.int32)
                           for p in polys if len(p) > 2], 255)
    return cv2.resize(big, (w, h), interpolation=cv2.INTER_AREA)


# ── checks ────────────────────────────────────────────────────────────────────
def test_id_map_round_trip():
    """id = R + G*256, and cv2 arrays are BGRA — reading the wrong channel decoded every id as a
    multiple of 256, so almost no region matched and fills came out transparent (§8.5an)."""
    rng = np.random.default_rng(7)
    fl = rng.integers(1, 900, size=(64, 48)).astype(np.int32)
    bgra = R.id_map_bgra(fl)
    back = bgra[:, :, 2].astype(np.int32) + bgra[:, :, 1].astype(np.int32) * 256
    assert np.array_equal(back, fl), "id_map does not decode back to the label map"


def test_region_meta_bbox_is_xywh():
    """regions.json bbox is [x, y, w, h]. Reading it as [l, t, r, b] made `right < left` for 421
    of 511 regions, so the paint loop never ran and nothing was revealed (§8.5q)."""
    fl = np.ones((40, 30), np.int32)
    fl[10:25, 6:20] = 2
    meta = L.region_meta(fl, {1: 1, 2: 2})
    by = {m["id"]: m for m in meta}
    assert tuple(by[2]["bb"]) == (6, 10, 14, 15), by[2]["bb"]
    for m in meta:                                   # the label must sit inside its own box
        x, y, w, h = m["bb"]
        assert x <= m["x"] < x + w and y <= m["y"] < y + h, m


def test_palette_is_contiguous_and_dark_first():
    """Numbers were the k-means cluster index, so a 25-colour picture came out numbered
    5, 8, 13, 30, 85… — which reads as "numbers are missing" (§8.5ax)."""
    numbers = {1: 40, 2: 7, 3: 91}
    colors = {40: [250, 250, 250], 7: [10, 10, 10], 91: [128, 128, 128]}
    numbers, colors = R._compact_palette(numbers, colors)
    assert sorted(colors) == [1, 2, 3]
    assert colors[1] == [10, 10, 10] and colors[3] == [250, 250, 250]
    assert set(numbers.values()) <= set(colors), "a region points at a number with no colour"


def test_close_colours_merge_but_distinct_ones_do_not():
    """One flat area came back as three numbers (#19 rgb(254,254,254) AND #18 rgb(252,252,252)),
    which is what "the colour fills in patches" looks like from the inside (§8.5bk)."""
    numbers = {1: 1, 2: 2, 3: 3}
    colors = {1: [254, 254, 254], 2: [252, 252, 252], 3: [10, 90, 200]}
    numbers, colors = R._merge_close_colours(numbers, colors)
    assert len(colors) == 2, colors
    assert numbers[1] == numbers[2] and numbers[1] != numbers[3]


def test_touching_same_number_regions_join():
    """Even with one number, a flat area stayed split into separately-tappable pieces (§8.5bk)."""
    fl = np.zeros((20, 20), np.int32)
    fl[:, :10] = 1
    fl[:, 10:] = 2
    fl2, numbers = R._merge_same_number_neighbours(fl, {1: 5, 2: 5})
    assert len(set(numbers.values())) == 1
    assert len(np.unique(fl2)) == 1, "touching regions of one colour did not become one region"


def test_border_raster_and_vector_are_the_same_drawing():
    """The raster used to be the model's raw ink and the vector a smoothed trace of it, so the
    picture visibly changed as the player zoomed past the switch-over (§8.5bp)."""
    a = np.zeros((120, 120), np.uint8)
    cv2.circle(a, (60, 60), 34, 255, 5)
    cv2.line(a, (10, 110), (110, 10), 255, 4)
    polys = R._ink_contours(a)
    assert polys, "nothing traced"
    h, w = a.shape
    raster = R._raster_from_contours(polys, h, w)[..., 3]
    svg = R._svg_from_contours(polys, h, w)
    A, B = raster >= 128, svg_raster(svg, w, h) >= 128
    iou = (A & B).sum() / max(1, (A | B).sum())
    assert iou > 0.97, "raster and vector disagree (IoU %.3f)" % iou
    assert "q" in svg, "the compact relative form was not emitted"


def test_shredded_region_is_retired_and_palette_stays_whole():
    """A region the ink leaves as crumbs cannot be seen, found or tapped; leaving it in the
    palette is only a way to lose (§8.5bo)."""
    # Kept in proportion on purpose: INK_SPECK_BUDGET caps this pass at 2% of the canvas, so a
    # sliver has to be small RELATIVE to the picture, exactly as it is on a real one.
    fl = np.ones((200, 200), np.int32)
    fl[40:43, 20:60] = 2                                   # a thin sliver (120 px, 0.3%)...
    border = np.zeros((200, 200, 4), np.uint8)
    border[38:45, 20:60, 3] = 255                          # ...that the ink swallows whole
    _, numbers, colors, out = R._specks_into_ink(
        fl, {1: 1, 2: 2}, {1: [10, 10, 10], 2: [200, 30, 30]}, border)
    assert 2 not in numbers, "a fully-inked region is still playable"
    assert sorted(colors) == list(range(1, len(colors) + 1)), "palette left with a hole"
    assert set(numbers.values()) <= set(colors)
    assert out[41, 40, 3] == 255


def test_ink_budget_protects_a_shattered_picture():
    """Past INK_SPECK_BUDGET the shattering is a content problem, not something the border should
    paper over — inking it all would black out an eighth of the artwork (§8.5bo)."""
    fl = np.ones((200, 200), np.int32)
    fl[20:120, 20:120] = 2                                 # a QUARTER of the canvas...
    border = np.zeros((200, 200, 4), np.uint8)
    border[18:122, 18:122, 3] = 255                        # ...hidden under ink
    _, numbers, _, _ = R._specks_into_ink(
        fl, {1: 1, 2: 2}, {1: [10, 10, 10], 2: [200, 30, 30]}, border)
    assert 2 in numbers, "the budget let a quarter of the picture be turned into line"


def test_hairline_only_draws_edges_you_could_see():
    """Drawing every undrawn boundary is the contour-map look; below HAIRLINE_MIN_DE the two
    fills read as one wash and a line there is only more ink (§8.5bp)."""
    fl = np.ones((80, 80), np.int32)
    fl[:, 40:] = 2
    blank = np.zeros((80, 80, 4), np.uint8)
    far = R._hairline_missing_edges(blank, fl, {1: 1, 2: 2},
                                    {1: [0, 0, 0], 2: [255, 255, 255]})
    near = R._hairline_missing_edges(blank, fl, {1: 1, 2: 2},
                                     {1: [128, 128, 128], 2: [132, 132, 132]})
    assert far[..., 3].max() == 255, "a black/white edge was left undrawn"
    assert near[..., 3].max() == 0, "two near-identical fills were given a line"


def test_region_boundaries_snap_under_the_ink():
    """The regions are cut from the raw line but the player sees the processed one; where the two
    disagreed the fill stopped short of the line and the neighbour showed through as a jagged
    fringe (§8.5bs). After the snap, every boundary near ink must sit ON the ink; a boundary
    with no ink near it must not move."""
    fl = np.ones((60, 120), np.int32)
    fl[:, 58:] = 2                                         # raw boundary at x=58...
    border = np.zeros((60, 120, 4), np.uint8)
    border[:, 60:65, 3] = 255                              # ...but the drawn line covers 60..64
    out = R._snap_regions_to_ink(fl, border)
    ink = border[..., 3] >= 128
    b = np.zeros(fl.shape, bool)
    b[:, :-1] |= out[:, :-1] != out[:, 1:]
    assert not (b & ~ink).any(), "a boundary near ink is still outside it"
    assert (out[:, :58] == 1).all() and (out[:, 66:] == 2).all(), "a fill leaked past the line"

    far = np.ones((60, 120), np.int32)
    far[:, 90:] = 2                                        # colour-meets-colour, no ink anywhere near
    out2 = R._snap_regions_to_ink(far, border)
    assert (out2[:, 85:] == far[:, 85:]).all(), "an un-inked boundary was moved"


def test_defaults_that_have_bitten_before():
    """New 2026 is ColorForge only. `number_merge` defaulting on meant a fresh database quietly
    blended the bought ionicsolutions catalogue back in (§8.5l)."""
    import number_tab
    assert number_tab._cfg()["number_merge"] is False


def main() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    bad = 0
    for t in tests:
        try:
            t()
            print("  ok    %s" % t.__name__)
        except Exception as e:                              # noqa: BLE001 — this IS the report
            bad += 1
            print("  FAIL  %s: %s" % (t.__name__, e))
    print("\n%d/%d passed" % (len(tests) - bad, len(tests)))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
