"""Convert a ColorForge colour-fill result into the two PNGs the LIVE Colour Mate
"Number" tab consumes, so ColorForge content can be served through the existing
`ionicsolutions.in`-shaped API (see CLAUDE.md §3.2 / §4 Phase 1).

The app (`model/game/GameView.java`) loads two images per level:

  * `{name}.png`          — `blackLineBitmap`: the coloring-page OUTLINE. It is drawn
                            ON TOP of the fill layer (`GameView.java:601-604,746`), so
                            it must be **black lines on a TRANSPARENT background** — an
                            opaque background would erase the user's colours.
  * `{name}.colored.png`  — `colorBitmap`: the flat-colour SOLUTION, with every region's
                            number hidden in the ALPHA channel. Decoded at
                            `GameView.java:2137-2207`:
                              - `alpha == 255`  → a fillable region pixel (RGB = its colour).
                              - `alpha == 0`    → transparent (nothing).
                              - RGB == black    → an outline wall (never fillable).
                              - `0 < alpha <255`→ a NUMBER ANCHOR. The alpha VALUE is the
                                text size; `resolvePixelColor()` reads the 4-neighbours to
                                recover which colour the number belongs to.
                            The app assigns the visible digits itself, by `HashSet`
                            iteration order of the distinct colours — so we only have to
                            guarantee *distinct colour → distinct number*; we do NOT (and
                            cannot) pin the digits here. (Open question CLAUDE.md §6.4.)

Critical rule: partial alpha must appear ONLY at number anchors. ColorForge's
`border.png` stores anti-aliased lines in its alpha channel — feeding that in as-is
would turn every line pixel into a spurious "number". So outlines here are BINARY
(alpha 0 or 255) and derived from the region-id map, keeping the visible outline and
the fill boundaries perfectly consistent.

Entry points:
  * `build_level(fl, id_to_color, ...)`     — core; array in, PNG bytes out.
  * `export_from_bundle(bundle_dir, ...)`   — from an exported `color_bundle` (id_map.png
                                              + regions.json). The documented artifact.
  * `export_from_job(job_dir, ...)`         — straight from a job folder via
                                              `reveal_game_builder._game_data_color`
                                              (no zip round-trip; for the server path).
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

# alpha must stay strictly inside (0, 255): 0 == transparent, 255 == solid fill.
ALPHA_LO = 8            # smallest number text-size we bother drawing
ALPHA_HI = 200          # largest; well clear of 255 so it never reads as "solid"
LINE_THICKNESS = 2
MAX_DIM = 1400          # cap output resolution: the app allocates 2 * w*h ints in RAM
THUMB_MAX = 640
MIN_ANCHOR_RADIUS = 1.5  # a region thinner than this cannot host a legible number


def _region_bboxes(fl: np.ndarray, maxid: int) -> dict:
    """{region_id: (x0, y0, x1, y1)} for all ids 1..maxid present, in one vectorised pass."""
    h, w = fl.shape
    flat = fl.ravel()
    xs = np.tile(np.arange(w), h)
    ys = np.repeat(np.arange(h), w)
    x0 = np.full(maxid + 1, w, np.int32); y0 = np.full(maxid + 1, h, np.int32)
    x1 = np.full(maxid + 1, -1, np.int32); y1 = np.full(maxid + 1, -1, np.int32)
    np.minimum.at(x0, flat, xs); np.minimum.at(y0, flat, ys)
    np.maximum.at(x1, flat, xs); np.maximum.at(y1, flat, ys)
    return {rid: (int(x0[rid]), int(y0[rid]), int(x1[rid]), int(y1[rid]))
            for rid in range(1, maxid + 1) if x1[rid] >= 0}


def _absorb(fl: np.ndarray, tiny_ids: list) -> np.ndarray:
    """Merge the given regions into their nearest surviving neighbour (fill each tiny pixel
    from the closest non-tiny pixel's region id)."""
    tiny_mask = np.isin(fl, tiny_ids)
    if not (tiny_mask.any() and (~tiny_mask).any()):
        return fl
    _, idx = cv2.distanceTransformWithLabels(
        tiny_mask.astype(np.uint8), cv2.DIST_L2, 3, labelType=cv2.DIST_LABEL_PIXEL)
    keep = ~tiny_mask
    lut = np.zeros(int(idx.max()) + 1, dtype=fl.dtype)
    lut[idx[keep]] = fl[keep]                          # pixel-index -> its region id
    return np.where(tiny_mask, lut[idx], fl)


def _edge_mask(fl: np.ndarray) -> np.ndarray:
    """Pixels on a region-id boundary, marked on BOTH sides so every region is fully
    enclosed by a wall (matches a real coloring page; stops flood-fill leaking)."""
    d = np.zeros(fl.shape, bool)
    diff_x = fl[:, :-1] != fl[:, 1:]
    d[:, :-1] |= diff_x
    d[:, 1:] |= diff_x
    diff_y = fl[:-1, :] != fl[1:, :]
    d[:-1, :] |= diff_y
    d[1:, :] |= diff_y
    return d


def _enc(img: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".png", img)
    if not ok:
        raise RuntimeError("PNG encode failed")
    return buf.tobytes()


def build_level(fl: np.ndarray, id_to_color: dict, *, max_dim: int | None = MAX_DIM,
                line_thickness: int = LINE_THICKNESS, alpha_lo: int = ALPHA_LO,
                alpha_hi: int = ALPHA_HI, thumb_max: int | None = THUMB_MAX,
                absorb: bool = True) -> dict:
    """Core converter. `fl` = region-id map (ints, 0 = no region / background);
    `id_to_color` = {region_id: (R, G, B)}. With `absorb` (default), regions too thin to
    carry a number are merged into a neighbour so the app has no un-fillable gaps. Returns
    {line, colored, thumb: PNG bytes, plus w, h, regions, numbered, colors}."""
    fl = np.asarray(fl)
    h0, w0 = fl.shape
    if max_dim and max(h0, w0) > max_dim:
        s = max_dim / max(h0, w0)
        fl = cv2.resize(fl.astype(np.int32), (max(1, round(w0 * s)), max(1, round(h0 * s))),
                        interpolation=cv2.INTER_NEAREST)
    fl = np.ascontiguousarray(fl, dtype=np.int32)
    h, w = fl.shape

    # region-id -> BGR lookup (cv2 channel order). Pure black is nudged to near-black so a
    # dark region is never mistaken for an outline wall (RGB==black) by the app.
    maxid = int(fl.max()) if fl.size else 0
    lut = np.zeros((maxid + 1, 3), np.uint8)
    safe_color: dict = {}
    for rid, rgb in id_to_color.items():
        rid = int(rid)
        if not (0 <= rid <= maxid):
            continue
        r, g, b = (int(rgb[0]), int(rgb[1]), int(rgb[2]))
        if (r, g, b) == (0, 0, 0):
            r, g, b = 16, 16, 16
        safe_color[rid] = (r, g, b)
        lut[rid] = (b, g, r)                       # BGR

    def build_edge(f):
        e = _edge_mask(f)
        if line_thickness > 1:
            e = cv2.dilate(e.astype(np.uint8),
                           np.ones((line_thickness, line_thickness), np.uint8)).astype(bool)
        return e

    # Place one number anchor per region at its interior inscribed-circle centre. A region
    # whose interior is too thin to host a legible number (or that has no colour) is absorbed
    # into its nearest neighbour and the pass repeats — so every surviving cell is numberable
    # (the app never gets an un-fillable gap). Merging only grows regions, so this converges.
    edge = build_edge(fl)
    anchors: dict = {}
    for _ in range(8):
        interior = ~edge
        anchors = {}
        tiny = []
        for rid, (x0, y0, x1, y1) in _region_bboxes(fl, int(fl.max())).items():
            if rid not in safe_color:
                tiny.append(rid)
                continue
            sub = ((fl[y0:y1 + 1, x0:x1 + 1] == rid)
                   & interior[y0:y1 + 1, x0:x1 + 1]).astype(np.uint8)
            if sub.any():
                dt = cv2.distanceTransform(sub, cv2.DIST_L2, 3)
                _, r, _, loc = cv2.minMaxLoc(dt)
            else:
                r, loc = 0.0, (0, 0)
            if r < MIN_ANCHOR_RADIUS:
                tiny.append(rid)
                continue
            anchors[rid] = (int(loc[0]) + x0, int(loc[1]) + y0,
                            int(np.clip(round(r), alpha_lo, alpha_hi)))
        if not absorb or not tiny:
            break
        fl = _absorb(fl, tiny)
        edge = build_edge(fl)

    # ── colored solution (BGRA): flat fill + black walls + partial-alpha number anchors ──
    colored = np.zeros((h, w, 4), np.uint8)
    colored[..., :3] = lut[fl]
    colored[..., 3] = 255
    colored[edge] = (0, 0, 0, 255)                 # opaque black walls
    for cx, cy, a in anchors.values():
        # anchor keeps its region colour in RGB; only alpha carries the text size, so the
        # app's `resolvePixelColor` recovers the colour from the (identical) neighbours.
        colored[cy, cx, 3] = a
    regions = sum(1 for rid in np.unique(fl) if int(rid) in safe_color)
    numbered = len(anchors)

    # ── outline: black lines on transparent (drawn on top in-app) ──
    line = np.zeros((h, w, 4), np.uint8)
    line[edge, 3] = 255

    # ── thumbnail: flat colours + black lines over white, opaque ──
    thumb = lut[fl].copy()
    thumb[edge] = (0, 0, 0)
    if thumb_max and max(h, w) > thumb_max:
        s = thumb_max / max(h, w)
        thumb = cv2.resize(thumb, (max(1, round(w * s)), max(1, round(h * s))),
                           interpolation=cv2.INTER_AREA)

    return {"line": _enc(line), "colored": _enc(colored), "thumb": _enc(thumb),
            "w": w, "h": h, "regions": regions, "numbered": numbered,
            "colors": len(set(safe_color.values()))}


def _write(out_dir: Path, name: str, out: dict) -> dict:
    out_dir = Path(out_dir)
    (out_dir / "t").mkdir(parents=True, exist_ok=True)
    (out_dir / f"{name}.png").write_bytes(out["line"])
    (out_dir / f"{name}.colored.png").write_bytes(out["colored"])
    (out_dir / "t" / f"{name}.png").write_bytes(out["thumb"])
    return {"name": name, "dir": str(out_dir), "w": out["w"], "h": out["h"],
            "regions": out["regions"], "numbered": out["numbered"], "colors": out["colors"],
            "files": [f"{name}.png", f"{name}.colored.png", f"t/{name}.png"]}


def _load_idmap(path: Path) -> np.ndarray:
    """Decode a ColorForge `id_map.png` (id = R + G*256) back to an id array."""
    im = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    if im is None:
        raise FileNotFoundError(f"cannot read id_map: {path}")
    R = im[..., 2].astype(np.int32)                # cv2: B=0, G=1, R=2
    G = im[..., 1].astype(np.int32)
    return R + (G << 8)


def _hex_rgb(hexc: str) -> tuple:
    h = hexc.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def export_from_bundle(bundle_dir: Path, out_dir: Path, name: str, **kw) -> dict:
    """Build the two level PNGs from an exported ColorForge `color_bundle` directory
    (needs `id_map.png` + `regions.json`)."""
    bundle_dir = Path(bundle_dir)
    fl = _load_idmap(bundle_dir / "id_map.png")
    reg = json.loads((bundle_dir / "regions.json").read_text(encoding="utf-8"))
    hexes = {p["number"]: p["color"] for p in reg["palette"]}
    id_to_color = {int(rr["id"]): _hex_rgb(hexes.get(rr["number"], "#c8c8c8"))
                   for rr in reg["regions"]}
    return _write(out_dir, name, build_level(fl, id_to_color, **kw))


def bytes_from_job(job_dir: Path, flatten: float = 1.0, **kw) -> dict:
    """Build the level PNGs straight from a ColorForge job folder — recomputes the same
    regions/palette the colour-fill game uses — and return the `build_level` dict
    (line/colored/thumb bytes + stats) WITHOUT touching disk. This is the server path:
    the bytes go straight to storage."""
    from services import reveal_game_builder as R
    master, fl, numbers, colors, meta, bdr = R._game_data_color(Path(job_dir), flatten)
    id_to_color = {int(m["id"]): tuple(colors.get(m["n"], [200, 200, 200])) for m in meta}
    return build_level(fl, id_to_color, **kw)


def export_from_job(job_dir: Path, out_dir: Path, name: str, flatten: float = 1.0, **kw) -> dict:
    """As `bytes_from_job`, but writes {name}.png / {name}.colored.png / t/{name}.png to disk."""
    return _write(out_dir, name, bytes_from_job(job_dir, flatten, **kw))
