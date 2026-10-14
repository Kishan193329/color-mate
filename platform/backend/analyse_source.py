#!/usr/bin/env python3
"""Is this picture flat enough for one-number-per-shape regions?

Answers the question that decides it: if each closed line-art cell were filled with a single
colour, how wrong would it look? Colour SPREAD alone is too strict — a smooth gradient down one
ear still reads fine filled flat; what looks wrong is a cell holding two different colours.
"""
import sys
sys.path.insert(0, '/opt/colorforge')
import cv2, numpy as np
from pathlib import Path
from services import reveal_game_builder as R
from services import lineart_region_builder as L

def report(job):
    folder = Path('/opt/colorforge/data/items')/job
    m = cv2.imread(str(folder/'reveal_master.png'))
    if m is None: print('  no master for', job); return
    line = R._load_lineart(folder, *m.shape[:2])
    if line is None: print('  no line art for', job); return
    ink = R._ai_border_bgra(line)[..., 3] > 96
    lab = cv2.cvtColor(m, cv2.COLOR_BGR2LAB).astype(np.float32)

    fl = L.build_regions(line, r_min=R.COLOR_LINE_RMIN)
    n = int(fl.max())
    e = np.zeros(fl.shape, bool)
    e[:, :-1] |= fl[:, :-1] != fl[:, 1:]
    e[:-1, :] |= fl[:-1, :] != fl[1:, :]
    cov = 100*(e & ink).sum()/max(1, e.sum())

    errs, spreads = [], []
    for rid in range(1, n+1):
        msk = fl == rid
        if msk.sum() < 40: continue
        px = lab[msk]
        mean = px.mean(axis=0)
        # how far the average pixel would sit from a single flat fill
        errs.append(float(np.linalg.norm(px - mean, axis=1).mean()))
        spreads.append(float(np.linalg.norm(px.std(axis=0))))
    errs = np.array(errs); spreads = np.array(spreads)
    print(f'  {job}')
    print(f'    cells                        {n}')
    print(f'    boundary with a line on it   {cov:.1f}%')
    print(f'    flat-fill error  median {np.median(errs):5.1f}  p90 {np.percentile(errs,90):5.1f}   (under ~10 looks right)')
    print(f'    cells that fill cleanly      {100*(errs<10).mean():.0f}%')
    print(f'    colour spread    median {np.median(spreads):5.1f}')

if __name__ == '__main__':
    for j in sys.argv[1:]:
        report(j)
