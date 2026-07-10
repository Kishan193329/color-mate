"""Validation for the reference-style progressive-reveal gameplay. Builds the playable artifact
for the cat (fresh pipeline) + the 27 finished jobs and checks each required property:
initial = clean black-border page (no revealed original pixels); partial = only tapped regions
reveal; border stays on top; final content == master pixel-identical (0 diff, 0 ΔE); no black
blobs (thin border only); no debug colours in the player view; every region closed + cells tile
100%; numbers present/readable. Also structurally checks play.html."""
from __future__ import annotations
import io, json, shutil, sys
from pathlib import Path
import numpy as np
import cv2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}
from services import reveal_game_builder as RG  # noqa
from job_store import create_job, job_dir  # noqa

OUT = REPO / "research" / "08_reports" / "reference_reveal"
OUT.mkdir(parents=True, exist_ok=True)


def _delta_e(a, b, mask):
    la = cv2.cvtColor(a, cv2.COLOR_BGR2LAB).astype(np.float32)
    lb = cv2.cvtColor(b, cv2.COLOR_BGR2LAB).astype(np.float32)
    d = np.sqrt(((la - lb) ** 2).sum(2))
    return float(d[mask].mean()) if mask.any() else 0.0


def _red(img):  # saturated debug-red pixels
    b, g, r = cv2.split(img)
    return int(((r > 150) & (g < 60) & (b < 60)).sum())


def check(job: str, make_viz=False) -> dict:
    f = job_dir(job)
    RG.build(f)
    lab = np.load(f / "labels.npy").astype(np.int32)
    m = cv2.imread(str(f / "reveal_master.png"))
    fl = RG.fill_labels(lab)
    bdr = RG.border_bgra(fl)
    meta = RG.region_meta(fl, json.loads((f / "game_config.json").read_text()))
    ids = [x["id"] for x in meta]

    disp0, cont0 = RG.compose(fl, m, set(), bdr, meta)                       # initial
    one = {ids[0]}
    disp1, cont1 = RG.compose(fl, m, one, bdr, meta)                         # single tap
    half = set(ids[: max(1, len(ids) // 2)])
    dispP, contP = RG.compose(fl, m, half, bdr, meta)                        # partial
    dispF, contF = RG.compose(fl, m, set(ids), bdr, meta)                    # final

    m1 = np.isin(fl, list(one)); mP = np.isin(fl, list(half))
    html = (f / "play.html").read_text(encoding="utf-8")
    r = {
        "job": job, "regions": len(meta),
        "cells_tile_100pct": bool((fl > 0).all()),
        "initial_no_reveal": bool((cont0 == 255).all()),
        "initial_has_border_numbers": bool((disp0 != 255).any()),
        "single_tap_only_that_region": bool(np.array_equal(cont1[m1], m[m1]) and (cont1[~m1] == 255).all()),
        "partial_only_tapped": bool(np.array_equal(contP[mP], m[mP]) and (contP[~mP] == 255).all()),
        "final_content_eq_master": bool(np.array_equal(contF, m)),
        "final_max_pixel_diff": int(np.abs(contF.astype(int) - m.astype(int)).max()),
        "final_delta_e": round(_delta_e(contF, m, np.ones(fl.shape, bool)), 6),
        "border_on_top_in_final": bool((dispF[bdr[..., 3] > 0] == 0).all()),   # black border overrides content
        "no_debug_red_initial": _red(disp0) == 0,
        "no_debug_red_partial_unrevealed": _red(dispP[~mP].reshape(-1, 1, 3)) == 0,
        "border_thin_no_blobs": bool(float((bdr[..., 3] > 0).mean() * 100) < 15.0),
        "border_coverage_pct": round(float((bdr[..., 3] > 0).mean() * 100), 2),
        "every_region_has_number": all(x.get("n") is not None for x in meta),
        "numbers_inside_region": sum(1 for x in meta if 0 <= x["y"] < fl.shape[0] and 0 <= x["x"] < fl.shape[1]
                                     and fl[x["y"], x["x"]] == x["id"]),
        "html_selfcontained": html.count("data:image/png;base64,") == 3 and "function revealRegion(" in html,
        "html_no_debug_red": "red" not in html.lower().split("data:image")[0],
    }
    if make_viz:
        def panel(d, title):
            p = cv2.resize(d, (460, 460), interpolation=cv2.INTER_AREA)
            cv2.rectangle(p, (0, 0), (460, 22), (30, 30, 35), -1)
            cv2.putText(p, title, (8, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (235, 238, 245), 1, cv2.LINE_AA)
            return p
        gap = np.full((460, 6, 3), 200, np.uint8)
        combo = np.hstack([panel(disp0, "1. initial (border+numbers)"), gap,
                           panel(dispP, f"2. partial ({len(half)}/{len(ids)} revealed)"), gap,
                           panel(dispF, "3. final = original (border on top)")])
        cv2.imwrite(str(OUT / f"reveal_states_{job.split('_')[-1]}.png"), combo)
    return r


def main_():
    rows, crashes = [], 0
    print("=== cat (fresh pipeline) ===")
    cat = "rr_cat"; cf = job_dir(cat)
    if cf.exists():
        shutil.rmtree(cf)
    create_job(cat); shutil.copyfile(REPO / "cat.png", cf / "original.png")
    pipeline.run(cat, "medium")
    try:
        r = check(cat, make_viz=True); rows.append(r)
        print("cat:", {k: r[k] for k in ("regions", "final_content_eq_master", "final_max_pixel_diff",
              "final_delta_e", "cells_tile_100pct", "initial_no_reveal", "partial_only_tapped")})
    except Exception as exc:  # noqa: BLE001
        crashes += 1; print("cat CRASH:", exc)

    print("=== 27 finished jobs ===")
    for i in range(1, 28):
        job = f"br_{i:02d}"
        if not (job_dir(job) / "reveal_master.png").exists():
            continue
        try:
            r = check(job, make_viz=(i in (6, 14)))
            rows.append(r)
            print(f"{job}: final_eq={r['final_content_eq_master']} diff={r['final_max_pixel_diff']} "
                  f"dE={r['final_delta_e']} tile100={r['cells_tile_100pct']} init_clean={r['initial_no_reveal']} "
                  f"partial_ok={r['partial_only_tapped']} border_top={r['border_on_top_in_final']} "
                  f"no_red={r['no_debug_red_initial']} thin_border={r['border_thin_no_blobs']}")
        except Exception as exc:  # noqa: BLE001
            crashes += 1; print(f"{job}: CRASH {type(exc).__name__}: {exc}")

    def cnt(k):
        return sum(1 for r in rows if bool(r.get(k)))
    agg = {
        "jobs": len(rows), "crashes": crashes,
        "cells_tile_100pct": cnt("cells_tile_100pct"),
        "initial_clean_border_page": sum(1 for r in rows if r["initial_no_reveal"] and r["initial_has_border_numbers"]),
        "single_tap_only_that_region": cnt("single_tap_only_that_region"),
        "partial_only_tapped": cnt("partial_only_tapped"),
        "final_content_pixel_identical": sum(1 for r in rows if r["final_content_eq_master"] and r["final_max_pixel_diff"] == 0),
        "final_delta_e_zero": sum(1 for r in rows if r["final_delta_e"] == 0),
        "border_on_top": cnt("border_on_top_in_final"),
        "no_debug_red": sum(1 for r in rows if r["no_debug_red_initial"] and r["no_debug_red_partial_unrevealed"]),
        "no_black_blobs_thin_border": cnt("border_thin_no_blobs"),
        "every_region_has_number": cnt("every_region_has_number"),
        "html_selfcontained_no_debug": sum(1 for r in rows if r["html_selfcontained"] and r["html_no_debug_red"]),
    }
    print("\nSUMMARY:", json.dumps(agg, indent=1))
    (OUT / "reveal_game_validation.json").write_text(json.dumps({"summary": agg, "rows": rows}, indent=2), encoding="utf-8")
    shutil.rmtree(cf, ignore_errors=True)


if __name__ == "__main__":
    main_()
