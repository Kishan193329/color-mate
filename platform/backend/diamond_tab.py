"""Diamond feed — what the app's Diamond tab downloads.

Deliberately the SAME request shape as the Number tab (`number_tab.py`), so the app can reuse its
existing REST list plumbing with nothing but a different root:

    {base}index.php/Diamond/category/            -> {"categories": [...]}
    {base}index.php/Diamond/gimg/c/{category}    -> {"data": [...], "next": ""}
    {base}dthumb/{category}/t/{file}.png         -> grid thumbnail
    {base}index.php/Diamond/data/{cat}/{file}.json -> the bead grid the game plays

Two deliberate differences from `number_tab`:

* **No upstream merge.** The Number tab blends in the paid ionicsolutions catalogue; there is no
  third-party diamond source, so this always serves ColorForge content only.
* **The payload is JSON, not images.** `grid.json` is `diamond_builder.to_json()` —
  `{cols, rows, palette, grid}` — which is exactly what `DiamondLevel` parses on the app side.

`fileName` is the same globally-unique `YYYYMMDD_slug` the Number tab uses (the app keys saved
progress by it, so it must never be reused — see `number_tab.make_file_name`).
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from fastapi import HTTPException
from fastapi.responses import FileResponse, RedirectResponse, Response

import db

# storage key prefix; kept apart from the Number tab's "number/" tree so a colour and a diamond
# picture can share a category name without colliding.
_PREFIX = "diamond"


def _key(category: str, leaf: str) -> str:
    return f"{_PREFIX}/{category.strip().lower()}/{leaf}"


def export_and_store(folder: Path, category: str, file_name: str, store,
                     thumb_bytes: bytes | None = None) -> None:
    """Publish one diamond item: its bead grid, plus a thumbnail for the gallery."""
    grid = folder / "grid.json"
    if not grid.is_file():
        raise FileNotFoundError("grid.json missing — the diamond build did not finish")
    store.put_bytes(_key(category, f"{file_name}.json"), grid.read_bytes())
    if thumb_bytes:
        store.put_bytes(_key(category, f"t/{file_name}.png"), thumb_bytes)


def categories_payload() -> dict:
    return {"categories": db.number_categories(kind="diamond")}


def gimg_payload(category: str) -> dict:
    """One category's pictures, newest publish first. `next` is always "" — the whole category
    fits in one page (the app still reads the field, so it must be present)."""
    items = []
    today = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d")
    for it in db.number_items(category, kind="diamond"):
        ts = it.get("publish_ts") or it.get("published") or it.get("created") or 0
        day = datetime.fromtimestamp(float(ts), tz=timezone.utc).strftime("%Y-%m-%d")
        items.append({
            "key": it["file_name"],
            "fileName": it["file_name"],
            "category": it.get("category") or category,
            "timeStamp": day,          # a future date hides the item client-side (§3.4)
            "isNewToday": day == today,
            "beads": it.get("regions") or 0,
            "colours": it.get("colours") or 0,
        })
    return {"data": items, "next": ""}


def _serve(key: str, store, media_type: str):
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "not found")
        return FileResponse(p, media_type=media_type)
    return RedirectResponse(store.url(key), status_code=302)


def thumb_response(category: str, subpath: str, store):
    return _serve(_key(category, subpath), store, "image/png")


def grid_response(category: str, file_name: str, store):
    """The bead grid. Served inline (not redirected) on local storage so the app gets the JSON in
    one hop; S3 redirects like every other asset."""
    key = _key(category, f"{file_name}.json")
    if store.kind == "local":
        p = store.local_path(key)
        if not p:
            raise HTTPException(404, "no grid for this picture")
        return Response(content=Path(p).read_bytes(), media_type="application/json")
    return RedirectResponse(store.url(key), status_code=302)
