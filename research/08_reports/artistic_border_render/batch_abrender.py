"""Artistic Border Render Pass batch: all 27 images through the full pipeline (which now
ends with the render pass). Validates per image: complete, 0 leaks, reveal==original,
SVG valid (XML parse), border-quality gate (accepted / safe), before/after jaggedness &
IoU. PASS = done & 0 leaks & reveal matches & SVG valid & border not worsened.
Writes batch_abrender.jsonl incrementally. Args: [start] [end] [suffix]."""
from __future__ import annotations
import json, shutil, sys, time, traceback
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
import quality_retry_engine as qre  # noqa
import auto_profile_selector as sel  # noqa
from job_store import create_job, job_dir, get_job  # noqa

INPUT = REPO / "test image"
OUT = REPO / "research" / "08_reports" / "artistic_border_render"
OUT.mkdir(parents=True, exist_ok=True)
SUFFIX = sys.argv[3] if len(sys.argv) > 3 else ""
JSONL = OUT / f"batch_abrender{SUFFIX}.jsonl"
LOG = OUT / f"batch_abrender{SUFFIX}.log"


def log(m):
    line = f"[{time.strftime('%H:%M:%S')}] {m}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def svg_valid(p: Path) -> bool:
    try:
        ET.parse(p)
        return True
    except Exception:  # noqa: BLE001
        return False


def main():
    images = sorted(INPUT.glob("*.png"))
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else len(images)
    if start <= 1:
        for p in (JSONL, LOG):
            if p.exists():
                p.unlink()
        log(f"artistic-border-render batch: {len(images)} images")
    log(f"chunk {start}..{end}")

    for i, img in enumerate(images, 1):
        if i < start or i > end:
            continue
        job = f"ren_{i:02d}"
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
            rv = json.loads((f / "reveal_validation.json").read_text()) if (f / "reveal_validation.json").exists() else {}
            rr = json.loads((f / "artistic_border_render_report.json").read_text()) if (f / "artistic_border_render_report.json").exists() else {}
            m = sel.assess(f, res.get("chosen_profile")) if st == "done" else {}
            q = rr.get("quality", {})
            svgok = svg_valid(f / "outline.svg")
            leaks = int(m.get("leak_count") or 0)
            reveal_ok = rv.get("matches_original_under_masks") is True
            # border not worsened: accepted (applied+improved) OR safely kept baseline
            not_worse = bool(rr.get("accepted") or (rr.get("outputs_updated") is False))
            row.update({
                "status": st, "chosen_profile": res.get("chosen_profile"),
                "leak_count": leaks,
                "reveal_matches": reveal_ok, "reveal_delta_e": rv.get("completed_reveal_delta_e"),
                "svg_valid": svgok,
                "segments": rr.get("segment_count"), "removed": rr.get("removed_count"),
                "actions": rr.get("action_applied_distribution"),
                "jag_before": q.get("jaggedness_before"), "jag_after": q.get("jaggedness_after"),
                "jag_delta": q.get("jaggedness_delta"),
                "iou_before": q.get("shape_iou_before"), "iou_after": q.get("shape_iou_after"),
                "frag_before": q.get("fragments_before"), "frag_after": q.get("fragments_after"),
                "accepted": rr.get("accepted"), "outputs_updated": rr.get("outputs_updated"),
                "not_worse": not_worse,
                "processing_time": dt, "failure_reason": "",
            })
            row["PASS"] = bool(st == "done" and leaks == 0 and reveal_ok and svgok and not_worse)
        except Exception as exc:  # noqa: BLE001
            row.update({"status": "crashed", "PASS": False,
                        "processing_time": round(time.monotonic() - t0, 1),
                        "failure_reason": f"{type(exc).__name__}: {exc}"})
            log("TRACEBACK " + job + "\n" + traceback.format_exc())
        with open(JSONL, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(row) + "\n")
        log(f"{job} PASS={row.get('PASS')} st={row.get('status')} leak={row.get('leak_count')} "
            f"revealDE={row.get('reveal_delta_e')} svg={row.get('svg_valid')} "
            f"acc={row.get('accepted')} jagd={row.get('jag_delta')} rm={row.get('removed')} t={row.get('processing_time')}s")
    log("batch complete")


if __name__ == "__main__":
    main()
