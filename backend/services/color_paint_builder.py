"""Colour-based paint-by-number builder.

Segments an image by COLOUR into closed regions that TILE the whole image (every pixel in exactly
one region), so a fill/reveal of one region never leaks into the background and every region
boundary is a closed loop. Emits the assets an app (e.g. a Flutter Happy-Color-style client) needs:

  paint_border.svg    — one closed <path> per region (data-region / data-number / data-color)
  paint_regions.json  — {job, w, h, regions:[{id,n,cx,cy,r,bbox,color}], palette:[{number,color}]}
  paint_id_map.png    — region-id map (id = R + G*256) for fast hit-testing / masking
  paint_preview.png   — numbered page · colour-wise reveal · flat-colour complete

Segmentation runs at a reduced resolution for speed; coordinates are scaled back to full res.
`target` is the wanted region count (hit adaptively per image, so a simple and a busy picture land
near the same size). Same colour -> same palette number.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

WORK_MAX = 720          # segmentation resolution (long edge)
DEDUP_LAB = 14.0        # merge palette colours within this LAB distance
FONT = cv2.FONT_HERSHEY_SIMPLEX


def _quantize(img: np.ndarray, k: int):
    """Mean-shift smooth -> k-means palette -> merge near-duplicate colours -> majority-smooth the
    label map. Returns (cmap: palette index per pixel, centers: BGR palette)."""
    h, w = img.shape[:2]
    sm = cv2.pyrMeanShiftFiltering(img, 13, 30)
    z = sm.reshape(-1, 3).astype(np.float32)
    _, lab, centers = cv2.kmeans(z, k, None,
                                 (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 16, 1.0),
                                 2, cv2.KMEANS_PP_CENTERS)
    centers = centers.astype(np.uint8)
    cmap = lab.reshape(h, w).astype(np.int32)

    labc = cv2.cvtColor(centers.reshape(1, k, 3), cv2.COLOR_BGR2LAB).reshape(k, 3).astype(np.float32)
    parent = list(range(k))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]; x = parent[x]
        return x
    for a in range(k):
        for b in range(a + 1, k):
            if np.linalg.norm(labc[a] - labc[b]) < DEDUP_LAB:
                parent[find(a)] = find(b)
    roots = sorted(set(find(i) for i in range(k)))
    newidx = {r: i for i, r in enumerate(roots)}
    palmap = np.array([newidx[find(i)] for i in range(k)], np.int32)
    merged = np.array([centers[[i for i in range(k) if find(i) == r]].mean(0) for r in roots], np.uint8)
    cmap = palmap[cmap]
    kk = len(roots)

    for _ in range(2):                                  # majority vote in 3x3 -> smoother edges
        pad = np.pad(cmap, 1, mode="edge")
        stack = np.stack([pad[dy:dy + h, dx:dx + w] for dy in range(3) for dx in range(3)], -1)
        best = np.zeros((h, w), np.int32); out = np.zeros((h, w), np.int32)
        for c in range(kk):
            cnt = (stack == c).sum(-1)
            upd = cnt > best; best = np.where(upd, cnt, best); out = np.where(upd, c, out)
        cmap = out
    return cmap, merged


def _regions(cmap: np.ndarray, k: int) -> np.ndarray:
    """Connected components per colour -> region-id map (1..R) tiling the image."""
    h, w = cmap.shape
    reg = np.zeros((h, w), np.int32); rid = 0
    for c in range(k):
        n, cc = cv2.connectedComponents((cmap == c).astype(np.uint8), 8)
        if n > 1:
            m = cc > 0
            reg[m] = cc[m] + rid; rid = int(reg.max())
    return reg


def _merge_small(reg: np.ndarray, min_area: int, rounds: int = 30) -> np.ndarray:
    """Absorb regions below `min_area` into their dominant neighbour (vectorised, keeps tiling)."""
    reg = reg.copy()
    for _ in range(rounds):
        m = int(reg.max()) + 1
        counts = np.bincount(reg.ravel(), minlength=m)
        small = (counts > 0) & (counts < min_area); small[0] = False
        if not small.any():
            break
        a = np.concatenate([reg[:, :-1].ravel(), reg[:-1, :].ravel()])
        b = np.concatenate([reg[:, 1:].ravel(), reg[1:, :].ravel()])
        sel = (a != b) & small[a] & (b > 0)
        a, b = a[sel], b[sel]
        if a.size == 0:
            break
        key = a.astype(np.int64) * m + b
        uk, cnt = np.unique(key, return_counts=True)
        ua, ub = (uk // m).astype(np.int64), (uk % m).astype(np.int64)
        order = np.lexsort((cnt, ua)); ua, ub = ua[order], ub[order]
        lut = np.arange(m, dtype=np.int64); lut[ua] = ub
        for _ in range(6):
            lut = lut[lut]
        reg = lut[reg].astype(np.int32)
    return reg


def _hex(bgr) -> str:
    return "#%02x%02x%02x" % (int(bgr[2]), int(bgr[1]), int(bgr[0]))


def generate(original_path: Path, out_dir: Path, target: int = 170, k: int = 30) -> dict:
    """Build the paint-by-number assets for `original_path` into `out_dir`. Returns a summary dict."""
    original_path, out_dir = Path(original_path), Path(out_dir)
    full = cv2.imread(str(original_path))
    if full is None:
        raise FileNotFoundError(f"cannot read {original_path}")
    fh, fw = full.shape[:2]
    scale = WORK_MAX / max(fh, fw)
    img = cv2.resize(full, (int(fw * scale), int(fh * scale)), interpolation=cv2.INTER_AREA) if scale < 1 else full.copy()
    h, w = img.shape[:2]; n = h * w

    cmap, centers = _quantize(img, k)
    kk = int(cmap.max()) + 1
    reg = _regions(cmap, kk)
    raw = len(np.unique(reg)) - 1

    min_area = max(20, int(n * 0.0006))
    reg = _merge_small(reg, min_area)
    while len(np.unique(reg)) - 1 > target:
        min_area = int(min_area * 1.4); reg = _merge_small(reg, min_area)

    uni = [u for u in np.unique(reg) if u > 0]
    lut = np.zeros(int(reg.max()) + 1, np.int32)
    for i, u in enumerate(uni, 1):
        lut[u] = i
    reg = lut[reg]
    R = len(uni)

    regnum = np.zeros(R + 1, np.int32); regcol = np.zeros((R + 1, 3), np.uint8)
    for i in range(1, R + 1):
        ci = int(np.bincount(cmap[reg == i]).argmax())
        regnum[i] = ci + 1; regcol[i] = centers[ci]
    ncol = int(len(set(regnum[1:].tolist())))

    # per-region centre (inscribed) + bbox, coords scaled back to full res
    inv = 1.0 / scale if scale < 1 else 1.0
    meta = []
    for i in range(1, R + 1):
        ys, xs = np.where(reg == i)
        x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
        d = cv2.distanceTransform((reg[y0:y1 + 1, x0:x1 + 1] == i).astype(np.uint8), cv2.DIST_L2, 3)
        _, r, _, loc = cv2.minMaxLoc(d)
        meta.append({"id": i, "n": int(regnum[i]),
                     "cx": int((loc[0] + x0) * inv), "cy": int((loc[1] + y0) * inv), "r": round(r * inv, 1),
                     "bbox": [int(x0 * inv), int(y0 * inv), int((x1 - x0 + 1) * inv), int((y1 - y0 + 1) * inv)],
                     "color": _hex(regcol[i])})

    _write_preview(out_dir, reg, regcol, regnum, meta, R, ncol, scale)
    _write_idmap(out_dir, reg, inv, fw, fh)
    npaths = _write_svg(out_dir, reg, regnum, regcol, R, inv, fw, fh, meta)
    palette = [{"number": int(nm), "color": _hex(centers[nm - 1])} for nm in sorted(set(regnum[1:].tolist()))]
    (out_dir / "paint_regions.json").write_text(
        json.dumps({"job": original_path.parent.name, "w": fw, "h": fh, "regions": meta, "palette": palette},
                   separators=(",", ":")), encoding="utf-8")

    return {"regions": R, "colours": ncol, "raw_regions": raw, "coverage_pct": round(float((reg > 0).mean() * 100), 1),
            "tiles_full": bool((reg > 0).all()), "svg_paths": npaths, "work_size": [w, h],
            "assets": {"svg": "paint_border.svg", "json": "paint_regions.json",
                       "preview": "paint_preview.png", "id_map": "paint_id_map.png",
                       "original": original_path.name}}


def _write_idmap(out_dir: Path, reg: np.ndarray, inv: float, fw: int, fh: int) -> None:
    """Region-id map (id = R + G*256) at full resolution for hit-testing/masking."""
    big = cv2.resize(reg, (fw, fh), interpolation=cv2.INTER_NEAREST) if inv != 1.0 else reg
    out = np.zeros((fh, fw, 3), np.uint8)
    out[..., 2] = (big & 0xFF).astype(np.uint8)          # R (BGR image -> idx 2)
    out[..., 1] = ((big >> 8) & 0xFF).astype(np.uint8)   # G
    cv2.imwrite(str(out_dir / "paint_id_map.png"), out)


def _write_svg(out_dir: Path, reg, regnum, regcol, R, inv, fw, fh, meta) -> int:
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{fw}" height="{fh}" viewBox="0 0 {fw} {fh}">']
    npaths = 0
    for i in range(1, R + 1):
        cnts, _ = cv2.findContours((reg == i).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for c in cnts:
            c = cv2.approxPolyDP(c, 1.2, True)
            if len(c) < 3:
                continue
            d = "M " + " L ".join(f"{int(p[0][0] * inv)},{int(p[0][1] * inv)}" for p in c) + " Z"
            svg.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="1" '
                       f'data-region="{i}" data-number="{int(regnum[i])}" data-color="{meta[i-1]["color"]}"/>')
            npaths += 1
    svg.append("</svg>")
    (out_dir / "paint_border.svg").write_text("\n".join(svg), encoding="utf-8")
    return npaths


def _write_preview(out_dir: Path, reg, regcol, regnum, meta, R, ncol, scale) -> None:
    h, w = reg.shape
    e = np.zeros((h, w), bool)
    e[:, :-1] |= reg[:, :-1] != reg[:, 1:]; e[:-1, :] |= reg[:-1, :] != reg[1:, :]
    edge = cv2.dilate((e * 255).astype(np.uint8), np.ones((2, 2), np.uint8)) > 0
    sc = scale if scale < 1 else 1.0

    def numbers(canvas):
        for m in meta:
            if m["r"] * sc < 7:
                continue
            s = min(m["r"] * sc * 0.06, 0.55); t = str(m["n"])
            (tw, th), _ = cv2.getTextSize(t, FONT, s, 1)
            org = (int(m["cx"] * sc) - tw // 2, int(m["cy"] * sc) + th // 2)
            cv2.putText(canvas, t, org, FONT, s, (255, 255, 255), 3, cv2.LINE_AA)
            cv2.putText(canvas, t, org, FONT, s, (110, 110, 110), 1, cv2.LINE_AA)

    page = np.full((h, w, 3), 255, np.uint8); page[edge] = 0; numbers(page)
    flat = regcol[reg]; flat[edge] = 0
    half = set(sorted(set(regnum[1:].tolist()))[: max(1, ncol // 2)])
    keep = np.array([0] + [1 if int(regnum[i]) in half else 0 for i in range(1, R + 1)], bool)
    part = np.where(keep[reg][..., None], regcol[reg], 255).astype(np.uint8); part[edge] = 0

    def tag(im, t):
        im = im.copy(); cv2.rectangle(im, (0, 0), (im.shape[1], 30), (20, 20, 20), -1)
        cv2.putText(im, t, (8, 21), FONT, 0.5, (255, 255, 255), 1, cv2.LINE_AA); return im
    board = np.hstack([tag(page, f"numbered ({R} regions, {ncol} colours)"),
                       tag(part, "colour-wise reveal (partial)"), tag(flat, "complete")])
    cv2.imwrite(str(out_dir / "paint_preview.png"), board)
