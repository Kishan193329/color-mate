"""Validation for HVI-Guided Border Decisions. For each finished job renders the artistic
border with HVI guidance OFF (baseline) then ON, and checks: render accepted; reveal ΔE stays
0; region count / per-region numbering / palette / reveal masks / labels all byte-identical
(the render only rewrites the SVG/vector layer). Captures the HVI action adjustments and
renders before/after border previews for a few images."""
from __future__ import annotations
import hashlib, io, json, re, shutil, sys
from pathlib import Path
import numpy as np
import cv2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa (experience policy active; realistic)
from services import artistic_border_renderer as R  # noqa
from services.contour_character_engine import _sample_d  # noqa
from job_store import job_dir  # noqa

OUT = REPO / "research" / "08_reports" / "hvi_border"
OUT.mkdir(parents=True, exist_ok=True)
INVARIANT_FILES = ["palette.json", "reveal_master.png", "reveal_completed.png",
                   "region_mask.png", "labels.npy", "labels_render.npy"]


def h(p: Path):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def gc_snapshot(f: Path):
    gc = json.loads((f / "game_config.json").read_text())
    regs = gc.get("regions", [])
    return {"count": len(regs), "numbers": [r.get("number") for r in regs],
            "palette": len(gc.get("palette", []))}


def rasterize(svg_path: Path, cell_w=520):
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


def render(f, hvi):
    R.HVI_GUIDANCE = hvi
    lab = f / "labels_render.npy" if (f / "labels_render.npy").exists() else f / "labels.npy"
    return R.render(lab, f / "artistic_border_report.json", f / "contour_character_report.json",
                    f / "regions.json", f / "game_config.json", f / "outline.svg",
                    f / "artistic_border_render_report.json", f / "artistic_border_overlay.png")


def check(i, make_viz=False):
    f = job_dir(f"br_{i:02d}")
    pre_gc = gc_snapshot(f)
    pre_files = {c: h(f / c) for c in INVARIANT_FILES}
    rev_de = (json.loads((f / "reveal_validation.json").read_text()).get("completed_reveal_delta_e")
              if (f / "reveal_validation.json").exists() else None)
    off = render(f, False)
    off_svg = (f / "outline.svg").read_text(encoding="utf-8")
    if make_viz:
        off_ras = rasterize(f / "outline.svg")
    on = render(f, True)
    on_svg = (f / "outline.svg").read_text(encoding="utf-8")
    post_gc = gc_snapshot(f)
    post_files = {c: h(f / c) for c in INVARIANT_FILES}
    if make_viz:
        on_ras = rasterize(f / "outline.svg")
        gap = np.full((on_ras.shape[0], 6, 3), 200, np.uint8)
        combo = np.hstack([off_ras, gap, on_ras])
        cv2.putText(combo, "HVI OFF", (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
        cv2.putText(combo, "HVI ON", (off_ras.shape[1] + 12, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 2, cv2.LINE_AA)
        cv2.imwrite(str(OUT / f"hvi_border_{i:02d}.png"), combo)
    return {
        "index": i, "accepted_on": on.get("accepted"), "hvi_guided": on.get("hvi_guided"),
        "hvi_changed": on.get("hvi_adjustments", {}).get("changed"),
        "by_rule": on.get("hvi_adjustments", {}).get("by_rule"),
        "regions_same": pre_gc["count"] == post_gc["count"],
        "numbers_same": pre_gc["numbers"] == post_gc["numbers"],
        "palette_same": pre_gc["palette"] == post_gc["palette"],
        "invariant_files_same": pre_files == post_files,
        "reveal_deltaE": rev_de,
        "outline_changed_by_hvi": off_svg != on_svg,
        "iou_delta_on": on.get("quality", {}).get("shape_iou_delta"),
        "removed_frac_on": on.get("quality", {}).get("removed_border_length_fraction"),
        "actions_off": off.get("action_applied_distribution"),
        "actions_on": on.get("action_applied_distribution"),
    }


def main_():
    viz = {1, 6, 14, 16}
    rows = []
    for i in range(1, 28):
        if not (job_dir(f"br_{i:02d}") / "game_config.json").exists():
            continue
        r = check(i, make_viz=(i in viz))
        rows.append(r)
        print(f"img {i:02d}: accepted={r['accepted_on']} hvi_changed={r['hvi_changed']:>4} "
              f"reveal_dE={r['reveal_deltaE']} regions/nums/palette/masks_same="
              f"{r['regions_same']}/{r['numbers_same']}/{r['palette_same']}/{r['invariant_files_same']} "
              f"outline_changed={r['outline_changed_by_hvi']}")
    agg = {
        "images": len(rows),
        "accepted": sum(1 for r in rows if r["accepted_on"]),
        "reveal_deltaE_zero": sum(1 for r in rows if r["reveal_deltaE"] == 0),
        "regions_unchanged": sum(1 for r in rows if r["regions_same"]),
        "numbers_unchanged": sum(1 for r in rows if r["numbers_same"]),
        "palette_unchanged": sum(1 for r in rows if r["palette_same"]),
        "reveal_masks_labels_unchanged": sum(1 for r in rows if r["invariant_files_same"]),
        "outline_changed_by_hvi": sum(1 for r in rows if r["outline_changed_by_hvi"]),
        "total_hvi_adjustments": sum(r["hvi_changed"] or 0 for r in rows),
    }
    by_rule_total = {}
    for r in rows:
        for k, v in (r["by_rule"] or {}).items():
            by_rule_total[k] = by_rule_total.get(k, 0) + v
    agg["by_rule_total"] = by_rule_total
    print("\nSUMMARY:", json.dumps(agg))
    (OUT / "hvi_border_validation.json").write_text(json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main_()
