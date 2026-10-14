"""Content + accounts database — SQLite (stdlib, one file, queryable).

Tables:
  users       — boss + employees (role, password hash, active, 2FA flag)
  sessions    — refresh-token sessions (device / IP) so logins can be listed and revoked
  categories  — dynamic; created on the fly
  images      — one row per uploaded image + its workflow (uploader, status, reason, tier)
  comments    — per-image discussion (employee ↔ boss)
  audit       — every action (user, action, target, IP, time) — nothing silent
  config      — global app settings

Workflow status:  processing → review → submitted → published   (boss approves → live)
                                              ↘ rejected (+reason, auto-deleted after 3 days)
All timestamps are epoch seconds, passed in by the caller.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

import auth

DB_PATH = Path(__file__).resolve().parent / "data" / "content.db"

_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  name     TEXT NOT NULL,
  email    TEXT UNIQUE NOT NULL,
  role     TEXT NOT NULL DEFAULT 'employee',   -- boss | reviewer | employee
  pw_hash  TEXT NOT NULL,
  active   INTEGER NOT NULL DEFAULT 1,
  twofa    INTEGER NOT NULL DEFAULT 0,
  created  REAL NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sessions (
  jti       TEXT PRIMARY KEY,
  user_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device    TEXT NOT NULL DEFAULT '',
  ip        TEXT NOT NULL DEFAULT '',
  created   REAL NOT NULL DEFAULT 0,
  last_seen REAL NOT NULL DEFAULT 0,
  revoked   INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL,
  icon TEXT NOT NULL DEFAULT '', ord INTEGER NOT NULL DEFAULT 0, created REAL NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS images (
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL DEFAULT '',
  type        TEXT NOT NULL DEFAULT 'colour',   -- colour (colour-fill) | diamond (bead grid)
  category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
  uploader_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
  reviewer_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
  tier        TEXT NOT NULL DEFAULT 'free',
  status      TEXT NOT NULL DEFAULT 'processing',
  rejection   TEXT NOT NULL DEFAULT '',
  regions     INTEGER, colours INTEGER,
  featured    INTEGER NOT NULL DEFAULT 0,
  base_url    TEXT NOT NULL DEFAULT '',
  created     REAL NOT NULL DEFAULT 0,
  submitted   REAL,
  published   REAL
);
CREATE TABLE IF NOT EXISTS comments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  image_id TEXT NOT NULL REFERENCES images(id) ON DELETE CASCADE,
  user_id  INTEGER REFERENCES users(id) ON DELETE SET NULL,
  text     TEXT NOT NULL, created REAL NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
  action TEXT NOT NULL, target TEXT NOT NULL DEFAULT '',
  ip TEXT NOT NULL DEFAULT '', created REAL NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS config ( k TEXT PRIMARY KEY, v TEXT NOT NULL );
"""

# columns added to `images` after the first release — ALTER-added if an old DB predates them
_IMG_COLS = {"uploader_id": "INTEGER", "reviewer_id": "INTEGER",
             "rejection": "TEXT NOT NULL DEFAULT ''", "submitted": "REAL",
             "type": "TEXT NOT NULL DEFAULT 'colour'",
             # Number-tab (live Colour Mate app): the globally-unique app fileName and the
             # publish date (epoch) that becomes the app's `timeStamp` for date-wise hiding.
             "file_name": "TEXT NOT NULL DEFAULT ''", "publish_ts": "REAL",
             # why the last generation failed, shown verbatim on the admin's error card.
             # Empty whenever the item is not in 'error'.
             "error": "TEXT NOT NULL DEFAULT ''",
             # what the uploaded picture measured as — 'flat' | 'textured' | 'mixed'. Drives the
             # admin's Detail advice; see reveal_game_builder.source_kind().
             "source_kind": "TEXT NOT NULL DEFAULT ''"}


def conn() -> sqlite3.Connection:
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA foreign_keys = ON")
    return c


def init(boss=("Kishan", "kishankalathiya1111@gmail.com", "colorforge123")) -> None:
    """Create tables, migrate old DBs, and seed the boss account if there are no users."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with conn() as c:
        c.executescript(_SCHEMA)
        have = {r["name"] for r in c.execute("PRAGMA table_info(images)")}
        for col, decl in _IMG_COLS.items():
            if col not in have:
                c.execute(f"ALTER TABLE images ADD COLUMN {col} {decl}")
        if c.execute("SELECT COUNT(*) n FROM users").fetchone()["n"] == 0:
            import time
            c.execute("INSERT INTO users(name,email,role,pw_hash,active,twofa,created) VALUES(?,?,?,?,1,0,?)",
                      (boss[0], boss[1].lower(), "boss", auth.hash_password(boss[2]), time.time()))


# ── users ─────────────────────────────────────────────────────────────────────
def get_user(uid: int) -> dict | None:
    with conn() as c:
        r = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    return dict(r) if r else None


def get_user_by_email(email: str) -> dict | None:
    with conn() as c:
        r = c.execute("SELECT * FROM users WHERE email=?", (email.lower().strip(),)).fetchone()
    return dict(r) if r else None


def users() -> list[dict]:
    with conn() as c:
        return [dict(r) for r in c.execute("SELECT * FROM users ORDER BY role='boss' DESC, created")]


def create_user(name: str, email: str, role: str, pw_hash: str, now: float) -> int:
    with conn() as c:
        cur = c.execute("INSERT INTO users(name,email,role,pw_hash,created) VALUES(?,?,?,?,?)",
                        (name.strip(), email.lower().strip(), role, pw_hash, now))
        return int(cur.lastrowid)


def update_user(uid: int, **f) -> None:
    allowed = {"name", "role", "active", "pw_hash", "twofa"}
    s = {k: v for k, v in f.items() if k in allowed}
    if not s:
        return
    with conn() as c:
        c.execute(f"UPDATE users SET {', '.join(k+'=?' for k in s)} WHERE id=?", (*s.values(), uid))


def delete_user(uid: int) -> None:
    with conn() as c:
        c.execute("DELETE FROM users WHERE id=?", (uid,))


def employee_stats() -> list[dict]:
    """Per-user counts: uploaded / published (approved) / rejected / pending-review."""
    q = """SELECT u.id, u.name, u.role, u.active,
              COUNT(i.id) uploaded,
              SUM(i.status='published') approved,
              SUM(i.status='rejected') rejected,
              SUM(i.status IN ('submitted','review')) pending
           FROM users u LEFT JOIN images i ON i.uploader_id=u.id
           GROUP BY u.id ORDER BY u.role='boss' DESC, u.created"""
    with conn() as c:
        return [dict(r) for r in c.execute(q)]


# ── sessions ──────────────────────────────────────────────────────────────────
def add_session(jti: str, user_id: int, device: str, ip: str, now: float) -> None:
    with conn() as c:
        c.execute("INSERT INTO sessions(jti,user_id,device,ip,created,last_seen) VALUES(?,?,?,?,?,?)",
                  (jti, user_id, device, ip, now, now))


def get_session(jti: str) -> dict | None:
    with conn() as c:
        r = c.execute("SELECT * FROM sessions WHERE jti=?", (jti,)).fetchone()
    return dict(r) if r else None


def revoke_session(jti: str) -> None:
    with conn() as c:
        c.execute("UPDATE sessions SET revoked=1 WHERE jti=?", (jti,))


def sessions() -> list[dict]:
    q = ("SELECT s.*, u.name uname FROM sessions s JOIN users u ON u.id=s.user_id "
         "WHERE s.revoked=0 ORDER BY s.last_seen DESC")
    with conn() as c:
        return [dict(r) for r in c.execute(q)]


# ── categories ────────────────────────────────────────────────────────────────
def categories() -> list[dict]:
    with conn() as c:
        return [dict(r) for r in c.execute("SELECT * FROM categories ORDER BY ord, name")]


def get_or_create_category(name: str, now: float, icon: str = "") -> int:
    name = name.strip()
    if not name:
        return 0
    with conn() as c:
        row = c.execute("SELECT id FROM categories WHERE name=?", (name,)).fetchone()
        if row:
            return int(row["id"])
        return int(c.execute("INSERT INTO categories(name,icon,created) VALUES(?,?,?)",
                             (name, icon, now)).lastrowid)


def rename_category(cid: int, name: str) -> None:
    with conn() as c:
        c.execute("UPDATE categories SET name=? WHERE id=?", (name.strip(), cid))


def update_category(cid: int, **fields) -> None:
    """Set name / icon / ord. `categories()` already sorts by (ord, name), so writing `ord`
    is what finally lets the owner choose the order the app shows categories in."""
    allowed = {"name", "icon", "ord"}
    sets, vals = [], []
    for k, v in fields.items():
        if k not in allowed:
            continue
        sets.append(f"{k}=?")
        vals.append(v.strip() if isinstance(v, str) else v)
    if not sets:
        return
    vals.append(cid)
    with conn() as c:
        c.execute(f"UPDATE categories SET {','.join(sets)} WHERE id=?", vals)


def delete_category(cid: int) -> None:
    with conn() as c:
        c.execute("DELETE FROM categories WHERE id=?", (cid,))


# ── images ────────────────────────────────────────────────────────────────────
def add_image(iid: str, title: str, category_id: int | None, uploader_id: int | None,
              now: float, status: str = "processing", kind: str = "colour") -> None:
    with conn() as c:
        c.execute("INSERT INTO images(id,title,type,category_id,uploader_id,created,status,tier) "
                  "VALUES(?,?,?,?,?,?,?, 'free')",
                  (iid, title, kind, category_id, uploader_id, now, status))


def update_image(iid: str, **fields) -> None:
    allowed = {"title", "category_id", "tier", "status", "featured", "base_url", "published",
               "regions", "colours", "reviewer_id", "rejection", "submitted",
               "file_name", "publish_ts", "error", "source_kind"}
    s = {k: v for k, v in fields.items() if k in allowed}
    if not s:
        return
    with conn() as c:
        c.execute(f"UPDATE images SET {', '.join(k+'=?' for k in s)} WHERE id=?", (*s.values(), iid))


def get_image(iid: str) -> dict | None:
    q = ("SELECT i.*, c.name AS category, u.name AS uploader_name "
         "FROM images i LEFT JOIN categories c ON c.id=i.category_id "
         "LEFT JOIN users u ON u.id=i.uploader_id WHERE i.id=?")
    with conn() as c:
        r = c.execute(q, (iid,)).fetchone()
    return dict(r) if r else None


def delete_image(iid: str) -> None:
    with conn() as c:
        c.execute("DELETE FROM images WHERE id=?", (iid,))


def images(status: str | None = None, uploader_id: int | None = None,
           kind: str | None = None) -> list[dict]:
    q = ("SELECT i.*, c.name AS category, u.name AS uploader_name "
         "FROM images i LEFT JOIN categories c ON c.id=i.category_id "
         "LEFT JOIN users u ON u.id=i.uploader_id")
    where, args = [], []
    if status:
        where.append("i.status=?")
        args.append(status)
    if uploader_id is not None:
        where.append("i.uploader_id=?")
        args.append(uploader_id)
    if kind:
        where.append("i.type=?")
        args.append(kind)
    if where:
        q += " WHERE " + " AND ".join(where)
    q += " ORDER BY i.created DESC"
    with conn() as c:
        return [dict(r) for r in c.execute(q, args)]


def file_name_exists(fn: str) -> bool:
    """True if any image already claims this app fileName (keep them globally unique — the app
    stores progress keyed by fileName, so a reused name overwrites another level's save)."""
    if not fn:
        return False
    with conn() as c:
        return c.execute("SELECT 1 FROM images WHERE file_name=?", (fn,)).fetchone() is not None


def iid_for_file_name(fn: str) -> str | None:
    """Internal image id for a published app fileName, or None. Lets the Number-tab API serve the
    play page by the fileName the app already has (see /index.php/Number/play/...)."""
    if not fn:
        return None
    with conn() as c:
        row = c.execute("SELECT id FROM images WHERE file_name=? AND status='published'",
                        (fn,)).fetchone()
        return row[0] if row else None


def number_categories(kind: str = "colour") -> list[str]:
    """Category names with at least one published item of `kind` carrying an app fileName.
    `kind` is 'colour' for the Number/New-2026 feed and 'diamond' for the Diamond feed."""
    q = ("SELECT DISTINCT c.name FROM images i JOIN categories c ON c.id=i.category_id "
         "WHERE i.status='published' AND i.type=? AND i.file_name<>'' ORDER BY c.name")
    with conn() as c:
        return [r["name"] for r in c.execute(q, (kind,))]


def number_items(category: str | None = None, kind: str = "colour") -> list[dict]:
    """Published items of `kind` exposed to the app, newest publish first (so a fresh image lands
    at the top of its category — the app renders server order verbatim)."""
    q = ("SELECT i.*, c.name AS category FROM images i "
         "LEFT JOIN categories c ON c.id=i.category_id "
         "WHERE i.status='published' AND i.type=? AND i.file_name<>''")
    args: list = [kind]
    if category:
        q += " AND LOWER(c.name)=LOWER(?)"   # match the app's category case-insensitively
        args.append(category)
    q += " ORDER BY COALESCE(i.publish_ts, i.published, i.created) DESC"
    with conn() as c:
        return [dict(r) for r in c.execute(q, args)]


def purge_rejected(older_than: float) -> list[str]:
    """Delete rejected images whose rejection is older than `older_than` (epoch). Returns their ids."""
    with conn() as c:
        rows = c.execute("SELECT id FROM images WHERE status='rejected' AND submitted IS NOT NULL "
                         "AND submitted < ?", (older_than,)).fetchall()
        ids = [r["id"] for r in rows]
        for i in ids:
            c.execute("DELETE FROM images WHERE id=?", (i,))
    return ids


# ── comments ──────────────────────────────────────────────────────────────────
def add_comment(image_id: str, user_id: int, text: str, now: float) -> None:
    with conn() as c:
        c.execute("INSERT INTO comments(image_id,user_id,text,created) VALUES(?,?,?,?)",
                  (image_id, user_id, text, now))


def comments_for(image_id: str) -> list[dict]:
    q = ("SELECT m.*, u.name uname, u.role urole FROM comments m "
         "LEFT JOIN users u ON u.id=m.user_id WHERE m.image_id=? ORDER BY m.created")
    with conn() as c:
        return [dict(r) for r in c.execute(q, (image_id,))]


# ── audit ─────────────────────────────────────────────────────────────────────
def add_audit(user_id: int | None, action: str, target: str, ip: str, now: float) -> None:
    with conn() as c:
        c.execute("INSERT INTO audit(user_id,action,target,ip,created) VALUES(?,?,?,?,?)",
                  (user_id, action, target, ip, now))


def audit(limit: int = 200) -> list[dict]:
    q = ("SELECT a.*, u.name uname FROM audit a LEFT JOIN users u ON u.id=a.user_id "
         "ORDER BY a.created DESC LIMIT ?")
    with conn() as c:
        return [dict(r) for r in c.execute(q, (limit,))]


# ── config ────────────────────────────────────────────────────────────────────
def get_config() -> dict:
    with conn() as c:
        return {r["k"]: json.loads(r["v"]) for r in c.execute("SELECT k,v FROM config")}


def set_config(key: str, value) -> None:
    with conn() as c:
        c.execute("INSERT INTO config(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v",
                  (key, json.dumps(value)))
