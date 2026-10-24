"""Our own "Colour Book" pictures — line-art pages for free colouring (pick any colour, tap a shape).

The app's current By Images tab plays a third-party book format (a native editor with its own index
files), which we cannot produce. So this is our own, simpler shelf: each page is a line-art PNG plus
a thumbnail, grouped by category, read from one feed:

    GET /book/feed.json     {"categories": [...], "items": [{id, title, category, art, thumb, created}]}
    GET /book/art/{id}.png  /book/thumb/{id}.png

The app side (a free-colour mode for these pages) is a separate step.
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

MAX_SIDE = 2048
THUMB = 420

_SCHEMA = """
CREATE TABLE IF NOT EXISTS book_items (
  id TEXT PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL,
  hidden INTEGER NOT NULL DEFAULT 0, w INTEGER, h INTEGER, created REAL NOT NULL, by_user TEXT
);
"""


def _ensure(c):
    c.executescript(_SCHEMA)


def _row(r) -> dict:
    return {"id": r["id"], "title": r["title"], "category": r["category"], "hidden": bool(r["hidden"]),
            "w": r["w"], "h": r["h"], "created": r["created"],
            "art": f"/book/art/{r['id']}.png", "thumb": f"/book/thumb/{r['id']}.png"}


def _flatten(img: np.ndarray) -> np.ndarray:
    """Any upload → opaque BGR on white (a transparent line-art PNG must not turn black)."""
    if img.ndim == 2:
        return cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    if img.shape[2] == 4:
        a = img[:, :, 3:4].astype(np.float32) / 255.0
        return (img[:, :, :3].astype(np.float32) * a + 255.0 * (1 - a)).astype(np.uint8)
    return img


def create(data: bytes, title: str, category: str, user: str) -> dict:
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_UNCHANGED)
    if img is None:
        raise ValueError("not an image")
    art = _flatten(img)
    h, w = art.shape[:2]
    if max(h, w) > MAX_SIDE:
        s = MAX_SIDE / max(h, w)
        art = cv2.resize(art, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
        h, w = art.shape[:2]
    s = THUMB / max(h, w)
    thumb = cv2.resize(art, (max(1, round(w * s)), max(1, round(h * s))), interpolation=cv2.INTER_AREA)
    ok1, art_png = cv2.imencode(".png", art)
    ok2, thumb_png = cv2.imencode(".png", thumb)
    if not (ok1 and ok2):
        raise ValueError("could not encode")
    cat = re.sub(r"\s+", " ", (category or "other").strip().lower())[:40] or "other"
    slug = re.sub(r"[^a-z0-9]+", "_", (title or "page").lower()).strip("_")[:32] or "page"
    store = storage.get_store()
    with db.conn() as c:
        _ensure(c)
        base = f"bk_{datetime.now(timezone.utc):%Y%m%d}_{slug}"
        iid, n = base, 2
        while c.execute("SELECT 1 FROM book_items WHERE id=?", (iid,)).fetchone():
            iid, n = f"{base}_{n}", n + 1
        store.put_bytes(f"book/art/{iid}.png", art_png.tobytes())
        store.put_bytes(f"book/thumb/{iid}.png", thumb_png.tobytes())
        c.execute("INSERT INTO book_items(id,title,category,hidden,w,h,created,by_user) VALUES(?,?,?,0,?,?,?,?)",
                  (iid, (title or "Colouring page").strip()[:80], cat, w, h, time.time(), user))
        return _row(c.execute("SELECT * FROM book_items WHERE id=?", (iid,)).fetchone())


def items() -> list[dict]:
    with db.conn() as c:
        _ensure(c)
        return [_row(r) for r in c.execute("SELECT * FROM book_items ORDER BY created DESC")]


def update(iid: str, payload: dict) -> dict:
    sets, vals = [], []
    if "title" in payload:
        sets.append("title=?"); vals.append(str(payload["title"] or "").strip()[:80] or "Colouring page")
    if "category" in payload:
        sets.append("category=?"); vals.append(str(payload["category"] or "other").strip().lower()[:40] or "other")
    if "hidden" in payload:
        sets.append("hidden=?"); vals.append(1 if payload["hidden"] else 0)
    with db.conn() as c:
        _ensure(c)
        if sets:
            c.execute(f"UPDATE book_items SET {','.join(sets)} WHERE id=?", (*vals, iid))
        r = c.execute("SELECT * FROM book_items WHERE id=?", (iid,)).fetchone()
    if not r:
        raise LookupError("no such page")
    return _row(r)


def delete(iid: str) -> None:
    with db.conn() as c:
        _ensure(c)
        if not c.execute("DELETE FROM book_items WHERE id=?", (iid,)).rowcount:
            raise LookupError("no such page")


router = APIRouter()


@router.get("/book/feed.json")
def feed():
    with db.conn() as c:
        _ensure(c)
        rows = c.execute("SELECT * FROM book_items WHERE hidden=0 ORDER BY created DESC").fetchall()
    out = [_row(r) for r in rows]
    for o in out:
        o.pop("hidden", None)
    cats = list(dict.fromkeys(o["category"] for o in out))
    return {"categories": cats, "items": out}


def _serve(key: str):
    store = storage.get_store()
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "not found")
        return FileResponse(p, media_type="image/png")
    return RedirectResponse(store.url(key), status_code=302)


@router.get("/book/{kind}/{name}")
def file(kind: str, name: str):
    if kind not in ("art", "thumb") or "/" in name or ".." in name or not name.endswith(".png"):
        raise HTTPException(404, "not found")
    return _serve(f"book/{kind}/{name}")
