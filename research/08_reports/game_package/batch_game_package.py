"""Validation for the Game Package Builder. Runs the existing cat image (fresh pipeline) plus
the 27 finished jobs. For each: approve, build the package, and check: 0 crashes; package
complete; Android-readable (relative forward-slash) asset paths; reveal ΔE = 0; game_package.json
+ game_assets.zip present; the zip is a valid archive whose entries match the manifest sha1.
Also confirms a non-approved image is refused."""
from __future__ import annotations
import hashlib, io, json, shutil, sys, zipfile
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}
from services import game_package_builder as GPB, publishing  # noqa
from job_store import create_job, job_dir, get_job  # noqa

OUT = REPO / "research" / "08_reports" / "game_package"
OUT.mkdir(parents=True, exist_ok=True)
REQUIRED = [n for n, _s, _z, req in GPB.ASSETS if req]


def android_ok(assets: dict) -> bool:
    return all(v.startswith("assets/") and "\\" not in v and ":" not in v and not v.startswith("/")
               for v in assets.values())


def verify(f: Path, m: dict) -> dict:
    reveal = json.loads((f / "reveal_validation.json").read_text()).get("completed_reveal_delta_e") \
        if (f / "reveal_validation.json").exists() else None
    zip_ok = integ_ok = False
    n_entries = 0
    zp = f / GPB.PACKAGE_ZIP
    if zp.exists():
        try:
            with zipfile.ZipFile(zp) as z:
                if z.testzip() is None:
                    zip_ok = True
                names = set(z.namelist())
                n_entries = len(names)
                integ_ok = ("game_package.json" in names) and all(
                    k in names and hashlib.sha1(z.read(k)).hexdigest() == v
                    for k, v in m["integrity_sha1"].items())
        except zipfile.BadZipFile:
            pass
    return {
        "complete": m["complete"], "missing": m["missing_assets"],
        "android_paths": android_ok(m["assets"]),
        "required_present": all(n in m["assets"] for n in REQUIRED),
        "reveal_deltaE": reveal,
        "json_exists": (f / GPB.PACKAGE_JSON).exists(), "zip_exists": zp.exists(),
        "zip_valid": zip_ok, "integrity_ok": integ_ok, "zip_entries": n_entries,
        "zip_bytes": zp.stat().st_size if zp.exists() else 0,
        "status": m["review_status"], "scores": m["scores"],
    }


def approve_and_build(job: str) -> dict:
    f = job_dir(job)
    if not (f / "publish.json").exists():
        publishing.init(f, category="test", title=job, job_id=job)
    publishing.set_status(f, "approved", validate=False)
    return verify(f, GPB.build_game_package(f))


def main_():
    rows, crashes = [], 0

    # cat via a fresh full pipeline run
    print("=== cat (fresh pipeline) ===")
    cat = "gpb_cat"; cf = job_dir(cat)
    if cf.exists():
        shutil.rmtree(cf)
    create_job(cat); shutil.copyfile(REPO / "cat.png", cf / "original.png")
    pipeline.run(cat, "medium")
    try:
        r = approve_and_build(cat); r["name"] = "cat"; rows.append(r)
        print(f"cat: complete={r['complete']} android={r['android_paths']} revDE={r['reveal_deltaE']} "
              f"zip_valid={r['zip_valid']} integrity={r['integrity_ok']} entries={r['zip_entries']} "
              f"bytes={r['zip_bytes']} scores={r['scores']}")
        shutil.copyfile(cf / GPB.PACKAGE_JSON, OUT / "sample_game_package_cat.json")
    except Exception as exc:  # noqa: BLE001
        crashes += 1; print("cat CRASH:", exc)

    # non-approved refusal check
    publishing.set_status(cf, "draft", validate=False)
    try:
        GPB.build_game_package(cf); print("BUG: packaged a non-approved image")
        refused = False
    except ValueError:
        refused = True
    print("non-approved refused:", refused)

    print("=== 27 finished jobs ===")
    for i in range(1, 28):
        job = f"br_{i:02d}"
        if not (job_dir(job) / "game_config.json").exists():
            continue
        try:
            r = approve_and_build(job); r["name"] = job; rows.append(r)
            print(f"img {i:02d}: complete={r['complete']} android={r['android_paths']} "
                  f"revDE={r['reveal_deltaE']} zip_valid={r['zip_valid']} integrity={r['integrity_ok']} "
                  f"req={r['required_present']} entries={r['zip_entries']} bytes={r['zip_bytes']}")
        except Exception as exc:  # noqa: BLE001
            crashes += 1; print(f"img {i:02d}: CRASH {type(exc).__name__}: {exc}")

    agg = {
        "packages": len(rows), "crashes": crashes, "non_approved_refused": refused,
        "complete": sum(1 for r in rows if r["complete"]),
        "android_paths": sum(1 for r in rows if r["android_paths"]),
        "required_present": sum(1 for r in rows if r["required_present"]),
        "reveal_deltaE_zero": sum(1 for r in rows if r["reveal_deltaE"] == 0),
        "json_and_zip_present": sum(1 for r in rows if r["json_exists"] and r["zip_exists"]),
        "zip_valid": sum(1 for r in rows if r["zip_valid"]),
        "integrity_ok": sum(1 for r in rows if r["integrity_ok"]),
        "total_zip_bytes": sum(r["zip_bytes"] for r in rows),
    }
    print("\nSUMMARY:", json.dumps(agg))
    (OUT / "game_package_validation.json").write_text(
        json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")
    shutil.rmtree(cf, ignore_errors=True)


if __name__ == "__main__":
    main_()
