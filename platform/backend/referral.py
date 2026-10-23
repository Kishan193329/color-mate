"""Refer-a-friend for the Colour Mate app.

Every install gets a short code tied to its Android ID (which survives a reinstall, so one phone
cannot keep posing as a new one). A friend on a NEW device enters the code: the friend gets
WELCOME coins at once and the referrer gets INVITE coins, plus SHARE of every coin the friend later
earns by finishing pictures. Coins owed to a referrer wait in `pending` until their app claims them.
"""
import secrets
import time

import db

INVITE = 200          # referrer, per friend who joins
WELCOME = 100         # the friend, once
SHARE = 0.10          # of what the friend earns finishing pictures
NEW_DEVICE_DAYS = 3   # a code can only be redeemed by an install this young
MAX_EARN_REPORT = 50  # one report can never claim more than a picture is worth

_SCHEMA = """
CREATE TABLE IF NOT EXISTS referrals (
  device TEXT PRIMARY KEY, code TEXT UNIQUE NOT NULL, created REAL NOT NULL,
  referred_by TEXT, pending INTEGER NOT NULL DEFAULT 0, invited INTEGER NOT NULL DEFAULT 0
);
"""


def _ensure(c):
    c.executescript(_SCHEMA)


def _clean(device) -> str:
    d = str(device or "").strip()
    if not (6 <= len(d) <= 64) or not d.isalnum():
        raise ValueError("bad device id")
    return d


def register(device) -> dict:
    """The device's own code, created on first sight."""
    d = _clean(device)
    with db.conn() as c:
        _ensure(c)
        row = c.execute("SELECT code FROM referrals WHERE device=?", (d,)).fetchone()
        if row:
            return {"code": row["code"]}
        while True:
            code = secrets.token_hex(3).upper()          # 6 chars, 16.7M codes
            if not c.execute("SELECT 1 FROM referrals WHERE code=?", (code,)).fetchone():
                break
        c.execute("INSERT INTO referrals(device,code,created) VALUES(?,?,?)", (d, code, time.time()))
        return {"code": code}


def redeem(device, code) -> dict:
    d = _clean(device)
    code = str(code or "").strip().upper()
    register(d)
    with db.conn() as c:
        me = c.execute("SELECT * FROM referrals WHERE device=?", (d,)).fetchone()
        owner = c.execute("SELECT device FROM referrals WHERE code=?", (code,)).fetchone()
        if not owner:
            return {"ok": False, "reason": "That code does not exist"}
        if owner["device"] == d:
            return {"ok": False, "reason": "You cannot use your own code"}
        if me["referred_by"]:
            return {"ok": False, "reason": "A code was already used on this phone"}
        if time.time() - me["created"] > NEW_DEVICE_DAYS * 86400:
            return {"ok": False, "reason": "Codes work only on a new phone"}
        c.execute("UPDATE referrals SET referred_by=? WHERE device=?", (owner["device"], d))
        c.execute("UPDATE referrals SET pending=pending+?, invited=invited+1 WHERE device=?",
                  (INVITE, owner["device"]))
    return {"ok": True, "welcome": WELCOME}


def earned(device, amount) -> dict:
    """The friend finished a picture: their referrer is owed SHARE of it."""
    d = _clean(device)
    amount = max(0, min(int(amount or 0), MAX_EARN_REPORT))
    cut = round(amount * SHARE)
    if cut:
        with db.conn() as c:
            _ensure(c)
            row = c.execute("SELECT referred_by FROM referrals WHERE device=?", (d,)).fetchone()
            if row and row["referred_by"]:
                c.execute("UPDATE referrals SET pending=pending+? WHERE device=?",
                          (cut, row["referred_by"]))
    return {"ok": True}


def claim(device) -> dict:
    """Hand over whatever this device's friends have earned it, once."""
    d = _clean(device)
    with db.conn() as c:
        _ensure(c)
        row = c.execute("SELECT pending, invited FROM referrals WHERE device=?", (d,)).fetchone()
        if not row:
            return {"coins": 0, "invited": 0}
        c.execute("UPDATE referrals SET pending=0 WHERE device=?", (d,))
        return {"coins": row["pending"], "invited": row["invited"]}
