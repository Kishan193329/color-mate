"""Validation for the Admin Review System. Runs build_review over the 27 finished job
dirs (br_01..br_27 from the border sprint), checking: all 8 previews resolve to real
files, all 10 statistics are present, and — crucially — the review pass is READ-ONLY
(sha1 of every core artifact is identical before/after building the review). Also does
one fresh WIRED pipeline run to confirm pipeline.run auto-emits review.json."""
from __future__ import annotations
import hashlib, io, json, shutil, sys, time
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}
from services import review_engine as R  # noqa
from job_store import create_job, job_dir, get_job  # noqa

OUT = REPO / "research" / "08_reports" / "admin_review"
OUT.mkdir(parents=True, exist_ok=True)
CORE = ["game_config.json", "outline.svg", "labels.npy", "palette.json", "regions.json",
        "reveal_completed.png", "game_preview.png"]
ASSET_KEYS = ["original", "border_preview", "number_preview", "artistic_border_preview",
              "reveal_preview", "region_heatmap", "tiny_region_overlay", "border_density_map"]
STAT_KEYS = ["region_count", "palette_count", "tiny_regions", "largest_region",
             "average_region_size", "estimated_difficulty", "estimated_play_time_min",
             "complexity_score", "border_quality_score", "artistic_score"]


def h(p: Path):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def check(folder: Path) -> dict:
    before = {c: h(folder / c) for c in CORE}
    rev = R.build_review(folder, folder.name)
    after = {c: h(folder / c) for c in CORE}
    readonly_ok = before == after
    assets = rev["assets"]
    present = 0
    missing = []
    for k in ASSET_KEYS:
        fn = assets.get(k)
        if fn and (folder / fn).exists():
            present += 1
        else:
            missing.append(k)
    stats = rev["statistics"]
    stats_ok = all(k in stats and stats[k] is not None for k in STAT_KEYS)
    return dict(readonly_ok=readonly_ok, assets_present=present, missing=missing,
                stats_ok=stats_ok, stats=stats,
                actions=[a["id"] for a in rev["actions"]])


def wired_run():
    """Fresh pipeline run to prove the wiring auto-creates review.json."""
    job = "rev_wire_06"; f = job_dir(job)
    if f.exists():
        shutil.rmtree(f)
    create_job(job)
    shutil.copyfile(sorted((REPO / "test image").glob("*.png"))[5], f / "original.png")
    pipeline.run(job, "hard")
    ok = (f / "review.json").exists() and (get_job(job) or {}).get("status") == "done"
    rev = json.loads((f / "review.json").read_text()) if (f / "review.json").exists() else {}
    return ok, len([k for k in ASSET_KEYS if rev.get("assets", {}).get(k)]), rev.get("status")


def main():
    rows = []
    t0 = time.time()
    for i in range(1, 28):
        folder = job_dir(f"br_{i:02d}")
        if not (folder / "game_config.json").exists():
            print(f"img {i:02d}: SKIP (no job dir)")
            continue
        r = check(folder)
        r["index"] = i
        rows.append(r)
        s = r["stats"]
        print(f"img {i:02d}: readonly={r['readonly_ok']} assets={r['assets_present']}/8 "
              f"stats_ok={r['stats_ok']} | reg={s['region_count']} pal={s['palette_count']} "
              f"tiny={s['tiny_regions']} diff={s['estimated_difficulty']} "
              f"play={s['estimated_play_time_min']}m cx={s['complexity_score']} "
              f"bq={s['border_quality_score']} art={s['artistic_score']}"
              + (f" MISSING={r['missing']}" if r['missing'] else ""))
    n = len(rows)
    ro = sum(1 for r in rows if r["readonly_ok"])
    a8 = sum(1 for r in rows if r["assets_present"] == 8)
    so = sum(1 for r in rows if r["stats_ok"])
    print(f"\nSUMMARY  images={n}  readonly_ok={ro}/{n}  all_8_assets={a8}/{n}  all_10_stats={so}/{n}")
    print("wired pipeline run (fresh, review auto-emitted)...")
    wok, wassets, wstatus = wired_run()
    print(f"  wired review.json auto-created={wok} assets={wassets}/8 status={wstatus}")
    (OUT / "review_validation.json").write_text(json.dumps(
        {"images": n, "readonly_ok": ro, "all_8_assets": a8, "all_10_stats": so,
         "wired_ok": wok, "rows": [{k: v for k, v in r.items() if k != "stats"} | {"stats": r["stats"]} for r in rows]},
        indent=2), encoding="utf-8")
    print(f"done in {time.time()-t0:.1f}s -> {OUT/'review_validation.json'}")


if __name__ == "__main__":
    main()
