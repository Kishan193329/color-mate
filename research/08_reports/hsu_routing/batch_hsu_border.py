"""Validation for HSU-Guided Strategy Routing. For each finished job renders the artistic
border with HSU strategy routing OFF (HVI only) then ON, and checks: render accepted; reveal
ΔE stays 0; region count / numbering / palette / reveal masks / labels byte-identical (SVG
layer only); the router hint is advisory (regions unchanged). Captures per-strategy border
adjustments and renders HVI-only vs HVI+HSU previews for a few scenes."""
from __future__ import annotations
import hashlib, io, json, re, sys
from collections import Counter
from pathlib import Path
import numpy as np
import cv2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa
from services import artistic_border_renderer as R  # noqa
from services.contour_character_engine import _sample_d  # noqa
from job_store import job_dir  # noqa

OUT = REPO / "research" / "08_reports" / "hsu_routing"
OUT.mkdir(parents=True, exist_ok=True)
# Files the render must NOT touch. regions.json / outline.svg / game_config hold the vector
# BORDER geometry (allowed to change on the SVG layer) so they are excluded here; the border
# render never touches reveal / masks / labels / palette / the numbered preview.
INV = ["palette.json", "reveal_master.png", "reveal_completed.png", "reveal_partial.png",
       "reveal_blank.png", "reveal_highlight.png", "region_mask.png", "labels.npy",
       "labels_render.npy", "game_preview.png", "lineart.png"]


def h(p):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def gc_snap(f):
    gc = json.loads((f / "game_config.json").read_text())
    regs = gc.get("regions", [])
    return {"count": len(regs), "numbers": [r.get("number") for r in regs], "palette": len(gc.get("palette", []))}


def rasterize(svg_path, cell_w=520):
    text = svg_path.read_text(encoding="utf-8")
    m = re.search(r'width="(\d+)" height="(\d+)"', text)
    w, hh = (int(m.group(1)), int(m.group(2))) if m else (1000, 1000)
    canvas = np.full((hh, w, 3), 255, np.uint8)
    for mm in re.finditer(r'<path d="([^"]+)"([^/]*)/>', text):
        d, attrs = mm.group(1), mm.group(2)
        sw = float((re.search(r'stroke-width="([\d.]+)"', attrs) or [None, "2"])[1])
        op = float((re.search(r'stroke-opacity="([\d.]+)"', attrs) or [None, "1"])[1])
        cm = re.search(r'stroke="#([0-9a-fA-F]{6})"', attrs)
        r_, g_, b_ = (int(cm.group(1)[0:2], 16), int(cm.group(1)[2:4], 16),
                      int(cm.group(1)[4:6], 16)) if cm else (17, 17, 17)
        col = (int(b_ * op + 255 * (1 - op)), int(g_ * op + 255 * (1 - op)), int(r_ * op + 255 * (1 - op)))
        for poly in _sample_d(d):
            cv2.polylines(canvas, [np.round(poly).astype(np.int32)], False, col, max(1, int(round(sw))), cv2.LINE_AA)
    r = cell_w / w
    return cv2.resize(canvas, (cell_w, int(hh * r)), interpolation=cv2.INTER_AREA)


def render(f, hsu):
    R.HVI_GUIDANCE = True
    R.HSU_GUIDANCE = hsu
    lab = f / "labels_render.npy" if (f / "labels_render.npy").exists() else f / "labels.npy"
    return R.render(lab, f / "artistic_border_report.json", f / "contour_character_report.json",
                    f / "regions.json", f / "game_config.json", f / "outline.svg",
                    f / "artistic_border_render_report.json", f / "artistic_border_overlay.png")


def check(i, viz=False):
    f = job_dir(f"br_{i:02d}")
    pre_gc, pre_files = gc_snap(f), {c: h(f / c) for c in INV}
    rev = json.loads((f / "reveal_validation.json").read_text()).get("completed_reveal_delta_e") \
        if (f / "reveal_validation.json").exists() else None
    off = render(f, False)
    off_svg = (f / "outline.svg").read_text(encoding="utf-8")
    if viz:
        off_ras = rasterize(f / "outline.svg")
    on = render(f, True)
    on_svg = (f / "outline.svg").read_text(encoding="utf-8")
    post_gc, post_files = gc_snap(f), {c: h(f / c) for c in INV}
    if viz:
        on_ras = rasterize(f / "outline.svg")
        gap = np.full((on_ras.shape[0], 6, 3), 200, np.uint8)
        combo = np.hstack([off_ras, gap, on_ras])
        cv2.putText(combo, "HVI only", (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
        cv2.putText(combo, f"HVI+HSU ({on.get('hsu_strategy')})", (off_ras.shape[1] + 12, 22),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
        cv2.imwrite(str(OUT / f"hsu_border_{i:02d}.png"), combo)
    return {
        "index": i, "strategy": on.get("hsu_strategy"), "accepted": on.get("accepted"),
        "hsu_guided": on.get("hsu_guided"), "start_hint": on.get("hsu_start_profile_hint_advisory"),
        "changed": on.get("hsu_adjustments", {}).get("changed"),
        "by_rule": on.get("hsu_adjustments", {}).get("by_rule"),
        "regions_same": pre_gc["count"] == post_gc["count"], "numbers_same": pre_gc["numbers"] == post_gc["numbers"],
        "palette_same": pre_gc["palette"] == post_gc["palette"], "inv_files_same": pre_files == post_files,
        "reveal_deltaE": rev, "outline_changed": off_svg != on_svg,
        "iou_delta": on.get("quality", {}).get("shape_iou_delta"),
    }


def main_():
    viz = {6, 14, 16, 8}
    rows, crashes = [], 0
    for i in range(1, 28):
        if not (job_dir(f"br_{i:02d}") / "game_config.json").exists():
            continue
        try:
            r = check(i, viz=(i in viz))
            rows.append(r)
            print(f"img {i:02d}: {r['strategy']:22} changed={r['changed']:>3} "
                  f"acc={r['accepted']} revDE={r['reveal_deltaE']} "
                  f"inv(reg/num/pal/files)={r['regions_same']}/{r['numbers_same']}/{r['palette_same']}/{r['inv_files_same']} "
                  f"outline_changed={r['outline_changed']}")
        except Exception as exc:  # noqa: BLE001
            crashes += 1
            print(f"img {i:02d}: CRASH {type(exc).__name__}: {exc}")
    br = Counter()
    for r in rows:
        for k, v in (r["by_rule"] or {}).items():
            br[k] += v
    agg = {
        "images": len(rows), "crashes": crashes,
        "accepted": sum(1 for r in rows if r["accepted"]),
        "reveal_deltaE_zero": sum(1 for r in rows if r["reveal_deltaE"] == 0),
        "regions_unchanged": sum(1 for r in rows if r["regions_same"]),
        "numbers_unchanged": sum(1 for r in rows if r["numbers_same"]),
        "palette_unchanged": sum(1 for r in rows if r["palette_same"]),
        "reveal_masks_labels_regions_unchanged": sum(1 for r in rows if r["inv_files_same"]),
        "outline_changed_by_hsu": sum(1 for r in rows if r["outline_changed"]),
        "total_hsu_adjustments": sum(r["changed"] or 0 for r in rows),
        "by_rule_total": dict(br.most_common()),
        "strategy_distribution": dict(Counter(r["strategy"] for r in rows).most_common()),
    }
    print("\nSUMMARY:", json.dumps(agg))
    (OUT / "hsu_routing_validation.json").write_text(json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main_()
