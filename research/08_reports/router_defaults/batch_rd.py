"""Router-Driven-Defaults batch: all 27 images through router->auto-start->quality-retry
+ reveal. Records completion, image type, router recommendation, start profile, chosen
profile, reveal-match, leaks. PASS = done & 0 leaks & reveal matches original. Writes
batch_rd.jsonl incrementally. Args: [start] [end]."""
from __future__ import annotations
import json, shutil, sys, time, traceback
from pathlib import Path

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
import quality_retry_engine as qre  # noqa
import auto_profile_selector as sel  # noqa
from job_store import create_job, job_dir, get_job  # noqa

INPUT = REPO / "test image"
OUT = REPO / "research" / "08_reports" / "router_defaults"
OUT.mkdir(parents=True, exist_ok=True)
SUFFIX = sys.argv[3] if len(sys.argv) > 3 else ""
JSONL = OUT / f"batch_rd{SUFFIX}.jsonl"
LOG = OUT / f"batch_rd{SUFFIX}.log"


def log(m):
    line = f"[{time.strftime('%H:%M:%S')}] {m}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def main():
    images = sorted(INPUT.glob("*.png"))
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else len(images)
    if start <= 1:
        for p in (JSONL, LOG):
            if p.exists():
                p.unlink()
        log(f"router-driven-defaults batch: {len(images)} images")
    log(f"chunk {start}..{end}")

    for i, img in enumerate(images, 1):
        if i < start or i > end:
            continue
        job = f"rd_{i:02d}"
        row = {"index": i, "filename": img.name}
        t0 = time.monotonic()
        try:
            f = job_dir(job)
            if f.exists():
                shutil.rmtree(f)
            create_job(job)
            shutil.copyfile(img, f / "original.png")
            res = qre.process_upload(job)
            dt = round(time.monotonic() - t0, 1)
            st = (get_job(job) or {}).get("status")
            rt = json.loads((f / "image_type_report.json").read_text()) if (f / "image_type_report.json").exists() else {}
            rv = json.loads((f / "reveal_validation.json").read_text()) if (f / "reveal_validation.json").exists() else {}
            m = sel.assess(f, res.get("chosen_profile")) if st == "done" else {}
            row.update({
                "status": st,
                "image_type": rt.get("image_type"), "type_confidence": rt.get("confidence"),
                "recommended_mode": rt.get("recommended_mode"),
                "recommended_profile": rt.get("recommended_profile"),
                "start_profile": res.get("start_profile"),
                "chosen_profile": res.get("chosen_profile"),
                "reveal_matches": rv.get("matches_original_under_masks"),
                "reveal_delta_e": rv.get("completed_reveal_delta_e"),
                "mask_coverage": rv.get("mask_coverage_percent"),
                "leak_count": m.get("leak_count"), "region_count": m.get("region_count"),
                "palette_count": m.get("palette_count"), "rendered_delta_e": m.get("rendered_deltaE"),
                "processing_time": dt, "failure_reason": "",
            })
            row["PASS"] = bool(st == "done" and (m.get("leak_count") or 0) == 0
                               and rv.get("matches_original_under_masks") is True)
        except Exception as exc:  # noqa: BLE001
            row.update({"status": "crashed", "PASS": False,
                        "processing_time": round(time.monotonic() - t0, 1),
                        "failure_reason": f"{type(exc).__name__}: {exc}"})
            log("TRACEBACK " + job + "\n" + traceback.format_exc())
        with open(JSONL, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(row) + "\n")
        log(f"{job} status={row.get('status')} PASS={row.get('PASS')} type={row.get('image_type')} "
            f"start={row.get('start_profile')} chosen={row.get('chosen_profile')} "
            f"revealDE={row.get('reveal_delta_e')} leak={row.get('leak_count')} t={row.get('processing_time')}s")
    log("batch complete")


if __name__ == "__main__":
    main()
