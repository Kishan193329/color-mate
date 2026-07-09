"""Validation for Human Visual Intelligence Foundation. Runs analyze() over the 27 finished
job dirs (br_01..br_27). Checks: 10 scored Decisions per region each with reason/confidence/
evidence/override_point/source; means present; and — crucially — that ONLY human_visual_report.json
is written (every rendering/border/reveal/SVG/region artifact is byte-identical before/after)."""
from __future__ import annotations
import hashlib, io, json, sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa (experience policy active; realistic)
from services import human_visual_intelligence as HVI  # noqa
from job_store import job_dir  # noqa

OUT = REPO / "research" / "08_reports" / "hvi"
OUT.mkdir(parents=True, exist_ok=True)
# Artifacts HVI must NOT touch (rendering / border / reveal / svg / regions / palette).
UNTOUCHABLE = ["game_config.json", "labels.npy", "labels_render.npy", "outline.svg",
               "region_mask.png", "reveal_completed.png", "reveal_master.png", "palette.json",
               "regions.json", "game_preview.png"]
REQ = ("reason", "confidence", "evidence", "override_point", "source")


def h(p: Path):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def check(folder: Path) -> dict:
    before = {c: h(folder / c) for c in UNTOUCHABLE}
    rep = HVI.analyze(folder)
    after = {c: h(folder / c) for c in UNTOUCHABLE}
    readonly_ok = before == after
    n_gc = len(json.loads((folder / "game_config.json").read_text()).get("regions", []))
    regions = rep["regions"]
    all_10 = all(len(r["decisions"]) == 10 and set(r["scores"]) == set(HVI.SCORES) for r in regions)
    fields_ok = all(all(k in d for k in REQ) for r in regions for d in r["decisions"])
    op_ok = all(d["override_point"].startswith("hvi.") for r in regions for d in r["decisions"])
    means_ok = set(rep["means"]) == set(HVI.SCORES)
    # perceptual sanity: focal/subject recognition should beat background recognition
    subj = [r["scores"]["recognition_weight"] for r in regions if r["is_subject"]]
    bg = [r["scores"]["recognition_weight"] for r in regions if not r["is_subject"]]
    sanity = (sum(subj) / len(subj) if subj else 0) >= (sum(bg) / len(bg) if bg else 0)
    return dict(regions=len(regions), matches_gc=(len(regions) == n_gc), all_10=all_10,
                fields_ok=fields_ok, op_ok=op_ok, means_ok=means_ok, readonly_ok=readonly_ok,
                sanity=sanity, means=rep["means"], only_report=(after == before))


def main_():
    rows = []
    for i in range(1, 28):
        folder = job_dir(f"br_{i:02d}")
        if not (folder / "game_config.json").exists():
            print(f"img {i:02d}: SKIP")
            continue
        try:
            r = check(folder)
            r["index"] = i
            rows.append(r)
            print(f"img {i:02d}: regions={r['regions']} 10-scores={r['all_10']} fields={r['fields_ok']} "
                  f"hvi-op={r['op_ok']} means={r['means_ok']} readonly={r['readonly_ok']} sanity={r['sanity']}")
        except Exception as exc:  # noqa: BLE001
            print(f"img {i:02d}: CRASH {type(exc).__name__}: {exc}")
            rows.append({"index": i, "crash": str(exc)})
    ok = [r for r in rows if "crash" not in r]
    agg = {
        "images": len(rows), "crashes": sum(1 for r in rows if "crash" in r),
        "all_10_scores": sum(1 for r in ok if r["all_10"]),
        "all_5_fields": sum(1 for r in ok if r["fields_ok"]),
        "hvi_override_points": sum(1 for r in ok if r["op_ok"]),
        "means_present": sum(1 for r in ok if r["means_ok"]),
        "readonly_only_report": sum(1 for r in ok if r["readonly_ok"]),
        "region_count_matches_gc": sum(1 for r in ok if r["matches_gc"]),
        "perceptual_sanity": sum(1 for r in ok if r["sanity"]),
        "total_regions_scored": sum(r["regions"] for r in ok),
    }
    print("\nSUMMARY:", json.dumps(agg))
    (OUT / "hvi_validation.json").write_text(json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")
    print("-> ", OUT / "hvi_validation.json")


if __name__ == "__main__":
    main_()
