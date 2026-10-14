"""Diamond-painting engine — a completely different content type from colour-fill.

A diamond painting is a GRID of round beads; every cell is exactly one palette colour. So the
correct model is not line-art regions but:

    image -> detect bead pitch -> grid (cols x rows) -> one colour per cell -> quantise to a
    palette of N colours -> each cell carries its palette number.

The game: pick a colour, then tap/drag its cells — each places one round bead. Finish the grid and
you have the exact artwork. Deterministic and pixel-accurate, which is why it looks right where the
line-art engine cannot.

Outputs: grid + palette (JSON), a finished bead render, and the numbered blank canvas.
"""
from __future__ import annotations

import cv2
import numpy as np

MIN_PITCH, MAX_PITCH = 6, 44        # plausible bead spacing in source pixels


# ── grid detection ────────────────────────────────────────────────────────────
def detect_pitch(img: np.ndarray) -> int:
    """Estimate bead spacing (px) from the repeating structure, via autocorrelation of the
    edge profile in both axes. Works when the source is an already-beaded render."""
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
    best = []
    for axis in (0, 1):
        d = cv2.Sobel(g, cv2.CV_32F, 1 - axis, axis, ksize=3)
        prof = np.abs(d).mean(axis=axis)
        prof = prof - prof.mean()
        ac = np.correlate(prof, prof, mode="full")[len(prof) - 1:]
        seg = ac[MIN_PITCH:MAX_PITCH]
        if seg.size:
            best.append(int(np.argmax(seg)) + MIN_PITCH)
    return int(round(float(np.median(best)))) if best else 14


def _cell_colours(img: np.ndarray, cols: int, rows: int) -> np.ndarray:
    """Median colour of each cell's centre (ignores bead gaps and specular highlights)."""
    h, w = img.shape[:2]
    out = np.zeros((rows, cols, 3), np.float32)
    xs = np.linspace(0, w, cols + 1).astype(int)
    ys = np.linspace(0, h, rows + 1).astype(int)
    for r in range(rows):
        y0, y1 = ys[r], ys[r + 1]
        cy0, cy1 = y0 + (y1 - y0) // 4, y1 - (y1 - y0) // 4          # central 50 %
        for c in range(cols):
            x0, x1 = xs[c], xs[c + 1]
            cx0, cx1 = x0 + (x1 - x0) // 4, x1 - (x1 - x0) // 4
            patch = img[max(cy0, y0):max(cy1, cy0 + 1), max(cx0, x0):max(cx1, cx0 + 1)]
            if patch.size == 0:
                patch = img[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)]
            out[r, c] = np.median(patch.reshape(-1, 3), axis=0)
    return out


def build(img: np.ndarray, cols: int | None = None, k: int = 32) -> dict:
    """Return {cols, rows, pitch, grid (rows x cols palette indices), palette [[B,G,R]…]}."""
    h, w = img.shape[:2]
    pitch = detect_pitch(img)
    if cols is None:
        cols = max(20, min(220, int(round(w / max(pitch, 1)))))
    rows = max(1, int(round(cols * h / w)))
    cells = _cell_colours(img, cols, rows)

    flat = cells.reshape(-1, 3).astype(np.float32)
    k = int(max(2, min(k, len(np.unique(flat, axis=0)))))
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5)
    _, labels, centres = cv2.kmeans(flat, k, None, crit, 4, cv2.KMEANS_PP_CENTERS)
    grid = labels.reshape(rows, cols).astype(np.int32)
    palette = np.clip(centres, 0, 255).astype(np.uint8)

    # order palette dark -> light so numbering feels natural, remap the grid to match
    order = np.argsort(palette.astype(np.int32).sum(axis=1))
    remap = np.zeros(len(order), np.int32)
    remap[order] = np.arange(len(order))
    return {"cols": cols, "rows": rows, "pitch": pitch,
            "grid": remap[grid], "palette": palette[order]}


# ── rendering ─────────────────────────────────────────────────────────────────
def _bead_sprite(size: int):
    """(alpha, shade, spec) for one round bead. The shading is NORMALISED so a bead's average
    colour equals its palette colour — otherwise the dome + highlight wash the picture out."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = (size - 1) / 2.0
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (size / 2.0)
    alpha = np.clip((0.98 - r) * size * 0.7, 0, 1)                      # anti-aliased disc
    lit = np.clip(1.0 - (((xx - c * 0.6) ** 2 + (yy - c * 0.6) ** 2) / (size * size * 0.42)), 0, 1)
    shade = 0.86 + 0.30 * lit - 0.26 * np.clip(r - 0.5, 0, 1)           # dome + rim falloff
    spec = np.exp(-(((xx - c * 0.62) ** 2 + (yy - c * 0.62) ** 2) / (size * 0.05 + 1e-6))) * 0.28
    w = alpha + 1e-6
    add = float((spec * w).sum() / w.sum())                             # white the highlight adds
    shade = shade / float((shade * w).sum() / w.sum())                  # mean multiplier -> 1.0
    shade = shade * (1.0 - add)                                         # …then pay for the highlight
    return alpha[..., None], shade[..., None], spec[..., None]


def render_beads(d: dict, cell: int = 16) -> np.ndarray:
    """The finished painting: every cell drawn as a real round bead on the dark adhesive canvas
    (the gaps are dark, like a real diamond painting — that is what keeps the colours rich)."""
    rows, cols, grid, pal = d["rows"], d["cols"], d["grid"], d["palette"]
    canvas = np.zeros((rows * cell, cols * cell, 3), np.float32)
    alpha, shade, spec = _bead_sprite(cell)
    for r in range(rows):
        y = r * cell
        for c in range(cols):
            col = pal[grid[r, c]].astype(np.float32)
            bead = np.clip(col * shade + 255.0 * spec, 0, 255)
            x = c * cell
            canvas[y:y + cell, x:x + cell] = bead * alpha + (col * 0.60) * (1 - alpha)
    return canvas.astype(np.uint8)


def render_canvas(d: dict, cell: int = 22) -> np.ndarray:
    """The blank printed canvas the player starts from: numbered empty cells."""
    rows, cols, grid = d["rows"], d["cols"], d["grid"]
    img = np.full((rows * cell, cols * cell, 3), 252, np.uint8)
    f, fs = cv2.FONT_HERSHEY_SIMPLEX, cell / 46.0
    for r in range(rows):
        for c in range(cols):
            x, y = c * cell, r * cell
            cv2.rectangle(img, (x + 1, y + 1), (x + cell - 2, y + cell - 2), (226, 222, 216), 1)
            n = str(int(grid[r, c]) + 1)
            (tw, th), _ = cv2.getTextSize(n, f, fs, 1)
            cv2.putText(img, n, (x + (cell - tw) // 2, y + (cell + th) // 2), f, fs,
                        (150, 145, 138), 1, cv2.LINE_AA)
    return img


def to_json(d: dict) -> dict:
    """App/game payload: palette as #rrggbb + the grid as rows of palette numbers (1-based)."""
    pal = [f"#{int(p[2]):02x}{int(p[1]):02x}{int(p[0]):02x}" for p in d["palette"]]
    return {"cols": d["cols"], "rows": d["rows"], "colours": len(pal), "palette": pal,
            "grid": [[int(v) + 1 for v in row] for row in d["grid"]]}


# ── playable game page ────────────────────────────────────────────────────────
def play_page(d: dict, title: str = "Diamond painting") -> str:
    """Self-contained playable diamond-painting page: pick a colour, tap or drag its cells,
    each places one bead. Pinch/wheel to zoom, two fingers (or right-drag) to pan."""
    import json as _json
    return _PLAY_HTML.replace("__DATA__", _json.dumps(to_json(d), separators=(",", ":"))) \
                     .replace("__TITLE__", title)


_PLAY_HTML = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>__TITLE__ — ColorForge Diamond</title>
<style>
 *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
 html,body{margin:0;height:100%;overflow:hidden;background:#17161a;color:#F0ECE4;
   font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;touch-action:none;user-select:none}
 #top{position:fixed;top:0;left:0;right:0;padding:10px 14px;display:flex;align-items:center;gap:11px;
   background:linear-gradient(#17161af2,#17161a00);z-index:5}
 #ttl{font-weight:750;font-size:14px;max-width:34vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
 #prog{flex:1;height:8px;border-radius:20px;background:#ffffff18;overflow:hidden;position:relative}
 #prog i{display:block;height:100%;width:0;border-radius:20px;
   background:linear-gradient(90deg,#7CC7FF,#C9A0FF,#FFD36E);transition:width .3s cubic-bezier(.2,.7,.2,1)}
 #pct{font-size:12.5px;font-weight:800;font-variant-numeric:tabular-nums;color:#D8D2C7;min-width:36px;text-align:right}
 .tb{width:34px;height:34px;border-radius:10px;border:1px solid #ffffff22;background:#ffffff12;color:#E8E2D8;
   display:grid;place-items:center;cursor:pointer;font-size:15px}
 canvas{position:absolute;inset:0;width:100%;height:100%;display:block}
 #pal{position:fixed;left:0;right:0;bottom:0;display:flex;gap:9px;padding:14px 12px 18px;overflow-x:auto;
   background:linear-gradient(#17161a00,#17161ae8 34%);z-index:5;scrollbar-width:none}
 #pal::-webkit-scrollbar{display:none}
 .sw{flex:none;width:58px;height:68px;border-radius:15px;border:2px solid #ffffff1f;background:#25232a;
   display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;cursor:pointer;transition:.16s}
 .sw.on{border-color:#fff;transform:translateY(-8px) scale(1.04);box-shadow:0 8px 22px #000a}
 .sw .dot{width:28px;height:28px;border-radius:50%;position:relative;
   box-shadow:inset -3px -4px 6px #0007, inset 3px 4px 7px #ffffffaa, 0 1px 3px #0006}
 .sw .dot::after{content:"";position:absolute;left:22%;top:18%;width:26%;height:26%;border-radius:50%;background:#fff9}
 .sw .n{font-size:11.5px;font-weight:850;font-variant-numeric:tabular-nums}
 .sw .left{font-size:10px;color:#B4ADA1;font-variant-numeric:tabular-nums}
 .sw.done{opacity:.32}.sw.done .left::after{content:" ✓"}
 #hint{position:fixed;left:50%;transform:translateX(-50%);bottom:104px;background:#000000c4;padding:8px 15px;
   border-radius:22px;font-size:12.5px;font-weight:600;z-index:6;pointer-events:none;transition:.3s;opacity:0}
 #hint.on{opacity:1}
 #done{position:fixed;inset:0;display:none;place-items:center;background:#17161ae8;z-index:9;text-align:center}
 #done.on{display:grid} #done h2{font-size:28px;margin:0 0 6px}#done p{color:#B4ADA1;margin:0}
</style></head><body>
<div id="top"><div id="ttl">__TITLE__</div><div id="prog"><i id="pi"></i></div><div id="pct">0%</div>
  <button class="tb" id="snd" title="Sound">🔊</button><button class="tb" id="fitb" title="Fit">⤢</button></div>
<canvas id="cv"></canvas>
<div id="hint"></div>
<div id="pal"></div>
<div id="done"><div><h2>✨ Finished!</h2><p>Every diamond placed — the picture is complete.</p></div></div>
<script>
const D=__DATA__;
const COLS=D.cols,ROWS=D.rows,PAL=D.palette,N=COLS*ROWS,CS=26;      // CS = board render size per cell
const PAPER='#EFE9DD', INK='rgba(64,56,46,.66)';
const grid=new Int16Array(N); for(let r=0;r<ROWS;r++)for(let c=0;c<COLS;c++)grid[r*COLS+c]=D.grid[r][c]-1;
const filled=new Uint8Array(N);
const total=new Int32Array(PAL.length), left=new Int32Array(PAL.length);
for(let i=0;i<N;i++){total[grid[i]]++;left[grid[i]]++;}
let sel=0, placed=0;
const RGB=PAL.map(h=>[parseInt(h.slice(1,3),16),parseInt(h.slice(3,5),16),parseInt(h.slice(5,7),16)]);
const cv=document.getElementById('cv'), ctx=cv.getContext('2d',{alpha:false});
const board=document.createElement('canvas'); board.width=COLS*CS; board.height=ROWS*CS;
const bx=board.getContext('2d');
let DPR=Math.min(devicePixelRatio||1,2),W=0,H=0,scale=1,ox=0,oy=0;
const pops=[],glints=[];

function sh(g,d){const c=RGB[g];const f=v=>Math.max(0,Math.min(255,v+d));return `rgb(${f(c[0])},${f(c[1])},${f(c[2])})`;}
function rr(x,y,w,h,r){bx.beginPath();bx.moveTo(x+r,y);bx.arcTo(x+w,y,x+w,y+h,r);bx.arcTo(x+w,y+h,x,y+h,r);
  bx.arcTo(x,y+h,x,y,r);bx.arcTo(x,y,x+w,y,r);bx.closePath();}

/* one bead, drawn into any 2d context */
function bead(g2,x,y,s,gi,a){
  const r=s/2, cx=x+r, cy=y+r;
  g2.save(); if(a!==undefined)g2.globalAlpha=a;
  const grd=g2.createRadialGradient(cx-r*0.32,cy-r*0.36,r*0.10,cx,cy,r*1.02);
  grd.addColorStop(0,sh(gi,62)); grd.addColorStop(.45,PAL[gi]); grd.addColorStop(1,sh(gi,-42));
  g2.fillStyle=grd; g2.beginPath(); g2.arc(cx,cy,r*0.93,0,7); g2.fill();
  g2.strokeStyle='rgba(0,0,0,.16)'; g2.lineWidth=Math.max(.6,s*.035); g2.stroke();
  g2.fillStyle='rgba(255,255,255,.72)';                       // specular
  g2.beginPath(); g2.ellipse(cx-r*0.30,cy-r*0.34,r*0.20,r*0.15,-0.6,0,7); g2.fill();
  g2.fillStyle='rgba(255,255,255,.28)';
  g2.beginPath(); g2.arc(cx+r*0.30,cy+r*0.32,r*0.11,0,7); g2.fill();
  g2.restore();
}
function socket(x,y,gi){                                       // empty bead slot on the canvas
  const s=CS, r=s/2, cx=x+r, cy=y+r, isSel=gi===sel;
  bx.fillStyle=PAPER; bx.fillRect(x,y,s,s);
  bx.globalAlpha=isSel?0.42:0.17; bx.fillStyle=PAL[gi];        // faint tint -> picture shows through
  rr(x+1,y+1,s-2,s-2,s*0.3); bx.fill(); bx.globalAlpha=1;
  bx.strokeStyle=isSel?PAL[gi]:'rgba(70,60,48,.16)';           // the round socket ring
  bx.lineWidth=isSel?2.4:1; bx.beginPath(); bx.arc(cx,cy,r*0.62,0,7); bx.stroke();
  if(isSel){bx.strokeStyle='rgba(255,255,255,.55)';bx.lineWidth=1;bx.beginPath();bx.arc(cx,cy,r*0.62,0,7);bx.stroke();}
  bx.fillStyle=isSel?'#2A241C':INK;
  bx.font=`${isSel?800:700} ${Math.round(s*0.4)}px system-ui`;
  bx.textAlign='center'; bx.textBaseline='middle'; bx.fillText(gi+1,cx,cy+s*0.02);
}
function paintCell(c,r){const i=r*COLS+c,x=c*CS,y=r*CS;
  bx.clearRect(x,y,CS,CS); if(filled[i])
    {bx.fillStyle=PAPER;bx.fillRect(x,y,CS,CS);bead(bx,x,y,CS,grid[i]);} else socket(x,y,grid[i]);}
function paintBoard(){for(let r=0;r<ROWS;r++)for(let c=0;c<COLS;c++)paintCell(c,r);}

function resize(){W=cv.clientWidth;H=cv.clientHeight;cv.width=W*DPR;cv.height=H*DPR;
  ctx.setTransform(DPR,0,0,DPR,0,0);fit();}
function fit(){const s=Math.min(W/COLS,(H-186)/ROWS);scale=s;ox=(W-COLS*s)/2;oy=(H-186-ROWS*s)/2+62;}

function frame(t){
  ctx.fillStyle='#17161a';ctx.fillRect(0,0,W,H);
  ctx.save();
  ctx.shadowColor='rgba(0,0,0,.55)';ctx.shadowBlur=26;ctx.fillStyle=PAPER;
  ctx.fillRect(ox,oy,COLS*scale,ROWS*scale);ctx.restore();
  ctx.imageSmoothingEnabled=true;
  ctx.drawImage(board,ox,oy,COLS*scale,ROWS*scale);
  /* pop-in animations */
  for(let k=pops.length-1;k>=0;k--){const p=pops[k],e=(t-p.t)/300;
    if(e>=1){pops.splice(k,1);continue;}
    const x=ox+p.c*scale,y=oy+p.r*scale;
    const k2=e<.45?1.6*Math.sin(e/.45*1.57):1+0.22*Math.cos((e-.45)/.55*3.14);
    const s2=scale*Math.max(.2,k2), d=(s2-scale)/2;
    ctx.save();ctx.globalAlpha=1;bead(ctx,x-d,y-d,s2,p.g);ctx.restore();
    ctx.save();ctx.globalAlpha=(1-e)*.55;ctx.strokeStyle='#fff';ctx.lineWidth=2;
    ctx.beginPath();ctx.arc(x+scale/2,y+scale/2,scale*(.5+e*1.3),0,7);ctx.stroke();ctx.restore();
    ctx.save();ctx.globalAlpha=(1-e);star(ctx,x+scale/2,y+scale/2,scale*(.5+e*.7));ctx.restore();
  }
  /* idle sparkle on placed diamonds — the glow */
  if(placed>0&&Math.random()<.16){const tries=6;
    for(let q=0;q<tries;q++){const c=(Math.random()*COLS)|0,r=(Math.random()*ROWS)|0;
      if(filled[r*COLS+c]){const x=ox+c*scale,y=oy+r*scale;
        if(x>-scale&&y>-scale&&x<W&&y<H){glints.push({c,r,t});} break;}}}
  for(let k=glints.length-1;k>=0;k--){const gl=glints[k],e=(t-gl.t)/620;
    if(e>=1){glints.splice(k,1);continue;}
    const a=Math.sin(e*3.14);
    ctx.save();ctx.globalAlpha=a*.95;
    star(ctx,ox+gl.c*scale+scale/2,oy+gl.r*scale+scale/2,scale*(.55+a*.55));ctx.restore();
  }
  requestAnimationFrame(frame);
}
function star(g2,x,y,r){                                        /* 4-point sparkle */
  g2.fillStyle='#fff';g2.beginPath();
  g2.moveTo(x,y-r);g2.quadraticCurveTo(x+r*.14,y-r*.14,x+r,y);
  g2.quadraticCurveTo(x+r*.14,y+r*.14,x,y+r);g2.quadraticCurveTo(x-r*.14,y+r*.14,x-r,y);
  g2.quadraticCurveTo(x-r*.14,y-r*.14,x,y-r);g2.fill();
}

/* ---- sound (synthesised, no files) ---- */
let AC=null,muted=false;
function tone(f,dur,type,vol){ if(muted)return;
  try{AC=AC||new (window.AudioContext||window.webkitAudioContext)();
    const o=AC.createOscillator(),g=AC.createGain();o.type=type||'triangle';o.frequency.value=f;
    g.gain.setValueAtTime(0,AC.currentTime);g.gain.linearRampToValueAtTime(vol||.05,AC.currentTime+.006);
    g.gain.exponentialRampToValueAtTime(.0001,AC.currentTime+(dur||.09));
    o.connect(g);g.connect(AC.destination);o.start();o.stop(AC.currentTime+(dur||.09)+.03);}catch(e){}}
function plink(){tone(880+Math.random()*260,.08,'triangle',.045);}
function chime(){[660,880,1320].forEach((f,i)=>setTimeout(()=>tone(f,.22,'sine',.05),i*90));}

function place(c,r){
  const i=r*COLS+c; filled[i]=1;placed++;left[sel]--;
  paintCell(c,r); pops.push({c,r,g:sel,t:performance.now()}); plink();
}
function paintAt(px,py,tapped){
  const c=Math.floor((px-ox)/scale),r=Math.floor((py-oy)/scale);
  if(c<0||r<0||c>=COLS||r>=ROWS)return false;
  const i=r*COLS+c; if(filled[i])return false;
  if(grid[i]!==sel){if(tapped)flash('That one is colour '+(grid[i]+1));return false;}
  place(c,r);return true;
}
let hintT=null;
function flash(m){const h=document.getElementById('hint');h.textContent=m;h.classList.add('on');
  clearTimeout(hintT);hintT=setTimeout(()=>h.classList.remove('on'),1200);}
function progress(){
  document.getElementById('pi').style.width=(placed/N*100)+'%';
  document.getElementById('pct').textContent=Math.round(placed/N*100)+'%';
  [...document.querySelectorAll('.sw')].forEach((b,i)=>{
    b.querySelector('.left').textContent=left[i]>0?left[i]:'done';b.classList.toggle('done',left[i]===0);});
  if(left[sel]===0){const nxt=left.findIndex(v=>v>0);
    if(nxt>=0){chime();pick(nxt);flash('Colour '+(nxt+1)+' next');}}
  if(placed===N){chime();document.getElementById('done').classList.add('on');}
}
function pick(i){sel=i;[...document.querySelectorAll('.sw')].forEach((b,k)=>b.classList.toggle('on',k===i));paintBoard();}
function buildPal(){
  document.getElementById('pal').innerHTML=PAL.map((hex,i)=>
    `<div class="sw ${i===0?'on':''}" data-i="${i}"><div class="dot" style="background:${hex}"></div>
     <div class="n">${i+1}</div><div class="left">${total[i]}</div></div>`).join('');
  [...document.querySelectorAll('.sw')].forEach(b=>b.onclick=()=>{pick(+b.dataset.i);tone(520,.06,'sine',.04);});
}
/* ---- input: 1 finger paints, 2 fingers pan+zoom, wheel zooms ---- */
let pts=new Map(),painting=false,lastD=0,lastMid=null;
cv.addEventListener('pointerdown',e=>{cv.setPointerCapture(e.pointerId);pts.set(e.pointerId,{x:e.clientX,y:e.clientY});
  if(pts.size===1){painting=true;if(paintAt(e.clientX,e.clientY,true))progress();}
  else{painting=false;lastD=0;lastMid=null;}});
cv.addEventListener('pointermove',e=>{
  if(!pts.has(e.pointerId))return;pts.set(e.pointerId,{x:e.clientX,y:e.clientY});
  if(pts.size===1&&painting){if(paintAt(e.clientX,e.clientY,false))progress();return;}
  if(pts.size===2){const [a,b]=[...pts.values()];
    const d=Math.hypot(a.x-b.x,a.y-b.y),mid={x:(a.x+b.x)/2,y:(a.y+b.y)/2};
    if(lastD){const k=d/lastD,ns=Math.max(2,Math.min(70,scale*k));
      ox=mid.x-(mid.x-ox)*(ns/scale);oy=mid.y-(mid.y-oy)*(ns/scale);scale=ns;}
    if(lastMid){ox+=mid.x-lastMid.x;oy+=mid.y-lastMid.y;}
    lastD=d;lastMid=mid;}});
function up(e){pts.delete(e.pointerId);if(pts.size<2){lastD=0;lastMid=null;}if(pts.size===0)painting=false;}
cv.addEventListener('pointerup',up);cv.addEventListener('pointercancel',up);
cv.addEventListener('wheel',e=>{e.preventDefault();const k=e.deltaY<0?1.12:1/1.12,ns=Math.max(2,Math.min(70,scale*k));
  ox=e.clientX-(e.clientX-ox)*(ns/scale);oy=e.clientY-(e.clientY-oy)*(ns/scale);scale=ns;},{passive:false});
document.getElementById('snd').onclick=e=>{muted=!muted;e.target.textContent=muted?'🔇':'🔊';if(!muted)tone(700,.07,'sine',.05);};
document.getElementById('fitb').onclick=()=>fit();
addEventListener('resize',resize);
buildPal();paintBoard();resize();progress();requestAnimationFrame(frame);
flash('Pick a colour, then tap or drag its diamonds');
</script></body></html>
"""
