"""Our own "By Pixel" pictures, served in the exact shape the app's Pixel tab reads.

The app (Utils/ContentProvider) fetches, from one base URL:
    images2.json          {"images": [{"id", "release_date": "yyyy-MM-dd HH:mm:ss", "free"}]}
    images/{id}.png       the pixel art — one pixel per cell, transparent = empty (pixel_builder)

Served here at /pixel/, so pointing the app's content base URL at {host}/pixel/ is the whole
switch. Ids are prefixed "cm_" so they can never collide with a picture the app has already cached
from the old source.
"""
from __future__ import annotations

import re
import time
from datetime import datetime, timezone

import cv2
import numpy as np
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, RedirectResponse

import db
import storage
from services import pixel_builder

_SCHEMA = """
CREATE TABLE IF NOT EXISTS pixel_items (
  id TEXT PRIMARY KEY, title TEXT NOT NULL, release_date TEXT NOT NULL,
  free INTEGER NOT NULL DEFAULT 1, hidden INTEGER NOT NULL DEFAULT 0,
  w INTEGER, h INTEGER, colours INTEGER, created REAL NOT NULL, by_user TEXT
);
"""


def _ensure(c):
    c.executescript(_SCHEMA)


def _row(r) -> dict:
    return {"id": r["id"], "title": r["title"], "release_date": r["release_date"],
            "free": bool(r["free"]), "hidden": bool(r["hidden"]), "w": r["w"], "h": r["h"],
            "colours": r["colours"], "created": r["created"],
            "preview": f"/pixel/preview/{r['id']}.png", "image": f"/pixel/images/{r['id']}.png"}


def _new_id(c, title: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", (title or "pixel").lower()).strip("_")[:32] or "pixel"
    base = f"cm_{datetime.now(timezone.utc):%Y%m%d}_{slug}"
    cand, n = base, 2
    while c.execute("SELECT 1 FROM pixel_items WHERE id=?", (cand,)).fetchone():
        cand, n = f"{base}_{n}", n + 1
    return cand


def _release(value) -> str:
    """'YYYY-MM-DD' or a full timestamp → the app's 'yyyy-MM-dd HH:mm:ss'. A future date keeps
    the picture locked in the app until then."""
    s = str(value or "").strip()
    if not s:
        return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    try:
        return datetime.strptime(s[:10], "%Y-%m-%d").strftime("%Y-%m-%d 00:00:00")
    except ValueError:
        raise ValueError("bad release date")


def create(data: bytes, title: str, size: int, colours: int, remove_bg: bool, free: bool,
           release_date: str, user: str) -> dict:
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_UNCHANGED)
    if img is None:
        raise ValueError("not an image")
    out, info = pixel_builder.build(img, size, colours, remove_bg)
    ok1, png = cv2.imencode(".png", out)
    ok2, prev = cv2.imencode(".png", pixel_builder.preview(out, max(2, 384 // max(info["w"], info["h"]))))
    if not (ok1 and ok2):
        raise ValueError("could not encode")
    store = storage.get_store()
    with db.conn() as c:
        _ensure(c)
        iid = _new_id(c, title)
        store.put_bytes(f"pixel/images/{iid}.png", png.tobytes())
        store.put_bytes(f"pixel/preview/{iid}.png", prev.tobytes())
        c.execute("INSERT INTO pixel_items(id,title,release_date,free,hidden,w,h,colours,created,by_user)"
                  " VALUES(?,?,?,?,0,?,?,?,?,?)",
                  (iid, (title or "Pixel art").strip()[:80], _release(release_date), 1 if free else 0,
                   info["w"], info["h"], info["colours"], time.time(), user))
        return _row(c.execute("SELECT * FROM pixel_items WHERE id=?", (iid,)).fetchone())


def items() -> list[dict]:
    with db.conn() as c:
        _ensure(c)
        return [_row(r) for r in c.execute("SELECT * FROM pixel_items ORDER BY release_date DESC, created DESC")]


def update(iid: str, payload: dict) -> dict:
    sets, vals = [], []
    if "title" in payload:
        sets.append("title=?"); vals.append(str(payload["title"] or "").strip()[:80] or "Pixel art")
    if "free" in payload:
        sets.append("free=?"); vals.append(1 if payload["free"] else 0)
    if "hidden" in payload:
        sets.append("hidden=?"); vals.append(1 if payload["hidden"] else 0)
    if "release_date" in payload:
        sets.append("release_date=?"); vals.append(_release(payload["release_date"]))
    with db.conn() as c:
        _ensure(c)
        if sets:
            c.execute(f"UPDATE pixel_items SET {','.join(sets)} WHERE id=?", (*vals, iid))
        r = c.execute("SELECT * FROM pixel_items WHERE id=?", (iid,)).fetchone()
    if not r:
        raise LookupError("no such picture")
    return _row(r)


def delete(iid: str) -> None:
    with db.conn() as c:
        _ensure(c)
        if not c.execute("DELETE FROM pixel_items WHERE id=?", (iid,)).rowcount:
            raise LookupError("no such picture")


# ─── What the app reads ───

router = APIRouter()


@router.get("/pixel/images2.json")
def gallery():
    with db.conn() as c:
        _ensure(c)
        rows = c.execute("SELECT id, release_date, free FROM pixel_items WHERE hidden=0 "
                         "ORDER BY release_date DESC, created DESC").fetchall()
    return {"images": [{"id": r["id"], "release_date": r["release_date"], "free": bool(r["free"])}
                       for r in rows]}


def _serve(key: str):
    store = storage.get_store()
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "not found")
        return FileResponse(p, media_type="image/png")
    return RedirectResponse(store.url(key), status_code=302)


@router.get("/pixel/images/{name}")
def image(name: str):
    if "/" in name or ".." in name or not name.endswith(".png"):
        raise HTTPException(404, "not found")
    return _serve(f"pixel/images/{name}")


@router.get("/pixel/preview/{name}")
def preview(name: str):
    if "/" in name or ".." in name or not name.endswith(".png"):
        raise HTTPException(404, "not found")
    return _serve(f"pixel/preview/{name}")
