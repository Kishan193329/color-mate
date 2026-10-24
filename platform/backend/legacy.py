"""The old ionicsolutions.in "By Number" catalogue, served from our own server.

The owner is retiring that domain. Its whole catalogue (10,720 pictures, 9 categories, 5 music
files) is copied into our storage by tools/legacy_mirror.py, and served here under the SAME paths
the app has always used, mounted at /ColoringBook/ — so the app only changes its host:

  GET /ColoringBook/index.php/Number/category/          {"categories": [...]}
  GET /ColoringBook/index.php/Number/gimg/c/{cat}        {"data": [rows], "next": ""}
  GET /ColoringBook/thumb/{cat}/t/{file}.png | {file}.png | {file}.colored.png
  GET /ColoringBook/index.php/d2number/Number/audios     {"data": [mp3 urls]}
  GET /ColoringBook/audios/{name}.mp3

Rows are stored exactly as the old server sent them (raw JSON), so the app parses them with no
change. Our own ColorForge pictures are a separate feed at the server root (number_tab.py).
"""
from __future__ import annotations

import json

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse, RedirectResponse

import db
import storage

PREFIX = "legacy/"          # storage key prefix: legacy/thumb/..., legacy/audios/...

_SCHEMA = """
CREATE TABLE IF NOT EXISTS legacy_items (
  category TEXT NOT NULL, file_name TEXT NOT NULL, ord INTEGER NOT NULL,
  row TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (category, file_name)
);
CREATE INDEX IF NOT EXISTS legacy_items_cat ON legacy_items(category, ord);
"""


def ensure(c):
    c.executescript(_SCHEMA)


def _cat(category: str) -> str:
    return (category or "").strip().lower()


# ─── Writing (the mirror tool and the admin) ───

def save_catalogue(categories: list[str], feeds: dict[str, list[dict]], audios: list[str]) -> int:
    """Replaces the stored lists with `feeds` (server order kept). Hidden flags survive."""
    with db.conn() as c:
        ensure(c)
        hidden = {(r["category"], r["file_name"]) for r in
                  c.execute("SELECT category, file_name FROM legacy_items WHERE hidden=1")}
        c.execute("DELETE FROM legacy_items")
        n = 0
        for cat, rows in feeds.items():
            for i, row in enumerate(rows):
                key = (_cat(cat), str(row.get("fileName")))
                c.execute("INSERT OR REPLACE INTO legacy_items VALUES(?,?,?,?,?)",
                          (key[0], key[1], i, json.dumps(row, separators=(",", ":")),
                           1 if key in hidden else 0))
                n += 1
    db.set_config("legacy_categories", [_cat(c) for c in categories])
    db.set_config("legacy_audios", [a.rsplit("/", 1)[-1] for a in audios])
    return n


def set_hidden(category: str, file_name: str, hidden: bool) -> bool:
    with db.conn() as c:
        ensure(c)
        cur = c.execute("UPDATE legacy_items SET hidden=? WHERE category=? AND file_name=?",
                        (1 if hidden else 0, _cat(category), file_name))
        return cur.rowcount > 0


def stats() -> dict:
    with db.conn() as c:
        ensure(c)
        rows = c.execute("SELECT category, COUNT(*) n, SUM(hidden) h FROM legacy_items "
                         "GROUP BY category").fetchall()
    order = db.get_config().get("legacy_categories") or []
    counts = {r["category"]: {"total": r["n"], "hidden": r["h"] or 0} for r in rows}
    return {"categories": [{"name": c, **counts.get(c, {"total": 0, "hidden": 0})} for c in order]}


def items(category: str, query: str = "", offset: int = 0, limit: int = 60) -> list[dict]:
    """For the admin: one page of a category, with the picture's thumbnail key."""
    with db.conn() as c:
        ensure(c)
        rows = c.execute(
            "SELECT file_name, ord, hidden, row FROM legacy_items WHERE category=? AND file_name LIKE ? "
            "ORDER BY ord LIMIT ? OFFSET ?", (_cat(category), f"%{query}%", limit, offset)).fetchall()
    return [{"fileName": r["file_name"], "ord": r["ord"], "hidden": bool(r["hidden"]),
             "timeStamp": json.loads(r["row"]).get("timeStamp")} for r in rows]


# ─── Serving ───

router = APIRouter()


@router.get("/index.php/Number/category/")
def categories():
    return {"categories": db.get_config().get("legacy_categories") or []}


@router.get("/index.php/Number/gimg/c/{category}")
def feed(category: str):
    with db.conn() as c:
        ensure(c)
        rows = c.execute("SELECT row FROM legacy_items WHERE category=? AND hidden=0 ORDER BY ord",
                         (_cat(category),)).fetchall()
    # The old server paged with ?next=; the app also accepts the whole list at once.
    return {"data": [json.loads(r["row"]) for r in rows], "next": ""}


def _file(key: str, media_type: str):
    store = storage.get_store()
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "not found")
        return FileResponse(p, media_type=media_type)
    return RedirectResponse(store.url(key), status_code=302)


@router.get("/thumb/{category}/{subpath:path}")
def thumb(category: str, subpath: str):
    if ".." in subpath:
        raise HTTPException(400, "bad path")
    return _file(f"{PREFIX}thumb/{_cat(category)}/{subpath}", "image/png")


@router.get("/index.php/d2number/Number/audios")
def audios(request: Request):
    base = str(request.base_url).rstrip("/") + "/ColoringBook/audios/"
    if request.headers.get("x-forwarded-proto") == "https":   # behind nginx TLS
        base = base.replace("http://", "https://", 1)
    return {"data": [base + n for n in db.get_config().get("legacy_audios") or []]}


@router.get("/audios/{name}")
def audio(name: str):
    if "/" in name or ".." in name:
        raise HTTPException(400, "bad name")
    return _file(f"{PREFIX}audios/{name}", "audio/mpeg")
