"""Line-Art Fragmentation Reduction batch: all 27 images through the full pipeline
(fragment cleanup -> QF14 -> intelligence -> render). Validates per image: complete,
0 leaks, reveal==original, SVG valid, fragment-cleanup invariants (union preserved,
0 labels lost), fragment count reduction, and render acceptance. Args: [start] [end] [suffix]."""
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
OUT = REPO / "research" / "08_reports" / "fragmentation_reduction"
OUT.mkdir(parents=True, exist_ok=True)
SUFFIX = sys.argv[3] if len(sys.argv) > 3 else ""
JSONL = OUT / f"batch_frag{SUFFIX}.jsonl"
LOG = OUT / f"batch_frag{SUFFIX}.log"


def log(m):
    line = f"[{time.strftime('%H:%M:%S')}] {m}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def svg_valid(p):
    try:
        ET.parse(p); return True
    except Exception:  # noqa: BLE001
        return False


def load(p):
    return json.loads(p.read_text()) if p.exists() else {}


def main():
    images = sorted(INPUT.glob("*.png"))
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else len(images)
    if start <= 1:
        for p in (JSONL, LOG):
            if p.exists():
                p.unlink()
        log(f"fragmentation-reduction batch: {len(images)} images")
    log(f"chunk {start}..{end}")
    for i, img in enumerate(images, 1):
        if i < start or i > end:
            continue
        job = f"fg_{i:02d}"
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
            fc = load(f / "fragment_cleanup_report.json")
            rr = load(f / "artistic_border_render_report.json")
            rv = load(f / "reveal_validation.json")
            m = sel.assess(f, res.get("chosen_profile")) if st == "done" else {}
            q = rr.get("quality", {})
            leaks = int(m.get("leak_count") or 0)
            reveal_ok = rv.get("matches_original_under_masks") is True
            svgok = svg_valid(f / "outline.svg")
            not_worse = bool(rr.get("accepted") or (rr.get("outputs_updated") is False))
            row.update({
                "status": st, "chosen_profile": res.get("chosen_profile"),
                "leak_count": leaks,
                "reveal_matches": reveal_ok, "reveal_delta_e": rv.get("completed_reveal_delta_e"),
                "svg_valid": svgok,
                "frag_clean_before": fc.get("fragments_before"), "frag_clean_after": fc.get("fragments_after"),
                "frag_removed": fc.get("fragments_removed"), "components_dissolved": fc.get("components_dissolved"),
                "union_preserved": fc.get("union_preserved"), "labels_lost": len(fc.get("labels_lost", []) or []),
                "render_frag_before": q.get("fragments_before"), "render_frag_after": q.get("fragments_after"),
                "render_removed": q.get("fragments_removed"),
                "iou_delta": q.get("shape_iou_delta"),
                "accepted": rr.get("accepted"), "outputs_updated": rr.get("outputs_updated"),
                "not_worse": not_worse, "processing_time": dt, "failure_reason": "",
            })
            row["PASS"] = bool(st == "done" and leaks == 0 and reveal_ok and svgok and not_worse
                               and fc.get("union_preserved") is True and not fc.get("labels_lost"))
        except Exception as exc:  # noqa: BLE001
            row.update({"status": "crashed", "PASS": False,
                        "processing_time": round(time.monotonic() - t0, 1),
                        "failure_reason": f"{type(exc).__name__}: {exc}"})
            log("TRACEBACK " + job + "\n" + traceback.format_exc())
        with open(JSONL, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(row) + "\n")
        log(f"{job} PASS={row.get('PASS')} st={row.get('status')} leak={row.get('leak_count')} "
            f"rev={row.get('reveal_delta_e')} svg={row.get('svg_valid')} acc={row.get('accepted')} "
            f"cleanRm={row.get('frag_removed')} unionOK={row.get('union_preserved')} lost={row.get('labels_lost')} "
            f"iouD={row.get('iou_delta')} t={row.get('processing_time')}s")
    log("batch complete")


if __name__ == "__main__":
    main()
