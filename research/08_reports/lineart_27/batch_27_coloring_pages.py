import io,sys,time,json; sys.stdout=io.TextIOWrapper(sys.stdout.buffer,encoding='utf-8')
sys.path.insert(0,'backend')
import numpy as np, cv2
from pathlib import Path
from services import reveal_game_builder as R
OUT=Path('research/08_reports/lineart_27'); OUT.mkdir(parents=True,exist_ok=True)
cells=[]; rows=[]; t0=time.time()
for i in range(1,28):
    f=Path('storage/jobs')/f'br_{i:02d}'
    if not (f/'reveal_master.png').exists(): continue
    g=R.game_regions(f); fl=g['fl']; master=g['master']; meta=g['meta']; bdr=g['border']
    ids=[m['id'] for m in meta]
    numbered,_=R.compose(fl,master,set(),bdr,meta,True)     # clean line-art + numbers, splits invisible
    _,cont=R.compose(fl,master,set(ids),bdr,meta,False)
    nnum=g['palette_count']; nreg=len(meta)
    ok=bool((fl>0).all()) and np.array_equal(cont,master)
    rows.append({'job':f'br_{i:02d}','regions':nreg,'palette_numbers':nnum,
                 'avg_shapes_per_number':round(nreg/max(1,nnum),1),'tiles_and_dE0':ok})
    print(f"br_{i:02d}: regions={nreg:3} numbers={nnum} shapes/number={round(nreg/max(1,nnum),1):4} tiles+dE0={ok}")
    cv2.imwrite(str(OUT/f'page_{i:02d}.png'), numbered)
    h,w=numbered.shape[:2]; s=max(h,w); pad=np.full((s,s,3),255,np.uint8); pad[:h,:w]=numbered
    th=cv2.resize(pad,(420,420)); cv2.rectangle(th,(0,0),(92,22),(28,28,34),-1)
    cv2.putText(th,f'br_{i:02d}',(5,16),cv2.FONT_HERSHEY_SIMPLEX,0.5,(240,240,245),1,cv2.LINE_AA)
    cells.append(th)
cols=6; nr=(len(cells)+cols-1)//cols; gp=3
W=cols*420+(cols+1)*gp; H=nr*420+(nr+1)*gp; grid=np.full((H,W,3),22,np.uint8)
for k,c in enumerate(cells):
    r,cc=divmod(k,cols); grid[gp+r*(420+gp):gp+r*(420+gp)+420, gp+cc*(420+gp):gp+cc*(420+gp)+420]=c
cv2.imwrite(str(OUT/'ALL_27_coloring_pages.png'), grid)
(OUT/'stats.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print(f"\n{len(cells)} pages in {round(time.time()-t0)}s | all tiles+dE0: {all(r['tiles_and_dE0'] for r in rows)}")
print("wrote ALL_27_coloring_pages.png")
