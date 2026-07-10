"""Stage 3 (STANDALONE, not wired to production): build playable Number-by-Color regions FROM
the clean AI line-art — not from colour polygons, not Voronoi. Pipeline:

  original -> AI clean line-art -> binarize -> close only small gaps -> flood-fill the CLOSED
  white cells between the lines -> merge tiny cells into their dominant neighbour -> absorb the
  line pixels into the nearest cell so masks tile the image -> sample original colour per cell ->
  group matching colours into palette numbers -> place a number inside each cell (sized to fit
  its cell, so numbers never overlap) -> tap-reveal original pixels per cell.

Honours the hard rules: no Voronoi/polygon fallback (cells are the line-art's own closed areas),
masks tile the image, reveal delta-E stays 0 (reveal is the original master), numbers sized to
their cell (no overlap). Produces the 5 required previews for a test image + verification.
"""
from __future__ import annotations
import io, json, sys
from pathlib import Path
import numpy as np
import cv2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
from services import ai_lineart_local as AL  # noqa
from services import reveal_game_builder as RG  # noqa

OUT = REPO / "research" / "08_reports" / "lineart_eval"
OUT.mkdir(parents=True, exist_ok=True)
FONT = cv2.FONT_HERSHEY_SIMPLEX


def merge_tiny(lab: np.ndarray, min_area: int, rounds: int = 8) -> np.ndarray:
    lab = lab.copy()
    for _ in range(rounds):
        areas = np.bincount(lab.ravel())
        tiny = [i for i in range(1, len(areas)) if 0 < areas[i] < min_area]
        if not tiny:
            break
        for t in tiny:
            m = lab == t
            if not m.any():
                continue
            edge = cv2.dilate(m.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~m
            nb = lab[edge]
            nb = nb[(nb != 0) & (nb != t)]
            if nb.size:
                lab[m] = int(np.bincount(nb).argmax())
    return lab


def lineart_regions(line_gray: np.ndarray, min_frac: float = 5e-4, close: int = 3):
    """Closed cells from the line-art, tiling the image. Returns labels (1..K) + line mask."""
    n_px = line_gray.size
    lines = (line_gray < 120).astype(np.uint8)                       # solid ink
    lines = cv2.morphologyEx(lines, cv2.MORPH_CLOSE, np.ones((close, close), np.uint8))  # close SMALL gaps only
    white = (lines == 0).astype(np.uint8)
    _, lab = cv2.connectedComponents(white, connectivity=4)          # 0 = line pixels, 1..n = closed cells
    lab = merge_tiny(lab, max(80, int(n_px * min_frac)))             # reject tiny arbitrary cells
    fl = RG.fill_labels(lab.astype(np.int32))                        # absorb line pixels -> tiles 100%
    # relabel contiguous 1..K
    uniq = [u for u in np.unique(fl) if u != 0]
    remap = {u: i + 1 for i, u in enumerate(uniq)}
    out = np.zeros_like(fl)
    for u, i in remap.items():
        out[fl == u] = i
    return out, lines


def palette_numbers(original: np.ndarray, fl: np.ndarray, n_colors: int = 24) -> dict:
    ids = [int(i) for i in np.unique(fl) if i != 0]
    cols = np.array([np.median(original[fl == i].reshape(-1, 3), axis=0) for i in ids], np.float32)
    k = min(n_colors, len(ids))
    _, klab, _ = cv2.kmeans(cols, k, None,
                            (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5),
                            4, cv2.KMEANS_PP_CENTERS)
    return {ids[j]: int(klab[j][0]) + 1 for j in range(len(ids))}


def region_meta(fl: np.ndarray, numbers: dict) -> list:
    meta = []
    for i in [int(u) for u in np.unique(fl) if u != 0]:
        m = (fl == i).astype(np.uint8)
        dist = cv2.distanceTransform(m, cv2.DIST_L2, 3)
        _, r, _, loc = cv2.minMaxLoc(dist)                           # most-interior point + inscribed radius
        meta.append({"id": i, "n": numbers[i], "x": int(loc[0]), "y": int(loc[1]), "r": float(r)})
    return meta


def _fit_scale(text: str, r: float) -> float:
    """Largest font scale whose text half-diagonal fits inside the cell radius (no overlap)."""
    for s in (1.3, 1.1, 0.9, 0.75, 0.6, 0.48, 0.38, 0.3):
        (tw, th), _ = cv2.getTextSize(text, FONT, s, max(1, int(s * 2)))
        if 0.5 * (tw ** 2 + th ** 2) ** 0.5 < r * 0.92:
            return s
    return 0.0                                                        # cell too small to place a legible number


def draw_numbers(canvas: np.ndarray, meta: list, revealed: set) -> None:
    for m in meta:
        if m["id"] in revealed:
            continue
        s = _fit_scale(str(m["n"]), m["r"])
        if s <= 0:
            continue
        th = max(1, int(round(s * 2)))
        (tw, ht), _ = cv2.getTextSize(str(m["n"]), FONT, s, th)
        org = (int(m["x"] - tw / 2), int(m["y"] + ht / 2))
        cv2.putText(canvas, str(m["n"]), org, FONT, s, (255, 255, 255), th + 2, cv2.LINE_AA)
        cv2.putText(canvas, str(m["n"]), org, FONT, s, (20, 20, 20), th, cv2.LINE_AA)


def compose(fl, master, border, meta, revealed, numbers=True):
    content = np.full_like(master, 255)
    if revealed:
        mask = np.isin(fl, list(revealed))
        content[mask] = master[mask]
    a = border[..., 3:4].astype(np.float32) / 255.0
    disp = (content.astype(np.float32) * (1 - a)).astype(np.uint8)    # AI line-art border on top
    if numbers:
        draw_numbers(disp, meta, revealed)
    return disp, content


def run(job: str):
    f = REPO / "storage" / "jobs" / job
    master = cv2.imread(str(f / "reveal_master.png"))
    h, w = master.shape[:2]
    line = AL.generate(f / "original.png", None, model="lineart", darken=1.5, size=1024)
    line = cv2.resize(line, (w, h), interpolation=cv2.INTER_AREA)
    fl, lines = lineart_regions(line)
    numbers = palette_numbers(master, fl)
    meta = region_meta(fl, numbers)
    ids = [m["id"] for m in meta]
    border = RG._ai_border_bgra(line)

    # deliverables 2-6
    p_border, _ = compose(fl, master, border, meta, set(), numbers=False)
    p_numbered, _ = compose(fl, master, border, meta, set(), numbers=True)
    biggest = max(meta, key=lambda m: m["r"])["id"]
    p_one, _ = compose(fl, master, border, meta, {biggest}, numbers=True)
    quarter = set(ids[: max(1, len(ids) // 4)])
    p_25, _ = compose(fl, master, border, meta, quarter, numbers=True)
    p_done, content_done = compose(fl, master, border, meta, set(ids), numbers=False)

    # verification
    tiled = bool((fl > 0).all())
    dE0 = bool(np.array_equal(content_done, master))
    diff = int(np.abs(content_done.astype(int) - master.astype(int)).max())
    n_palette = len(set(numbers.values()))
    print(f"[{job}] regions={len(ids)} palette_numbers={n_palette} tiles_100%={tiled} "
          f"reveal==original={dE0} (max_diff={diff}) n_placed_numbers="
          f"{sum(1 for m in meta if _fit_scale(str(m['n']), m['r'])>0)}")

    def panel(im, t):
        p = cv2.resize(im, (440, 440)); cv2.rectangle(p, (0, 0), (440, 24), (28, 28, 34), -1)
        cv2.putText(p, t, (6, 17), FONT, 0.52, (240, 240, 245), 1, cv2.LINE_AA); return p
    g = np.full((440, 5, 3), 200, np.uint8)
    board = np.hstack([panel(p_border, "2 clean border"), g, panel(p_numbered, "3 numbered"), g,
                       panel(p_one, "4 one-region reveal"), g, panel(p_25, "5 25% reveal"), g,
                       panel(p_done, "6 completed = original")])
    cv2.imwrite(str(OUT / f"previews_{job}.png"), board)
    return {"job": job, "regions": len(ids), "palette_numbers": n_palette, "tiles_100pct": tiled,
            "reveal_eq_original": dE0, "max_diff": diff}


if __name__ == "__main__":
    res = [run("br_03"), run("br_14")]
    (OUT / "stage3_verification.json").write_text(json.dumps(res, indent=2), encoding="utf-8")
    print("done")
