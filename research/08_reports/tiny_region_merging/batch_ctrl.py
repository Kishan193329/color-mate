"""Controlled before/after for Upstream Tiny-Region Merging. Deterministic local line art
(AI helper patched out) so merge OFF vs ON differ ONLY by the merge. Each image runs at its
baseline profile twice. Records region count off/on, flat-fill accuracy (rendered ΔE) off/on,
reveal ΔE (must be 0), leaks, palette off/on. Args: [start] [end] [suffix]."""
from __future__ import annotations
import json, shutil, sys, time, traceback
from pathlib import Path

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}  # deterministic line art
from services import region_detector as rd  # noqa
import auto_profile_selector as sel  # noqa
from job_store import create_job, job_dir, get_job  # noqa

INPUT = REPO / "test image"
OUT = REPO / "research" / "08_reports" / "tiny_region_merging"
OUT.mkdir(parents=True, exist_ok=True)
SUFFIX = sys.argv[3] if len(sys.argv) > 3 else ""
JSONL = OUT / f"ctrl{SUFFIX}.jsonl"
LOG = OUT / f"ctrl{SUFFIX}.log"

# baseline profile per image (from the process_upload auto-selection, tm batch)
PROFILE = {1:"easy",2:"easy",3:"easy",4:"medium",5:"easy",6:"hard",7:"professional",8:"professional",
           9:"professional",10:"professional",11:"professional",12:"professional",13:"professional",
           14:"hard",15:"easy",16:"medium",17:"easy",18:"easy",19:"professional",20:"easy",21:"easy",
           22:"professional",23:"professional",24:"professional",25:"professional",26:"professional",27:"hard"}


def log(m):
    line = f"[{time.strftime('%H:%M:%S')}] {m}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def run_side(i, prof, merge, tag):
    job = f"ctl_{tag}_{i:02d}"; f = job_dir(job)
    if f.exists():
        shutil.rmtree(f)
    create_job(job); shutil.copyfile(sorted(INPUT.glob("*.png"))[i - 1], f / "original.png")
    rd.INSIGNIFICANT_AREA_MULT = 5.0 if merge else 0.0
    pipeline.run(job, prof)
    st = (get_job(job) or {}).get("status")
    gc = json.loads((f / "game_config.json").read_text())
    rv = json.loads((f / "reveal_validation.json").read_text()) if (f / "reveal_validation.json").exists() else {}
    m = sel.assess(f, prof) if st == "done" else {}
    fc = json.loads((f / "fragment_cleanup_report.json").read_text()) if (f / "fragment_cleanup_report.json").exists() else {}
    return dict(status=st, regions=len(gc.get("regions", [])), renderDE=m.get("rendered_deltaE"),
                revDE=rv.get("completed_reveal_delta_e"), revMatch=rv.get("matches_original_under_masks"),
                leaks=int(m.get("leak_count") or 0), palette=m.get("palette_count"),
                insignificant_merged=None)


def main():
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else 27
    if start <= 1:
        for p in (JSONL, LOG):
            if p.exists():
                p.unlink()
        log("controlled tiny-region-merging: 27 images")
    log(f"chunk {start}..{end}")
    for i in range(start, end + 1):
        prof = PROFILE[i]; row = {"index": i, "profile": prof}
        t0 = time.monotonic()
        try:
            off = run_side(i, prof, False, "off"); on = run_side(i, prof, True, "on")
            row.update({
                "regions_off": off["regions"], "regions_on": on["regions"],
                "regions_reduced": off["regions"] - on["regions"],
                "renderDE_off": off["renderDE"], "renderDE_on": on["renderDE"],
                "renderDE_delta": round((on["renderDE"] or 0) - (off["renderDE"] or 0), 4),
                "palette_off": off["palette"], "palette_on": on["palette"],
                "reveal_deltaE_on": on["revDE"], "reveal_match_on": on["revMatch"],
                "leaks_on": on["leaks"], "status_on": on["status"],
                "time": round(time.monotonic() - t0, 1),
            })
            row["PASS"] = bool(on["status"] == "done" and on["leaks"] == 0
                               and on["revDE"] == 0 and on["revMatch"] is True
                               and row["renderDE_delta"] <= 0.5)
        except Exception as exc:  # noqa: BLE001
            row.update({"PASS": False, "failure": f"{type(exc).__name__}: {exc}"})
            log("TRACEBACK " + str(i) + "\n" + traceback.format_exc())
        with open(JSONL, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(row) + "\n")
        log(f"img {i:02d} {prof} PASS={row.get('PASS')} regions {row.get('regions_off')}->{row.get('regions_on')} "
            f"(-{row.get('regions_reduced')}) renderDEd={row.get('renderDE_delta')} revDE={row.get('reveal_deltaE_on')} "
            f"leak={row.get('leaks_on')} t={row.get('time')}s")
    log("batch complete")


if __name__ == "__main__":
    main()
