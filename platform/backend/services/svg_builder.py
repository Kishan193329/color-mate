"""Figma-vectorised SVG -> colour-fill game data, with NO AI pass.

A Figma "Vectorize" export is already exactly what the engine tries to reconstruct from a photo:
every shape is a closed, flat-coloured path, and the drawing's own black outlines are paths too.
So instead of line-art detection + colour clustering, this module reads the shapes directly:

  * dark paths (luminance < INK_LUM)          -> the black line art (pre-drawn, never numbered)
  * every other path                          -> one region, numbered by its exact colour
  * slivers smaller than MIN_REGION_PX        -> absorbed into their nearest neighbour

It writes the same intermediate files a photo upload ends with (`reveal_master.png`,
`original.png`) plus `svg_game.npz` / `svg_game.json`, and `game_data()` returns the exact tuple
`reveal_game_builder._game_data_color()` returns. That function checks for `svg_game.npz` first, so
build_color / export_color_bundle / the Number-tab export all work unchanged for SVG items.

Supported SVG: what Figma writes for a vectorised frame — <path d fill [fill-opacity]>, an
optional viewBox offset, and translate() on groups. Gradients, strokes and masks are ignored.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import cv2
import numpy as np

INK_LUM = 0.10          # Rec.601 luminance below which a path is treated as outline ink
MIN_REGION_PX = 24      # visible area (px) under which a region is merged into its neighbour
SS = 3                  # supersampling for the anti-aliased colour render and the ink alpha
MAX_SIDE = 1254         # same cap as photo uploads (app.MAX_SIDE)

_NUM = re.compile(r"-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_PATH = re.compile(r"<path\b([^>]*?)/?>", re.S)
_ATTR = re.compile(r'([\w:-]+)\s*=\s*"([^"]*)"')
_TAG = re.compile(r"<(/?)(g|svg)\b([^>]*)>", re.S)


def _lum(rgb) -> float:
    r, g, b = rgb
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def _hex(v: str):
    v = v.strip()
    if v.startswith("#") and len(v) == 7:
        return int(v[1:3], 16), int(v[3:5], 16), int(v[5:7], 16)
    if v.startswith("#") and len(v) == 4:
        return tuple(int(c * 2, 16) for c in v[1:4])
    m = re.match(r"rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)", v)
    if m:
        return tuple(int(x) for x in m.groups())
    return None


# ── path flattening (absolute + relative M/L/H/V/C/S/Q/T/Z; A approximated by its chord) ──
def _flatten(d: str):
    """Return a list of polylines (np.float32 Nx2) for one path's subpaths."""
    toks = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|" + _NUM.pattern, d)
    polys, cur, start = [], [], (0.0, 0.0)
    x = y = 0.0
    cmd = None
    lc = None                # last control point for S/T reflection
    i = 0

    def nums(k):
        nonlocal i
        out = [float(t) for t in toks[i:i + k]]
        i += k
        return out

    def bez(p0, ctrl, p3, cubic=True):
        pts = [p0] + ctrl + [p3]
        est = sum(np.hypot(pts[j + 1][0] - pts[j][0], pts[j + 1][1] - pts[j][1]) for j in range(len(pts) - 1))
        n = int(min(40, max(3, est / 1.5)))
        t = np.linspace(0, 1, n + 1)[1:, None]
        a = np.array(pts, np.float64)
        if cubic:
            c = ((1 - t) ** 3) * a[0] + 3 * ((1 - t) ** 2) * t * a[1] + 3 * (1 - t) * t * t * a[2] + t ** 3 * a[3]
        else:
            c = ((1 - t) ** 2) * a[0] + 2 * (1 - t) * t * a[1] + t * t * a[2]
        return [tuple(p) for p in c]

    while i < len(toks):
        t = toks[i]
        if re.fullmatch(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                if cur:
                    polys.append(cur)
                cur = []
                x, y = start
                lc = None
                continue
        if cmd is None:
            break
        rel = cmd.islower()
        C = cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if C == "M":
            a, b = nums(2)
            if cur:
                polys.append(cur)
            x, y = a + ox, b + oy
            start = (x, y)
            cur = [(x, y)]
            cmd = "l" if rel else "L"          # implicit lineto after the first pair
            lc = None
        elif C == "L":
            a, b = nums(2); x, y = a + ox, b + oy; cur.append((x, y)); lc = None
        elif C == "H":
            (a,) = nums(1); x = a + ox; cur.append((x, y)); lc = None
        elif C == "V":
            (b,) = nums(1); y = b + oy; cur.append((x, y)); lc = None
        elif C == "C":
            v = nums(6)
            p1 = (v[0] + ox, v[1] + oy); p2 = (v[2] + ox, v[3] + oy); p3 = (v[4] + ox, v[5] + oy)
            cur += bez((x, y), [p1, p2], p3); lc = p2; x, y = p3
        elif C == "S":
            v = nums(4)
            p1 = (2 * x - lc[0], 2 * y - lc[1]) if lc else (x, y)
            p2 = (v[0] + ox, v[1] + oy); p3 = (v[2] + ox, v[3] + oy)
            cur += bez((x, y), [p1, p2], p3); lc = p2; x, y = p3
        elif C == "Q":
            v = nums(4)
            p1 = (v[0] + ox, v[1] + oy); p3 = (v[2] + ox, v[3] + oy)
            cur += bez((x, y), [p1], p3, cubic=False); lc = p1; x, y = p3
        elif C == "T":
            v = nums(2)
            p1 = (2 * x - lc[0], 2 * y - lc[1]) if lc else (x, y)
            p3 = (v[0] + ox, v[1] + oy)
            cur += bez((x, y), [p1], p3, cubic=False); lc = p1; x, y = p3
        elif C == "A":
            v = nums(7); x, y = v[5] + ox, v[6] + oy; cur.append((x, y)); lc = None
        else:
            i += 1
    if cur:
        polys.append(cur)
    return [np.asarray(p, np.float32) for p in polys if len(p) >= 3]


def parse(svg_text: str):
    """-> (w, h, vb_x, vb_y, [(rgb, opacity, polys), ...]) in document order."""
    head = re.search(r"<svg\b([^>]*)>", svg_text, re.S)
    if not head:
        raise ValueError("not an SVG file")
    a = dict(_ATTR.findall(head.group(1)))
    vb = [float(v) for v in _NUM.findall(a.get("viewBox", ""))]
    if len(vb) == 4:
        vx, vy, w, h = vb
    else:
        vx = vy = 0.0
        w = float(_NUM.findall(a.get("width", "0"))[0]); h = float(_NUM.findall(a.get("height", "0"))[0])
    if w <= 0 or h <= 0:
        raise ValueError("SVG has no size")

    # walk tags to carry group translate() offsets onto paths
    shapes = []
    stack = [(0.0, 0.0)]
    defs = [(m.start(), m.end()) for m in re.finditer(r"<defs\b.*?</defs>", svg_text, re.S)]
    events = sorted([(m.start(), "tag", m) for m in _TAG.finditer(svg_text)] +
                    [(m.start(), "path", m) for m in _PATH.finditer(svg_text)], key=lambda e: e[0])
    for _, kind, m in events:
        if kind == "tag":
            closing, name, attrs = m.group(1), m.group(2), m.group(3)
            if name != "g":
                continue
            if closing:
                if len(stack) > 1:
                    stack.pop()
            else:
                tx, ty = stack[-1]
                tm = re.search(r"translate\(\s*(" + _NUM.pattern + r")[\s,]*(" + _NUM.pattern + r")?", attrs)
                if tm:
                    tx += float(tm.group(1)); ty += float(tm.group(2) or 0)
                if not attrs.rstrip().endswith("/"):
                    stack.append((tx, ty))
            continue
        pa = dict(_ATTR.findall(m.group(1)))
        if any(a <= m.start() < b for a, b in defs):
            continue                                        # clip/mask geometry, not artwork
        rgb = _hex(pa.get("fill", "#000000"))
        if rgb is None or pa.get("fill", "").lower() == "none":
            continue
        op = float(pa.get("fill-opacity", pa.get("opacity", "1")) or 1)
        polys = _flatten(pa.get("d", ""))
        if not polys:
            continue
        tx, ty = stack[-1]
        polys = [p + np.float32([tx - vx, ty - vy]) for p in polys]
        shapes.append((rgb, op, polys))
    return w, h, shapes


def _fill(canvas, polys, value, scale):
    pts = [np.round(p * scale * 16).astype(np.int32) for p in polys]
    cv2.fillPoly(canvas, pts, value, lineType=cv2.LINE_8, shift=4)


def prepare(folder: Path, src_svg: Path) -> dict:
    """Read the SVG and write reveal_master.png, original.png, svg_game.npz and svg_game.json."""
    folder = Path(folder)
    w0, h0, shapes = parse(Path(src_svg).read_text(encoding="utf-8", errors="ignore"))
    s = min(1.0, MAX_SIDE / max(w0, h0))
    W, H = int(round(w0 * s)), int(round(h0 * s))
    if not shapes:
        raise ValueError("no filled shapes found in the SVG")

    ink_flags = [_lum(rgb) < INK_LUM for rgb, _, _ in shapes]

    # 1. anti-aliased colour render (supersampled, painter's order, opacity over what is below)
    big = np.full((H * SS, W * SS, 3), 255, np.uint8)
    inkbig = np.zeros((H * SS, W * SS), np.uint8)
    for (rgb, op, polys), ink in zip(shapes, ink_flags):
        bgr = (rgb[2], rgb[1], rgb[0])
        if op >= 0.999:
            _fill(big, polys, bgr, s * SS)
        else:
            m = np.zeros(big.shape[:2], np.uint8)
            _fill(m, polys, 255, s * SS)
            sel = m > 0
            big[sel] = (big[sel] * (1 - op) + np.array(bgr) * op).astype(np.uint8)
        _fill(inkbig, polys, 255 if ink else 0, s * SS)
    master = cv2.resize(big, (W, H), interpolation=cv2.INTER_AREA)
    ink_alpha = cv2.resize(inkbig, (W, H), interpolation=cv2.INTER_AREA)
    del big, inkbig

    # 2. region labels at native resolution (no anti-aliasing: one id per pixel)
    lab = np.zeros((H, W), np.int32)
    colour_of = {}
    for k, ((rgb, op, polys), ink) in enumerate(zip(shapes, ink_flags), start=1):
        _fill(lab, polys, 0 if ink else k, s)
        if not ink:
            colour_of[k] = rgb

    # 3. drop slivers, then hand ink + slivers to the nearest region so cells tile the canvas
    ids, counts = np.unique(lab, return_counts=True)
    small = ids[(counts < MIN_REGION_PX) & (ids > 0)]
    if small.size:
        lab[np.isin(lab, small)] = 0
    if not (lab > 0).any():
        raise ValueError("every shape was outline ink — nothing left to colour")
    from services import reveal_game_builder as R   # local import: R imports this module
    lab = R.fill_labels(lab)

    # 4. compact ids 1..N and number colours 1..K, darkest first
    uniq = [int(u) for u in np.unique(lab) if u > 0]
    remap = np.zeros(int(lab.max()) + 1, np.int32)
    for new, old in enumerate(uniq, start=1):
        remap[old] = new
    fl = remap[lab]
    region_rgb = {remap[old]: colour_of[old] for old in uniq}
    palette = sorted(set(region_rgb.values()), key=_lum)
    num_of = {c: i for i, c in enumerate(palette, start=1)}
    numbers = {rid: num_of[c] for rid, c in region_rgb.items()}
    colors = {n: list(c) for c, n in num_of.items()}

    cv2.imwrite(str(folder / "reveal_master.png"), master)
    cv2.imwrite(str(folder / "original.png"), master)
    np.savez_compressed(folder / "svg_game.npz", fl=fl.astype(np.uint16), ink=ink_alpha)
    (folder / "svg_game.json").write_text(json.dumps(
        {"numbers": {str(k): v for k, v in numbers.items()},
         "colors": {str(k): v for k, v in colors.items()},
         "w": W, "h": H, "shapes": len(shapes), "ink_shapes": int(sum(ink_flags))}), encoding="utf-8")
    return {"regions": len(uniq), "palette_numbers": len(palette), "w": W, "h": H,
            "shapes": len(shapes), "ink_shapes": int(sum(ink_flags))}


def is_svg_item(folder: Path) -> bool:
    return (Path(folder) / "svg_game.npz").is_file()


def game_data(folder: Path):
    """Same tuple as reveal_game_builder._game_data_color: (master, fl, numbers, colors, meta, border)."""
    from services import lineart_region_builder
    folder = Path(folder)
    master = cv2.imread(str(folder / "reveal_master.png"))
    z = np.load(folder / "svg_game.npz")
    fl = z["fl"].astype(np.int32)
    ink = z["ink"]
    js = json.loads((folder / "svg_game.json").read_text(encoding="utf-8"))
    numbers = {int(k): int(v) for k, v in js["numbers"].items()}
    colors = {int(k): v for k, v in js["colors"].items()}
    meta = lineart_region_builder.region_meta(fl, numbers)
    border = np.zeros((*fl.shape, 4), np.uint8)
    border[..., 3] = ink
    return master, fl, numbers, colors, meta, border
