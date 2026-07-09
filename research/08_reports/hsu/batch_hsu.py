"""Validation for Human Scene Understanding Foundation. Runs analyze() over the 27 finished
jobs. Checks: 14 fields each a Decision with reason/confidence/evidence/override_point/source;
scene_type + strategy in valid sets; region-class lists reference real labels; and that ONLY
human_scene_report.json is written (every product artifact byte-identical). Reveal ΔE stays 0."""
from __future__ import annotations
import hashlib, io, json, sys
from collections import Counter
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa (experience policy active; realistic)
from services import human_scene_understanding as HSU  # noqa
from job_store import job_dir  # noqa

OUT = REPO / "research" / "08_reports" / "hsu"
OUT.mkdir(parents=True, exist_ok=True)
PRODUCT = ["game_config.json", "labels.npy", "labels_render.npy", "outline.svg", "region_mask.png",
           "reveal_completed.png", "reveal_master.png", "palette.json", "regions.json",
           "game_preview.png", "human_visual_report.json"]
REQ = ("reason", "confidence", "evidence", "override_point", "source")


def h(p: Path):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def check(i):
    f = job_dir(f"br_{i:02d}")
    before = {c: h(f / c) for c in PRODUCT}
    rep = HSU.analyze(f)
    after = {c: h(f / c) for c in PRODUCT}
    dec = rep["decisions"]
    labels = {r.get("label") for r in json.loads((f / "game_config.json").read_text()).get("regions", [])}
    list_fields = ["main_subject_regions", "secondary_subject_regions", "background_regions",
                   "foreground_regions", "focal_regions", "low_importance_regions"]
    lists_valid = all(all(x in labels for x in dec[k]["value"]) for k in list_fields)
    rev = json.loads((f / "reveal_validation.json").read_text()).get("completed_reveal_delta_e") \
        if (f / "reveal_validation.json").exists() else None
    return {
        "index": i,
        "fields_ok": len(dec) == 14 and all(all(kk in d for kk in REQ) for d in dec.values()),
        "hsu_ops": all(d["override_point"].startswith("hsu.") for d in dec.values()),
        "scene_type": rep["scene"]["scene_type"],
        "strategy": rep["scene"]["recommended_scene_strategy"],
        "strategy_valid": rep["scene"]["recommended_scene_strategy"] in HSU.STRATEGIES,
        "lists_valid": lists_valid,
        "separation": rep["scene"]["subject_background_separation_score"],
        "composition": rep["scene"]["composition_score"],
        "depth": rep["scene"]["depth_layers"],
        "readonly": before == after,
        "reveal_deltaE": rev,
    }


def main_():
    rows, crashes = [], 0
    for i in range(1, 28):
        if not (job_dir(f"br_{i:02d}") / "game_config.json").exists():
            continue
        try:
            r = check(i)
            rows.append(r)
            print(f"img {i:02d}: {r['scene_type']:11} -> {r['strategy']:22} sep={r['separation']:.2f} "
                  f"comp={r['composition']:.2f} depth={r['depth']} | 14dec={r['fields_ok']} "
                  f"lists={r['lists_valid']} readonly={r['readonly']} revDE={r['reveal_deltaE']}")
        except Exception as exc:  # noqa: BLE001
            crashes += 1
            print(f"img {i:02d}: CRASH {type(exc).__name__}: {exc}")
    agg = {
        "images": len(rows), "crashes": crashes,
        "fields_ok_14": sum(1 for r in rows if r["fields_ok"]),
        "hsu_override_points": sum(1 for r in rows if r["hsu_ops"]),
        "strategy_valid": sum(1 for r in rows if r["strategy_valid"]),
        "region_lists_valid": sum(1 for r in rows if r["lists_valid"]),
        "readonly_only_report": sum(1 for r in rows if r["readonly"]),
        "reveal_deltaE_zero": sum(1 for r in rows if r["reveal_deltaE"] == 0),
        "scene_type_distribution": dict(Counter(r["scene_type"] for r in rows).most_common()),
        "strategy_distribution": dict(Counter(r["strategy"] for r in rows).most_common()),
    }
    print("\nSUMMARY:", json.dumps(agg, indent=1))
    (OUT / "hsu_validation.json").write_text(json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main_()
