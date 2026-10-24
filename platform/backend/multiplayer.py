"""Multiplayer for the reveal game: Challenge, Race and Co-op.

The whole state of a picture is the set of regions that are filled (the app's progress string is
just their ids), so playing together costs a few bytes per tap.

* Challenge — asynchronous. A player who finished a picture shares a code; friends play the same
  picture alone and the server keeps a best-time board. Stored in the DB.
* Race / Co-op — live rooms over a WebSocket. Race: everyone plays their own copy, sees the others'
  percent, first to 100% wins. Co-op: everyone fills ONE shared picture. Rooms live in memory: the
  server runs a single uvicorn process, and a room is worth nothing after its players leave.

Nobody is matched with strangers — a room or challenge is reached only through its code — and there
is no chat, on purpose (moderation, and the app carries some 18+ pictures).
"""
from __future__ import annotations

import asyncio
import hashlib
import secrets
import time

import db

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   # no 0/O or 1/I — codes are read out loud
CODE_LEN = 6
MAX_PLAYERS = 4
ROOM_IDLE_S = 15 * 60          # an empty room is dropped after this
RACE_COUNTDOWN_MS = 3000
MIN_MS, MAX_MS = 5_000, 24 * 3600 * 1000
MAX_FILLED = 50_000            # far more regions than any picture has
BOARD_SIZE = 10
# Player colours, the app's own paints, so a friend's taps read as theirs.
COLOURS = ["#7A5CFF", "#FF5CA8", "#22B3A8", "#F5A623"]

_SCHEMA = """
CREATE TABLE IF NOT EXISTS challenges (
  code TEXT PRIMARY KEY, file_name TEXT NOT NULL, category TEXT NOT NULL, created REAL NOT NULL,
  by_device TEXT NOT NULL, by_name TEXT NOT NULL, by_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS challenge_results (
  code TEXT NOT NULL, device TEXT NOT NULL, name TEXT NOT NULL, ms INTEGER NOT NULL,
  created REAL NOT NULL, PRIMARY KEY (code, device)
);
"""


def _ensure(c):
    c.executescript(_SCHEMA)


def _device(device) -> str:
    d = str(device or "").strip()
    if not (6 <= len(d) <= 64) or not d.isalnum():
        raise ValueError("bad device id")
    return d


def _name(name) -> str:
    n = " ".join(str(name or "").split())[:16]
    return n or "Player"


def _ms(ms) -> int:
    try:
        v = int(ms)
    except (TypeError, ValueError):
        raise ValueError("bad time")
    if not MIN_MS <= v <= MAX_MS:
        raise ValueError("bad time")
    return v


def _picture(file_name, category) -> tuple[str, str]:
    """Only a published picture can be played together — the code would lead nowhere otherwise."""
    f = str(file_name or "").strip()
    if not f or not db.iid_for_file_name(f):
        raise ValueError("unknown picture")
    cat = str(category or "").strip().lower() or "other"
    return f, cat


def _new_code(taken) -> str:
    while True:
        code = "".join(secrets.choice(ALPHABET) for _ in range(CODE_LEN))
        if not taken(code):
            return code


def normalise_code(code) -> str:
    return "".join(ch for ch in str(code or "").upper() if ch in ALPHABET)[:CODE_LEN]


def _pid(device: str) -> str:
    """What other players see instead of the Android ID."""
    return hashlib.sha1(device.encode()).hexdigest()[:8]


# ─── Challenge ───

def _board(c, code) -> list[dict]:
    rows = c.execute("SELECT device, name, ms FROM challenge_results WHERE code=? "
                     "ORDER BY ms, created LIMIT ?", (code, BOARD_SIZE)).fetchall()
    return [{"id": _pid(r["device"]), "name": r["name"], "ms": r["ms"]} for r in rows]


def create_challenge(file_name, category, device, name, ms) -> dict:
    f, cat = _picture(file_name, category)
    d, n, t = _device(device), _name(name), _ms(ms)
    with db.conn() as c:
        _ensure(c)
        code = _new_code(lambda k: c.execute("SELECT 1 FROM challenges WHERE code=?", (k,)).fetchone()
                         or k in _rooms)
        now = time.time()
        c.execute("INSERT INTO challenges VALUES(?,?,?,?,?,?,?)", (code, f, cat, now, d, n, t))
        c.execute("INSERT INTO challenge_results VALUES(?,?,?,?,?)", (code, d, n, t, now))
    return {"code": code}


def challenge_info(code) -> dict | None:
    with db.conn() as c:
        _ensure(c)
        row = c.execute("SELECT * FROM challenges WHERE code=?", (code,)).fetchone()
        if not row:
            return None
        return {"kind": "challenge", "code": code, "fileName": row["file_name"],
                "category": row["category"], "by": row["by_name"], "ms": row["by_ms"],
                "board": _board(c, code)}


def challenge_result(code, device, name, ms) -> dict:
    d, n, t = _device(device), _name(name), _ms(ms)
    with db.conn() as c:
        _ensure(c)
        row = c.execute("SELECT by_ms FROM challenges WHERE code=?", (code,)).fetchone()
        if not row:
            raise LookupError("no such challenge")
        old = c.execute("SELECT ms FROM challenge_results WHERE code=? AND device=?",
                        (code, d)).fetchone()
        if old is None:
            c.execute("INSERT INTO challenge_results VALUES(?,?,?,?,?)", (code, d, n, t, time.time()))
        elif t < old["ms"]:   # keep each player's best
            c.execute("UPDATE challenge_results SET ms=?, name=?, created=? WHERE code=? AND device=?",
                      (t, n, time.time(), code, d))
        rank = 1 + c.execute("SELECT COUNT(*) FROM challenge_results WHERE code=? AND ms<?",
                             (code, t)).fetchone()[0]
        return {"rank": rank, "beat": t < row["by_ms"], "board": _board(c, code)}


# ─── Live rooms (Race, Co-op) ───

class Player:
    def __init__(self, device, name, colour):
        self.device = device
        self.id = _pid(device)
        self.name = name
        self.colour = colour
        self.ws = None
        self.pct = 0
        self.done_ms = None

    def public(self):
        return {"id": self.id, "name": self.name, "colour": self.colour, "online": self.ws is not None,
                "pct": self.pct, "doneMs": self.done_ms}


class Room:
    def __init__(self, code, mode, file_name, category, host):
        self.code = code
        self.mode = mode
        self.file_name = file_name
        self.category = category
        self.host = host
        self.players: dict[str, Player] = {}
        self.filled: set[int] = set()
        self.start_at = None           # race: server epoch ms when play begins
        self.finishers = 0
        self.empty_since = time.time()

    def info(self):
        return {"kind": self.mode, "code": self.code, "fileName": self.file_name,
                "category": self.category, "players": len(self.players), "max": MAX_PLAYERS,
                "started": self.start_at is not None}

    def state(self, you: Player):
        return {"t": "state", "mode": self.mode, "code": self.code, "fileName": self.file_name,
                "category": self.category, "host": _pid(self.host), "you": you.id,
                "players": [p.public() for p in self.players.values()],
                "filled": sorted(self.filled) if self.mode == "coop" else [],
                "startAt": self.start_at, "now": _now_ms()}


_rooms: dict[str, Room] = {}


def _now_ms() -> int:
    return int(time.time() * 1000)


def _sweep():
    now = time.time()
    for code in [k for k, r in _rooms.items()
                 if not any(p.ws for p in r.players.values()) and now - r.empty_since > ROOM_IDLE_S]:
        _rooms.pop(code, None)


def create_room(mode, file_name, category, device) -> dict:
    """Call from the event loop (an async route): rooms are only touched there."""
    if mode not in ("race", "coop"):
        raise ValueError("bad mode")
    f, cat = _picture(file_name, category)
    d = _device(device)
    _sweep()
    with db.conn() as c:
        _ensure(c)
        code = _new_code(lambda k: k in _rooms
                         or c.execute("SELECT 1 FROM challenges WHERE code=?", (k,)).fetchone())
    _rooms[code] = Room(code, mode, f, cat, d)
    return {"code": code}


def lookup(code) -> dict | None:
    code = normalise_code(code)
    room = _rooms.get(code)
    if room:
        return room.info()
    return challenge_info(code)


async def _send(p: Player, msg: dict):
    if p.ws is None:
        return
    try:
        await p.ws.send_json(msg)
    except Exception:
        p.ws = None


async def _broadcast(room: Room, msg: dict, skip: Player | None = None):
    await asyncio.gather(*(_send(p, msg) for p in list(room.players.values()) if p is not skip))


async def _players(room: Room):
    await _broadcast(room, {"t": "players", "players": [p.public() for p in room.players.values()]})


async def serve(ws, code, device, name):
    """One player's connection to a room. Close codes: 4404 no room, 4400 bad id, 4409 full."""
    code = normalise_code(code)
    room = _rooms.get(code)
    await ws.accept()
    if room is None:
        await ws.close(code=4404)
        return
    try:
        d = _device(device)
    except ValueError:
        await ws.close(code=4400)
        return
    player = room.players.get(d)
    if player is None:
        if len(room.players) >= MAX_PLAYERS:
            await ws.close(code=4409)
            return
        used = {p.colour for p in room.players.values()}
        colour = next((c for c in COLOURS if c not in used), COLOURS[0])
        player = Player(d, _name(name), colour)
        room.players[d] = player
    else:
        player.name = _name(name) if name else player.name
        if player.ws is not None:            # same phone reconnecting: drop the stale socket
            old, player.ws = player.ws, None
            try:
                await old.close(code=4000)
            except Exception:
                pass
    player.ws = ws

    await _send(player, room.state(player))
    await _players(room)
    try:
        while True:
            msg = await ws.receive_json()
            if not isinstance(msg, dict):
                continue
            await _handle(room, player, msg)
    except Exception:
        pass
    finally:
        if player.ws is ws:
            player.ws = None
        if not any(p.ws for p in room.players.values()):
            room.empty_since = time.time()
        await _players(room)


async def _handle(room: Room, player: Player, msg: dict):
    t = msg.get("t")
    if t == "fill" and room.mode == "coop":
        try:
            rid = int(msg.get("id"))
        except (TypeError, ValueError):
            return
        if rid <= 0 or rid in room.filled or len(room.filled) >= MAX_FILLED:
            return
        room.filled.add(rid)
        await _broadcast(room, {"t": "fill", "id": rid, "by": player.id}, skip=player)
    elif t == "progress":
        try:
            player.pct = max(0, min(100, int(msg.get("pct"))))
        except (TypeError, ValueError):
            return
        await _broadcast(room, {"t": "progress", "id": player.id, "pct": player.pct}, skip=player)
    elif t == "start" and room.mode == "race":
        if player.device != room.host or room.start_at is not None or len(room.players) < 2:
            return
        room.start_at = _now_ms() + RACE_COUNTDOWN_MS
        await _broadcast(room, {"t": "start", "at": room.start_at, "now": _now_ms()})
    elif t == "done":
        if player.done_ms is not None:
            return
        if room.mode == "race":
            if room.start_at is None:
                return
            # The server's clock decides, not the phone's: a race result cannot be typed in.
            player.done_ms = max(0, _now_ms() - room.start_at)
        else:
            player.done_ms = 0
        player.pct = 100
        room.finishers += 1
        await _broadcast(room, {"t": "done", "id": player.id, "name": player.name,
                                "ms": player.done_ms, "rank": room.finishers})
