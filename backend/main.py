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
    publishing,
    quality_analyzer,
    region_editor,
    region_hierarchy,
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
    return review_engine.set_status(job_dir(job_id), "approved")


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
async function rej(){const reason=prompt("Reject reason:")||"";
  await fetch(`/api/job/${cur}/review/reject`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({reason})});
  toast("rejected");open_(cur);}
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
    or the package is incomplete."""
    _require_job(job_id)
    try:
        return publishing.publish(job_dir(job_id))
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))


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
