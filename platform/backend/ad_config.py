"""Ad configuration served to the Android app — the admin's replacement for Firebase.

The app has always read its ad settings from Firebase Realtime Database node "V1" into
`AdsManager.AdModel`, then written every value into SharedPreferences; the other ~9,000 lines of
ad code only ever read SharedPreferences. So swapping the SOURCE is all that is needed, and the
JSON here deliberately uses the EXACT AdModel field names so the app can decode straight into it.

Stored as one row in the existing config table, so no migration.
"""
from __future__ import annotations

import json

import db

CONFIG_KEY = "ad_config"

# (name, type, default) — names must match AdsManager.AdModel exactly, or the app silently
# falls back to the field's own default and the setting looks like it did nothing.
SCHEMA: list[tuple[str, str, object]] = [
    ("adIdOpen", "str", ""), ("adIdSplash", "str", ""), ("adIdSplashOpen", "str", ""),
    ("adIdStart", "str", ""), ("adIdInter", "str", ""), ("adIdBanner", "str", ""),
    ("adIdNative", "str", ""), ("adIdRewarded", "str", ""), ("adIdRewardedInter", "str", ""),
    ("bPolicy", "str", ""), ("bDevEmail", "str", ""), ("bDevName", "str", ""), ("bMsg", "str", ""),
    ("bVersionCode", "int", 2),
    ("dAdsCount", "int", 3), ("dAdsStartCount", "int", 1), ("dAllowedPlay", "int", 25),
    ("dPlayVideo", "bool", True), ("dAllowBack", "bool", False), ("dIsSplashOpen", "bool", False),
    ("dIsExitDialog", "bool", True), ("dPreloadLimit", "int", 2),
    ("dShowIntroNative", "bool", True), ("dShowExitLargeNative", "bool", False),
    ("dShowIntroInter", "bool", False), ("reward_ads_inter", "bool", False),
    ("dShowIntro", "bool", False), ("dShowLanguage", "bool", False),
    ("dShowLanguageNative", "bool", False), ("dShowLanguageInter", "bool", False),
    ("show_reward_confirm", "bool", True),
    ("reward_confirm_title", "str", "Watch Video?"),
    ("reward_confirm_msg", "str", "Watch a short video to get your reward."),
    ("remove_ads_sku", "str", "remove_ads_testing"),
]
_TYPES = {n: t for n, t, _ in SCHEMA}
_DEFAULTS = {n: d for n, _, d in SCHEMA}

# Google's public sample unit ids. Serving one of these earns nothing, so the admin warns.
TEST_IDS = {
    "ca-app-pub-3940256099942544/1033173712", "ca-app-pub-3940256099942544/6300978111",
    "ca-app-pub-3940256099942544/5224354917", "ca-app-pub-3940256099942544/2247696110",
    "ca-app-pub-3940256099942544/3419835294", "ca-app-pub-3940256099942544/5354046379",
    "ca-app-pub-3940256099942544/1044960115",
}


def _coerce(name: str, value):
    t = _TYPES.get(name)
    if t == "bool":
        return value if isinstance(value, bool) else str(value).strip().lower() in ("1", "true", "yes", "on")
    if t == "int":
        try:
            return int(value)
        except (TypeError, ValueError):
            return _DEFAULTS[name]
    return "" if value is None else str(value).strip()


def get() -> dict:
    """Every field, defaults filled in — the app must never receive a partial object."""
    raw = db.get_config().get(CONFIG_KEY) or {}
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except ValueError:
            raw = {}
    out = dict(_DEFAULTS)
    for name in out:
        if name in raw:
            out[name] = _coerce(name, raw[name])
    return out


def save(patch: dict) -> dict:
    """Merge a partial update. An empty string never overwrites a stored value: one blank box in
    the admin would otherwise wipe a live ad unit id and stop the app earning."""
    cur = get()
    for name, value in (patch or {}).items():
        if name not in _TYPES:
            continue
        coerced = _coerce(name, value)
        if _TYPES[name] == "str" and coerced == "" and cur.get(name):
            continue
        cur[name] = coerced
    db.set_config(CONFIG_KEY, cur)
    return cur


def warnings(cfg: dict) -> list[str]:
    """What the admin should tell the owner before he trusts this config."""
    out = []
    for name, t, _ in SCHEMA:
        if not name.startswith("adId"):
            continue
        v = (cfg.get(name) or "").strip()
        if not v:
            continue
        if v in TEST_IDS:
            out.append(f"{name} is one of Google's TEST unit ids — it will never earn money.")
        elif not v.startswith("ca-app-pub-") or "/" not in v:
            out.append(f"{name} does not look like an AdMob unit id (ca-app-pub-…/…).")
    if not any((cfg.get(n) or "").strip() for n, _, _ in SCHEMA if n.startswith("adId")):
        out.append("No ad unit ids are set yet — the app will keep using whatever it already had.")
    return out
