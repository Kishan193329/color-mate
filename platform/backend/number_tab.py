"""Live Colour Mate "Number" tab API — ionicsolutions-shaped routes.

Serves ColorForge-published colour items as:
  GET /index.php/Number/category/
  GET /index.php/Number/gimg/c/{category}
  GET /thumb/{category}/{file}.png|.colored.png|t/{file}.png

Optional upstream merge (config `number_merge`) appends ionicsolutions and 302-redirects
image keys we don't own. It is **OFF by default**: New 2026 is ColorForge content only
(the §8.1 pivot). It used to default ON, which meant a fresh database or a rebuilt server
silently blended the bought ionicsolutions catalogue back into the tab — that happened once
and took a while to spot, because nothing about it looks like a failure.
"""
from __future__ import annotations

import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen

from fastapi import HTTPException
from fastapi.responses import FileResponse, RedirectResponse, Response

import db

# Default upstream — the paid catalogue the owner already buys.
_DEFAULT_UPSTREAM = "http://ionicsolutions.in/ColoringBook/"


def _cfg() -> dict:
    cfg = db.get_config()
    return {
        "number_merge": bool(cfg.get("number_merge", False)),
        "number_upstream": (cfg.get("number_upstream") or _DEFAULT_UPSTREAM).rstrip("/") + "/",
    }


def parse_publish_ts(value) -> float | None:
    """Accept 'YYYY-MM-DD', epoch seconds/ms, or None → epoch seconds or None."""
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        v = float(value)
        return v / 1000.0 if v > 1e12 else v
    s = str(value).strip()
    if not s:
        return None
    if re.fullmatch(r"\d+(\.\d+)?", s):
        v = float(s)
        return v / 1000.0 if v > 1e12 else v
    try:
        dt = datetime.strptime(s[:10], "%Y-%m-%d").replace(tzinfo=timezone.utc)
        return dt.timestamp()
    except ValueError as e:
        raise HTTPException(400, f"bad publish_date: {value!r}") from e


def make_file_name(title: str, ts: float) -> str:
    """Date-prefixed unique slug — avoids SavedSteps collision across categories."""
    day = datetime.fromtimestamp(ts or time.time(), tz=timezone.utc).strftime("%Y%m%d")
    slug = re.sub(r"[^a-z0-9]+", "_", (title or "image").lower()).strip("_")[:48] or "image"
    base = f"{day}_{slug}"
    if not db.file_name_exists(base):
        return base
    for i in range(2, 1000):
        candidate = f"{base}_{i}"
        if not db.file_name_exists(candidate):
            return candidate
    return f"{base}_{int(time.time())}"


def export_and_store(folder: Path, category: str, file_name: str, store) -> None:
    """Convert a job folder → Number-tab PNGs and put them in storage."""
    from services import number_tab_builder as N

    out = folder / "_number_export"
    out.mkdir(exist_ok=True)
    N.export_from_job(str(folder), str(out), file_name)
    cat = category.strip().lower()
    mapping = {
        f"number/{cat}/{file_name}.png": out / f"{file_name}.png",
        f"number/{cat}/{file_name}.colored.png": out / f"{file_name}.colored.png",
        f"number/{cat}/t/{file_name}.png": out / "t" / f"{file_name}.png",
    }
    for key, path in mapping.items():
        if path.is_file():
            store.put_bytes(key, path.read_bytes())


def categories_payload() -> dict:
    ours = db.number_categories()
    # Normalise casing for display: keep DB names; app matches case-insensitively.
    names = list(ours)
    cfg = _cfg()
    if cfg["number_merge"]:
        up = _upstream_categories(cfg["number_upstream"])
        seen = {n.lower() for n in names}
        for n in up:
            if n.lower() not in seen:
                names.append(n)
                seen.add(n.lower())
    return {"categories": names}


def gimg_payload(category: str, next_key: str | None = None) -> dict:
    cfg = _cfg()
    ours = []
    # Only our page-0 (no cursor) prepends ColorForge items — upstream owns its cursor.
    if not next_key:
        ours = [_row(it, category) for it in db.number_items(category)]
    if not cfg["number_merge"]:
        return {"data": ours, "next": ""}
    up_data, up_next = _upstream_gimg(cfg["number_upstream"], category, next_key)
    # Dedup by fileName — our items win.
    seen = {d["fileName"] for d in ours}
    merged = ours + [d for d in up_data if d.get("fileName") not in seen]
    return {"data": merged, "next": up_next or ""}


def _row(it: dict, category: str = "") -> dict:
    ts = it.get("publish_ts") or it.get("published") or it.get("created") or 0
    day = datetime.fromtimestamp(float(ts), tz=timezone.utc).strftime("%Y-%m-%d")
    return {"key": it["file_name"], "fileName": it["file_name"],
            "category": it.get("category") or category, "title": it.get("title") or "",
            "timeStamp": day,
            # free | reward | premium — the app unlocks premium with coins, reward with an ad
            "tier": (it.get("tier") or "free").lower(),
            "isNewToday": day == datetime.now(tz=timezone.utc).strftime("%Y-%m-%d")}


def daily_payload() -> dict:
    """Every published colour picture of ours, newest first, across all categories — the Daily
    tab groups them by `timeStamp`. Future-dated items are left in; the app hides them until
    their day, exactly as the category feed does."""
    return {"data": [_row(it) for it in db.number_items(None)], "next": ""}


def hit_payload() -> dict:
    """The one featured "Hit" picture and when it unlocks (epoch seconds), or {} when none is set.
    `now` is the server clock so the app's countdown cannot be cheated by changing the phone time."""
    cfg = db.get_config().get("hit") or {}
    iid = cfg.get("iid")
    if not iid:
        return {}
    it = db.get_image(iid)
    if not it or it.get("status") != "published" or not it.get("file_name"):
        return {}
    import time as _t
    row = _row(it)
    row.update({"unlockAt": int(cfg.get("unlock_ts") or 0), "now": int(_t.time())})
    return row


def image_response(category: str, subpath: str, store):
    """Serve line / colored / thumb, or 302 to upstream if merge is on and we don't own it."""
    cat = category.strip()
    key = f"number/{cat.lower()}/{subpath}"
    # Also try the category casing as stored.
    candidates = [key, f"number/{cat}/{subpath}"]
    if store.kind == "local":
        for k in candidates:
            p = store.local_path(k)
            if p:
                return FileResponse(p, media_type="image/png")
    else:
        for k in candidates:
            # S3: redirect to the object URL (caller / CDN handles 404).
            return RedirectResponse(store.url(k), status_code=302)

    cfg = _cfg()
    if cfg["number_merge"]:
        url = f"{cfg['number_upstream']}thumb/{quote(cat)}/{subpath}"
        return RedirectResponse(url, status_code=302)
    raise HTTPException(404, "image not found")


# ── upstream helpers (best-effort — never fail the request) ───────────────────
def _upstream_get_json(url: str, timeout: float = 6.0) -> dict | list | None:
    try:
        req = Request(url, headers={"User-Agent": "ColorForge-NumberTab/1.0"})
        with urlopen(req, timeout=timeout) as r:  # noqa: S310 — configured upstream only
            import json
            return json.loads(r.read().decode("utf-8", errors="replace"))
    except Exception:  # noqa: BLE001
        return None


def _upstream_categories(base: str) -> list[str]:
    data = _upstream_get_json(f"{base}index.php/Number/category/")
    if isinstance(data, dict):
        cats = data.get("categories") or []
        return [str(c) for c in cats]
    return []


def _upstream_gimg(base: str, category: str, next_key: str | None) -> tuple[list[dict], str]:
    url = f"{base}index.php/Number/gimg/c/{quote(category)}"
    if next_key:
        url += f"?next={quote(next_key)}"
    data = _upstream_get_json(url)
    if not isinstance(data, dict):
        return [], ""
    items = data.get("data") or []
    out = []
    for it in items:
        if not isinstance(it, dict):
            continue
        fn = it.get("fileName") or it.get("file_name") or ""
        if not fn:
            continue
        out.append({
            "key": it.get("key") or fn,
            "fileName": fn,
            "category": it.get("category") or category,
            "timeStamp": it.get("timeStamp") or it.get("timestamp") or "",
            "isNewToday": bool(it.get("isNewToday")),
        })
    return out, str(data.get("next") or "")
