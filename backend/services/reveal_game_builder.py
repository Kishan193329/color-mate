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


# Colour-fill game tuning: denser regions + a big palette so one image is a ~15-minute,
# non-boring colour session (many taps, many colours to switch between).
COLOR_R_MIN = 6.0        # ~400 regions on a typical image
COLOR_PALETTE = 110      # 100+ palette colours


def _palette_avg(master: np.ndarray, fl: np.ndarray, meta: list) -> dict:
    """One representative colour (R,G,B) per palette number = median original colour over that
    number's regions. Used as the palette swatch so it FEELS like choosing a paint colour."""
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


def build_color(folder: Path) -> dict:
    """Colour-FILL play page: same regions + reveal-the-original tech, but a paint-by-number FEEL —
    a palette of the regions' own colours; pick a colour, tap its numbered regions, each paints its
    flat colour and then morphs into the ORIGINAL photo pixels. Completing paints the real photo."""
    folder = Path(folder)
    job = folder.name
    if (folder / "game_config.json").exists():
        job = json.loads((folder / "game_config.json").read_text(encoding="utf-8")).get("job_id", job)
    # denser regions + a big palette so one image is a long (~15 min), non-boring colour session
    g = game_regions(folder, r_min=COLOR_R_MIN, n_colors=COLOR_PALETTE)
    fl, meta, bdr, master = g["fl"], g["meta"], g["border"], g["master"]
    h, w = fl.shape
    idm = id_map_bgra(fl)
    cv2.imwrite(str(folder / "play_id_map.png"), idm)
    cv2.imwrite(str(folder / "play_border.png"), bdr)
    palette = _palette_avg(master, fl, meta)
    html = _PLAY_COLOR_HTML.format(
        job=job, w=w, h=h,
        master=_data_uri((folder / "reveal_master.png").read_bytes()),
        idmap=_data_uri(_png(idm)), border=_data_uri(_png(bdr)),
        meta=json.dumps(meta, separators=(",", ":")),
        palette=json.dumps(palette, separators=(",", ":")))
    (folder / "play_color.html").write_text(html, encoding="utf-8")
    return {"mode": "colour_fill_reveal", "regions": len(meta), "palette_numbers": len(palette),
            "assets": {"id_map": "play_id_map.png", "border": "play_border.png", "page": "play_color.html"}}


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
justify-content:center;font-weight:800;font-size:17px;cursor:pointer;position:relative}}
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
  anim.set(id,{{px:idx,t0:performance.now(),col:PALETTE[m.n]||[200,200,200]}});   // paint flat -> morph to photo
  if(!raf) raf=requestAnimationFrame(step); return true; }}
function step(now){{ let active=false; const d=display.data,mm=masterData;
  for(const [id,a] of anim){{ let t=(now-a.t0)/300; if(t>=1) t=1; const e=t*t*(3-2*t), c=a.col;
    for(const i of a.px){{ d[i]=c[0]+(mm[i]-c[0])*e; d[i+1]=c[1]+(mm[i+1]-c[1])*e; d[i+2]=c[2]+(mm[i+2]-c[2])*e; d[i+3]=255; }}
    if(t>=1) anim.delete(id); else active=true; }}
  repaint(); raf = active ? requestAnimationFrame(step) : 0; }}
function buildPalette(){{ const bar=document.getElementById('palette'); bar.innerHTML='';
  for(const n of NUMS){{ const c=PALETTE[n], b=document.createElement('button');
    b.className='sw'+(n===sel?' on':''); b.style.background=rgb(c); b.style.color=dark(c)?'#111':'#fff';
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
    if(m.n===sel){{ const c=PALETTE[m.n]; ctx.beginPath(); ctx.arc(m.x,m.y,Math.max(fp*0.95,6),0,7); ctx.fillStyle=rgb(c); ctx.fill();
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
