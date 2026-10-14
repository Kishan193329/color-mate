"""ColorForge platform backend — Admin CRM + content engine (FastAPI).

    drop image (into a category)  ->  auto-generate  ->  REVIEW live in browser  ->
    Publish (one click)  ->  bundle pushed to storage (local now / S3 at deploy)  ->
    it appears in /api/library, which the mobile app reads.

Everything an admin does runs from one page (/) so one person handles the whole catalogue.
Categories are dynamic (create on the fly). Per-image tier = free | reward-ad | premium.
Global app settings (logo, banner, ad unit ids, premium price, daily image) live in config.

Pieces:  services/ = the carved colour-fill engine · db.py = SQLite content model ·
         storage.py = local/S3 abstraction.
"""
from __future__ import annotations

import json
import logging
import queue
import secrets
import threading
import shutil
import time
import uuid
import zipfile
from pathlib import Path

import cv2
import numpy as np
from fastapi import (APIRouter, BackgroundTasks, Body, Depends, FastAPI, File, Form, Header,
                     HTTPException, Request, UploadFile)
from fastapi.responses import FileResponse, HTMLResponse, RedirectResponse

import auth
import db
import diamond_builder as DIA
import ad_config
import diamond_tab
import number_tab
import simplify as simplify_mod
import storage
from services import reveal_game_builder as R

log = logging.getLogger("colorforge")

BASE = Path(__file__).resolve().parent
ITEMS = BASE / "data" / "items"        # working folder per image (git-ignored)
ITEMS.mkdir(parents=True, exist_ok=True)
MAX_SIDE = 1254                        # master resolution cap
THUMB = 420                            # catalogue thumbnail size
REJECT_TTL = 3 * 24 * 3600             # rejected images auto-delete after 3 days
FLATTEN = {"": 1.0, "light": 1.5, "medium": 2.1, "strong": 2.8}   # Simplify -> colour flattening

# ── generation runs ONE AT A TIME ────────────────────────────────────────────
# The upload form takes several files at once and each one used to be handed straight to a
# BackgroundTask. Those are sync functions, so Starlette runs them in its threadpool — ten
# photos meant ten torch passes at once on a 2-CPU box, AND `reveal_game_builder._flattened()`
# patches module globals, so two jobs at different Simplify levels silently corrupted each
# other's settings. (The comment there claiming "one job at a time" was an assumption, not a
# fact.) A single worker thread now drains a queue, so that assumption is finally true and the
# box is never asked to run more than one model at a time. Order is preserved.
# ── login throttle ───────────────────────────────────────────────────────────
# The admin sits on the public internet behind one password, and nothing counted failed
# attempts. In-memory is enough: this runs as a single uvicorn process, and a restart
# clearing the counters is not a weakness worth adding a table for.
_LOGIN_FAILS: dict[str, list[float]] = {}
_LOGIN_MAX = 8           # failures allowed ...
_LOGIN_WINDOW = 600.0    # ... within this many seconds, per IP
_LOGIN_BLOCK = 900.0     # then locked out for this long


def _login_guard(ip: str) -> None:
    """Raise 429 if this IP has been guessing. Called BEFORE the password is checked."""
    now = time.time()
    fails = [t for t in _LOGIN_FAILS.get(ip, []) if now - t < _LOGIN_BLOCK]
    _LOGIN_FAILS[ip] = fails
    recent = [t for t in fails if now - t < _LOGIN_WINDOW]
    if len(recent) >= _LOGIN_MAX:
        wait = int(_LOGIN_BLOCK - (now - max(recent)))
        raise HTTPException(429, f"too many sign-in attempts — try again in {max(1, wait // 60)} min",
                            headers={"Retry-After": str(max(1, wait))})


def _login_failed(ip: str) -> None:
    _LOGIN_FAILS.setdefault(ip, []).append(time.time())
    if len(_LOGIN_FAILS) > 5000:                 # never let the map grow without bound
        cutoff = time.time() - _LOGIN_BLOCK
        for k in [k for k, v in _LOGIN_FAILS.items() if not any(t > cutoff for t in v)]:
            _LOGIN_FAILS.pop(k, None)


_GEN_Q: "queue.Queue[tuple]" = queue.Queue()


def _gen_worker() -> None:
    while True:
        fn, args = _GEN_Q.get()
        try:
            fn(*args)
        except Exception:  # noqa: BLE001 — a failed job must never kill the worker
            log.exception("generation job failed: %s%r", getattr(fn, "__name__", fn), args)
        finally:
            _GEN_Q.task_done()


threading.Thread(target=_gen_worker, name="cf-generate", daemon=True).start()


def _enqueue(fn, *args) -> None:
    """Hand a generation job to the single worker. Returns at once; the item stays
    'processing' while it waits, which is exactly what the admin already shows."""
    _GEN_Q.put((fn, args))


db.init()
db.purge_rejected(time.time() - REJECT_TTL)   # tidy old rejections on startup
store = storage.get_store()
app = FastAPI(title="ColorForge Platform")


# ── helpers ───────────────────────────────────────────────────────────────────
def _need(iid: str) -> dict:
    it = db.get_image(iid)
    if it is None:
        raise HTTPException(404, "item not found")
    return it


def _client_ip(req: Request) -> str:
    return req.client.host if req.client else ""


def _log(user: dict | None, action: str, target: str, req: Request) -> None:
    db.add_audit(user["id"] if user else None, action, target, _client_ip(req), time.time())


def _pub_user(u: dict) -> dict:
    return {"id": u["id"], "name": u["name"], "email": u["email"], "role": u["role"],
            "active": bool(u["active"]), "twofa": bool(u["twofa"])}


# ── auth (JWT) — every protected endpoint depends on current_user / require_boss ─
def current_user(authorization: str = Header(default="")) -> dict:
    token = authorization[7:] if authorization[:7].lower() == "bearer " else ""
    p = auth.decode(token)
    if not p or p.get("typ") != "access":
        raise HTTPException(401, "not authenticated")
    u = db.get_user(int(p["sub"]))
    if not u or not u["active"]:
        raise HTTPException(401, "account inactive")
    return u


def require_boss(user: dict = Depends(current_user)) -> dict:
    if user["role"] not in ("boss", "reviewer"):
        raise HTTPException(403, "not allowed — boss only")
    return user


@app.post("/api/auth/login")
def login(request: Request, payload: dict = Body(...)):
    ip = _client_ip(request)
    _login_guard(ip)
    u = db.get_user_by_email(payload.get("email", ""))
    if not u or not auth.verify_password(payload.get("password", ""), u["pw_hash"]):
        _login_failed(ip)
        log.warning("failed sign-in for %r from %s", payload.get("email", "")[:80], ip)
        raise HTTPException(401, "wrong email or password")
    if not u["active"]:
        raise HTTPException(403, "account deactivated — contact the owner")
    _LOGIN_FAILS.pop(ip, None)                   # a good password clears the slate
    now, jti = time.time(), auth.new_jti()
    ua = (request.headers.get("user-agent", "") or "")[:120]
    db.add_session(jti, u["id"], ua, _client_ip(request), now)
    _log(u, "Login", "", request)
    return {"access": auth.make_access(u, now), "refresh": auth.make_refresh(u["id"], jti, now),
            "user": _pub_user(u)}


@app.post("/api/auth/refresh")
def refresh(payload: dict = Body(...)):
    p = auth.decode(payload.get("refresh", ""))
    if not p or p.get("typ") != "refresh":
        raise HTTPException(401, "invalid refresh token")
    s = db.get_session(p.get("jti", ""))
    if not s or s["revoked"]:
        raise HTTPException(401, "session revoked — sign in again")
    u = db.get_user(int(p["sub"]))
    if not u or not u["active"]:
        raise HTTPException(401, "account inactive")
    return {"access": auth.make_access(u, time.time())}


@app.post("/api/auth/logout")
def logout(payload: dict = Body(default={})):
    p = auth.decode(payload.get("refresh", "")) or {}
    if p.get("jti"):
        db.revoke_session(p["jti"])
    return {"ok": True}


@app.get("/api/me")
def whoami(user: dict = Depends(current_user)):
    return _pub_user(user)


# ── users / employees (boss only) ─────────────────────────────────────────────
@app.get("/api/users")
def list_users(user: dict = Depends(require_boss)):
    return db.employee_stats()


@app.post("/api/users")
def create_user(request: Request, payload: dict = Body(...), user: dict = Depends(require_boss)):
    name = (payload.get("name") or "").strip()
    email = (payload.get("email") or "").strip().lower()
    if not name or not email:
        raise HTTPException(400, "name and email are required")
    if db.get_user_by_email(email):
        raise HTTPException(409, "that email already has an account")
    role = payload.get("role") if payload.get("role") in ("employee", "reviewer") else "employee"
    temp = payload.get("password") or secrets.token_urlsafe(9)
    uid = db.create_user(name, email, role, auth.hash_password(temp), time.time())
    _log(user, "Added employee", name, request)
    return {"id": uid, "name": name, "email": email, "role": role, "tempPassword": temp}


@app.patch("/api/users/{uid}")
def edit_user(uid: int, request: Request, payload: dict = Body(...), user: dict = Depends(require_boss)):
    tgt = db.get_user(uid)
    if not tgt:
        raise HTTPException(404, "user not found")
    if tgt["role"] == "boss":
        raise HTTPException(403, "the owner account can't be changed here")
    fields = {k: payload[k] for k in ("name", "role", "active") if k in payload}
    if payload.get("password"):
        fields["pw_hash"] = auth.hash_password(payload["password"])
    db.update_user(uid, **fields)
    _log(user, "Updated employee", tgt["name"], request)
    return _pub_user(db.get_user(uid))


@app.delete("/api/users/{uid}")
def remove_user(uid: int, request: Request, user: dict = Depends(require_boss)):
    tgt = db.get_user(uid)
    if not tgt:
        raise HTTPException(404, "user not found")
    if tgt["role"] == "boss":
        raise HTTPException(403, "the owner account can't be removed")
    db.delete_user(uid)
    _log(user, "Removed employee", tgt["name"], request)
    return {"deleted": uid}


# ── audit + sessions (boss only) ──────────────────────────────────────────────
@app.get("/api/audit")
def get_audit(user: dict = Depends(require_boss)):
    return db.audit()


@app.get("/api/sessions")
def get_sessions(user: dict = Depends(require_boss)):
    return db.sessions()


@app.post("/api/sessions/{jti}/revoke")
def revoke_sess(jti: str, request: Request, user: dict = Depends(require_boss)):
    db.revoke_session(jti)
    _log(user, "Revoked session", jti[:8], request)
    return {"ok": True}


def _generate(folder: Path, simplify_level: str | None = None) -> dict:
    """Raw upload -> capped true reveal_master.png (the fill/reveal source) + line-art source
    original.png (optionally simplified for busy/photo images) + self-contained play_color.html.
    The reveal always stays the TRUE photo; only the line-art detection sees the simplified copy."""
    img = cv2.imread(str(folder / "original.png"))
    if img is None:
        raise HTTPException(400, "not a readable image")
    h, w = img.shape[:2]
    s = max(h, w)
    if s > MAX_SIDE:
        sc = MAX_SIDE / s
        img = cv2.resize(img, (round(w * sc), round(h * sc)), interpolation=cv2.INTER_AREA)
    cv2.imwrite(str(folder / "reveal_master.png"), img)                 # true reveal/fill source
    line_src = simplify_mod.simplify(img, simplify_level) if simplify_level else img
    cv2.imwrite(str(folder / "original.png"), line_src)                 # line-art source (maybe simplified)
    # the same Simplify choice also flattens the COLOUR detail, so a busy image yields a
    # playable number of regions instead of ~2000 one-colour slivers
    return R.build_color(folder, FLATTEN.get(simplify_level or "", 1.0))


def _thumb_bytes(master: Path) -> bytes:
    img = cv2.imread(str(master))
    h, w = img.shape[:2]
    sc = THUMB / max(h, w)
    if sc < 1:
        img = cv2.resize(img, (round(w * sc), round(h * sc)), interpolation=cv2.INTER_AREA)
    return cv2.imencode(".png", img)[1].tobytes()


# ── categories ────────────────────────────────────────────────────────────────
@app.get("/api/categories")
def list_categories():
    return db.categories()


@app.post("/api/categories")
def create_category(payload: dict = Body(...), user: dict = Depends(current_user)):
    cid = db.get_or_create_category(payload.get("name", ""), time.time(), payload.get("icon", ""))
    return {"id": cid, "name": payload.get("name", "").strip()}


@app.patch("/api/categories/{cid}")
def edit_category(cid: int, payload: dict = Body(...), user: dict = Depends(require_boss)):
    fields = {k: payload[k] for k in ("name", "icon", "ord") if k in payload}
    if "ord" in fields:
        try:
            fields["ord"] = int(fields["ord"])
        except (TypeError, ValueError):
            raise HTTPException(400, "ord must be a number") from None
    if fields:
        db.update_category(cid, **fields)
    return {"ok": True}


@app.delete("/api/categories/{cid}")
def remove_category(cid: int, user: dict = Depends(require_boss)):
    db.delete_category(cid)
    return {"deleted": cid}


# ── diamond painting: a separate content type (bead grid, not line-art) ───────
DIA_MAX = 1600                       # source cap; the grid is what matters, not the pixels


def _fail(iid: str, exc: BaseException, stage: str) -> None:
    """Record WHY a generation failed. The admin prints this text on the error card, so a
    failure is diagnosable from the browser instead of only from a traceback nobody kept."""
    log.exception("%s failed for %s", stage, iid)
    reason = str(exc).strip() or exc.__class__.__name__
    db.update_image(iid, status="error", error=reason[:400])


def _process_diamond(iid: str, mode: str = "auto", cols: int | None = None, k: int = 32) -> None:
    """Background: image -> bead grid -> palette -> bead render + numbered canvas + playable page.
    mode 'auto' detects the bead pitch (an already-beaded render); 'flat' uses the chosen grid."""
    try:
        folder = ITEMS / iid
        img = cv2.imread(str(folder / "original.png"))
        if img is None:
            raise ValueError("unreadable image")
        h, w = img.shape[:2]
        s = max(h, w)
        if s > DIA_MAX:
            sc = DIA_MAX / s
            img = cv2.resize(img, (round(w * sc), round(h * sc)), interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(folder / "reveal_master.png"), img)
        d = DIA.build(img, cols=(None if mode == "auto" else cols), k=k)
        cv2.imwrite(str(folder / "beads.png"), DIA.render_beads(d, cell=14))
        cv2.imwrite(str(folder / "canvas.png"), DIA.render_canvas(d, cell=20))
        (folder / "grid.json").write_text(json.dumps(DIA.to_json(d), separators=(",", ":")), encoding="utf-8")
        it = db.get_image(iid) or {}
        (folder / "play_diamond.html").write_text(
            DIA.play_page(d, it.get("title") or "Diamond painting"), encoding="utf-8")
        db.update_image(iid, status="review", error="",
                        regions=d["cols"] * d["rows"], colours=len(d["palette"]))
    except Exception as e:  # noqa: BLE001
        _fail(iid, e, "diamond generation")


# ── images: upload -> generate -> review -> publish ───────────────────────────
def _source_kind(folder: Path) -> str:
    """What the picture is made of, for the admin's Detail advice. Always measured on
    reveal_master.png — `original.png` is overwritten by a regenerate with the simplified copy."""
    try:
        return R.source_kind(cv2.imread(str(folder / "reveal_master.png")))
    except Exception:                     # advice is never worth failing a generation over
        log.exception("source_kind failed for %s", folder.name)
        return ""


def _process(iid: str, simplify_level: str | None = None) -> None:
    """Background: run the ~40 s generation, then flip the item to 'review' (or 'error')."""
    try:
        info = _generate(ITEMS / iid, simplify_level)
        db.update_image(iid, status="review", error="", source_kind=_source_kind(ITEMS / iid),
                        regions=info.get("regions"), colours=info.get("palette_numbers"))
    except Exception as e:  # noqa: BLE001 — surface as an error card, keep the server alive
        _fail(iid, e, "colour generation")


@app.post("/api/items")
async def create_item(background: BackgroundTasks, request: Request, file: UploadFile = File(...),
                      title: str = Form(""), category: str = Form(""), simplify: str = Form(""),
                      type: str = Form("colour"), mode: str = Form("auto"),
                      cols: int = Form(78), colours: int = Form(32),
                      user: dict = Depends(current_user)):
    """Save the upload under the signed-in user, register it 'processing', and generate in the
    background. `type` = colour (colour-fill) | diamond (bead grid). For diamond, `mode` is
    'auto' (detect the bead pitch of an existing render) or 'flat' (build a grid `cols` wide)."""
    kind = "diamond" if type.strip().lower() == "diamond" else "colour"
    iid = uuid.uuid4().hex[:12]
    folder = ITEMS / iid
    folder.mkdir(parents=True, exist_ok=True)
    with open(folder / "original.png", "wb") as out:
        shutil.copyfileobj(file.file, out)
    cid = db.get_or_create_category(category, time.time()) if category.strip() else None
    ttl = title.strip() or Path(file.filename or iid).stem
    db.add_image(iid, ttl, cid, user["id"], time.time(), status="processing", kind=kind)
    _log(user, "Uploaded", f"{ttl} ({kind})", request)
    if kind == "diamond":
        background.add_task(_enqueue, _process_diamond, iid, mode.strip().lower(),
                            max(20, min(220, cols)), max(4, min(80, colours)))
    else:
        level = simplify.strip().lower()
        background.add_task(_enqueue, _process, iid, level if level in simplify_mod.LEVELS else None)
    return db.get_image(iid)


@app.get("/api/items")
def list_items(status: str | None = None, type: str | None = None,
               user: dict = Depends(current_user)):
    """Boss/reviewer see everything; an employee sees only their own uploads. `type` filters
    the department (colour | diamond)."""
    kind = type if type in ("colour", "diamond") else None
    if user["role"] == "employee":
        return db.images(status, uploader_id=user["id"], kind=kind)
    return db.images(status, kind=kind)


def _regenerate(iid: str, level: str | None) -> None:
    """Background: re-run line-art + regions from the TRUE reveal_master (always unsimplified),
    at a new simplify level — no re-upload needed."""
    try:
        folder = ITEMS / iid
        img = cv2.imread(str(folder / "reveal_master.png"))
        line_src = simplify_mod.simplify(img, level) if level else img
        cv2.imwrite(str(folder / "original.png"), line_src)
        info = R.build_color(folder, FLATTEN.get(level or "", 1.0))
        db.update_image(iid, status="review", error="", source_kind=_source_kind(folder),
                        regions=info.get("regions"), colours=info.get("palette_numbers"))
    except Exception as e:  # noqa: BLE001
        _fail(iid, e, "colour regeneration")


@app.post("/api/items/{iid}/regenerate")
def regenerate(iid: str, background: BackgroundTasks, payload: dict = Body(default={}),
               user: dict = Depends(current_user)):
    """Re-generate an item. Colour-fill: a new simplify level. Diamond: a new grid size / palette."""
    it = _need(iid)
    if user["role"] == "employee" and it["uploader_id"] != user["id"]:
        raise HTTPException(403, "not your image")
    db.update_image(iid, status="processing")
    if it.get("type") == "diamond":
        mode = (payload.get("mode") or "auto").strip().lower()
        cols = max(20, min(220, int(payload.get("cols") or 78)))
        k = max(4, min(80, int(payload.get("colours") or 32)))
        background.add_task(_enqueue, _process_diamond, iid, mode, cols, k)
    else:
        lvl = (payload.get("simplify") or "").strip().lower()
        background.add_task(_enqueue, _regenerate, iid, lvl if lvl in simplify_mod.LEVELS else None)
    return db.get_image(iid)


@app.patch("/api/items/{iid}")
def edit_item(iid: str, payload: dict = Body(...), user: dict = Depends(current_user)):
    it = _need(iid)
    if user["role"] == "employee" and it["uploader_id"] != user["id"]:
        raise HTTPException(403, "not your image")
    fields = {k: payload[k] for k in ("title", "featured") if k in payload}
    if "tier" in payload and user["role"] != "employee":     # only boss/reviewer set the tier
        fields["tier"] = payload["tier"]
    if "category" in payload:
        name = (payload["category"] or "").strip()
        fields["category_id"] = db.get_or_create_category(name, time.time()) if name else None
    db.update_image(iid, **fields)
    return db.get_image(iid)


# ── workflow: employee submits → boss approves (live) / rejects (+reason) ──────
@app.post("/api/items/{iid}/submit")
def submit_item(iid: str, request: Request, user: dict = Depends(current_user)):
    it = _need(iid)
    if user["role"] == "employee" and it["uploader_id"] != user["id"]:
        raise HTTPException(403, "not your image")
    if it["status"] not in ("review", "rejected"):
        raise HTTPException(409, "only a reviewed image can be submitted")
    db.update_image(iid, status="submitted", submitted=time.time(), rejection="")
    _log(user, "Submitted", it["title"], request)
    return db.get_image(iid)


@app.post("/api/items/{iid}/approve")
def approve_item(iid: str, background: BackgroundTasks, request: Request,
                 payload: dict = Body(default={}), user: dict = Depends(require_boss)):
    """Boss/reviewer only: approve → publish to storage → live in the app. Optional tier and
    `publish_date` ('YYYY-MM-DD' or epoch) — a future date keeps the image hidden in the app
    until then (date-wise scheduling, CLAUDE.md §3.4)."""
    it = _need(iid)
    if payload.get("tier") in ("free", "reward", "premium"):
        db.update_image(iid, tier=payload["tier"])
    pts = number_tab.parse_publish_ts(payload.get("publish_date"))
    if pts:
        db.update_image(iid, publish_ts=pts)
    db.update_image(iid, status="publishing", reviewer_id=user["id"])
    _log(user, "Approved", it["title"], request)
    background.add_task(_enqueue, _publish, iid)
    return db.get_image(iid)


@app.post("/api/items/{iid}/reject")
def reject_item(iid: str, request: Request, payload: dict = Body(...), user: dict = Depends(current_user)):
    it = _need(iid)
    if user["role"] == "employee" and it["uploader_id"] != user["id"]:
        raise HTTPException(403, "not allowed")
    reason = (payload.get("reason") or "").strip()
    db.update_image(iid, status="rejected", rejection=reason, reviewer_id=user["id"], submitted=time.time())
    _log(user, "Rejected", f'{it["title"]} — {reason[:40]}', request)
    return db.get_image(iid)


@app.get("/api/items/{iid}/comments")
def get_comments(iid: str, user: dict = Depends(current_user)):
    _need(iid)
    return db.comments_for(iid)


@app.post("/api/items/{iid}/comments")
def add_comment_ep(iid: str, request: Request, payload: dict = Body(...), user: dict = Depends(current_user)):
    it = _need(iid)
    txt = (payload.get("text") or "").strip()
    if not txt:
        raise HTTPException(400, "empty comment")
    db.add_comment(iid, user["id"], txt, time.time())
    _log(user, "Commented", it["title"], request)
    return db.comments_for(iid)


@app.get("/api/items/{iid}/thumb")
def thumb(iid: str):
    it = _need(iid)
    folder = ITEMS / iid
    # a diamond item shows its bead render; colour-fill shows the master photo
    for name in (["beads.png", "reveal_master.png"] if it.get("type") == "diamond"
                 else ["reveal_master.png"]):
        p = folder / name
        if p.exists():
            return FileResponse(p, media_type="image/png")
    raise HTTPException(404, "no image")


@app.get("/api/items/{iid}/canvas")
def diamond_canvas(iid: str):
    """The numbered blank canvas of a diamond item (what the player starts from)."""
    _need(iid)
    p = ITEMS / iid / "canvas.png"
    if not p.exists():
        raise HTTPException(404, "no canvas")
    return FileResponse(p, media_type="image/png")


_WAIT_PAGE = """<!doctype html><html><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/><meta http-equiv="refresh" content="6"/>
<title>{title} — preparing…</title><style>
 html,body{{margin:0;height:100%;display:grid;place-items:center;background:#17161a;color:#EFEAE2;
  font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;text-align:center}}
 .w{{max-width:420px;padding:28px}} h1{{font-size:20px;margin:0 0 8px}} p{{color:#A79F94;margin:0 0 18px;font-size:14px}}
 .sp{{width:42px;height:42px;margin:0 auto 20px;border:4px solid #ffffff22;border-top-color:#8B7DFF;
  border-radius:50%;animation:s 1s linear infinite}} @keyframes s{{to{{transform:rotate(360deg)}}}}
 .b{{font-size:12.5px;color:#777066}}</style></head><body><div class="w">
 <div class="sp"></div><h1>{head}</h1><p>{msg}</p><div class="b">This page refreshes itself — keep it open.</div>
</div></body></html>"""


@app.get("/review/{iid}", response_class=HTMLResponse)
def review(iid: str):
    """The live, playable review page. While the engine is still working (or if it failed) this
    returns a friendly self-refreshing page instead of a raw error."""
    it = _need(iid)
    page = ITEMS / iid / ("play_diamond.html" if it.get("type") == "diamond" else "play_color.html")
    if not page.exists():
        st = it.get("status")
        head, msg = ("Still preparing…", "The engine is building this image. It will appear here automatically.")
        if st == "error":
            head, msg = ("Couldn't generate this one", "Try Regenerate in the admin, or upload a cleaner image.")
        elif st == "publishing":
            head, msg = ("Publishing…", "The bundle is being pushed to storage.")
        return HTMLResponse(_WAIT_PAGE.format(title=it.get("title", "Image"), head=head, msg=msg))
    return HTMLResponse(page.read_text(encoding="utf-8"))


def _publish(iid: str) -> None:
    """Background: export the app bundle -> push every asset to storage -> also emit the live
    Colour Mate 'Number' tab assets (line + colored PNG + thumbnail) -> mark published."""
    try:
        folder = ITEMS / iid
        it = db.get_image(iid)
        is_diamond = it.get("type") == "diamond"
        thumb = _thumb_bytes(folder / "reveal_master.png")

        if not is_diamond:
            # Colour-fill: the app bundle (id_map / border / regions / original / swatches).
            info = R.export_color_bundle(folder)
            with zipfile.ZipFile(info["zip"]) as z:
                for name in z.namelist():
                    data = z.read(name)
                    if data:                        # skip directory entries
                        store.put_bytes(f"images/{iid}/{name}", data)
            # Delivery extras — NOT part of the engine (the July image-processing logic stays
            # copy-to-copy, §8.5bv). The bundle's photo is a ~1-2 MB PNG and its full border is
            # what the grid was loading per TILE; on a phone that is 30 seconds per open. The
            # photo also ships as JPEG q95 (same pixels to the eye, ~a quarter the bytes) and the
            # line art as a 420px thumbnail for the grid.
            master = cv2.imread(str(folder / "reveal_master.png"))
            if master is not None:
                ok, jb = cv2.imencode(".jpg", master, [cv2.IMWRITE_JPEG_QUALITY, 95])
                if ok:
                    store.put_bytes(f"images/{iid}/original.jpg", jb.tobytes())
            with zipfile.ZipFile(info["zip"]) as z:
                bd = cv2.imdecode(np.frombuffer(z.read("border.png"), np.uint8),
                                  cv2.IMREAD_UNCHANGED)
            if bd is not None:
                sc = 420.0 / max(bd.shape[:2])
                if sc < 1:
                    bd = cv2.resize(bd, (round(bd.shape[1] * sc), round(bd.shape[0] * sc)),
                                    interpolation=cv2.INTER_AREA)
                store.put_bytes(f"images/{iid}/border_thumb.png",
                                cv2.imencode(".png", bd)[1].tobytes())
        else:
            # Diamond: the playable payload is grid.json, plus the renders for the admin preview.
            # Running export_color_bundle() here would spend the whole line-art + region pass
            # producing colour-fill data this item can never use.
            for name in ("grid.json", "beads.png", "canvas.png", "original.png"):
                f = folder / name
                if f.is_file():
                    store.put_bytes(f"images/{iid}/{name}", f.read_bytes())

        store.put_bytes(f"images/{iid}/thumb.png", thumb)
        fields = {"status": "published", "base_url": store.url(f"images/{iid}"),
                  "published": time.time()}

        # Per-tab feed assets. Both tabs list per category and key saved progress by fileName, so
        # an item without a category simply is not listed (it stays reachable by its base_url).
        cat = (it.get("category") or "").strip()
        if cat:
            ts = it.get("publish_ts") or time.time()
            fn = it.get("file_name") or number_tab.make_file_name(it.get("title") or iid, ts)
            if is_diamond:
                diamond_tab.export_and_store(folder, cat, fn, store, thumb)
            else:
                number_tab.export_and_store(folder, cat, fn, store)
            fields["file_name"] = fn
            fields["publish_ts"] = ts
        db.update_image(iid, **fields)
    except Exception:  # noqa: BLE001 — revert to the queue so the boss can retry
        db.update_image(iid, status="submitted")


@app.post("/api/items/{iid}/publish")
def publish(iid: str, background: BackgroundTasks, payload: dict = Body(default={}),
            user: dict = Depends(require_boss)):
    """Push the bundle to storage (boss only). `approve` is the normal path; this re-publishes.
    Optional `publish_date` ('YYYY-MM-DD' or epoch) sets the app's date-wise `timeStamp`."""
    _need(iid)
    pts = number_tab.parse_publish_ts(payload.get("publish_date"))
    if pts:
        db.update_image(iid, publish_ts=pts)
    db.update_image(iid, status="publishing")
    background.add_task(_enqueue, _publish, iid)     # through the single worker, like approve()
    return db.get_image(iid)


@app.post("/api/items/{iid}/schedule")
def schedule_item(iid: str, request: Request, payload: dict = Body(...),
                  user: dict = Depends(require_boss)):
    """Set ONLY the go-live date of an already-published item.

    The bulk daily scheduler in the admin dates a batch of pictures one day apart. Routing that
    through `publish` would put every one of them through the full 40-second export for a change
    that is a single column — the app's date-wise hiding reads `publish_ts` from the DB row
    (`number_items`), not from anything in storage."""
    it = _need(iid)
    if it["status"] != "published":
        raise HTTPException(409, "only a published image can be re-dated — publish it instead")
    pts = number_tab.parse_publish_ts(payload.get("publish_date"))
    if not pts:
        raise HTTPException(422, "publish_date required ('YYYY-MM-DD' or epoch)")
    db.update_image(iid, publish_ts=pts)
    _log(user, "Scheduled", it["title"], request)
    return db.get_image(iid)


@app.delete("/api/items/{iid}")
def delete_item(iid: str, user: dict = Depends(current_user)):
    it = _need(iid)
    if user["role"] == "employee" and it["uploader_id"] != user["id"]:
        raise HTTPException(403, "not your image")
    shutil.rmtree(ITEMS / iid, ignore_errors=True)
    store.delete_prefix(f"images/{iid}")
    db.delete_image(iid)
    return {"deleted": iid}


# ── global config (logo, banner, ads, premium, daily) ─────────────────────────
@app.get("/api/config")
def get_config():
    return db.get_config()


@app.get("/api/ads")
def get_ads(user: dict = Depends(require_boss)):
    cfg = ad_config.get()
    return {"config": cfg, "warnings": ad_config.warnings(cfg)}


@app.put("/api/ads")
def put_ads(request: Request, payload: dict = Body(...), user: dict = Depends(require_boss)):
    cfg = ad_config.save(payload)
    _log(user, "Ad config", "", request)
    return {"config": cfg, "warnings": ad_config.warnings(cfg)}


@app.put("/api/config")
def put_config(payload: dict = Body(...), user: dict = Depends(require_boss)):
    for k, v in payload.items():
        db.set_config(k, v)
    return db.get_config()


# ── app-facing catalogue (what the mobile app downloads) ──────────────────────
@app.get("/api/library")
def library():
    """Published catalogue for the app: categories + images (tier, thumb, bundle base) + config.
    Each image's assets: <bundle_base>/original.png, /id_map.png, /border.png, /regions.json,
    /swatches/<n>.png, /thumb.png."""
    imgs = db.images(status="published")
    return {
        "categories": [{"id": c["id"], "name": c["name"], "icon": c["icon"]} for c in db.categories()],
        "images": [{
            "id": i["id"], "title": i["title"], "category": i["category"],
            "tier": i["tier"], "featured": bool(i["featured"]),
            "regions": i["regions"], "colours": i["colours"],
            "thumb": f'{i["base_url"]}/thumb.png',
            "bundle_base": i["base_url"],
        } for i in imgs],
        "config": db.get_config(),
    }


# ── live Colour Mate "Number" tab (the exact API the app reads — CLAUDE.md §3.2) ──────
# Public, unauthenticated: the mobile app hits these on its base URL. See number_tab.py.
# Registered at BOTH the server root and under /ColoringBook/, so it works whether the app's
# base URL was changed to bare host:port or still carries the original /ColoringBook/ path.
number_router = APIRouter()


@number_router.get("/index.php/App/ads")
def app_ads():
    """The ad settings, keyed exactly like AdsManager.AdModel so the app decodes straight into it.
    Public on purpose — the app calls it before anyone signs in, and it carries no secrets."""
    return ad_config.get()


@number_router.get("/index.php/Number/category/")
def number_category_list():
    return number_tab.categories_payload()


@number_router.get("/index.php/Number/gimg/c/{category}")
def number_gallery(category: str, next: str | None = None):
    return number_tab.gimg_payload(category, next)


@number_router.get("/thumb/{category}/{subpath:path}")
def number_image(category: str, subpath: str):
    # subpath is one of: {file}.png (line art), {file}.colored.png (solution), t/{file}.png (thumb)
    return number_tab.image_response(category, subpath, store)


@number_router.get("/index.php/Number/play/{category}/{file_name}", response_class=HTMLResponse)
def number_play(category: str, file_name: str):
    """The EXACT playable page the admin's 'Open playable preview' shows — the photo-reveal game —
    served by the app's own key (category + fileName) so the New 2026 tab can open it in a WebView.
    Maps fileName → internal iid, then returns the same self-contained play_color.html as
    /review/{iid}."""
    iid = db.iid_for_file_name(file_name)
    if not iid:
        raise HTTPException(404, "unknown picture")
    return review(iid)


# The four raw files the NATIVE photo-reveal game (RevealActivity/RevealLevel) downloads, served
# by the app's own key (category + fileName) so New 2026 can play WITHOUT the play_color.html
# WebView. fileName → iid → bundle base_url (/cdn/images/<job>) → the asset. Works identically on
# the local disk store and on S3 (redirect), same routing idea as image_response().
# border.svg is the TRUE vector of the drawn line (see reveal_game_builder.ink_svg). The app
# prefers it because a 1254px raster shows its own pixels once the player zooms in; the
# vector stays crisp at any zoom and is smaller than the PNG it replaces.
_REVEAL_ASSETS = {"regions.json", "id_map.png", "border.png", "border.svg",
                  "original.png", "original.jpg", "border_thumb.png"}


@number_router.get("/index.php/Number/reveal/{category}/{file_name}/{asset}")
def number_reveal_asset(category: str, file_name: str, asset: str):
    if asset not in _REVEAL_ASSETS:
        raise HTTPException(404, "unknown reveal asset")
    iid = db.iid_for_file_name(file_name)
    if not iid:
        raise HTTPException(404, "unknown picture")
    # base_url is "/cdn/images/<iid>" on local storage but an absolute S3 URL on S3 —
    # it only proves the item is published; the key is always images/<iid>/<asset>.
    if not (db.get_image(iid) or {}).get("base_url"):
        raise HTTPException(404, "no bundle for this picture")
    if asset == "border_thumb.png" and not store.exists(f"images/{iid}/border_thumb.png"):
        asset = "border.png"                         # older item: the grid still gets a picture
    if asset.startswith("original."):
        # Bundles have carried the photo as JPEG since it became the app's heaviest download;
        # anything published before that only has the PNG. Serve whichever this item actually
        # has, so neither the app nor an old item needs to know which era it came from.
        for cand in ("original.jpg", "original.png"):
            if store.exists(f"images/{iid}/{cand}"):
                asset = cand
                break
    key = f"images/{iid}/{asset}"
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "reveal asset missing")
        return FileResponse(p)
    return RedirectResponse(store.url(key), status_code=302)


# ── Diamond tab feed ──────────────────────────────────────────────────────────
# Same shape as the Number tab so the app reuses its REST list code with another root; see
# diamond_tab.py. No upstream merge — diamond content is ours alone.
diamond_router = APIRouter()


@diamond_router.get("/index.php/Diamond/category/")
def diamond_category_list():
    return diamond_tab.categories_payload()


@diamond_router.get("/index.php/Diamond/gimg/c/{category}")
def diamond_gallery(category: str):
    return diamond_tab.gimg_payload(category)


@diamond_router.get("/dthumb/{category}/{subpath:path}")
def diamond_thumb(category: str, subpath: str):
    return diamond_tab.thumb_response(category, subpath, store)


@diamond_router.get("/index.php/Diamond/data/{category}/{file_name}.json")
def diamond_grid(category: str, file_name: str):
    """The bead grid the game plays — diamond_builder.to_json(): {cols, rows, palette, grid}."""
    return diamond_tab.grid_response(category, file_name, store)


app.include_router(number_router)                            # base URL = http://host:port/
app.include_router(number_router, prefix="/ColoringBook")    # base URL = http://host:port/ColoringBook/
app.include_router(diamond_router)
app.include_router(diamond_router, prefix="/ColoringBook")


# ── local CDN (dev only; S3/CloudFront serves these in production) ─────────────
@app.get("/cdn/{key:path}")
def cdn(key: str):
    if store.kind != "local":
        raise HTTPException(404, "cdn served by S3/CloudFront in production")
    p = store.local_path(key)
    if not p:
        raise HTTPException(404, "not found")
    return FileResponse(p)


@app.get("/", response_class=HTMLResponse)
def studio():
    """The admin app. It logs in via /api/auth/login; every API call is authorised server-side."""
    return HTMLResponse((BASE / "admin.html").read_text(encoding="utf-8"))


_STUDIO_HTML = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>ColorForge — Colour Studio (CRM)</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font:15px/1.5 system-ui,Segoe UI,Roboto,sans-serif; background:#0e1116; color:#e6edf3; }
  header { padding:14px 22px; border-bottom:1px solid #222b36; display:flex; align-items:center; gap:12px; }
  header h1 { font-size:17px; margin:0; font-weight:650; }
  .tag { font-size:12px; color:#8b98a5; background:#161b22; padding:3px 9px; border-radius:20px; }
  .spacer { flex:1; }
  main { padding:22px; max-width:1240px; margin:0 auto; }
  .panel { background:#11161d; border:1px solid #222b36; border-radius:14px; padding:18px; margin-bottom:18px; }
  .panel h2 { font-size:14px; margin:0 0 12px; color:#8b98a5; text-transform:uppercase; letter-spacing:.04em; }
  .up-row { display:flex; gap:12px; align-items:stretch; flex-wrap:wrap; }
  #drop { flex:1; min-width:260px; border:2px dashed #2f3a46; border-radius:12px; padding:24px; text-align:center;
          color:#8b98a5; cursor:pointer; transition:.15s; }
  #drop.hot { border-color:#4c9; color:#cfe; background:#0f1a17; }
  #drop b { color:#e6edf3; }
  .catbox { width:230px; display:flex; flex-direction:column; gap:8px; }
  select, input[type=text], input[type=number] { font:inherit; padding:8px 10px; border-radius:8px;
          border:1px solid #2f3a46; background:#0b0e12; color:#e6edf3; width:100%; }
  label.mini { font-size:12px; color:#8b98a5; }
  .chips { display:flex; gap:7px; flex-wrap:wrap; margin-top:10px; }
  .chip { font-size:12px; padding:4px 10px; border-radius:20px; background:#1c242e; border:1px solid #2f3a46;
          cursor:pointer; }
  .chip.on { background:#1f6feb; border-color:#1f6feb; color:#fff; }
  .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(215px,1fr)); gap:15px; }
  .card { background:#151b23; border:1px solid #222b36; border-radius:12px; overflow:hidden; display:flex; flex-direction:column; }
  .card .thumb { aspect-ratio:1/1; background:#0b0e12 center/cover no-repeat; }
  .card .body { padding:10px 12px; display:flex; flex-direction:column; gap:7px; }
  .card .t { font-weight:600; font-size:13.5px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .card .meta { font-size:11.5px; color:#8b98a5; }
  .badge { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; }
  .b-review { background:#3a2f0b; color:#f2cf5b; } .b-published { background:#0f2a1c; color:#5be08f; }
  .b-proc { background:#0d2436; color:#6cc7ff; } .b-error { background:#3a1414; color:#ff8a8a; }
  .card.busy .thumb { position:relative; opacity:.55; }
  .spin { position:absolute; inset:0; margin:auto; width:34px; height:34px; border:3px solid #ffffff33;
          border-top-color:#6cc7ff; border-radius:50%; animation:sp 0.8s linear infinite; }
  @keyframes sp { to { transform:rotate(360deg); } }
  .row { display:flex; gap:6px; flex-wrap:wrap; align-items:center; }
  select.tier { padding:4px 6px; font-size:12px; width:auto; }
  a.btn, button.btn { font:inherit; font-size:12.5px; font-weight:600; padding:5px 10px; border-radius:7px;
        border:1px solid #2f3a46; background:#1c242e; color:#e6edf3; cursor:pointer; text-decoration:none; }
  button.btn.primary { background:#1f6feb; border-color:#1f6feb; }
  button.btn.ghost { color:#8b98a5; }
  #empty { color:#6b7684; text-align:center; padding:30px; }
  details summary { cursor:pointer; color:#8b98a5; font-size:13px; }
  .cfg { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:12px; margin-top:12px; }
  .cfg label { display:flex; flex-direction:column; gap:4px; font-size:12px; color:#8b98a5; }
</style></head>
<body>
<header>
  <h1>🎨 ColorForge — Colour Studio</h1><span class="tag">Admin CRM</span>
  <span class="spacer"></span><span class="tag" id="count">0</span>
</header>
<main>
  <div class="panel">
    <h2>Upload</h2>
    <div class="up-row">
      <div class="catbox">
        <label class="mini">Category</label>
        <select id="cat"></select>
        <input type="text" id="newcat" placeholder="+ new category name" style="display:none"/>
        <label class="mini" style="margin-top:6px">Simplify (dark / photo / busy art)</label>
        <select id="simplify">
          <option value="">Off — clean line art</option>
          <option value="light">Light</option>
          <option value="medium">Medium — photos</option>
          <option value="strong">Strong — very busy</option>
        </select>
      </div>
      <div id="drop">
        <p><b>Drop image(s) here</b> or click</p>
        <p style="font-size:12.5px">→ auto-generate → review → publish</p>
        <input id="file" type="file" accept="image/*" multiple hidden/>
      </div>
    </div>
    <div class="chips" id="catchips"></div>
  </div>

  <div class="panel">
    <details><summary>⚙️ App config — logo, banner, ads, premium</summary>
      <div class="cfg" id="cfg">
        <label>App logo URL<input type="text" data-k="logo"/></label>
        <label>Banner URL<input type="text" data-k="banner"/></label>
        <label>AdMob banner id<input type="text" data-k="ad_banner"/></label>
        <label>AdMob interstitial id<input type="text" data-k="ad_interstitial"/></label>
        <label>AdMob rewarded id<input type="text" data-k="ad_rewarded"/></label>
        <label>Ad frequency (every N opens)<input type="number" data-k="ad_frequency"/></label>
        <label>Premium price<input type="text" data-k="premium_price"/></label>
        <label>Premium SKU<input type="text" data-k="premium_sku"/></label>
      </div>
      <div style="margin-top:12px"><button class="btn primary" onclick="saveCfg()">Save config</button></div>
    </details>
  </div>

  <div class="panel">
    <h2>Library</h2>
    <div class="chips" id="filter"></div>
    <div class="grid" id="grid" style="margin-top:12px"></div>
    <div id="empty">No images yet — pick a category and drop one above.</div>
  </div>
</main>
<script>
const $ = s => document.querySelector(s);
const drop=$('#drop'), file=$('#file'), grid=$('#grid'), catSel=$('#cat'), newcat=$('#newcat');
let CATS=[], FILTER=null;

const NEW='__new__';
catSel.onchange = () => { newcat.style.display = catSel.value===NEW ? 'block' : 'none'; if(catSel.value===NEW) newcat.focus(); };

drop.onclick=()=>file.click();
['dragover','dragenter'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.add('hot');}));
['dragleave','drop'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.remove('hot');}));
drop.addEventListener('drop',ev=>up([...ev.dataTransfer.files]));
file.addEventListener('change',()=>up([...file.files]));

function chosenCategory(){
  if(catSel.value===NEW) return newcat.value.trim();
  return catSel.value || '';
}
async function up(files){
  const cat = chosenCategory();
  const imgs = [...files].filter(f=>f.type.startsWith('image/'));
  if(!imgs.length) return;
  $('#empty').style.display='none';
  const simp = $('#simplify').value;
  for(const f of imgs){
    const fd=new FormData(); fd.append('file',f); fd.append('title',f.name.replace(/\.[^.]+$/,'')); fd.append('category',cat); fd.append('simplify',simp);
    try{
      const r=await fetch('/api/items',{method:'POST',body:fd});
      if(!r.ok) throw new Error(await r.text());
      await load();                       // show the 'generating…' card immediately
    }
    catch(e){ alert('upload failed: '+e.message); }
  }
  newcat.value=''; await loadCats(); await load();
}

async function loadCats(){
  CATS = await (await fetch('/api/categories')).json();
  catSel.innerHTML = '<option value="">— choose category —</option>' +
    CATS.map(c=>`<option value="${c.name}">${c.name}</option>`).join('') +
    `<option value="${NEW}">＋ New category…</option>`;
  $('#catchips').innerHTML = CATS.map(c=>`<span class="chip">${c.name}</span>`).join('') || '<span class="mini">no categories yet</span>';
  $('#filter').innerHTML = `<span class="chip ${FILTER?'':'on'}" onclick="setFilter(null)">All</span>` +
    CATS.map(c=>`<span class="chip ${FILTER===c.name?'on':''}" onclick="setFilter('${c.name}')">${c.name}</span>`).join('');
}
function setFilter(n){ FILTER=n; loadCats(); load(); }

let poller=null;
async function load(){
  let items = await (await fetch('/api/items')).json();
  if(FILTER) items = items.filter(i=>i.category===FILTER);
  $('#count').textContent = items.length+' images';
  $('#empty').style.display = items.length?'none':'';
  grid.innerHTML = items.map(card).join('');
  const busy = items.some(i=>i.status==='processing'||i.status==='publishing');
  if(busy && !poller) poller=setInterval(load, 2500);      // keep refreshing until all done
  if(!busy && poller){ clearInterval(poller); poller=null; }
}

const BADGE = {
  processing:'<span class="badge b-proc">⏳ generating…</span>',
  publishing:'<span class="badge b-proc">⏳ publishing…</span>',
  review:'<span class="badge b-review">● review</span>',
  published:'<span class="badge b-published">✓ live</span>',
  error:'<span class="badge b-error">⚠ failed</span>',
};
function card(it){
  const S=it.status, busy=(S==='processing'||S==='publishing');
  const tier = ['free','reward','premium'].map(t=>`<option ${it.tier===t?'selected':''}>${t}</option>`).join('');
  let actions;
  if(S==='processing'){ actions=`<button class="btn ghost" onclick="del('${it.id}')">✕</button>`; }
  else if(S==='error'){ actions=`<button class="btn ghost" onclick="del('${it.id}')">✕ remove</button>`; }
  else {
    const review=`<a class="btn" href="/review/${it.id}" target="_blank">▶ Review</a>`;
    const pub = S==='review' ? `<button class="btn primary" onclick="publish('${it.id}')">Publish</button>`
              : S==='published' ? `<a class="btn" href="${it.base_url}/${it.id}_color_bundle.zip">⬇</a>` : '';
    actions=`${review}${pub}<button class="btn ghost" onclick="del('${it.id}')">✕</button>`;
  }
  const regen = S==='review'
    ? `<select class="tier" title="re-generate cleaner (busy/photo art)" onchange="regen('${it.id}',this.value)">
         <option value="">↻ Simplify…</option><option value="light">Light</option>
         <option value="medium">Medium</option><option value="strong">Strong</option><option value="off">Off</option>
       </select>` : '';
  const tierRow = (busy||S==='error') ? '' :
    `<div class="row"><select class="tier" onchange="setTier('${it.id}',this.value)">${tier}</select>${regen}</div>`;
  const counts = it.regions ? ` · ${it.regions}r/${it.colours}c` : '';
  return `<div class="card ${busy?'busy':''}">
    <div class="thumb" style="background-image:url('/api/items/${it.id}/thumb')">${busy?'<div class="spin"></div>':''}</div>
    <div class="body">
      <div class="t">${it.title}</div>
      <div class="meta">${BADGE[S]||S} · ${it.category||'—'}${counts}</div>
      ${tierRow}
      <div class="row">${actions}</div>
    </div></div>`;
}

async function regen(id,lvl){ if(lvl==='') return; await fetch('/api/items/'+id+'/regenerate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({simplify: lvl==='off'?'':lvl})}); load(); }
async function publish(id){ await fetch('/api/items/'+id+'/publish',{method:'POST'}); load(); }
async function setTier(id,t){ await fetch('/api/items/'+id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({tier:t})}); }
async function del(id){ if(confirm('Delete this image?')){ await fetch('/api/items/'+id,{method:'DELETE'}); load(); } }

async function loadCfg(){
  const cfg = await (await fetch('/api/config')).json();
  document.querySelectorAll('#cfg input').forEach(i=>{ if(cfg[i.dataset.k]!=null) i.value=cfg[i.dataset.k]; });
}
async function saveCfg(){
  const payload={}; document.querySelectorAll('#cfg input').forEach(i=>{ if(i.value!=='') payload[i.dataset.k]=i.value; });
  await fetch('/api/config',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  alert('config saved');
}

loadCats(); load(); loadCfg();
</script>
</body></html>
"""
