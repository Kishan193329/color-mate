"""Aggregate the Semantic Border Rendering validation (border a/b/c.jsonl) into headline
numbers + the per-image markdown table for BORDER_RENDER_REPORT.md."""
from __future__ import annotations
import io, json, sys
from collections import Counter
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
OUT = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai\research\08_reports\border_render")
ACTS = ["THICKEN", "EMPHASIZE", "SOFTEN", "SIMPLIFY", "THIN", "REMOVE", "KEEP"]

rows = []
for suf in ("a", "b", "c"):
    p = OUT / f"border{suf}.jsonl"
    if p.exists():
        rows += [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()]
rows.sort(key=lambda r: r["index"])

n = len(rows)
passes = sum(1 for r in rows if r.get("PASS"))
accepted = sum(1 for r in rows if r.get("accepted"))
inv = sum(1 for r in rows if r.get("invariant_ok"))
rev0 = sum(1 for r in rows if r.get("reveal_deltaE") == 0)
base = sum(1 for r in rows if r.get("baseline_svg"))
tot = Counter()
imgs_with = Counter()
for r in rows:
    for a, c in (r.get("actions") or {}).items():
        tot[a] += c
        imgs_with[a] += 1
deltas = [r.get("iou_delta") for r in rows if r.get("iou_delta") is not None]
fid_loss = [(r["index"], r["iou_delta"]) for r in rows if (r.get("iou_delta") or 0) < -0.010]
rfrac = [r.get("removed_frac") for r in rows if r.get("removed_frac") is not None]
opac_ok = sum(1 for r in rows if (r.get("stroke_opacity_in_svg") or 0) > 0 and r.get("rendered_count"))

print(f"images={n}  PASS={passes}/{n}")
print(f"render accepted: {accepted}/{n}")
print(f"invariants (regions+numbers+labels+palette unchanged): {inv}/{n}")
print(f"reveal completed delta-E == 0: {rev0}/{n}    baseline SVG emitted: {base}/{n}")
print(f"outline.svg carries per-segment stroke-opacity: {opac_ok}/{n}")
print(f"total segments rendered by action: {dict((a, tot.get(a,0)) for a in ACTS)}")
print(f"images exercising each action: {dict((a, imgs_with.get(a,0)) for a in ACTS)}")
print(f"shape-fidelity IoU delta  min={min(deltas):+.4f}  mean={sum(deltas)/len(deltas):+.4f}  max={max(deltas):+.4f}")
print(f"images with fidelity loss > 0.010: {fid_loss}")
print(f"removed border-length fraction  max={max(rfrac):.3f}  mean={sum(rfrac)/len(rfrac):.3f}  (gate <= 0.20)")
print()
hdr = "| # | Prof | Regions | Acc | THK | EMP | SFT | SMP | THN | REM | KEEP | IoU d | RemF | revDE | inv |"
print(hdr)
print("|---|------|--------:|:---:|----:|----:|----:|----:|----:|----:|-----:|------:|-----:|:-----:|:---:|")
for r in rows:
    a = r.get("actions") or {}
    print(f"| {r['index']} | {r['profile'][:4]} | {r.get('n_regions')} | "
          f"{'Y' if r.get('accepted') else 'n'} | {a.get('THICKEN',0)} | {a.get('EMPHASIZE',0)} | "
          f"{a.get('SOFTEN',0)} | {a.get('SIMPLIFY',0)} | {a.get('THIN',0)} | {a.get('REMOVE',0)} | "
          f"{a.get('KEEP',0)} | {r.get('iou_delta'):+.3f} | {r.get('removed_frac'):.3f} | "
          f"{r.get('reveal_deltaE')} | {'Y' if r.get('invariant_ok') else 'n'} |")
