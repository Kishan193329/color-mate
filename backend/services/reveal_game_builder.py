"""Reference-style progressive reveal gameplay builder.

Reproduces the exact gameplay model of the reference (IMG_1376 + contourv(6).svg): a clean
black-border coloring page where tapping a numbered region **reveals the original pixels** for
that region — never a repaint — until, region by region, the canvas becomes the original.

This assembles existing ColorForge artifacts only (no new AI, no scoring, no algorithm change):
`labels.npy` (region masks + stable ids), `reveal_master.png` (the immutable original master —
already proven ΔE 0 / 0-px identical), and `game_config.json` (per-region number + position).

Key move: the pipeline leaves a ~19% "skeleton" (label 0) that is neither clean line-art nor a
colorable cell. To make the **completed canvas equal the original exactly**, the skeleton is
absorbed into its nearest region (`fill_labels`) so the colorable cells **tile 100%** of the
canvas — region numbers/colours are unchanged. Revealing every cell then reproduces the master
pixel-for-pixel. The visible border is a **thin, clean overlay drawn on top** (no black blobs,
no debug colours).

Outputs per job: `play_id_map.png` (region-id map, R+G·256), `play_border.png` (thin black
lines, transparent elsewhere) and a self-contained `play.html` (the playable reveal engine).
`compose()` is the deterministic reference renderer used by validation and mirrored by the JS.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path

import cv2
import numpy as np

from services import ai_lineart_local, lineart_region_builder

FONT = cv2.FONT_HERSHEY_SIMPLEX
BORDER_THICKNESS = 2


def fill_labels(labels: np.ndarray) -> np.ndarray:
    """Absorb the label-0 skeleton into the nearest region so colorable cells tile 100% of the
    canvas. Region ids (and therefore numbers/colours) are preserved exactly."""
    mask0 = (labels == 0).astype(np.uint8)
    if not mask0.any():
        return labels.copy()
    _, idx = cv2.distanceTransformWithLabels(mask0, cv2.DIST_L2, 3, labelType=cv2.DIST_LABEL_PIXEL)
    labeled = ~mask0.astype(bool)
    lut = np.zeros(int(idx.max()) + 1, dtype=labels.dtype)
    lut[idx[labeled]] = labels[labeled]           # index -> original region label
    fill = lut[idx]
    fill[labeled] = labels[labeled]
    return fill


def _boundaries(fl: np.ndarray) -> np.ndarray:
    edge = np.zeros(fl.shape, bool)
    edge[:, :-1] |= fl[:, :-1] != fl[:, 1:]
    edge[:-1, :] |= fl[:-1, :] != fl[1:, :]
    return edge


def border_bgra(fl: np.ndarray, thickness: int = BORDER_THICKNESS) -> np.ndarray:
    """Region-boundary lines on transparent — the fallback border when no AI line-art exists."""
    e = (_boundaries(fl).astype(np.uint8)) * 255
    if thickness > 1:
        e = cv2.dilate(e, np.ones((thickness, thickness), np.uint8))
    out = np.zeros((*fl.shape, 4), np.uint8)      # RGB stays 0 (black); alpha = edge
    out[..., 3] = e
    return out


def _ai_border_bgra(line: np.ndarray) -> np.ndarray:
    """Turn a grayscale AI line-art (white paper, dark lines) into a black RGBA overlay whose
    alpha is the line darkness — a real coloring-book outline, not region boundaries."""
    out = np.zeros((*line.shape, 4), np.uint8)    # RGB = black
    out[..., 3] = (255 - line).astype(np.uint8)   # alpha = how dark the line is
    return out


def _border(folder: Path, fl: np.ndarray, w: int, h: int):
    """The coloring-page border: the local AI line-art (clean, subject-aware coloring-book
    outline) when available, else the region-boundary lines. Returns (border_bgra, source)."""
    ai_path = folder / "ai_lineart.png"
    line = cv2.imread(str(ai_path), cv2.IMREAD_GRAYSCALE) if ai_path.exists() else None
    if line is None:
        line = ai_lineart_local.generate(folder / "original.png", ai_path)
    if line is not None:
        if line.shape[:2] != (h, w):
            line = cv2.resize(line, (w, h), interpolation=cv2.INTER_AREA)
        return _ai_border_bgra(line), "ai_lineart"
    return border_bgra(fl), "region_boundary"


def id_map_bgra(fl: np.ndarray) -> np.ndarray:
    """Region-id map: id = R + G*256 (cv2 BGRA: R=idx 2, G=idx 1). Opaque."""
    h, w = fl.shape
    out = np.zeros((h, w, 4), np.uint8)
    out[..., 2] = (fl & 0xFF).astype(np.uint8)
    out[..., 1] = ((fl >> 8) & 0xFF).astype(np.uint8)
    out[..., 3] = 255
    return out


def _fit_scale(text: str, r: float) -> float:
    """Largest font scale whose text stays inside the cell's inscribed radius (no overlap)."""
    for s in (1.3, 1.1, 0.9, 0.75, 0.6, 0.48, 0.38, 0.3):
        (tw, th), _ = cv2.getTextSize(text, FONT, s, max(1, int(s * 2)))
        if 0.5 * (tw ** 2 + th ** 2) ** 0.5 < r * 0.92:
            return s
    return 0.0


def region_meta(fl: np.ndarray, game_config: dict) -> list:
    """Per-region {id, number, x, y, bb} with bbox recomputed from the *filled* labels so the
    tap-reveal scan and number placement stay inside the region."""
    num_by_label = {r.get("label"): r.get("number", r.get("color_id"))
                    for r in game_config.get("regions", [])}
    pos_by_label = {r.get("label"): r.get("label_pos", {"x": r.get("cx"), "y": r.get("cy")})
                    for r in game_config.get("regions", [])}
    meta = []
    for rid in (int(v) for v in np.unique(fl) if v != 0):
        ys, xs = np.where(fl == rid)
        if xs.size == 0:
            continue
        x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
        bb = [x0, y0, x1 - x0 + 1, y1 - y0 + 1]
        pos = pos_by_label.get(rid) or {"x": (x0 + x1) // 2, "y": (y0 + y1) // 2}
        px = int(pos.get("x", (x0 + x1) // 2)); py = int(pos.get("y", (y0 + y1) // 2))
        # keep the number label inside the region footprint
        if not (0 <= py < fl.shape[0] and 0 <= px < fl.shape[1] and fl[py, px] == rid):
            px, py = int(round(xs.mean())), int(round(ys.mean()))
        meta.append({"id": rid, "n": num_by_label.get(rid, rid), "x": px, "y": py, "bb": bb})
    return meta


def _put_number(canvas: np.ndarray, text: str, x: int, y: int, r: float) -> None:
    s = _fit_scale(text, r)
    if s <= 0:
        return
    th = max(1, int(round(s * 2)))
    (tw, tht), _ = cv2.getTextSize(text, FONT, s, th)
    org = (int(x - tw / 2), int(y + tht / 2))
    cv2.putText(canvas, text, org, FONT, s, (255, 255, 255), th + 2, cv2.LINE_AA)
    cv2.putText(canvas, text, org, FONT, s, (20, 20, 20), th, cv2.LINE_AA)


def compose(fl: np.ndarray, master: np.ndarray, revealed_ids, border: np.ndarray,
            meta: list, draw_numbers: bool = True):
    """Deterministic renderer for a set of revealed region ids. Returns
    (display, content): `content` is the reveal layer only (== master when all revealed →
    pixel-identical); `display` adds the thin black border on top + numbers on unrevealed cells.
    The JS engine mirrors this exactly."""
    revealed = set(int(i) for i in revealed_ids)
    content = np.full_like(master, 255)
    if revealed:
        content[np.isin(fl, list(revealed))] = master[np.isin(fl, list(revealed))]
    a = border[..., 3:4].astype(np.float32) / 255.0   # alpha-composite the black line-art on top
    display = (content.astype(np.float32) * (1.0 - a)).astype(np.uint8)
    if draw_numbers:
        for m in meta:
            if m["id"] not in revealed:
                r = m.get("r") or 0.5 * min(m["bb"][2], m["bb"][3])
                _put_number(display, str(m["n"]), m["x"], m["y"], r)
    return display, content


# ── build ─────────────────────────────────────────────────────────────────── #
def _png(img: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".png", img)
    return buf.tobytes()


def _data_uri(b: bytes) -> str:
    return "data:image/png;base64," + base64.b64encode(b).decode("ascii")


def _load_lineart(folder: Path, h: int, w: int):
    """The clean AI line-art (grayscale) at canvas size, or None if unavailable."""
    p = folder / "ai_lineart.png"
    line = cv2.imread(str(p), cv2.IMREAD_GRAYSCALE) if p.exists() else None
    if line is None:
        line = ai_lineart_local.generate(folder / "original.png", p, darken=1.5)  # crisp -> closes cells
    if line is None:
        return None
    if line.shape[:2] != (h, w):
        line = cv2.resize(line, (w, h), interpolation=cv2.INTER_AREA)
    return line


def game_regions(folder: Path, r_min: float = 13.0, n_colors: int = 24) -> dict:
    """The Number-by-Color game data for a job. Playable regions are the clean AI line-art's own
    closed shapes, split by the original colour regions so each region is one colour inside one
    shape (same colour -> same palette number, even across different shapes). The visible border is
    ONLY the clean line-art — the region-split boundaries are invisible (they live only in the
    tap-map). Falls back to colour-label regions + a region-boundary border if the model is absent.
    `r_min` sets region density (smaller -> more, smaller regions -> longer game); `n_colors` sets
    the palette size. Returns {master, fl, meta, border, region_source, border_source, palette_count}."""
    folder = Path(folder)
    master = cv2.imread(str(folder / "reveal_master.png"))
    if master is None:
        raise FileNotFoundError("reveal_master.png missing — run the reveal pass first")
    h, w = master.shape[:2]
    line = _load_lineart(folder, h, w)
    if line is not None:                                   # regions FROM the line-art shapes
        fl = lineart_region_builder.build_regions(line, r_min=r_min)
        if (folder / "labels.npy").exists():               # split big cells by colour (distinct, harder)
            color = fill_labels(np.load(folder / "labels.npy").astype(np.int32))
            if color.shape != fl.shape:
                color = cv2.resize(color, (fl.shape[1], fl.shape[0]), interpolation=cv2.INTER_NEAREST)
            fl = lineart_region_builder.intersect_color(fl, color, r_min=r_min)
        numbers = lineart_region_builder.palette_numbers(master, fl, n_colors=n_colors)
        meta = lineart_region_builder.region_meta(fl, numbers)
        bdr = _ai_border_bgra(line)
        return {"master": master, "fl": fl, "meta": meta, "border": bdr,
                "region_source": "lineart_shapes", "border_source": "ai_lineart",
                "palette_count": len(set(numbers.values()))}
    fl = fill_labels(np.load(folder / "labels.npy").astype(np.int32))
    gc = json.loads((folder / "game_config.json").read_text(encoding="utf-8"))
    meta = region_meta(fl, gc)
    return {"master": master, "fl": fl, "meta": meta, "border": border_bgra(fl),
            "region_source": "color_labels", "border_source": "region_boundary",
            "palette_count": len({m["n"] for m in meta})}


def build(folder: Path) -> dict:
    """Build play_id_map.png, play_border.png and the self-contained play.html for a job."""
    folder = Path(folder)
    job = folder.name
    if (folder / "game_config.json").exists():
        job = json.loads((folder / "game_config.json").read_text(encoding="utf-8")).get("job_id", job)
    g = game_regions(folder)
    fl, meta, bdr = g["fl"], g["meta"], g["border"]
    border_source, region_source, palette_count = g["border_source"], g["region_source"], g["palette_count"]
    h, w = fl.shape

    idm = id_map_bgra(fl)
    cv2.imwrite(str(folder / "play_id_map.png"), idm)
    cv2.imwrite(str(folder / "play_border.png"), bdr)

    master_uri = _data_uri((folder / "reveal_master.png").read_bytes())
    idmap_uri = _data_uri(_png(idm))
    border_uri = _data_uri(_png(bdr))
    html = _PLAY_HTML.format(
        job=job, w=w, h=h, master=master_uri, idmap=idmap_uri, border=border_uri,
        meta=json.dumps(meta, separators=(",", ":")))
    (folder / "play.html").write_text(html, encoding="utf-8")
    return {
        "mode": "progressive_reveal",
        "regions": len(meta),
        "palette_numbers": palette_count,
        "cells_tile_full_canvas": bool((fl > 0).all()),
        "region_source": region_source,   # lineart_shapes | color_labels
        "border_source": border_source,    # ai_lineart | region_boundary
        "assets": {"id_map": "play_id_map.png", "border": "play_border.png", "page": "play.html"},
        "reveal_source": "reveal_master.png (immutable original master)",
        "note": "tap the region matching a palette number -> reveal its original pixels; the AI "
                "line-art stays on top; revealing every region reproduces the master exactly.",
    }


# Colour-fill game tuning. Regions are now COLOUR-based (see _color_regions): every region is one
# colour and every palette number is one exact colour, so selecting a number highlights only
# same-colour regions (no "why are different colours under one number?"), and no giant leaked region.
COLOR_TARGET = 400       # ~region count for the pure-colour fallback (no line-art model)
COLOR_K = 100            # colour-quantiser clusters -> ~100 distinct palette numbers
COLOR_LINE_RMIN = 9.0    # line-art cell density before the colour split


def _palette_avg(master: np.ndarray, fl: np.ndarray, meta: list) -> dict:
    """One representative colour (R,G,B) per palette number = median original colour over that
    number's regions. Used for the fill's flat-paint start + the on-canvas highlight."""
    by_num: dict = {}
    for m in meta:
        by_num.setdefault(int(m["n"]), []).append(m["id"])
    out = {}
    for n, ids in by_num.items():
        px = master[np.isin(fl, ids)].reshape(-1, 3)
        if px.size:
            b, g, r = np.median(px, axis=0)
            out[n] = [int(r), int(g), int(b)]
    return out


def _palette_swatches(master: np.ndarray, fl: np.ndarray, meta: list, colors: dict,
                      size: int = 64) -> dict:
    """Per palette number: {c:[R,G,B], img:<data-uri>}. `img` is a real crop of the ORIGINAL photo at
    that number's most-textured region (masked to its shape), finished with a glossy top-left
    highlight + soft edge vignette so each swatch looks like a shiny gem of the real image — rich,
    not a flat plain colour."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    gloss = np.clip(1 - np.sqrt((xx - size * 0.34) ** 2 + (yy - size * 0.30) ** 2) / (size * 0.85), 0, 1) ** 2.2
    vig = np.clip(np.sqrt((xx - size / 2) ** 2 + (yy - size / 2) ** 2) / (size * 0.72), 0, 1)
    gloss, vig = gloss[..., None], vig[..., None]

    def _texture(m):
        x, y, w, h = m["bb"]
        sub = master[y:y + h, x:x + w][fl[y:y + h, x:x + w] == m["id"]]
        return float(sub.std()) if sub.size else 0.0

    by_num: dict = {}
    for m in meta:
        by_num.setdefault(int(m["n"]), []).append(m)
    out = {}
    for n, ms in by_num.items():
        c = colors.get(n, [200, 200, 200])
        cand = sorted(ms, key=lambda m: m["bb"][2] * m["bb"][3], reverse=True)[:6]  # bigger regions
        rep = max(cand, key=_texture) if cand else ms[0]         # ...pick the most-textured one
        x, y, w, h = rep["bb"]
        crop = master[y:y + h, x:x + w].copy()
        mask = fl[y:y + h, x:x + w] == rep["id"]
        crop[~mask] = (c[2], c[1], c[0])                          # non-region -> avg colour (BGR)
        thumb = cv2.resize(crop, (size, size), interpolation=cv2.INTER_AREA).astype(np.float32)
        thumb = thumb * (1 - 0.30 * vig)                         # darken the rim
        thumb = thumb + (255.0 - thumb) * (0.55 * gloss)         # shiny top-left highlight
        thumb = np.clip(thumb, 0, 255).astype(np.uint8)
        out[n] = {"c": c, "img": _data_uri(_png(thumb))}
    return out


def _subdivide_big(reg: np.ndarray, numbers: dict, cap: int):
    """Split any region larger than ~1.5*cap into compact ~cap-px sub-cells that KEEP the region's
    number (same colour). One tap then never fills a big uniform area (black hoodie, sky) while the
    palette/colours stay identical. Returns (reg, numbers)."""
    out = reg.copy()
    nxt = int(reg.max()) + 1
    newnums = dict(numbers)
    counts = np.bincount(reg.ravel())
    for rid in range(1, len(counts)):
        if counts[rid] <= cap * 1.5:
            continue
        ys, xs = np.where(reg == rid)
        if xs.size == 0:
            continue
        x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
        crop = reg[y0:y1 + 1, x0:x1 + 1] == rid
        step, lbl = max(int(cap ** 0.5), 5), None
        while step >= 4:
            seeds = np.ones(crop.shape, np.uint8); nseed = 0
            for yy in range(step // 2, crop.shape[0], step):
                row = crop[yy]
                for xx in range(step // 2, crop.shape[1], step):
                    if row[xx]:
                        seeds[yy, xx] = 0; nseed += 1
            if nseed >= 2:
                _, lbl = cv2.distanceTransformWithLabels(seeds, cv2.DIST_L2, 3, labelType=cv2.DIST_LABEL_PIXEL)
                break
            step = int(step * 0.6)
        if lbl is None:
            continue
        sub = lbl[crop]
        uniq = np.unique(sub)
        if uniq.size < 2:
            continue
        remap = {int(u): (rid if i == 0 else nxt + i - 1) for i, u in enumerate(uniq)}
        for i, u in enumerate(uniq):
            if i > 0:
                newnums[nxt + i - 1] = numbers[rid]
        nxt += uniq.size - 1
        out[y0:y1 + 1, x0:x1 + 1][crop] = np.array([remap[int(v)] for v in sub], np.int32)
    uni = [u for u in np.unique(out) if u > 0]
    lut = np.zeros(int(out.max()) + 1, np.int32)
    outnums = {}
    for i, u in enumerate(uni, 1):
        lut[u] = i
        outnums[i] = newnums[u]
    return lut[out], outnums


def _color_regions(master: np.ndarray, target: int = COLOR_TARGET, k: int = COLOR_K):
    """COLOUR-based regions for the colour-fill game: segment by colour so every region is ONE colour
    and every palette number is ONE exact colour (a blue arm and a different-colour background can
    never share a region/number), and no region leaks into a giant blob. Returns (fl, numbers,
    colors, meta): fl=region id map (full res), numbers={id:number}, colors={number:[R,G,B]},
    meta=[{id,n,x,y,r,bb}]."""
    from services import color_paint_builder as C
    fh, fw = master.shape[:2]
    scale = C.WORK_MAX / max(fh, fw)
    img = cv2.resize(master, (int(fw * scale), int(fh * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else master.copy()
    hh, ww = img.shape[:2]
    cmap, centers = C._quantize(img, k)
    reg = C._regions(cmap, int(cmap.max()) + 1)
    min_area = max(20, int(hh * ww * 0.0006))
    reg = C._merge_small(reg, min_area)
    while len(np.unique(reg)) - 1 > target:
        min_area = int(min_area * 1.4)
        reg = C._merge_small(reg, min_area)
    uni = [u for u in np.unique(reg) if u > 0]
    lut = np.zeros(int(reg.max()) + 1, np.int32)
    for i, u in enumerate(uni, 1):
        lut[u] = i
    reg = lut[reg]
    R = len(uni)
    numbers, colors = {}, {}
    for i in range(1, R + 1):
        ci = int(np.bincount(cmap[reg == i]).argmax())
        numbers[i] = ci + 1
        c = centers[ci]
        colors[ci + 1] = [int(c[2]), int(c[1]), int(c[0])]        # BGR -> RGB
    # cap one-tap fill: split big uniform regions into same-number (same-colour) sub-cells (~2%)
    reg, numbers = _subdivide_big(reg, numbers, cap=max(400, int(hh * ww * 0.02)))
    fl = cv2.resize(reg, (fw, fh), interpolation=cv2.INTER_NEAREST)   # region map at full res
    R = int(fl.max())
    meta = []
    for i in range(1, R + 1):
        ys, xs = np.where(fl == i)
        x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
        d = cv2.distanceTransform((fl[y0:y1 + 1, x0:x1 + 1] == i).astype(np.uint8), cv2.DIST_L2, 3)
        _, r, _, loc = cv2.minMaxLoc(d)
        meta.append({"id": i, "n": numbers[i], "x": int(loc[0]) + x0, "y": int(loc[1]) + y0,
                     "r": round(float(r), 1), "bb": [x0, y0, x1 - x0 + 1, y1 - y0 + 1]})
    return fl, numbers, colors, meta


def _fine_color(master: np.ndarray, k: int = COLOR_K):
    """Full-res k-means colour-index map (0..k-1) of the image + centres (BGR)."""
    fh, fw = master.shape[:2]
    scale = 720.0 / max(fh, fw)
    small = cv2.resize(master, (int(fw * scale), int(fh * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else master.copy()
    sm = cv2.pyrMeanShiftFiltering(small, 13, 30)
    z = sm.reshape(-1, 3).astype(np.float32)
    _, lab, centers = cv2.kmeans(z, k, None, (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 14, 1.0),
                                 2, cv2.KMEANS_PP_CENTERS)
    idx = lab.reshape(small.shape[:2]).astype(np.int32)
    if scale < 1:
        idx = cv2.resize(idx, (fw, fh), interpolation=cv2.INTER_NEAREST)
    return idx, centers.astype(np.uint8)


def _game_data_color(folder: Path):
    """Regions + palette + CLEAN line-art border for the colour-fill game. Regions are the line-art's
    OWN closed cells split by fine colour: the visible border stays the clean line-art, yet every
    region is one colour and every number one exact colour (a blue arm never shares a region/number
    with a different-colour background). Big regions are area-capped. Returns
    (master, fl, numbers, colors, meta, border)."""
    folder = Path(folder)
    master = cv2.imread(str(folder / "reveal_master.png"))
    if master is None:
        raise FileNotFoundError("reveal_master.png missing — run the reveal pass first")
    h, w = master.shape[:2]
    line = _load_lineart(folder, h, w)
    if line is None:                                               # no model -> pure-colour fallback
        fl, numbers, colors, meta = _color_regions(master)
        return master, fl, numbers, colors, meta, border_bgra(fl)
    color_idx, centers = _fine_color(master, COLOR_K)
    fl = lineart_region_builder.build_regions(line, r_min=COLOR_LINE_RMIN)       # clean line-art cells
    fl = lineart_region_builder.intersect_color(fl, color_idx, r_min=COLOR_LINE_RMIN)  # split by colour
    rmax = int(fl.max()) + 1
    key = (fl.astype(np.int64) * COLOR_K + color_idx).ravel()      # dominant colour per region (vectorised)
    dom = np.bincount(key, minlength=rmax * COLOR_K).reshape(rmax, COLOR_K).argmax(1)
    numbers, colors = {}, {}
    for rid in (int(u) for u in np.unique(fl) if u > 0):
        ci = int(dom[rid])
        numbers[rid] = ci + 1
        c = centers[ci]; colors[ci + 1] = [int(c[2]), int(c[1]), int(c[0])]
    fl, numbers = _subdivide_big(fl, numbers, cap=max(400, int(h * w * 0.02)))   # cap one-tap fill
    meta = lineart_region_builder.region_meta(fl, numbers)
    return master, fl, numbers, colors, meta, _ai_border_bgra(line)  # CLEAN line-art border


def build_color(folder: Path) -> dict:
    """Colour-FILL play page: line-art cells split by colour (clean line-art border, one colour per
    region/number) + reveal-the-original tech + paint-by-number FEEL. Pick a colour, tap its numbered
    regions; each paints its flat colour then morphs into the ORIGINAL photo pixels. Completing paints
    the real photo. Selecting a number highlights only same-colour regions; no giant one-tap fills."""
    folder = Path(folder)
    job = folder.name
    if (folder / "game_config.json").exists():
        job = json.loads((folder / "game_config.json").read_text(encoding="utf-8")).get("job_id", job)
    master, fl, numbers, colors, meta, bdr = _game_data_color(folder)
    h, w = fl.shape
    idm = id_map_bgra(fl)
    cv2.imwrite(str(folder / "play_id_map.png"), idm)
    cv2.imwrite(str(folder / "play_border.png"), bdr)
    palette = _palette_swatches(master, fl, meta, colors)          # swatch = real photo texture
    html = _PLAY_COLOR_HTML.format(
        job=job, w=w, h=h,
        master=_data_uri((folder / "reveal_master.png").read_bytes()),
        idmap=_data_uri(_png(idm)), border=_data_uri(_png(bdr)),
        meta=json.dumps(meta, separators=(",", ":")),
        palette=json.dumps(palette, separators=(",", ":")))
    (folder / "play_color.html").write_text(html, encoding="utf-8")
    return {"mode": "colour_fill_reveal", "regions": len(meta), "palette_numbers": len(palette),
            "assets": {"id_map": "play_id_map.png", "border": "play_border.png", "page": "play_color.html"}}


_COLOR_BUNDLE_README = """# ColorForge — Colour-Fill bundle ({job})

Data to rebuild the colour-fill game in an app (Flutter / Android). The game: a white coloring page
with numbered regions + a palette; pick a colour, tap its numbered regions — each paints its flat
colour then morphs (~0.3s) into the ORIGINAL photo pixels. Finishing paints the real photograph.

Image size: {w} x {h} px. {nreg} regions, {ncol} palette colours. All coordinates below are in
ORIGINAL image pixels.

## Files
- `original.png`  — the real photo. This is the fill/reveal source (what a filled region shows).
- `id_map.png`    — region id per pixel, encoded **id = Red + Green*256** (Blue unused). Use it to
                    (a) find which region a tap hit, (b) mask a region's pixels when filling.
- `border.svg`    — one closed `<path>` per region (`data-region`, `data-number`, `data-color`).
                    Crisp scalable coloring-page outlines; you can also hit-test taps against paths.
- `border.png`    — same outlines as a transparent raster overlay (alternative to the SVG).
- `regions.json`  — see below.
- `swatches/<n>.png` — the palette swatch for colour `n`: a real crop of the photo (looks original,
                    not a flat colour). Show these as the palette buttons.

## regions.json
```
{{ "job", "w", "h",
   "regions": [ {{ "id", "number", "cx", "cy", "r", "bbox":[x,y,w,h] }} ... ],   // cx,cy = number position
   "palette": [ {{ "number", "color":"#rrggbb", "swatch":"swatches/5.png" }} ... ]
}}
```
Same `number` = same colour. `color` is the flat paint colour (use for the paint-then-morph start).

## Rebuild logic (per region)
1. Draw the white page + `border` on top; draw each region's `number` at (cx, cy) if unfilled.
2. Palette bar = the `swatches` (one button per number, showing the real texture).
3. On tap at (x, y): read `id_map` pixel -> `id = R + G*256` -> look up its `number`.
4. Fill: set the region's pixels (where id_map == id) to the flat `color`, then over ~300ms
   cross-fade them to the matching pixels of `original.png` (ease). That's the "paint -> real" morph.
5. Region is done when filled; a colour is done when all its regions are filled; finishing all =
   `original.png` exactly.

Flutter tips: decode `id_map.png` once to a pixel buffer for hit-testing/masking; use `ClipPath`
with the SVG path (or the id-map mask) to draw a region's slice of `original.png`.
"""


def export_color_bundle(folder: Path, out_zip: Path | None = None) -> dict:
    """Package everything an app needs to rebuild the colour-fill game, as a zip: original.png,
    id_map.png (id=R+G*256), border.svg + border.png, regions.json (regions + palette), a real-photo
    swatch per colour, and a README explaining the format + game logic."""
    import base64
    import shutil
    import zipfile
    folder = Path(folder)
    job = folder.name
    if (folder / "game_config.json").exists():
        job = json.loads((folder / "game_config.json").read_text(encoding="utf-8")).get("job_id", job)
    master, fl, numbers, colors, meta, bdr = _game_data_color(folder)   # same regions as the game
    h, w = fl.shape
    swatches = _palette_swatches(master, fl, meta, colors)

    bdir = folder / "color_bundle"
    if bdir.exists():
        shutil.rmtree(bdir)
    (bdir / "swatches").mkdir(parents=True)
    shutil.copyfile(folder / "reveal_master.png", bdir / "original.png")
    cv2.imwrite(str(bdir / "id_map.png"), id_map_bgra(fl))
    cv2.imwrite(str(bdir / "border.png"), bdr)

    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">']
    for m in meta:
        cnts, _ = cv2.findContours((fl == m["id"]).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        hexc = "#%02x%02x%02x" % tuple(colors.get(m["n"], [200, 200, 200]))
        for c in cnts:
            c = cv2.approxPolyDP(c, 1.2, True)
            if len(c) < 3:
                continue
            d = "M " + " L ".join(f"{int(p[0][0])},{int(p[0][1])}" for p in c) + " Z"
            svg.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="1" '
                       f'data-region="{m["id"]}" data-number="{m["n"]}" data-color="{hexc}"/>')
    svg.append("</svg>")
    (bdir / "border.svg").write_text("\n".join(svg), encoding="utf-8")

    palette = []
    for n in sorted(swatches):
        c = swatches[n]["c"]
        (bdir / "swatches" / f"{n}.png").write_bytes(base64.b64decode(swatches[n]["img"].split(",")[1]))
        palette.append({"number": n, "color": "#%02x%02x%02x" % (c[0], c[1], c[2]), "swatch": f"swatches/{n}.png"})
    regions = [{"id": m["id"], "number": m["n"], "cx": m["x"], "cy": m["y"], "r": m.get("r"), "bbox": m["bb"]}
               for m in meta]
    (bdir / "regions.json").write_text(
        json.dumps({"job": job, "w": w, "h": h, "regions": regions, "palette": palette},
                   separators=(",", ":")), encoding="utf-8")
    (bdir / "README.md").write_text(
        _COLOR_BUNDLE_README.format(job=job, w=w, h=h, nreg=len(regions), ncol=len(palette)), encoding="utf-8")

    out_zip = Path(out_zip) if out_zip else (folder / f"{job}_color_bundle.zip")
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(bdir.rglob("*")):
            if f.is_file():
                z.write(f, arcname=f.relative_to(bdir))
    shutil.rmtree(bdir)
    return {"zip": str(out_zip), "regions": len(regions), "colours": len(palette), "w": w, "h": h}


_PLAY_HTML = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Progressive Reveal ({job})</title>
<style>
:root{{color-scheme:dark}}
body{{margin:0;background:#0f1216;color:#e6e9ef;font:14px system-ui,Segoe UI,Roboto,sans-serif;
display:flex;flex-direction:column;align-items:center}}
header{{width:100%;box-sizing:border-box;display:flex;gap:14px;align-items:center;padding:10px 16px;
border-bottom:1px solid #262d37;position:sticky;top:0;background:#0f1216;z-index:2}}
header b{{font-size:15px}} .sp{{flex:1}}
button{{border:1px solid #2a323d;background:#161b22;color:#e6e9ef;padding:7px 14px;border-radius:7px;
font-weight:600;cursor:pointer}} button:hover{{border-color:#4f8cff;color:#4f8cff}}
#prog{{color:#9aa4b2}}
#wrap{{padding:16px;max-width:100%}}
canvas{{background:#fff;max-width:100%;height:auto;border-radius:8px;box-shadow:0 6px 24px #0008;cursor:pointer}}
</style></head><body>
<header><b>ColorForge · Progressive Reveal</b><span id="prog">loading…</span><span class="sp"></span>
<button onclick="reset()">Reset</button><button onclick="revealAll()">Reveal all</button></header>
<div id="wrap"><canvas id="c" width="{w}" height="{h}"></canvas></div>
<script>
const W={w},H={h},META={meta};
const bboxOf={{}}; for(const m of META) bboxOf[m.id]=m.bb;
const cv=document.getElementById('c'), ctx=cv.getContext('2d');
const master=new Image(), idmap=new Image(), border=new Image();
master.src="{master}"; idmap.src="{idmap}"; border.src="{border}";
let masterData,idData,display,revealed=new Set(),ready=0;
function boot(){{ if(++ready<3) return;
  const o=document.createElement('canvas'); o.width=W; o.height=H; const oc=o.getContext('2d');
  oc.drawImage(master,0,0); masterData=oc.getImageData(0,0,W,H);
  oc.clearRect(0,0,W,H); oc.drawImage(idmap,0,0); idData=oc.getImageData(0,0,W,H);
  display=ctx.createImageData(W,H); display.data.fill(255);    // white coloring page
  repaint();
}}
master.onload=boot; idmap.onload=boot; border.onload=boot;
function idAt(x,y){{ const i=(y*W+x)*4; return idData.data[i]+idData.data[i+1]*256; }}  // R+G*256
function revealRegion(id){{ if(id<=0||revealed.has(id)) return; revealed.add(id);
  const bb=bboxOf[id]; if(!bb) return; const d=display.data,m=masterData.data;
  for(let y=bb[1];y<bb[1]+bb[3];y++) for(let x=bb[0];x<bb[0]+bb[2];x++){{
    const i=(y*W+x)*4; if(idData.data[i]+idData.data[i+1]*256===id){{
      d[i]=m[i]; d[i+1]=m[i+1]; d[i+2]=m[i+2]; d[i+3]=255; }} }}
  repaint(); }}
function repaint(){{ ctx.putImageData(display,0,0);
  ctx.drawImage(border,0,0);                                   // thin black border ON TOP
  ctx.textAlign='center'; ctx.textBaseline='middle';
  for(const m of META){{ if(revealed.has(m.id)) continue;      // numbers on unrevealed cells
    const t=''+m.n, rr=(m.r||0.5*Math.min(m.bb[2],m.bb[3]));   // fit the number inside its cell
    let fp=Math.min(rr*1.6,46); ctx.font='700 '+fp+'px system-ui,Arial';
    let hd=0.5*Math.hypot(ctx.measureText(t).width,fp);
    while(hd>rr*0.9 && fp>9){{ fp-=1; ctx.font='700 '+fp+'px system-ui,Arial'; hd=0.5*Math.hypot(ctx.measureText(t).width,fp); }}
    if(fp<9) continue;
    ctx.lineWidth=Math.max(2,fp*0.16); ctx.strokeStyle='#fff'; ctx.strokeText(t,m.x,m.y);
    ctx.fillStyle='#141414'; ctx.fillText(t,m.x,m.y); }}
  document.getElementById('prog').textContent=revealed.size+' / '+META.length+' regions'+
    (revealed.size===META.length?' — complete (canvas = original)':''); }}
cv.addEventListener('click',e=>{{ const r=cv.getBoundingClientRect();
  const x=Math.floor((e.clientX-r.left)*W/r.width), y=Math.floor((e.clientY-r.top)*H/r.height);
  if(x>=0&&y>=0&&x<W&&y<H) revealRegion(idAt(x,y)); }});
function revealAll(){{ const d=display.data,m=masterData.data;
  for(let i=0;i<d.length;i++) d[i]=m[i]; for(const mm of META) revealed.add(mm.id); repaint(); }}
function reset(){{ display.data.fill(255); revealed.clear(); repaint(); }}
</script></body></html>"""


_PLAY_COLOR_HTML = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Colour Fill ({job})</title>
<style>
:root{{color-scheme:dark}}*{{box-sizing:border-box}}
body{{margin:0;background:#0f1216;color:#e6e9ef;font:14px system-ui,Segoe UI,Roboto,sans-serif;
display:flex;flex-direction:column;align-items:center;height:100vh;overflow:hidden}}
header{{width:100%;display:flex;gap:12px;align-items:center;padding:10px 16px;border-bottom:1px solid #262d37;background:#0f1216}}
header b{{font-size:15px}} .sp{{flex:1}} #prog{{color:#9aa4b2}}
button{{border:1px solid #2a323d;background:#161b22;color:#e6e9ef;padding:7px 14px;border-radius:7px;font-weight:600;cursor:pointer}}
button:hover{{border-color:#4f8cff;color:#4f8cff}}
#wrap{{flex:1;display:flex;align-items:center;justify-content:center;min-height:0;padding:12px;width:100%}}
canvas{{background:#fff;max-width:100%;max-height:100%;height:auto;border-radius:8px;box-shadow:0 6px 24px #0008;cursor:crosshair;touch-action:none}}
#palette{{width:100%;display:flex;gap:10px;overflow-x:auto;padding:12px 16px;background:#12161c;border-top:1px solid #262d37}}
.sw{{flex:0 0 auto;width:52px;height:52px;border-radius:50%;border:3px solid transparent;display:flex;align-items:center;
justify-content:center;font-weight:800;font-size:17px;cursor:pointer;position:relative;
background-size:cover;background-position:center;color:#fff;text-shadow:0 1px 3px #000,0 0 3px #000}}
.sw.on{{border-color:#fff;transform:scale(1.08)}} .sw.done{{opacity:.28}}
.sw .ct{{position:absolute;bottom:-4px;right:-4px;background:#0f1216;color:#9aa4b2;font-size:10px;font-weight:700;border-radius:8px;padding:0 4px;border:1px solid #2a323d}}
</style></head><body>
<header><b>ColorForge · Colour Fill</b><span id="prog">loading…</span><span class="sp"></span>
<button onclick="fillAll()">Fill all</button><button onclick="reset()">Reset</button></header>
<div id="wrap"><canvas id="c" width="{w}" height="{h}"></canvas></div>
<div id="palette"></div>
<script>
const W={w},H={h},META={meta},PALETTE={palette};
const metaById={{}}, idsByNum={{}};
for(const m of META){{ metaById[m.id]=m; (idsByNum[m.n]=idsByNum[m.n]||[]).push(m.id); }}
const NUMS=Object.keys(PALETTE).map(Number).sort((a,b)=>a-b);
const cv=document.getElementById('c'), ctx=cv.getContext('2d');
const master=new Image(), idmap=new Image(), border=new Image();
master.src="{master}"; idmap.src="{idmap}"; border.src="{border}";
let masterData,idData,display,filled=new Set(),filledByNum={{}},sel=NUMS[0],ready=0,anim=new Map(),raf=0;
const rgb=c=>'rgb('+c[0]+','+c[1]+','+c[2]+')';
const dark=c=>0.299*c[0]+0.587*c[1]+0.114*c[2]>150;
function boot(){{ if(++ready<3) return;
  const o=document.createElement('canvas'); o.width=W; o.height=H; const oc=o.getContext('2d');
  oc.drawImage(master,0,0); masterData=oc.getImageData(0,0,W,H).data;
  oc.clearRect(0,0,W,H); oc.drawImage(idmap,0,0); idData=oc.getImageData(0,0,W,H).data;
  display=ctx.createImageData(W,H); display.data.fill(255);
  for(const n of NUMS) filledByNum[n]=0; buildPalette(); repaint(); }}
master.onload=boot; idmap.onload=boot; border.onload=boot;
function idAt(x,y){{ const i=(y*W+x)*4; return idData[i]+idData[i+1]*256; }}
function fillRegion(id){{ if(id<=0||filled.has(id)) return false; const m=metaById[id]; if(!m) return false;
  filled.add(id); filledByNum[m.n]++; const bb=m.bb, idx=[];
  for(let y=bb[1];y<bb[1]+bb[3];y++) for(let x=bb[0];x<bb[0]+bb[2];x++){{
    const i=(y*W+x)*4; if(idData[i]+idData[i+1]*256===id) idx.push(i); }}
  anim.set(id,{{px:idx,t0:performance.now(),col:(PALETTE[m.n]&&PALETTE[m.n].c)||[200,200,200]}});  // flat -> photo
  if(!raf) raf=requestAnimationFrame(step); return true; }}
function step(now){{ let active=false; const d=display.data,mm=masterData;
  for(const [id,a] of anim){{ let t=(now-a.t0)/300; if(t>=1) t=1; const e=t*t*(3-2*t), c=a.col;
    for(const i of a.px){{ d[i]=c[0]+(mm[i]-c[0])*e; d[i+1]=c[1]+(mm[i+1]-c[1])*e; d[i+2]=c[2]+(mm[i+2]-c[2])*e; d[i+3]=255; }}
    if(t>=1) anim.delete(id); else active=true; }}
  repaint(); raf = active ? requestAnimationFrame(step) : 0; }}
function buildPalette(){{ const bar=document.getElementById('palette'); bar.innerHTML='';
  for(const n of NUMS){{ const p=PALETTE[n], b=document.createElement('button');
    b.className='sw'+(n===sel?' on':''); b.style.backgroundImage='url('+p.img+')';   // real photo texture
    b.textContent=n; b.dataset.n=n; const ct=document.createElement('span'); ct.className='ct'; ct.textContent=idsByNum[n].length; b.appendChild(ct);
    b.onclick=()=>selectColor(n); bar.appendChild(b); }} }}
function updatePalette(){{ for(const b of document.querySelectorAll('.sw')){{ const n=+b.dataset.n;
    b.classList.toggle('on',n===sel); const done=filledByNum[n]>=idsByNum[n].length; b.classList.toggle('done',done);
    b.querySelector('.ct').textContent=done?'✓':(idsByNum[n].length-filledByNum[n]); }} }}
function selectColor(n){{ sel=n; updatePalette(); repaint(); }}
function repaint(){{ ctx.putImageData(display,0,0); ctx.drawImage(border,0,0);
  ctx.textAlign='center'; ctx.textBaseline='middle';
  for(const m of META){{ if(filled.has(m.id)) continue; const t=''+m.n, rr=(m.r||0.5*Math.min(m.bb[2],m.bb[3]));
    let fp=Math.min(rr*0.85,22); ctx.font='700 '+fp+'px system-ui,Arial';
    let hd=0.5*Math.hypot(ctx.measureText(t).width,fp);
    while(hd>rr*0.72 && fp>7){{ fp-=1; ctx.font='700 '+fp+'px system-ui,Arial'; hd=0.5*Math.hypot(ctx.measureText(t).width,fp); }}
    if(m.n===sel){{ const c=PALETTE[m.n].c; ctx.beginPath(); ctx.arc(m.x,m.y,Math.max(fp*0.95,6),0,7); ctx.fillStyle=rgb(c); ctx.fill();
      if(fp>=7){{ ctx.font='700 '+fp+'px system-ui,Arial'; ctx.fillStyle=dark(c)?'#111':'#fff'; ctx.fillText(t,m.x,m.y); }} }}
    else if(fp>=8){{ ctx.font='400 '+fp+'px system-ui,Arial';
      ctx.lineWidth=Math.max(2,fp*0.14); ctx.strokeStyle='rgba(255,255,255,.9)'; ctx.strokeText(t,m.x,m.y);
      ctx.fillStyle='#7a7a7a'; ctx.fillText(t,m.x,m.y); }} }}
  document.getElementById('prog').textContent=filled.size+' / '+META.length+(filled.size===META.length?' — done! (real photo)':' coloured'); }}
cv.addEventListener('pointerdown',e=>{{ const r=cv.getBoundingClientRect();
  const x=Math.floor((e.clientX-r.left)*W/r.width), y=Math.floor((e.clientY-r.top)*H/r.height);
  if(x<0||y<0||x>=W||y>=H) return; const id=idAt(x,y); const m=metaById[id]; if(!m) return;
  if(m.n!==sel){{ sel=m.n; updatePalette(); }}
  if(fillRegion(id)){{ updatePalette(); autoAdvance(); }} }});
function autoAdvance(){{ if(filledByNum[sel]>=idsByNum[sel].length){{ const nx=NUMS.find(n=>filledByNum[n]<idsByNum[n].length); if(nx!=null){{ sel=nx; updatePalette(); }} }} }}
function fillAll(){{ for(const m of META) if(!filled.has(m.id)) fillRegion(m.id); updatePalette(); }}
function reset(){{ display.data.fill(255); filled.clear(); anim.clear(); for(const n of NUMS) filledByNum[n]=0; sel=NUMS[0]; updatePalette(); repaint(); }}
</script></body></html>"""
