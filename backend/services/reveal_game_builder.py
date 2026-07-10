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


def build(folder: Path) -> dict:
    """Build play_id_map.png, play_border.png and the self-contained play.html for a job.

    Number-by-Color from the clean AI line-art: playable regions are the line-art's own closed
    shapes (built + numbered by `lineart_region_builder`), the border is that line-art, and the
    reveal source is the immutable master (so reveal ΔE stays 0). Falls back to the colour-label
    regions + region-boundary border if the line-art model is unavailable."""
    folder = Path(folder)
    master = cv2.imread(str(folder / "reveal_master.png"))
    if master is None:
        raise FileNotFoundError("reveal_master.png missing — run the reveal pass first")
    h, w = master.shape[:2]
    job = folder.name
    if (folder / "game_config.json").exists():
        job = json.loads((folder / "game_config.json").read_text(encoding="utf-8")).get("job_id", job)

    line = _load_lineart(folder, h, w)
    if line is not None:                                   # regions FROM the line-art shapes
        fl = lineart_region_builder.build_regions(line)
        if (folder / "labels.npy").exists():               # split big cells by colour (distinct, harder)
            color = fill_labels(np.load(folder / "labels.npy").astype(np.int32))
            if color.shape != fl.shape:
                color = cv2.resize(color, (fl.shape[1], fl.shape[0]), interpolation=cv2.INTER_NEAREST)
            fl = lineart_region_builder.intersect_color(fl, color)
        numbers = lineart_region_builder.palette_numbers(master, fl)
        meta = lineart_region_builder.region_meta(fl, numbers)
        bdr = _ai_border_bgra(line)
        border_source, region_source = "ai_lineart", "lineart_shapes"
        palette_count = len(set(numbers.values()))
    else:                                                  # fallback: colour-label regions
        fl = fill_labels(np.load(folder / "labels.npy").astype(np.int32))
        gc = json.loads((folder / "game_config.json").read_text(encoding="utf-8"))
        meta = region_meta(fl, gc)
        bdr = border_bgra(fl)
        border_source, region_source = "region_boundary", "color_labels"
        palette_count = len({m["n"] for m in meta})

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
