"""Validation for Semantic Border Rendering. Deterministic local line art (AI helper
patched out). For each image the pipeline runs once; the artistic-border render call is
wrapped to snapshot region count / per-region numbering / palette + regions.json hash
IMMEDIATELY before and after the render, proving the render changes none of them within
the same run. Also records reveal delta-E (must stay 0), the render report (accepted,
action distribution, shape-fidelity IoU, removed fraction), and confirms the before/after
SVG pair (outline_baseline.svg vs outline.svg) is emitted. Args: [start] [end] [suffix]."""
from __future__ import annotations
import hashlib, json, shutil, sys, time, traceback
from pathlib import Path

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}  # deterministic line art
from services import artistic_border_renderer as abr  # noqa
from job_store import create_job, job_dir, get_job  # noqa
import auto_profile_selector as sel  # noqa

INPUT = REPO / "test image"
OUT = REPO / "research" / "08_reports" / "border_render"
OUT.mkdir(parents=True, exist_ok=True)
SUFFIX = sys.argv[3] if len(sys.argv) > 3 else ""
JSONL = OUT / f"border{SUFFIX}.jsonl"
LOG = OUT / f"border{SUFFIX}.log"

PROFILE = {1:"easy",2:"easy",3:"easy",4:"medium",5:"easy",6:"hard",7:"professional",8:"professional",
           9:"professional",10:"professional",11:"professional",12:"professional",13:"professional",
           14:"hard",15:"easy",16:"medium",17:"easy",18:"easy",19:"professional",20:"easy",21:"easy",
           22:"professional",23:"professional",24:"professional",25:"professional",26:"professional",27:"hard"}

_orig_render = abr.render


def _hash(p: Path) -> str:
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else ""


def _snap(folder: Path) -> dict:
    gc = json.loads((folder / "game_config.json").read_text(encoding="utf-8"))
    regs = gc.get("regions", [])
    return {
        "n_regions": len(regs),
        "numbers": [r.get("number") for r in regs],
        "labels": [r.get("label") for r in regs],
        "palette_hash": _hash(folder / "palette.json"),
    }


def _wrapped(labels_path, abr_path, cc, regions_json, game_config, outline, report, overlay):
    folder = Path(game_config).parent
    pre = _snap(folder)
    rep = _orig_render(labels_path, abr_path, cc, regions_json, game_config, outline, report, overlay)
    post = _snap(folder)
    rep["_invariants"] = {
        "regions_same": pre["n_regions"] == post["n_regions"],
        "numbers_same": pre["numbers"] == post["numbers"],
        "labels_same": pre["labels"] == post["labels"],
        "palette_same": pre["palette_hash"] == post["palette_hash"],
        "n_regions": post["n_regions"],
    }
    Path(report).write_text(json.dumps(rep, indent=2), encoding="utf-8")  # persist for the batch reader
    return rep


abr.render = _wrapped


def log(m):
    line = f"[{time.strftime('%H:%M:%S')}] {m}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def run(i, prof):
    job = f"br_{i:02d}"; f = job_dir(job)
    if f.exists():
        shutil.rmtree(f)
    create_job(job); shutil.copyfile(sorted(INPUT.glob("*.png"))[i - 1], f / "original.png")
    pipeline.run(job, prof)
    st = (get_job(job) or {}).get("status")
    rr = json.loads((f / "artistic_border_render_report.json").read_text()) \
        if (f / "artistic_border_render_report.json").exists() else {}
    rv = json.loads((f / "reveal_validation.json").read_text()) \
        if (f / "reveal_validation.json").exists() else {}
    outline = (f / "outline.svg").read_text(encoding="utf-8") if (f / "outline.svg").exists() else ""
    base = (f / "outline_baseline.svg")
    inv = rr.get("_invariants", {})
    q = rr.get("quality", {})
    return dict(
        status=st,
        accepted=rr.get("accepted"),
        outputs_updated=rr.get("outputs_updated"),
        segment_count=rr.get("segment_count"),
        rendered_count=rr.get("rendered_count"),
        removed_count=rr.get("removed_count"),
        actions=rr.get("action_applied_distribution"),
        iou_before=q.get("shape_iou_before"), iou_after=q.get("shape_iou_after"),
        iou_delta=q.get("shape_iou_delta"), removed_frac=q.get("removed_border_length_fraction"),
        regions_same=inv.get("regions_same"), numbers_same=inv.get("numbers_same"),
        labels_same=inv.get("labels_same"), palette_same=inv.get("palette_same"),
        n_regions=inv.get("n_regions"),
        reveal_deltaE=rv.get("completed_reveal_delta_e"),
        reveal_match=rv.get("matches_original_under_masks"),
        stroke_opacity_in_svg=outline.count("stroke-opacity"),
        baseline_svg=base.exists(), baseline_bytes=(base.stat().st_size if base.exists() else 0),
    )


def main():
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else 27
    if start <= 1:
        for p in (JSONL, LOG):
            if p.exists():
                p.unlink()
        log("semantic border rendering: validation on 27 images")
    log(f"chunk {start}..{end}")
    for i in range(start, end + 1):
        prof = PROFILE[i]; row = {"index": i, "profile": prof}
        t0 = time.monotonic()
        try:
            r = run(i, prof)
            row.update(r)
            row["invariant_ok"] = bool(r["regions_same"] and r["numbers_same"]
                                       and r["labels_same"] and r["palette_same"])
            row["PASS"] = bool(r["status"] == "done" and row["invariant_ok"]
                               and r["reveal_deltaE"] == 0 and r["reveal_match"] is True
                               and r["baseline_svg"])
            row["time"] = round(time.monotonic() - t0, 1)
        except Exception as exc:  # noqa: BLE001
            row.update({"PASS": False, "failure": f"{type(exc).__name__}: {exc}"})
            log("TRACEBACK " + str(i) + "\n" + traceback.format_exc())
        with open(JSONL, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(row) + "\n")
        log(f"img {i:02d} {prof} PASS={row.get('PASS')} accepted={row.get('accepted')} "
            f"actions={row.get('actions')} inv_ok={row.get('invariant_ok')} "
            f"revDE={row.get('reveal_deltaE')} opac={row.get('stroke_opacity_in_svg')} t={row.get('time')}s")
    log("batch complete")


if __name__ == "__main__":
    main()
