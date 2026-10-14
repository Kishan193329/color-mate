"""Authentication — password hashing (stdlib pbkdf2) + JWT access/refresh tokens.

No external crypto deps for hashing (pbkdf2_hmac is in the stdlib). JWT uses PyJWT.
The signing secret is generated once and persisted in data/.jwt_secret so tokens survive
restarts; keep that file private (it's under the git-ignored data/ dir).

Tokens:
  access  — 15 min, carries {sub: user_id, role, name, typ: 'access'}
  refresh — 30 days, carries {sub, jti, typ: 'refresh'} and is tracked in the sessions table
            so it can be revoked (logout / "revoke device").
"""
from __future__ import annotations

import hashlib
import hmac
import secrets
import time
from pathlib import Path

import jwt

BASE = Path(__file__).resolve().parent
SECRET_FILE = BASE / "data" / ".jwt_secret"
ALGO = "HS256"
ACCESS_TTL = 15 * 60            # 15 minutes
REFRESH_TTL = 30 * 24 * 3600   # 30 days
_PBKDF2_ROUNDS = 200_000


def _secret() -> str:
    SECRET_FILE.parent.mkdir(parents=True, exist_ok=True)
    if SECRET_FILE.exists():
        return SECRET_FILE.read_text(encoding="utf-8").strip()
    s = secrets.token_hex(32)
    SECRET_FILE.write_text(s, encoding="utf-8")
    return s


SECRET = _secret()


# ── password hashing (pbkdf2-sha256, constant-time verify) ────────────────────
def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _PBKDF2_ROUNDS)
    return f"pbkdf2_sha256${_PBKDF2_ROUNDS}${salt.hex()}${dk.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        _, rounds, salt_hex, hash_hex = stored.split("$")
        dk = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), int(rounds))
        return hmac.compare_digest(dk.hex(), hash_hex)
    except Exception:  # noqa: BLE001 - malformed stored hash never authenticates
        return False


# ── tokens ────────────────────────────────────────────────────────────────────
def make_access(user: dict, now: float) -> str:
    # `sub` must be a string per RFC 7519 (PyJWT 2.10+ enforces this on decode).
    payload = {"sub": str(user["id"]), "role": user["role"], "name": user["name"],
               "typ": "access", "iat": int(now), "exp": int(now) + ACCESS_TTL}
    return jwt.encode(payload, SECRET, algorithm=ALGO)


def make_refresh(user_id: int, jti: str, now: float) -> str:
    payload = {"sub": str(user_id), "jti": jti, "typ": "refresh",
               "iat": int(now), "exp": int(now) + REFRESH_TTL}
    return jwt.encode(payload, SECRET, algorithm=ALGO)


def decode(token: str) -> dict | None:
    """Return the token payload, or None if invalid/expired/tampered."""
    try:
        return jwt.decode(token, SECRET, algorithms=[ALGO])
    except Exception:  # noqa: BLE001
        return None


def new_jti() -> str:
    return secrets.token_hex(12)
