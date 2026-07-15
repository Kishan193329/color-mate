"""ColorForge AI — FastAPI application.

Endpoints (Sprint 1):
    POST /api/upload                      -> create job, save original
    POST /api/process                     -> kick off mock processing pipeline
    GET  /api/job/{job_id}                -> job status + progress + artifacts
    GET  /api/jobs                        -> list all jobs
    GET  /api/download/{job_id}/{file}    -> download an artifact

Run locally:
    cd backend
    uvicorn main:app --reload --port 8000
"""
from __future__ import annotations

import json
import shutil
import uuid
from pathlib import Path

from fastapi import BackgroundTasks, Body, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse

import pipeline
import quality_retry_engine
from config import ALLOWED_ORIGINS, ARTIFACTS, JOBS_DIR
from job_store import (
    create_job,
    get_job,
    has_backup,
    job_dir,
    list_jobs,
    read_progress,
    region_total,
    restore_backup,
    write_progress,
)
from config import LINEART_MODES, SEMANTIC_MODE
from job_store import (
    has_versions,
    read_lineart_settings,
    redo as redo_version,
    snapshot,
    undo as undo_version,
    versions_state,
    write_lineart_settings,
)
from services import (
    advanced_lineart_generator,
    ai_mode_router,
    color_paint_builder,
    experience_policy,
    experience_store,
    game_package_builder,
    publishing,
    quality_analyzer,
    region_editor,
    region_hierarchy,
    reveal_game_builder,
    review_engine,
)
from schemas import (
    JobResponse,
    JobsListResponse,
    JobStatus,
    JobSummary,
    ProcessRequest,
    ProgressResponse,
    SaveProgressRequest,
    UploadResponse,
)

app = FastAPI(title="ColorForge AI", version="0.1.0")

# Experience Learning Policy v1 — register the safe policy provider on the decision seam so
# the review layer's advisory scores reflect learned rejection patterns. Overrides ONLY the
# four whitelisted review decisions; with no experience data it is a no-op (identical output).
experience_policy.activate()

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ALLOWED_UPLOAD_TYPES = {"image/png", "image/jpeg", "image/webp"}


@app.get("/api/health")
def health() -> dict:
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Phase 8 — AI assist settings (RunPod optional; local stays the region source)
# ---------------------------------------------------------------------------


@app.get("/api/ai/status")
def ai_status() -> dict:
    return ai_mode_router.status()


@app.post("/api/ai/test")
def ai_test() -> dict:
    return ai_mode_router.test_connection()


@app.post("/api/ai/mode")
def ai_set_mode(body: dict) -> dict:
    mode = (body or {}).get("mode")
    try:
        ai_mode_router.set_mode(mode)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return ai_mode_router.status()


@app.post("/api/upload", response_model=UploadResponse)
async def upload(file: UploadFile = File(...)) -> UploadResponse:
    if file.content_type not in ALLOWED_UPLOAD_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type '{file.content_type}'. "
            "Use PNG, JPEG, or WebP.",
        )

    job_id = uuid.uuid4().hex[:12]
    create_job(job_id)

    original_path = job_dir(job_id) / ARTIFACTS["original"]
    # Persist the upload. Pillow re-encodes to PNG later in preprocess; here we
    # just store the raw bytes under a .png name (Pillow opens by content).
    with original_path.open("wb") as out:
        shutil.copyfileobj(file.file, out)

    return UploadResponse(
        job_id=job_id,
        status=JobStatus.created,
        original_url=f"/api/download/{job_id}/original",
    )


@app.post("/api/process", response_model=JobResponse)
def process(req: ProcessRequest, background: BackgroundTasks) -> JobResponse:
    record = get_job(req.job_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Job not found")
    if record["status"] == "processing":
        raise HTTPException(status_code=409, detail="Job already processing")

    # Product Sprint 2: auto-select the best output profile with quality retry
    # (runs Medium first, escalates/steps down only if quality is poor, keeps the
    # best). Falls back to a plain Medium run identically when Medium is already good.
    background.add_task(quality_retry_engine.process_upload, req.job_id)
    # Reflect that processing has been queued (job status + publishing lifecycle).
    publishing.set_status(job_dir(req.job_id), "processing", validate=False, actor="admin")
    record["status"] = "processing"
    return JobResponse(**record)


@app.get("/api/job/{job_id}", response_model=JobResponse)
def job_status(job_id: str) -> JobResponse:
    record = get_job(job_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return JobResponse(**record)


@app.get("/api/jobs", response_model=JobsListResponse)
def jobs() -> JobsListResponse:
    summaries: list[JobSummary] = []
    for record in list_jobs():
        preview_url = record.get("artifacts", {}).get("preview")
        summaries.append(
            JobSummary(
                job_id=record["job_id"],
                status=record["status"],
                progress=record["progress"],
                created_at=record["created_at"],
                preview_url=preview_url,
            )
        )
    return JobsListResponse(jobs=summaries)


# ---------------------------------------------------------------------------
# Phase 4 — interactive color-by-number game
# ---------------------------------------------------------------------------


def _progress_response(job_id: str, record: dict) -> ProgressResponse:
    filled = record.get("filled", [])
    total = region_total(job_id)
    percent = int(round(len(filled) / total * 100)) if total else 0
    return ProgressResponse(
        job_id=job_id,
        filled=filled,
        total=total,
        percent=percent,
        updated_at=record.get("updated_at"),
    )


@app.get("/api/job/{job_id}/game-config")
def game_config(job_id: str) -> dict:
    """Game-ready config with each region's live `filled` state merged in.

    The on-disk game_config.json is never modified; `filled` is computed from
    saved progress and added to the response only.
    """
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    path = job_dir(job_id) / ARTIFACTS["game_config"]
    if not path.exists():
        raise HTTPException(status_code=409, detail="Job not processed yet")

    data = json.loads(path.read_text(encoding="utf-8"))
    filled = set(read_progress(job_id)["filled"])
    for region in data.get("regions", []):
        region["filled"] = region.get("id") in filled
    return data


@app.post("/api/job/{job_id}/save-progress", response_model=ProgressResponse)
def save_progress(job_id: str, req: SaveProgressRequest) -> ProgressResponse:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    record = write_progress(job_id, req.filled)
    return _progress_response(job_id, record)


@app.get("/api/job/{job_id}/progress", response_model=ProgressResponse)
def get_progress(job_id: str) -> ProgressResponse:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return _progress_response(job_id, read_progress(job_id))


# ---------------------------------------------------------------------------
# Phase 5 — quality cleanup & difficulty
# ---------------------------------------------------------------------------


def _require_processed(job_id: str) -> Path:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    folder = job_dir(job_id)
    if not (folder / ARTIFACTS["preprocessed"]).exists():
        raise HTTPException(status_code=409, detail="Job not processed yet")
    return folder


def _read_quality(job_id: str) -> dict:
    folder = job_dir(job_id)
    path = folder / ARTIFACTS["quality_report"]
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    report = quality_analyzer.report_from_files(folder)
    if report is None:
        raise HTTPException(status_code=404, detail="No quality report available")
    return report


def _current_difficulty(job_id: str) -> str:
    path = job_dir(job_id) / ARTIFACTS["quality_report"]
    if path.exists():
        prof = json.loads(path.read_text(encoding="utf-8")).get("profile")
        if prof in ("easy", "medium", "hard"):
            return prof
    return "medium"


def _reprocess(job_id: str, difficulty: str) -> dict:
    _require_processed(job_id)
    pipeline.reprocess(job_id, difficulty)  # synchronous; fast
    record = get_job(job_id)
    if record and record.get("status") == "error":
        raise HTTPException(status_code=500, detail=record.get("error", "Cleanup failed"))
    return _read_quality(job_id)


@app.post("/api/job/{job_id}/auto-clean")
def auto_clean(job_id: str) -> dict:
    """Re-run cleanup at the job's current difficulty."""
    return _reprocess(job_id, _current_difficulty(job_id))


@app.post("/api/job/{job_id}/make-easier")
def make_easier(job_id: str) -> dict:
    return _reprocess(job_id, "easy")


@app.post("/api/job/{job_id}/make-detailed")
def make_detailed(job_id: str) -> dict:
    return _reprocess(job_id, "hard")


@app.get("/api/job/{job_id}/quality-report")
def quality_report(job_id: str) -> dict:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return _read_quality(job_id)


@app.post("/api/job/{job_id}/undo-cleanup")
def undo_cleanup(job_id: str) -> dict:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    if not has_backup(job_id):
        raise HTTPException(status_code=409, detail="Nothing to undo")
    restore_backup(job_id)
    return _read_quality(job_id)


# ---------------------------------------------------------------------------
# Phase 7 — semantic grouping (helper layer)
# ---------------------------------------------------------------------------


@app.post("/api/job/{job_id}/smart-group")
def smart_group(job_id: str) -> dict:
    """Run the semantic classifier/grouper over the job's regions and write the
    semantic fields back into game_config.json and regions.json. Idempotent and
    works on jobs created before Phase 7 too."""
    folder = _require_processed(job_id)
    gc_path = folder / ARTIFACTS["game_config"]
    if not gc_path.exists():
        raise HTTPException(status_code=409, detail="Job not processed yet")

    gc = json.loads(gc_path.read_text(encoding="utf-8"))
    regions = gc.get("regions", [])
    width, height = gc.get("width", 0), gc.get("height", 0)

    info = region_hierarchy.apply_all(regions, width, height, SEMANTIC_MODE)
    gc["semantic"] = {"mode": info["mode"], "hierarchy": info["hierarchy"]}
    gc_path.write_text(json.dumps(gc, indent=2), encoding="utf-8")

    # Mirror the per-region fields into regions.json.
    reg_path = folder / ARTIFACTS["regions"]
    if reg_path.exists():
        rj = json.loads(reg_path.read_text(encoding="utf-8"))
        by_id = {r["id"]: r for r in regions}
        for rr in rj.get("regions", []):
            src = by_id.get(rr.get("id"))
            if src:
                for k in ("semantic_label", "parent_object", "group_id", "region_type"):
                    rr[k] = src.get(k)
        reg_path.write_text(json.dumps(rj, indent=2), encoding="utf-8")

    labels: dict[str, int] = {}
    for r in regions:
        lab = r.get("semantic_label") or "unknown"
        labels[lab] = labels.get(lab, 0) + 1

    return {
        "mode": info["mode"],
        "group_count": info["group_count"],
        "objects": info["objects"],
        "labels": labels,
    }


# ---------------------------------------------------------------------------
# Phase 9 — advanced line art
# ---------------------------------------------------------------------------

_LINEART_KEYS = ("mode", "thickness", "detail", "gap", "remove_noise")


def _overrides(body: dict | None) -> dict:
    body = body or {}
    return {k: body.get(k) for k in _LINEART_KEYS}


@app.get("/api/job/{job_id}/lineart-quality")
def lineart_quality(job_id: str) -> dict:
    folder = _require_processed(job_id)
    path = folder / ARTIFACTS["lineart_quality"]
    if not path.exists():
        raise HTTPException(status_code=404, detail="No line-art quality report")
    report = json.loads(path.read_text(encoding="utf-8"))
    resolved = advanced_lineart_generator.resolve_params(read_lineart_settings(job_id))
    report["settings"] = {k: resolved[k] for k in _LINEART_KEYS}
    report["modes"] = [{"id": k, "label": v["label"]} for k, v in LINEART_MODES.items()]
    return report


@app.post("/api/job/{job_id}/lineart-mode")
def lineart_mode(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    mode = (body or {}).get("mode")
    if mode is not None and mode not in LINEART_MODES:
        raise HTTPException(status_code=400, detail=f"Unknown line-art mode '{mode}'")
    return write_lineart_settings(job_id, _overrides(body))


@app.post("/api/job/{job_id}/regenerate-lineart")
def regenerate_lineart(job_id: str, body: dict | None = None) -> dict:
    _require_processed(job_id)
    overrides = _overrides(body)
    mode = overrides.get("mode")
    if mode is not None and mode not in LINEART_MODES:
        raise HTTPException(status_code=400, detail=f"Unknown line-art mode '{mode}'")

    if (body or {}).get("preview_only"):
        quality = pipeline.preview_lineart(job_id, overrides)
        return {
            "preview_url": f"/api/download/{job_id}/lineart_preview",
            "quality": quality,
        }

    pipeline.regenerate_lineart(job_id, overrides, _current_difficulty(job_id))
    record = get_job(job_id)
    if record and record.get("status") == "error":
        raise HTTPException(status_code=500, detail=record.get("error", "Regeneration failed"))
    folder = job_dir(job_id)
    quality = json.loads((folder / ARTIFACTS["lineart_quality"]).read_text(encoding="utf-8"))
    return {"quality": quality, "settings": read_lineart_settings(job_id)}


# ---------------------------------------------------------------------------
# Phase 10 — manual correction tools (versioned edits)
# ---------------------------------------------------------------------------

ALLOWED_LINEART_TYPES = {"image/png", "image/jpeg", "image/webp"}


def _edit(job_id: str, label: str, fn) -> dict:
    """Snapshot a baseline (once), apply the edit, snapshot the result."""
    if not has_versions(job_id):
        snapshot(job_id, "baseline")
    fn()
    record = get_job(job_id)
    if record and record.get("status") == "error":
        raise HTTPException(status_code=500, detail=record.get("error", "Edit failed"))
    vid = snapshot(job_id, label)
    return {"ok": True, "version": vid, "versions": versions_state(job_id)}


@app.post("/api/job/{job_id}/lineart/save")
async def lineart_save(job_id: str, file: UploadFile = File(...)) -> dict:
    """Save a manually-edited line art (drawn in the correction canvas)."""
    folder = _require_processed(job_id)
    if file.content_type not in ALLOWED_LINEART_TYPES:
        raise HTTPException(status_code=400, detail="Line art must be PNG/JPEG/WebP")
    data = await file.read()

    def apply():
        (folder / ARTIFACTS["lineart"]).write_bytes(data)

    return _edit(job_id, "lineart-edit", apply)


@app.post("/api/job/{job_id}/lineart/recalculate-regions")
def lineart_recalculate(job_id: str) -> dict:
    _require_processed(job_id)
    return _edit(job_id, "recalculate-regions", lambda: pipeline.recalculate_regions(job_id))


@app.post("/api/job/{job_id}/regions/merge")
def regions_merge(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    ids = [int(i) for i in (body or {}).get("region_ids", [])]
    return _edit(job_id, "merge-regions", lambda: region_editor.merge(job_id, ids))


@app.post("/api/job/{job_id}/regions/split")
def regions_split(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    rid = int((body or {}).get("region_id"))
    points = (body or {}).get("points", [])
    return _edit(job_id, "split-region", lambda: region_editor.split(job_id, rid, points))


@app.post("/api/job/{job_id}/regions/delete")
def regions_delete(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    ids = [int(i) for i in (body or {}).get("region_ids", [])] or None
    min_area = (body or {}).get("min_area")
    return _edit(job_id, "delete-regions", lambda: region_editor.delete(job_id, ids, min_area))


@app.post("/api/job/{job_id}/regions/update-label")
def regions_update_label(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    rid = int((body or {}).get("region_id"))
    label_pos = (body or {}).get("label_pos")
    visible = (body or {}).get("visible")
    return _edit(
        job_id, "update-label",
        lambda: region_editor.update_label(job_id, rid, label_pos, visible),
    )


@app.post("/api/job/{job_id}/regions/update-color")
def regions_update_color(job_id: str, body: dict) -> dict:
    _require_processed(job_id)
    rid = int((body or {}).get("region_id"))
    color_id = int((body or {}).get("color_id"))
    return _edit(
        job_id, "update-color",
        lambda: region_editor.update_color(job_id, rid, color_id),
    )


@app.get("/api/job/{job_id}/versions")
def get_versions(job_id: str) -> dict:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return versions_state(job_id)


@app.post("/api/job/{job_id}/undo")
def edit_undo(job_id: str) -> dict:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    vid = undo_version(job_id)
    if vid is None:
        raise HTTPException(status_code=409, detail="Nothing to undo")
    return {"ok": True, "version": vid, "versions": versions_state(job_id)}


@app.post("/api/job/{job_id}/redo")
def edit_redo(job_id: str) -> dict:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")
    vid = redo_version(job_id)
    if vid is None:
        raise HTTPException(status_code=409, detail="Nothing to redo")
    return {"ok": True, "version": vid, "versions": versions_state(job_id)}


# ---------------------------------------------------------------------------
# Admin Review System — read-only review layer + one-click approval actions
# ---------------------------------------------------------------------------

_REVIEW_PAGE_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Admin Review</title>
<style>
:root{--bg:#0f1216;--card:#181d24;--line:#262d37;--fg:#e6e9ef;--mut:#9aa4b2;--acc:#4f8cff;
--ok:#2ecc71;--warn:#f5a623;--bad:#ff5d5d;}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
font:14px/1.5 system-ui,Segoe UI,Roboto,sans-serif}
header{display:flex;align-items:center;gap:14px;padding:16px 22px;border-bottom:1px solid var(--line);
position:sticky;top:0;background:var(--bg);z-index:5}
header h1{font-size:16px;margin:0;font-weight:600}
#status{padding:3px 12px;border-radius:999px;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:.04em}
.s-pending{background:#3a3410;color:var(--warn)}.s-approved{background:#123020;color:var(--ok)}
.s-rejected{background:#3a1414;color:var(--bad)}.s-regenerating{background:#10233a;color:var(--acc)}
main{padding:22px;max-width:1400px;margin:0 auto}
.actions{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:22px}
button{border:1px solid var(--line);background:var(--card);color:var(--fg);padding:10px 18px;border-radius:8px;
font-weight:600;cursor:pointer;transition:.15s}button:hover{border-color:var(--acc)}
button.approve:hover{border-color:var(--ok);color:var(--ok)}button.reject:hover{border-color:var(--bad);color:var(--bad)}
h2{font-size:12px;text-transform:uppercase;letter-spacing:.08em;color:var(--mut);margin:26px 0 12px}
.stats{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}
.stat{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}
.stat .k{color:var(--mut);font-size:11px;text-transform:uppercase;letter-spacing:.05em}
.stat .v{font-size:22px;font-weight:700;margin-top:4px}
.stat .sub{color:var(--mut);font-size:12px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.card .cap{padding:10px 14px;font-weight:600;border-bottom:1px solid var(--line);display:flex;justify-content:space-between}
.card .cap span{color:var(--mut);font-weight:400}
.card img{width:100%;display:block;background:#fff}
.card .none{padding:40px;text-align:center;color:var(--mut)}
#cmp{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px;margin-top:12px;display:none}
table{border-collapse:collapse;width:100%}td,th{padding:6px 12px;text-align:right;border-bottom:1px solid var(--line)}
th:first-child,td:first-child{text-align:left}.up{color:var(--ok)}.dn{color:var(--bad)}
#toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--card);
border:1px solid var(--acc);padding:12px 20px;border-radius:8px;opacity:0;transition:.3s;pointer-events:none}
</style></head><body>
<header><h1>ColorForge · Admin Review</h1><span id="status" class="s-pending">…</span>
<span style="color:var(--mut);font-size:12px">job __JOB_ID__</span></header>
<main>
<div class="actions">
  <button class="approve" onclick="act('approve','POST')">✓ Approve</button>
  <button class="reject" onclick="reject()">✕ Reject</button>
  <button onclick="act('regenerate','POST')">↻ Regenerate</button>
  <button onclick="compare()">⇄ Compare Previous Version</button>
</div>
<div id="cmp"></div>
<h2>Statistics</h2><div class="stats" id="stats"></div>
<h2>Previews</h2><div class="grid" id="grid"></div>
</main><div id="toast"></div>
<script>
const JOB="__JOB_ID__";
const A=n=>`/api/job/${JOB}/review/asset/${n}`;
const PREV=[["original","Original Image"],["border_preview","Border Preview"],
["number_preview","Number Preview"],["artistic_border_preview","Artistic Border Preview"],
["reveal_preview","Reveal Preview"],["region_heatmap","Region Heatmap"],
["tiny_region_overlay","Tiny Region Overlay"],["border_density_map","Border Density Map"]];
const STAT=[["region_count","Region count",s=>s.region_count],
["palette_count","Palette count",s=>s.palette_count],
["tiny_regions","Tiny regions",s=>s.tiny_regions,s=>`< ${s.tiny_threshold_px}px`],
["largest_region","Largest region",s=>s.largest_region.area_pct+"%",s=>s.largest_region.area_px.toLocaleString()+" px"],
["average_region_size","Avg region size",s=>s.average_region_size.toLocaleString(),()=>"px"],
["estimated_difficulty","Difficulty",s=>s.estimated_difficulty||"—"],
["estimated_play_time_min","Play time",s=>s.estimated_play_time_min,()=>"min"],
["complexity_score","Complexity",s=>s.complexity_score,()=>"/ 100"],
["border_quality_score","Border quality",s=>s.border_quality_score??"—",()=>"/ 100"],
["artistic_score","Artistic score",s=>s.artistic_score??"—",()=>"/ 100"]];
function toast(m){const t=document.getElementById("toast");t.textContent=m;t.style.opacity=1;
setTimeout(()=>t.style.opacity=0,2200);}
function setStatus(s){const e=document.getElementById("status");e.textContent=s;e.className="s-"+s;}
async function load(){
  const r=await fetch(`/api/job/${JOB}/review`); if(!r.ok){toast("Review not available");return;}
  const d=await r.json(); setStatus(d.status||"pending");
  document.getElementById("stats").innerHTML=STAT.map(([k,lbl,v,sub])=>{
    let val,s2=""; try{val=v(d.statistics);}catch(e){val="—";}
    try{s2=sub?sub(d.statistics):"";}catch(e){}
    return `<div class="stat"><div class="k">${lbl}</div><div class="v">${val}</div><div class="sub">${s2}</div></div>`;
  }).join("");
  document.getElementById("grid").innerHTML=PREV.map(([k,lbl])=>{
    const f=d.assets[k];
    const body=f?`<img loading="lazy" src="${A(f)}" alt="${lbl}"/>`:`<div class="none">not available</div>`;
    return `<div class="card"><div class="cap">${lbl}<span>${f||""}</span></div>${body}</div>`;
  }).join("");
}
async function act(name,method){
  const r=await fetch(`/api/job/${JOB}/review/${name}`,{method});
  const d=await r.json().catch(()=>({}));
  if(name==="regenerate"){setStatus("regenerating");toast("Regenerating — review rebuilds when done");return;}
  if(d.status)setStatus(d.status); toast(name+" ✓");
}
async function reject(){
  const reason=prompt("Reject reason (optional):")||"";
  const r=await fetch(`/api/job/${JOB}/review/reject`,{method:"POST",
    headers:{"Content-Type":"application/json"},body:JSON.stringify({reason})});
  const d=await r.json().catch(()=>({})); if(d.status)setStatus(d.status); toast("rejected ✕");
}
async function compare(){
  const r=await fetch(`/api/job/${JOB}/review/compare`); const d=await r.json();
  const box=document.getElementById("cmp"); box.style.display="block";
  if(!d.available){box.innerHTML="<b>Compare Previous Version</b><br>"+(d.note||"No previous version.");return;}
  const rows=Object.entries(d.deltas).map(([k,v])=>{
    const cls=v.delta>0?"up":v.delta<0?"dn":""; const sign=v.delta>0?"+":"";
    return `<tr><td>${k}</td><td>${v.previous}</td><td>${v.current}</td><td class="${cls}">${sign}${v.delta}</td></tr>`;
  }).join("");
  box.innerHTML=`<b>Compare Previous Version</b> (was: ${d.previous_status||"—"})
    <table><tr><th>Metric</th><th>Previous</th><th>Current</th><th>Δ</th></tr>${rows}</table>`;
}
load();
</script></body></html>"""


def _require_job(job_id: str) -> None:
    if get_job(job_id) is None:
        raise HTTPException(status_code=404, detail="Job not found")


@app.get("/api/job/{job_id}/review")
def get_review(job_id: str) -> dict:
    """The review layer (eight previews, ten statistics, four actions). Built on demand
    from the finished outputs if not already present. Read-only."""
    _require_job(job_id)
    folder = job_dir(job_id)
    if not (folder / "review.json").exists():
        if not (folder / ARTIFACTS["game_config"]).exists():
            raise HTTPException(status_code=409, detail="Job not processed yet")
        review_engine.build_review(folder, job_id)
    return json.loads((folder / "review.json").read_text(encoding="utf-8"))


@app.post("/api/job/{job_id}/review/rebuild")
def rebuild_review(job_id: str) -> dict:
    """Force-regenerate the review assets + review.json from the current outputs."""
    _require_job(job_id)
    folder = job_dir(job_id)
    if not (folder / ARTIFACTS["game_config"]).exists():
        raise HTTPException(status_code=409, detail="Job not processed yet")
    return review_engine.build_review(folder, job_id)


@app.post("/api/job/{job_id}/review/approve")
def review_approve(job_id: str) -> dict:
    _require_job(job_id)
    # The review decision also advances the publishing lifecycle (review -> approved).
    publishing.set_status(job_dir(job_id), "approved", validate=False, actor="admin")
    result = review_engine.set_status(job_dir(job_id), "approved")
    try:
        experience_store.record_approval(job_id, job_dir(job_id))  # positive experience signal
    except Exception:  # noqa: BLE001 - experience capture is best-effort
        pass
    return result


@app.post("/api/job/{job_id}/review/reject")
def review_reject(job_id: str, payload: dict | None = Body(default=None)) -> dict:
    _require_job(job_id)
    reason = (payload or {}).get("reason") if isinstance(payload, dict) else None
    publishing.set_status(job_dir(job_id), "rejected", validate=False, reason=reason, actor="admin")
    return review_engine.set_status(job_dir(job_id), "rejected", reason)


@app.post("/api/job/{job_id}/review/regenerate")
def review_regenerate(job_id: str, background: BackgroundTasks) -> dict:
    """Snapshot the current review (for Compare Previous Version) then reprocess the
    upload. The review layer rebuilds automatically when processing finishes."""
    record = get_job(job_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Job not found")
    if record["status"] == "processing":
        raise HTTPException(status_code=409, detail="Job already processing")
    review_engine.archive_current(job_dir(job_id))
    background.add_task(quality_retry_engine.process_upload, job_id)
    record["status"] = "processing"
    return {"ok": True, "status": "regenerating",
            "note": "Reprocessing; the review layer rebuilds when it finishes."}


@app.get("/api/job/{job_id}/review/compare")
def review_compare(job_id: str) -> dict:
    """Diff the current review statistics against the archived previous version."""
    _require_job(job_id)
    return review_engine.compare_previous(job_dir(job_id))


@app.get("/api/job/{job_id}/review/asset/{name}")
def review_asset(job_id: str, name: str) -> FileResponse:
    """Serve a review preview. Whitelisted to the filenames declared in review.json's
    asset set (prevents path traversal / arbitrary reads)."""
    _require_job(job_id)
    folder = job_dir(job_id)
    if not (folder / "review.json").exists():
        raise HTTPException(status_code=404, detail="Review not built")
    review = json.loads((folder / "review.json").read_text(encoding="utf-8"))
    allowed = {v for v in review.get("assets", {}).values() if v}
    if name not in allowed:
        raise HTTPException(status_code=404, detail="Not a review asset")
    path = folder / name
    if not path.exists():
        raise HTTPException(status_code=404, detail="Asset not found")
    return FileResponse(path)


@app.get("/api/job/{job_id}/review/page", response_class=HTMLResponse)
def review_page(job_id: str) -> HTMLResponse:
    """A self-contained admin review page: the eight previews, ten statistics, and the
    four one-click actions. No build step — vanilla HTML/JS that consumes the review API."""
    _require_job(job_id)
    return HTMLResponse(_REVIEW_PAGE_HTML.replace("__JOB_ID__", job_id))


# ---------------------------------------------------------------------------
# Admin Publishing Flow — admin uploads, lifecycle, package, catalog
# ---------------------------------------------------------------------------

_PUBLISH_PAGE_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Admin Publishing</title>
<style>
:root{--bg:#0f1216;--card:#181d24;--line:#262d37;--fg:#e6e9ef;--mut:#9aa4b2;--acc:#4f8cff;
--ok:#2ecc71;--warn:#f5a623;--bad:#ff5d5d;--pub:#8b5cf6}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
font:14px/1.5 system-ui,Segoe UI,Roboto,sans-serif;display:flex;flex-direction:column;height:100vh}
header{display:flex;align-items:center;gap:14px;padding:14px 20px;border-bottom:1px solid var(--line)}
header h1{font-size:16px;margin:0}header a{color:var(--acc);text-decoration:none;font-size:13px}
.wrap{display:flex;flex:1;overflow:hidden}
.list{width:320px;border-right:1px solid var(--line);overflow:auto;padding:10px}
.job{display:flex;gap:10px;padding:8px;border:1px solid transparent;border-radius:8px;cursor:pointer}
.job:hover{background:var(--card)}.job.sel{border-color:var(--acc);background:var(--card)}
.job img{width:48px;height:48px;object-fit:cover;border-radius:6px;background:#000}
.job .jn{font-weight:600}.job .jc{color:var(--mut);font-size:12px}
.detail{flex:1;overflow:auto;padding:20px}
.chip{padding:2px 10px;border-radius:999px;font-size:11px;font-weight:700;text-transform:uppercase}
.st-draft{background:#2a2f36;color:var(--mut)}.st-processing{background:#10233a;color:var(--acc)}
.st-review{background:#3a3410;color:var(--warn)}.st-approved{background:#123020;color:var(--ok)}
.st-rejected{background:#3a1414;color:var(--bad)}.st-published{background:#241a3a;color:var(--pub)}
.flow{display:flex;gap:6px;align-items:center;margin:8px 0 18px;flex-wrap:wrap}
.flow .s{padding:3px 10px;border-radius:6px;background:#20252d;color:var(--mut);font-size:12px}
.flow .s.on{background:var(--acc);color:#fff}.flow .a{color:var(--mut)}
.row{display:flex;gap:10px;flex-wrap:wrap;margin:14px 0}
button{border:1px solid var(--line);background:var(--card);color:var(--fg);padding:9px 16px;border-radius:8px;font-weight:600;cursor:pointer}
button:hover{border-color:var(--acc)}button.pub{border-color:var(--pub);color:#c4b5fd}
button.ok:hover{border-color:var(--ok);color:var(--ok)}button.bad:hover{border-color:var(--bad);color:var(--bad)}
input{background:#10141a;border:1px solid var(--line);color:var(--fg);padding:8px;border-radius:6px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:12px;margin-top:8px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.card .cap{padding:8px 12px;font-weight:600;border-bottom:1px solid var(--line);font-size:13px}
.card img{width:100%;display:block;background:#fff}
.stats{display:grid;grid-template-columns:1fr 1fr;gap:6px 16px;padding:12px}
.stats div{display:flex;justify-content:space-between;border-bottom:1px solid var(--line);padding:3px 0}
.stats .k{color:var(--mut)}
.pkg{margin-top:16px;font-size:13px}.pkg .c{color:var(--ok)}.pkg .x{color:var(--bad)}
.ml{display:block;color:var(--mut);font-size:12px;margin:8px 0 3px}
.mi{width:100%;background:#10141a;border:1px solid var(--line);color:var(--fg);padding:8px;border-radius:6px;font:inherit}
h2{font-size:12px;text-transform:uppercase;letter-spacing:.08em;color:var(--mut);margin:22px 0 6px}
#toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--card);border:1px solid var(--acc);padding:12px 20px;border-radius:8px;opacity:0;transition:.3s}
</style></head><body>
<header><h1>ColorForge · Admin Publishing</h1><a href="/api/catalog/page" target="_blank">↗ view user catalog</a>
<span style="flex:1"></span><span id="count" style="color:var(--mut);font-size:12px"></span></header>
<div class="wrap"><div class="list" id="list"></div><div class="detail" id="detail">
<p style="color:var(--mut)">Select an image to review and publish.</p></div></div>
<div id="toast"></div>
<script>
const STATES=["draft","processing","review","approved","published"];
const PREV=[["publish_thumbnail.png","1. Category Grid Preview"],["original.png","2. Original"],
["review_border_preview.png","3. Border-only"],["game_preview.png","4. Numbered"],
["reveal_partial.png","5. Reveal / Fill Simulation"],["reveal_completed.png","6. Final Completed"]];
let cur=null;
function toast(m){const t=document.getElementById("toast");t.textContent=m;t.style.opacity=1;setTimeout(()=>t.style.opacity=0,2200);}
const PA=(j,n)=>`/api/job/${j}/publish/asset/${n}`;
async function loadList(){
  const d=await (await fetch("/api/admin/publishing")).json();
  document.getElementById("count").textContent=d.jobs.length+" images";
  document.getElementById("list").innerHTML=d.jobs.map(j=>{
    const cat=(j.category||{});const th=j.thumbnail?`<img src="${PA(j.job_id,j.thumbnail)}"/>`:`<img/>`;
    return `<div class="job ${cur===j.job_id?'sel':''}" onclick="open_('${j.job_id}')">${th}
      <div><div class="jn">${cat.title||j.job_id}</div>
      <div class="jc">${cat.name||'—'} · <span class="chip st-${j.status}">${j.status}</span></div></div></div>`;
  }).join("")||"<p style='color:var(--mut)'>No images yet.</p>";
}
async function open_(job){
  cur=job; loadList();
  const p=await (await fetch(`/api/job/${job}/publish`)).json();
  let stats={};try{stats=(await (await fetch(`/api/job/${job}/review`)).json()).statistics||{};}catch(e){}
  const cat=p.category||{};const st=p.status;
  const flow=STATES.map((s,i)=>`<span class="s ${s===st?'on':''}">${s}</span>`+(i<STATES.length-1?'<span class="a">›</span>':'')).join("");
  const stEntries=[["Region count",stats.region_count],["Palette count",stats.palette_count],
    ["Tiny regions",stats.tiny_regions],["Difficulty",stats.estimated_difficulty],
    ["Play time",stats.estimated_play_time_min!=null?stats.estimated_play_time_min+" min":"—"],
    ["Complexity",stats.complexity_score],["Border quality",stats.border_quality_score],["Artistic",stats.artistic_score]];
  const previews=PREV.map(([f,l])=>`<div class="card"><div class="cap">${l}</div><img loading="lazy" src="${PA(job,f)}" onerror="this.style.opacity=.15"/></div>`).join("");
  const m=p.manifest||{components:{}};
  const pkg=Object.entries(m.components).map(([k,v])=>`<span class="${v.complete?'c':'x'}">${v.complete?'✓':'✗'} ${k}</span>`).join(" &nbsp; ");
  const acts=[];
  if(st==="review"){acts.push(`<button class="ok" onclick="act('review/approve')">✓ Approve</button>`,`<button class="bad" onclick="rej()">✕ Reject</button>`);}
  if(st==="approved"){acts.push(`<button class="pub" onclick="act('publish/publish')">▲ Publish</button>`,`<button class="bad" onclick="rej()">✕ Reject</button>`);}
  if(st==="published"){acts.push(`<button onclick="act('publish/unpublish')">▼ Unpublish</button>`);}
  acts.push(`<button onclick="act('review/regenerate')">↻ Regenerate</button>`);
  document.getElementById("detail").innerHTML=`
    <div style="display:flex;align-items:center;gap:12px"><h1 style="margin:0;font-size:18px">${cat.title||job}</h1>
      <span class="chip st-${st}">${st}</span></div>
    <div class="flow">${flow}</div>
    <div class="row"><input id="cn" value="${cat.name||''}" placeholder="category"/>
      <input id="ct" value="${cat.title||''}" placeholder="title" style="flex:1"/>
      <button onclick="saveCat('${job}')">Save category</button></div>
    <div class="row">${acts.join("")}</div>
    <div class="pkg"><b>Approved package:</b> ${m.complete?'<span class=c>complete ✓</span>':'<span class=x>incomplete</span>'}<br>${pkg}</div>
    <h2>Quality stats</h2><div class="card"><div class="stats">${stEntries.map(([k,v])=>`<div><span class="k">${k}</span><span>${v??'—'}</span></div>`).join("")}</div></div>
    <h2>Admin review previews</h2><div class="grid">${previews}</div>`;
}
async function act(path){const r=await fetch(`/api/job/${cur}/${path}`,{method:"POST"});
  const d=await r.json().catch(()=>({}));
  if(!r.ok){toast("⚠ "+(d.detail||"failed"));return;}
  toast(path.split("/").pop()+" ✓");open_(cur);}
async function rej(){
  const job=cur;
  let cats=["bad_border","broken_shape","too_many_tiny_regions","important_detail_lost","ugly_lineart","wrong_numbering","bad_mask","not_fun_to_color","over_complicated","too_simple","wrong_category_profile","other"];
  try{const j=await (await fetch("/api/admin/reject-categories")).json(); if(j.categories) cats=j.categories;}catch(e){}
  const ov=document.createElement("div");
  ov.style.cssText="position:fixed;inset:0;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;z-index:50";
  const opts=cats.map(c=>`<option value="${c}">${c.replace(/_/g,' ')}</option>`).join("");
  const notes=[["nw","What is wrong"],["nu","Why it is not useful"],["na","Which area failed"],["nx","What should happen next time"]];
  ov.innerHTML=`<div style="background:#181d24;border:1px solid #262d37;border-radius:12px;padding:20px;width:min(560px,92vw);max-height:90vh;overflow:auto">
    <h2 style="margin:0 0 12px;color:#e6e9ef">Reject image — required feedback</h2>
    <label class="ml">Reason category *</label><select id="rc" class="mi">${opts}</select>
    <label class="ml">Reason *</label><textarea id="rr" class="mi" rows="2"></textarea>
    ${notes.map(([id,l])=>`<label class="ml">${l}</label><textarea id="${id}" class="mi" rows="2"></textarea>`).join("")}
    <label class="ml">Screenshot (optional)</label><input id="rs" type="file" accept="image/*" style="color:#9aa4b2"/>
    <div style="display:flex;gap:10px;margin-top:16px;justify-content:flex-end">
      <button id="rcancel">Cancel</button><button class="bad" id="rsub">Submit rejection</button></div></div>`;
  document.body.appendChild(ov);
  const close=()=>ov.remove();
  ov.addEventListener("click",e=>{if(e.target===ov)close();});
  ov.querySelector("#rcancel").onclick=close;
  ov.querySelector("#rsub").onclick=async()=>{
    const reason=ov.querySelector("#rr").value.trim();
    if(!reason){toast("Reason is required");return;}
    const fd=new FormData();
    fd.append("category",ov.querySelector("#rc").value); fd.append("reason",reason);
    fd.append("what_is_wrong",ov.querySelector("#nw").value);
    fd.append("why_not_useful",ov.querySelector("#nu").value);
    fd.append("which_area_failed",ov.querySelector("#na").value);
    fd.append("what_next_time",ov.querySelector("#nx").value);
    const f=ov.querySelector("#rs").files[0]; if(f) fd.append("screenshot",f);
    const r=await fetch(`/api/admin/job/${job}/reject-feedback`,{method:"POST",body:fd});
    if(!r.ok){const d=await r.json().catch(()=>({}));toast("⚠ "+(d.detail||"failed"));return;}
    close();toast("rejected + feedback saved");open_(job);
  };
}
async function saveCat(job){const name=document.getElementById("cn").value,title=document.getElementById("ct").value;
  await fetch(`/api/job/${job}/publish/category`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({name,title})});
  toast("category saved");open_(job);}
loadList();
</script></body></html>"""

_CATALOG_PAGE_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Catalog</title>
<style>
:root{--bg:#0f1216;--card:#181d24;--line:#262d37;--fg:#e6e9ef;--mut:#9aa4b2}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,Segoe UI,Roboto,sans-serif}
header{padding:20px 26px;border-bottom:1px solid var(--line)}header h1{margin:0;font-size:18px}
header p{margin:4px 0 0;color:var(--mut);font-size:13px}
main{padding:22px 26px;max-width:1300px;margin:0 auto}
h2{font-size:13px;text-transform:uppercase;letter-spacing:.08em;color:var(--mut);margin:26px 0 12px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:16px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:12px;overflow:hidden}
.tile img{width:100%;aspect-ratio:1;object-fit:cover;display:block;background:#000}
.tile .m{padding:10px 12px}.tile .t{font-weight:600}.tile .s{color:var(--mut);font-size:12px;margin-top:2px}
.empty{color:var(--mut);padding:40px 0}
</style></head><body>
<header><h1>ColorForge · Colour-by-Number Catalog</h1><p>Published images available to play.</p></header>
<main id="main"><p class="empty">Loading…</p></main>
<script>
const PA=(j,n)=>`/api/job/${j}/publish/asset/${n}`;
async function load(){
  const d=await (await fetch("/api/catalog")).json();
  const main=document.getElementById("main");
  if(!d.images.length){main.innerHTML='<p class="empty">No published images yet.</p>';return;}
  const byCat={};d.images.forEach(i=>{const c=(i.category||{}).name||"uncategorized";(byCat[c]=byCat[c]||[]).push(i);});
  main.innerHTML=Object.entries(byCat).map(([cat,items])=>`
    <h2>${cat}</h2><div class="grid">${items.map(i=>{
      const c=i.category||{},s=i.stats||{};
      const th=i.thumbnail?`<img src="${PA(i.job_id,i.thumbnail)}"/>`:`<img/>`;
      return `<div class="tile">${th}<div class="m"><div class="t">${c.title||i.job_id}</div>
        <div class="s">${s.regions??'—'} regions · ${s.difficulty||'—'}</div></div></div>`;
    }).join("")}</div>`).join("");
}
load();
</script></body></html>"""


@app.post("/api/admin/upload", response_model=UploadResponse)
async def admin_upload(file: UploadFile = File(...),
                       category: str = Form("uncategorized"),
                       title: str | None = Form(None)) -> UploadResponse:
    """Admin-only upload of a colour image. Creates the job in the `draft` publish state
    with its category metadata. (Users never upload — they only see published images.)"""
    if file.content_type not in ALLOWED_UPLOAD_TYPES:
        raise HTTPException(status_code=400,
                            detail=f"Unsupported file type '{file.content_type}'. Use PNG, JPEG, or WebP.")
    job_id = uuid.uuid4().hex[:12]
    create_job(job_id)
    with (job_dir(job_id) / ARTIFACTS["original"]).open("wb") as out:
        shutil.copyfileobj(file.file, out)
    publishing.init(job_dir(job_id), category=category, title=title, job_id=job_id)  # -> draft
    return UploadResponse(job_id=job_id, status=JobStatus.created,
                          original_url=f"/api/download/{job_id}/original")


@app.get("/api/job/{job_id}/publish")
def get_publish(job_id: str) -> dict:
    """Publish status + category + package manifest for a job."""
    _require_job(job_id)
    folder = job_dir(job_id)
    data = publishing.load(folder) or publishing.init(folder, job_id=job_id)
    publishing.ensure_thumbnail(folder)                 # so the grid preview is available
    return {**data, "manifest": publishing.package_manifest(folder)}


@app.put("/api/job/{job_id}/publish/category")
def set_publish_category(job_id: str, payload: dict = Body(...)) -> dict:
    _require_job(job_id)
    return publishing.set_category(job_dir(job_id), name=payload.get("name"),
                                   title=payload.get("title"), tags=payload.get("tags"))


@app.post("/api/job/{job_id}/publish/publish")
def publish_job(job_id: str) -> dict:
    """approved -> published. Assembles + verifies the package; refuses if not approved
    or the package is incomplete. Also builds the Android game package."""
    _require_job(job_id)
    folder = job_dir(job_id)
    try:
        result = publishing.publish(folder)
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    try:
        result["game_package"] = {"complete": game_package_builder.build_game_package(folder)["complete"]}
    except Exception:  # noqa: BLE001 - packaging is best-effort at publish time
        pass
    return result


@app.post("/api/job/{job_id}/package")
def build_package(job_id: str) -> dict:
    """Build the Android game package (game_package.json + game_assets.zip). Only approved
    (or published) images can be packaged."""
    _require_job(job_id)
    try:
        return game_package_builder.build_game_package(job_dir(job_id))
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))


@app.get("/api/job/{job_id}/package")
def get_package(job_id: str) -> dict:
    """The game_package.json manifest (must be built first)."""
    _require_job(job_id)
    pkg = game_package_builder.load_package(job_dir(job_id))
    if pkg is None:
        raise HTTPException(status_code=404, detail="Package not built")
    return pkg


@app.get("/api/job/{job_id}/package/download")
def download_package(job_id: str) -> FileResponse:
    """Download game_assets.zip."""
    _require_job(job_id)
    path = job_dir(job_id) / game_package_builder.PACKAGE_ZIP
    if not path.exists():
        raise HTTPException(status_code=404, detail="Package not built")
    return FileResponse(path, media_type="application/zip", filename=f"{job_id}_game_assets.zip")


@app.get("/api/job/{job_id}/play", response_class=HTMLResponse)
def play_page(job_id: str, rebuild: bool = False) -> HTMLResponse:
    """The reference-style progressive-reveal game: a clean black-border page where tapping a
    numbered region reveals its original pixels, until the canvas becomes the original. Built
    on demand from labels + reveal_master + game_config (read-only)."""
    _require_job(job_id)
    folder = job_dir(job_id)
    page = folder / "play.html"
    if rebuild or not page.exists():
        if not (folder / "reveal_master.png").exists() or not (folder / ARTIFACTS["game_config"]).exists():
            raise HTTPException(status_code=409, detail="Job needs reveal_master + game_config first")
        reveal_game_builder.build(folder)
    return HTMLResponse(page.read_text(encoding="utf-8"))


@app.get("/api/job/{job_id}/color", response_class=HTMLResponse)
def color_page(job_id: str, rebuild: bool = False) -> HTMLResponse:
    """Colour-FILL play: same regions + reveal-the-original tech, but a paint-by-number FEEL — pick a
    palette colour, tap its numbered regions; each paints its flat colour then morphs into the real
    photo pixels. Feels like colouring; the finished canvas is the original photograph."""
    _require_job(job_id)
    folder = job_dir(job_id)
    page = folder / "play_color.html"
    if rebuild or not page.exists():
        if not (folder / "reveal_master.png").exists() or not (folder / ARTIFACTS["game_config"]).exists():
            raise HTTPException(status_code=409, detail="Job needs reveal_master + game_config first")
        reveal_game_builder.build_color(folder)
    return HTMLResponse(page.read_text(encoding="utf-8"))


@app.get("/api/job/{job_id}/color/export")
def color_export(job_id: str) -> FileResponse:
    """Download the app bundle for the colour-fill game (Flutter/Android): original.png, id_map.png,
    border.svg + border.png, regions.json, swatches/<n>.png, README.md — as a zip."""
    _require_job(job_id)
    folder = job_dir(job_id)
    if not (folder / "reveal_master.png").exists() or not (folder / ARTIFACTS["game_config"]).exists():
        raise HTTPException(status_code=409, detail="Job needs reveal_master + game_config first")
    info = reveal_game_builder.export_color_bundle(folder)
    return FileResponse(info["zip"], media_type="application/zip", filename=f"{job_id}_color_bundle.zip")


@app.get("/api/admin/studio", response_class=HTMLResponse)
def color_studio() -> HTMLResponse:
    """Admin drag-drop studio: drop an image -> upload + full pipeline -> playable colour-fill game
    in an iframe + a download-bundle button. One screen, no manual steps."""
    return HTMLResponse(_STUDIO_HTML)


_STUDIO_HTML = r"""<!doctype html><html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Colour Studio</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#0f1216;color:#e6e9ef;font:14px system-ui,Segoe UI,Roboto,sans-serif;display:flex;flex-direction:column;min-height:100vh}
header{padding:14px 20px;border-bottom:1px solid #262d37;display:flex;gap:12px;align-items:center}
h1{font-size:17px;margin:0}.sp{flex:1}.hint{color:#9aa4b2;font-size:13px}
main{flex:1;display:flex;flex-direction:column;align-items:center;padding:24px;gap:18px;width:100%}
#drop{width:100%;max-width:720px;min-height:240px;border:2px dashed #2a3a52;border-radius:16px;display:flex;flex-direction:column;
align-items:center;justify-content:center;gap:10px;cursor:pointer;background:#12161c;transition:.15s;text-align:center;padding:24px}
#drop.hot{border-color:#4f8cff;background:#141c28}#drop b{font-size:18px}
#prog{max-width:720px;width:100%;display:none;flex-direction:column;gap:10px}
.bar{height:8px;background:#1c2230;border-radius:6px;overflow:hidden}.bar i{display:block;height:100%;background:#4f8cff;width:10%;transition:.4s}
#msg{color:#cdd6e4;font-size:15px}
#stage{width:100%;max-width:920px;display:none;flex-direction:column;gap:12px;align-items:center}
iframe{width:100%;height:72vh;border:1px solid #262d37;border-radius:12px;background:#fff}
.acts{display:flex;gap:12px;flex-wrap:wrap;justify-content:center}
a.btn,button{border:1px solid #2a323d;background:#161b22;color:#e6e9ef;padding:10px 16px;border-radius:9px;font-weight:700;cursor:pointer;text-decoration:none;font-size:14px}
a.btn:hover,button:hover{border-color:#4f8cff;color:#4f8cff}a.go{background:#1f6f43;border-color:#2a8f57;color:#fff}a.go:hover{background:#248050;color:#fff}
.spin{width:18px;height:18px;border:3px solid #2a3a52;border-top-color:#4f8cff;border-radius:50%;display:inline-block;animation:s .8s linear infinite;vertical-align:middle;margin-right:6px}@keyframes s{to{transform:rotate(360deg)}}
</style></head><body>
<header><h1>🎨 ColorForge · Colour Studio</h1><span class="sp"></span>
<span class="hint">upload → auto-generate → play + download the app bundle</span></header>
<main>
<div id="drop"><b>⬆ Drop an image here</b><span class="hint">or click to choose · PNG / JPG / WebP</span>
<input id="file" type="file" accept="image/*" hidden/></div>
<div id="prog"><div id="msg"></div><div class="bar"><i id="fill"></i></div></div>
<div id="stage"><div class="hint" id="stat"></div>
  <iframe id="game" title="colour fill"></iframe>
  <div class="acts">
    <a id="open" class="btn go" target="_blank">▶ Open colour fill</a>
    <a id="dl" class="btn">⬇ Download app bundle (.zip)</a>
    <button onclick="reset()">↺ Upload another</button>
  </div>
</div></main>
<script>
const drop=document.getElementById('drop'),file=document.getElementById('file'),prog=document.getElementById('prog'),
msg=document.getElementById('msg'),fill=document.getElementById('fill'),stage=document.getElementById('stage'),game=document.getElementById('game');
drop.onclick=()=>file.click();
file.onchange=e=>{if(e.target.files[0])run(e.target.files[0]);};
['dragover','dragenter'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.add('hot');}));
['dragleave'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.remove('hot');}));
drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('hot');const f=e.dataTransfer.files[0];if(f)run(f);});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function status(t,pct){prog.style.display='flex';msg.innerHTML=t;if(pct!=null)fill.style.width=pct+'%';}
async function run(f){
  drop.style.display='none';stage.style.display='none';
  try{
    status('<span class="spin"></span> Uploading…',10);
    const fd=new FormData();fd.append('file',f);
    const up=await(await fetch('/api/upload',{method:'POST',body:fd})).json();
    const job=up.job_id;
    status('<span class="spin"></span> Processing — line-art → regions → colours…',30);
    await fetch('/api/process',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({job_id:job})});
    let st='processing',n=0;
    while((st==='processing'||st==='created')&&n<160){await sleep(2500);n++;
      const j=await(await fetch('/api/job/'+job)).json();st=j.status;
      status('<span class="spin"></span> Processing — '+(j.current_step||'working')+'…',30+Math.min(45,n*3));}
    if(st!=='done'&&st!=='completed'){status('⚠ Failed: '+st);return;}
    status('<span class="spin"></span> Building the colour-fill game (≈30s)…',88);
    game.onload=()=>{document.getElementById('open').href='/api/job/'+job+'/color';
      document.getElementById('dl').href='/api/job/'+job+'/color/export';
      document.getElementById('stat').textContent='job '+job+' — ready 🎉 pick a colour, tap regions';
      prog.style.display='none';stage.style.display='flex';};
    game.src='/api/job/'+job+'/color';
  }catch(err){status('⚠ Error: '+err);}
}
function reset(){stage.style.display='none';prog.style.display='none';drop.style.display='flex';file.value='';game.removeAttribute('src');}
</script></body></html>"""


# ── Colour-based paint-by-number (admin: upload → generate → review → approve → export) ─────── #
_PAINT_ASSETS = {"paint_border.svg": "image/svg+xml", "paint_regions.json": "application/json",
                 "paint_preview.png": "image/png", "paint_id_map.png": "image/png",
                 "original.png": "image/png"}


@app.post("/api/job/{job_id}/paint/generate")
def paint_generate(job_id: str, target: int = 170) -> dict:
    """Colour-based paint-by-number: segment the original by colour into closed regions that tile
    the image (no border leak) and emit border SVG + regions JSON + preview + id-map. Read-once,
    write to the job folder; safe to re-run with a different `target`."""
    _require_job(job_id)
    folder = job_dir(job_id)
    original = folder / "original.png"
    if not original.exists():
        raise HTTPException(status_code=409, detail="Job has no original.png")
    info = color_paint_builder.generate(original, folder, target=max(30, min(600, target)))
    (folder / "paint_status.json").write_text(json.dumps({"approved": False, **info}), encoding="utf-8")
    return info


@app.get("/api/job/{job_id}/paint/asset/{name}")
def paint_asset(job_id: str, name: str) -> FileResponse:
    _require_job(job_id)
    if name not in _PAINT_ASSETS:
        raise HTTPException(status_code=404, detail="unknown paint asset")
    path = job_dir(job_id) / name
    if not path.exists():
        raise HTTPException(status_code=404, detail="asset not generated yet")
    return FileResponse(path, media_type=_PAINT_ASSETS[name])


@app.post("/api/job/{job_id}/paint/approve")
def paint_approve(job_id: str, approved: bool = True) -> dict:
    """Mark a generated paint-by-number as approved (→ live in the app) or un-approve it."""
    _require_job(job_id)
    folder = job_dir(job_id)
    sp = folder / "paint_status.json"
    if not sp.exists():
        raise HTTPException(status_code=409, detail="Generate the paint-by-number first")
    st = json.loads(sp.read_text(encoding="utf-8"))
    st["approved"] = bool(approved)
    sp.write_text(json.dumps(st), encoding="utf-8")
    return {"job_id": job_id, "approved": st["approved"]}


@app.get("/api/job/{job_id}/paint/export")
def paint_export(job_id: str) -> FileResponse:
    """Download the app bundle: border SVG + regions JSON + original image, as a zip."""
    _require_job(job_id)
    folder = job_dir(job_id)
    need = ["paint_border.svg", "paint_regions.json", "original.png"]
    missing = [f for f in need if not (folder / f).exists()]
    if missing:
        raise HTTPException(status_code=409, detail=f"Generate first (missing {missing})")
    import zipfile
    zp = folder / "paint_bundle.zip"
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
        for f in need:
            z.write(folder / f, arcname=f)
    return FileResponse(zp, media_type="application/zip", filename=f"{job_id}_paint_by_number.zip")


@app.get("/api/job/{job_id}/paint/review", response_class=HTMLResponse)
def paint_review(job_id: str, target: int = 170, regenerate: bool = False) -> HTMLResponse:
    """Admin review page: generates (if needed) and shows the preview + original with Approve /
    Regenerate / Export controls."""
    _require_job(job_id)
    folder = job_dir(job_id)
    sp = folder / "paint_status.json"
    if regenerate or not sp.exists() or not (folder / "paint_preview.png").exists():
        if not (folder / "original.png").exists():
            raise HTTPException(status_code=409, detail="Job has no original.png")
        info = color_paint_builder.generate(folder / "original.png", folder, target=max(30, min(600, target)))
        st = {"approved": False, **info}
        sp.write_text(json.dumps(st), encoding="utf-8")
    else:
        st = json.loads(sp.read_text(encoding="utf-8"))
    return HTMLResponse(_PAINT_REVIEW_HTML.replace("__JOB__", job_id)
                        .replace("__STAT__", json.dumps(st).replace("<", "\\u003c")))


_PAINT_REVIEW_HTML = r"""<!doctype html><html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Paint-by-Number Review — __JOB__</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#0f1216;color:#e6e9ef;font:14px system-ui,Segoe UI,Roboto,sans-serif}
header{display:flex;gap:12px;align-items:center;flex-wrap:wrap;padding:12px 18px;border-bottom:1px solid #262d37;
position:sticky;top:0;background:#0f1216;z-index:2}
h1{font-size:16px;margin:0}.sp{flex:1}
button,a.btn{border:1px solid #2a323d;background:#161b22;color:#e6e9ef;padding:8px 14px;border-radius:8px;
font-weight:600;cursor:pointer;text-decoration:none;font-size:13px}
button:hover,a.btn:hover{border-color:#4f8cff;color:#4f8cff}
button.go{background:#1f6f43;border-color:#2a8f57;color:#fff}button.go:hover{background:#248050}
#badge{font-weight:700;padding:4px 10px;border-radius:20px;font-size:12px}
.ok{background:#123d26;color:#5fd08a}.no{background:#3a2016;color:#e0965f}
.stat{color:#9aa4b2;font-size:13px}
main{padding:18px;display:grid;grid-template-columns:1fr;gap:16px;max-width:1500px;margin:0 auto}
figure{margin:0}figcaption{color:#9aa4b2;font-size:12px;margin:6px 2px}
img{max-width:100%;border-radius:10px;box-shadow:0 6px 24px #0007;display:block}
input{width:64px;background:#161b22;border:1px solid #2a323d;color:#e6e9ef;border-radius:7px;padding:7px}
</style></head><body>
<header>
<h1>🎨 Paint-by-Number · __JOB__</h1>
<span id="badge">—</span><span id="stat" class="stat"></span>
<span class="sp"></span>
regions <input id="tgt" type="number" min="30" max="600" value="170"/>
<button onclick="regen()">↻ Regenerate</button>
<button id="apv" class="go" onclick="approve()">✓ Approve (go live)</button>
<a class="btn" href="/api/job/__JOB__/paint/export">⬇ Export bundle (.zip)</a>
</header>
<main>
<figure><img src="/api/job/__JOB__/paint/asset/paint_preview.png" alt="preview"/>
<figcaption>numbered page · colour-wise reveal (partial) · complete (flat colours) — closed loops, no leak</figcaption></figure>
<figure><img src="/api/job/__JOB__/paint/asset/original.png" alt="original"/><figcaption>original</figcaption></figure>
</main>
<script>
const JOB="__JOB__"; let ST=__STAT__;
function render(){
  document.getElementById('tgt').value=ST.regions||170;
  document.getElementById('stat').textContent=(ST.regions||0)+' regions · '+(ST.colours||0)+' colours · '+
    (ST.svg_paths||0)+' SVG paths · '+(ST.coverage_pct||0)+'% coverage';
  const b=document.getElementById('badge'), on=!!ST.approved;
  b.textContent=on?'APPROVED · live':'not approved'; b.className=on?'ok':'no';
  document.getElementById('apv').textContent=on?'✓ Approved — un-approve':'✓ Approve (go live)';
}
async function approve(){ const on=!ST.approved;
  const r=await fetch('/api/job/'+JOB+'/paint/approve?approved='+on,{method:'POST'});
  ST.approved=(await r.json()).approved; render(); }
function regen(){ const t=document.getElementById('tgt').value;
  location.href='/api/job/'+JOB+'/paint/review?regenerate=true&target='+t; }
render();
</script></body></html>"""


@app.post("/api/job/{job_id}/publish/unpublish")
def unpublish_job(job_id: str) -> dict:
    _require_job(job_id)
    try:
        return publishing.unpublish(job_dir(job_id))
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))


@app.get("/api/job/{job_id}/publish/asset/{name}")
def publish_asset(job_id: str, name: str) -> FileResponse:
    """Serve a publishing/catalog asset (thumbnail, previews, package files). Whitelisted."""
    _require_job(job_id)
    if name not in publishing.SERVEABLE:
        raise HTTPException(status_code=404, detail="Not a publishable asset")
    path = job_dir(job_id) / name
    if not path.exists():
        raise HTTPException(status_code=404, detail="Asset not found")
    return FileResponse(path)


@app.get("/api/catalog")
def catalog() -> dict:
    """The user-facing catalog — ONLY published images (the category grid users see)."""
    return {"images": publishing.catalog(published_only=True)}


@app.get("/api/admin/publishing")
def admin_publishing() -> dict:
    """Admin dashboard — every job with its publish status (all lifecycle states)."""
    return {"jobs": publishing.catalog(published_only=False)}


@app.get("/api/admin/publishing/page", response_class=HTMLResponse)
def admin_publishing_page() -> HTMLResponse:
    return HTMLResponse(_PUBLISH_PAGE_HTML)


@app.get("/api/catalog/page", response_class=HTMLResponse)
def catalog_page() -> HTMLResponse:
    return HTMLResponse(_CATALOG_PAGE_HTML)


# ---------------------------------------------------------------------------
# Experience Learning — structured rejection feedback (data collection only)
# ---------------------------------------------------------------------------


@app.get("/api/admin/reject-categories")
def reject_categories() -> dict:
    """The required reject taxonomy for the admin reject form."""
    return {"categories": experience_store.REJECT_CATEGORIES}


@app.post("/api/admin/job/{job_id}/reject-feedback")
async def reject_feedback(job_id: str,
                          category: str = Form(...),
                          reason: str = Form(...),
                          what_is_wrong: str | None = Form(None),
                          why_not_useful: str | None = Form(None),
                          which_area_failed: str | None = Form(None),
                          what_next_time: str | None = Form(None),
                          affected_area: str | None = Form(None),
                          screenshot: UploadFile | None = File(None)) -> dict:
    """Record a structured rejection (required category + reason, optional notes + screenshot)
    into the experience store, then advance the lifecycle to `rejected`. Collection only."""
    _require_job(job_id)
    folder = job_dir(job_id)
    shot_bytes = shot_name = None
    if screenshot is not None and getattr(screenshot, "filename", None):
        shot_bytes = await screenshot.read()
        shot_name = screenshot.filename
    try:
        record = experience_store.record_rejection(
            job_id, folder, category=category, reason=reason,
            notes={"what_is_wrong": what_is_wrong, "why_not_useful": why_not_useful,
                   "which_area_failed": which_area_failed, "what_next_time": what_next_time},
            affected_area_description=affected_area,
            screenshot_bytes=shot_bytes, screenshot_name=shot_name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    # Advance the lifecycle to rejected (publishing + review), same as /review/reject.
    publishing.set_status(folder, "rejected", validate=False, reason=reason, actor="admin")
    review_engine.set_status(folder, "rejected", reason)
    return {"ok": True, "status": "rejected", "record": record}


@app.get("/api/admin/experience")
def admin_experience() -> dict:
    """Experience-store summary — the reject taxonomy + the rolled-up feedback patterns."""
    return experience_store.summary()


@app.get("/api/admin/experience-policy")
def admin_experience_policy() -> dict:
    """Experience Learning Policy v1 status — whether it is active, which review decisions it
    may override, and the patterns currently backing it."""
    return experience_policy.status()


# ---------------------------------------------------------------------------
# Local admin gallery — visual review of a batch of processed jobs
# ---------------------------------------------------------------------------

_GALLERY_PAGE_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Admin Gallery (27)</title>
<style>
:root{--bg:#0f1216;--card:#181d24;--line:#262d37;--fg:#e6e9ef;--mut:#9aa4b2;--acc:#4f8cff;
--ok:#2ecc71;--warn:#f5a623;--bad:#ff5d5d;--pub:#8b5cf6}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
font:13px/1.5 system-ui,Segoe UI,Roboto,sans-serif}
header{display:flex;align-items:center;gap:14px;padding:14px 20px;border-bottom:1px solid var(--line);
position:sticky;top:0;background:var(--bg);z-index:5}
header h1{font-size:16px;margin:0}header .sp{flex:1}
header a{color:var(--acc);text-decoration:none;font-size:12px;margin-left:14px}
#summary{color:var(--mut);font-size:12px}
main{padding:18px;display:grid;grid-template-columns:repeat(auto-fill,minmax(460px,1fr));gap:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;overflow:hidden}
.card .top{display:flex;align-items:center;gap:8px;padding:10px 12px;border-bottom:1px solid var(--line)}
.card .jn{font-weight:700}
.chip{padding:2px 9px;border-radius:999px;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.03em}
.v-PASS{background:#123020;color:var(--ok)}.v-REVIEW{background:#3a3410;color:var(--warn)}.v-FAIL{background:#3a1414;color:var(--bad)}
.st-draft{background:#2a2f36;color:var(--mut)}.st-processing{background:#10233a;color:var(--acc)}
.st-review{background:#20252d;color:var(--fg)}.st-approved{background:#123020;color:var(--ok)}
.st-rejected{background:#3a1414;color:var(--bad)}.st-published{background:#241a3a;color:var(--pub)}
.previews{display:grid;grid-template-columns:repeat(5,1fr);gap:2px;background:#0c0f13}
.previews figure{margin:0;position:relative}
.previews img{width:100%;aspect-ratio:1;object-fit:cover;display:block;background:#fff}
.previews figcaption{position:absolute;bottom:0;left:0;right:0;font-size:9px;text-align:center;
background:rgba(0,0,0,.55);color:#dfe3ea;padding:1px 0}
.meta{padding:8px 12px;color:var(--mut);font-size:12px;display:flex;flex-wrap:wrap;gap:4px 12px}
.meta b{color:var(--fg);font-weight:600}
.acts{display:flex;gap:8px;padding:0 12px 12px;align-items:center}
button{border:1px solid var(--line);background:#10141a;color:var(--fg);padding:7px 14px;border-radius:7px;
font-weight:600;cursor:pointer}button.ok:hover{border-color:var(--ok);color:var(--ok)}
button.bad:hover{border-color:var(--bad);color:var(--bad)}
.acts a{color:var(--acc);text-decoration:none;font-size:12px}
#toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--card);
border:1px solid var(--acc);padding:12px 20px;border-radius:8px;opacity:0;transition:.3s;pointer-events:none}
</style></head><body>
<header><h1>ColorForge · Admin Gallery</h1><span id="summary">loading…</span><span class="sp"></span>
<a href="/api/admin/publishing/page" target="_blank">publishing dashboard ↗</a>
<a href="/api/catalog/page" target="_blank">user catalog ↗</a></header>
<main id="grid"></main><div id="toast"></div>
<script>
const P=[["original","Original"],["border","Border"],["numbered","Numbered"],["reveal","Reveal"],["completed","Completed"]];
function toast(m){const t=document.getElementById("toast");t.textContent=m;t.style.opacity=1;setTimeout(()=>t.style.opacity=0,2200);}
async function load(){
  const d=await (await fetch("/api/admin/gallery")).json();
  const v={PASS:0,REVIEW:0,FAIL:0}; d.items.forEach(i=>v[i.verdict]++);
  document.getElementById("summary").textContent=
    `${d.count} images · PASS ${v.PASS} · REVIEW ${v.REVIEW} · FAIL ${v.FAIL}`;
  document.getElementById("grid").innerHTML=d.items.map(card).join("");
}
function card(i){
  const pv=P.map(([k,l])=>`<figure><img loading="lazy" src="${i.previews[k]}" onerror="this.style.opacity=.12"/><figcaption>${l}</figcaption></figure>`).join("");
  return `<div class="card" id="c-${i.job_id}">
    <div class="top"><span class="jn">${i.job_id}</span>
      <span class="chip v-${i.verdict}">${i.verdict}</span>
      <span class="chip st-${i.review_status}" id="s-${i.job_id}">${i.review_status}</span></div>
    <div class="previews">${pv}</div>
    <div class="meta"><span><b>${i.image_type||'?'}</b> type</span><span><b>${i.profile||'?'}</b> profile</span>
      <span><b>${i.region_count}</b> regions</span><span><b>${i.palette_count}</b> colours</span>
      <span><b>${i.play_time_min}</b> min</span><span>reveal ΔE <b>${i.reveal_deltaE}</b></span></div>
    <div class="acts"><button class="ok" onclick="approve('${i.job_id}')">✓ Approve</button>
      <button class="bad" onclick="reject('${i.job_id}')">✕ Reject</button>
      <span class="sp" style="flex:1"></span>
      <a href="${i.color_url}" target="_blank">🎨 colour fill</a>
      <a href="${i.play_url}" target="_blank">▶ reveal</a>
      <a href="${i.review_url}" target="_blank">full review ↗</a>
      <a href="${i.package_url}" target="_blank">package ↗</a></div></div>`;
}
function setStatus(job,s){const e=document.getElementById("s-"+job);if(e){e.textContent=s;e.className="chip st-"+s;}}
async function approve(job){const r=await fetch(`/api/job/${job}/review/approve`,{method:"POST"});
  const d=await r.json().catch(()=>({}));setStatus(job,d.status||"approved");toast(job+" approved ✓");}
async function reject(job){const reason=prompt("Reject reason:")||"";
  const r=await fetch(`/api/job/${job}/review/reject`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({reason})});
  const d=await r.json().catch(()=>({}));setStatus(job,d.status||"rejected");toast(job+" rejected ✕");}
load();
</script></body></html>"""


def _gj(folder: Path, name: str):
    p = folder / name
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _gallery_verdict(record: dict, stats: dict, review: dict, rv: dict, bq: dict) -> str:
    """PASS / REVIEW / FAIL from the review + reveal + border-quality signals."""
    if record.get("status") != "done" or not review:
        return "FAIL"
    if rv.get("completed_reveal_delta_e") not in (0, 0.0) or rv.get("matches_original_under_masks") is False:
        return "FAIL"
    if int(bq.get("region_leak_count", 0) or 0) > 0:
        return "FAIL"
    verdict = (review.get("recommendation") or {}).get("verdict")
    bqs = stats.get("border_quality_score")
    cx = stats.get("complexity_score")
    if verdict in ("likely_reject", "needs_attention") or (isinstance(bqs, (int, float)) and bqs < 65) \
            or (isinstance(cx, (int, float)) and cx >= 82):
        return "REVIEW"
    return "PASS"


@app.get("/api/admin/gallery")
def admin_gallery(prefix: str = "br_") -> dict:
    """Aggregated visual-review data for the processed test batch (jobs whose id starts with
    `prefix`; default the 27 test-image jobs). Read-only."""
    items = []
    for record in list_jobs():
        job_id = record.get("job_id", "")
        if prefix and not job_id.startswith(prefix):
            continue
        folder = job_dir(job_id)
        gc = _gj(folder, "game_config.json")
        if not gc:
            continue
        review = _gj(folder, "review.json")
        stats = review.get("statistics", {}) or {}
        itr = _gj(folder, "image_type_report.json")
        rv = _gj(folder, "reveal_validation.json")
        bq = _gj(folder, "border_quality_report.json")
        base = f"/api/job/{job_id}"
        items.append({
            "job_id": job_id,
            "previews": {
                "original": f"{base}/review/asset/original.png",
                "border": f"{base}/review/asset/review_border_preview.png",
                "numbered": f"{base}/review/asset/game_preview.png",
                "reveal": f"{base}/publish/asset/reveal_partial.png",
                "completed": f"{base}/review/asset/reveal_completed.png",
            },
            "profile": gc.get("difficulty") or (gc.get("stats") or {}).get("difficulty"),
            "image_type": itr.get("image_type"),
            "region_count": stats.get("region_count") or len(gc.get("regions", [])),
            "palette_count": stats.get("palette_count") or len(gc.get("palette", [])),
            "play_time_min": stats.get("estimated_play_time_min"),
            "reveal_deltaE": rv.get("completed_reveal_delta_e"),
            "verdict": _gallery_verdict(record, stats, review, rv, bq),
            "review_status": publishing.status(folder),
            "review_url": f"{base}/review/page",
            "package_url": f"{base}/package",
            "play_url": f"{base}/play",
            "color_url": f"{base}/color",
        })
    items.sort(key=lambda x: x["job_id"])
    return {"count": len(items), "prefix": prefix, "items": items}


@app.get("/api/admin/gallery/page", response_class=HTMLResponse)
def admin_gallery_page() -> HTMLResponse:
    return HTMLResponse(_GALLERY_PAGE_HTML)


@app.get("/api/download/{job_id}/{file_type}")
def download(job_id: str, file_type: str) -> FileResponse:
    if file_type not in ARTIFACTS:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown file type '{file_type}'. "
            f"Valid: {', '.join(ARTIFACTS)}",
        )
    path: Path = job_dir(job_id) / ARTIFACTS[file_type]
    if not path.exists():
        raise HTTPException(status_code=404, detail="Artifact not found")
    return FileResponse(path)


# Friendly root.
@app.get("/")
def root() -> dict:
    return {
        "name": "ColorForge AI",
        "docs": "/docs",
        "jobs_dir": str(JOBS_DIR),
    }
