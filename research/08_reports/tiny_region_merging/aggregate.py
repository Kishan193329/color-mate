"""Aggregate the controlled cap=6 off/on batch (ctrl6a/6b/6c.jsonl) into the numbers
and per-image markdown table rows used by TINY_REGION_MERGING_REPORT.md."""
from __future__ import annotations
import json, io, sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
OUT = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai\research\08_reports\tiny_region_merging")

rows = []
for suf in ("6a", "6b", "6c"):
    p = OUT / f"ctrl{suf}.jsonl"
    if p.exists():
        rows += [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()]
rows.sort(key=lambda r: r["index"])

tot_off = sum(r.get("regions_off") or 0 for r in rows)
tot_on = sum(r.get("regions_on") or 0 for r in rows)
merged_imgs = [r for r in rows if (r.get("regions_reduced") or 0) > 0]
rev0 = sum(1 for r in rows if r.get("reveal_deltaE_on") == 0)
revmatch = sum(1 for r in rows if r.get("reveal_match_on") is True)
leaks = sum(r.get("leaks_on") or 0 for r in rows)
passes = sum(1 for r in rows if r.get("PASS"))
deltas = [r.get("renderDE_delta") or 0 for r in rows]
pal_changed = [r for r in rows if r.get("palette_off") != r.get("palette_on")]
creep = sorted(((r["index"], r.get("renderDE_delta")) for r in rows if (r.get("renderDE_delta") or 0) > 0.3), key=lambda x: -x[1])

print(f"images={len(rows)}  PASS={passes}/{len(rows)}")
print(f"regions {tot_off} -> {tot_on}  (-{tot_off-tot_on}, -{100*(tot_off-tot_on)/tot_off:.1f}%)")
print(f"images_merged={len(merged_imgs)}")
print(f"reveal_deltaE==0: {rev0}/{len(rows)}   reveal_match: {revmatch}/{len(rows)}   leaks={leaks}")
print(f"renderDE_delta  max={max(deltas):+.3f}  min={min(deltas):+.3f}  mean={sum(deltas)/len(deltas):+.3f}")
print(f"renderDE_delta>0.3: {creep}")
print(f"palette_count changed on: {[r['index'] for r in pal_changed]}")
print()
print("| # | Profile | Regions before | Regions after | Merged | Reveal ΔE | Flat-fill ΔE shift | Leaks |")
print("|---|---------|---------------:|--------------:|-------:|:---------:|:------------------:|:-----:|")
for r in rows:
    d = r.get("renderDE_delta") or 0
    print(f"| {r['index']} | {r['profile']} | {r.get('regions_off')} | {r.get('regions_on')} | "
          f"{r.get('regions_reduced')} | {r.get('reveal_deltaE_on')} | {d:+.3f} | {r.get('leaks_on')} |")
