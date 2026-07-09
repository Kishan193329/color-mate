"""Experience Learning — rejection / approval feedback store (data collection ONLY).

When an admin rejects (or approves) an image, this records **structured feedback** into an
experience database:

    experience/rejections/   one JSON record (+ optional screenshot) per rejection
    experience/approved/     one JSON record per approval (positive signal)
    experience/patterns/     a rolled-up summary of what tends to get rejected

A FUTURE learning policy — registered on `core.decision.set_policy_provider` (see
FUTURE_LEARNING_ARCHITECTURE.md) — will consume this store to replace static rules. **This
sprint only collects.** It changes no processing algorithm, and no engine reads the store
back yet.
"""
from __future__ import annotations

import hashlib
import json
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from config import APPROVED_DIR, PATTERNS_DIR, REJECTIONS_DIR, ROOT_DIR

# The required reject taxonomy the admin chooses from.
REJECT_CATEGORIES = [
    "bad_border", "broken_shape", "too_many_tiny_regions", "important_detail_lost",
    "ugly_lineart", "wrong_numbering", "bad_mask", "not_fun_to_color",
    "over_complicated", "too_simple", "wrong_category_profile", "other",
]

# Reports referenced (by path + content hash) in a feedback snapshot.
_REPORT_FILES = [
    "game_config.json", "review.json", "border_quality_report.json",
    "artistic_border_report.json", "artistic_border_render_report.json",
    "contour_character_report.json", "semantic_region_report.json",
    "material_behavior_report.json", "auto_profile_report.json",
    "fragment_cleanup_report.json", "artistic_decision_report.json",
    "publish.json", "publish_manifest.json",
]

# Metrics pulled from review.json statistics into the pattern signal aggregates.
_SIGNAL_KEYS = ["region_count", "palette_count", "tiny_regions", "complexity_score",
                "border_quality_score", "artistic_score", "estimated_play_time_min"]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _load(p: Path):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _sha1(p: Path) -> Optional[str]:
    try:
        return hashlib.sha1(p.read_bytes()).hexdigest()
    except OSError:
        return None


def _repo_rel(p: Path) -> str:
    try:
        return str(p.relative_to(ROOT_DIR)).replace("\\", "/")
    except ValueError:
        return str(p)


def image_id(folder: Path) -> str:
    """Stable identity of the source image (survives reprocessing into a new job id)."""
    h = _sha1(folder / "original.png")
    return ("img_" + h[:12]) if h else ("job_" + folder.name)


def _profile(folder: Path) -> Optional[str]:
    gc = _load(folder / "game_config.json") or {}
    ap = _load(folder / "auto_profile_report.json") or {}
    return ap.get("chosen_profile") or gc.get("difficulty") or (gc.get("stats") or {}).get("difficulty")


def _image_category(folder: Path):
    return (_load(folder / "publish.json") or {}).get("category")


def _metrics_snapshot(folder: Path) -> dict:
    """The metrics as they were at feedback time — from the review layer (statistics + the
    future-learning-compatible Decisions)."""
    rev = _load(folder / "review.json") or {}
    return {"statistics": rev.get("statistics", {}), "decisions": rev.get("decisions", [])}


def _reports_snapshot(folder: Path) -> list:
    out = []
    for name in _REPORT_FILES:
        p = folder / name
        if p.exists():
            out.append({"name": name, "reference": _repo_rel(p), "sha1": _sha1(p)})
    return out


def record_rejection(job_id: str, folder: Path, *, category: str, reason: str,
                     notes: Optional[dict] = None, affected_area_description: Optional[str] = None,
                     screenshot_bytes: Optional[bytes] = None,
                     screenshot_name: Optional[str] = None) -> dict:
    """Persist a structured rejection record. `category` must be one of REJECT_CATEGORIES and
    `reason` is required. Returns the record (also written to the job's rejection_feedback.json)."""
    folder = Path(folder)
    if category not in REJECT_CATEGORIES:
        raise ValueError(f"unknown reject category '{category}'; must be one of {REJECT_CATEGORIES}")
    if not (reason and str(reason).strip()):
        raise ValueError("a reject reason is required")
    notes = notes or {}
    record_id = "rej_" + uuid.uuid4().hex[:12]

    screenshot_path = None
    if screenshot_bytes:
        ext = ".png"
        if screenshot_name and "." in screenshot_name:
            cand = "." + screenshot_name.rsplit(".", 1)[-1].lower()
            if cand in (".png", ".jpg", ".jpeg", ".webp"):
                ext = cand
        shot = REJECTIONS_DIR / f"{record_id}_screenshot{ext}"
        shot.write_bytes(screenshot_bytes)
        screenshot_path = _repo_rel(shot)

    which_area = notes.get("which_area_failed")
    record = {
        "record_id": record_id,
        "kind": "rejection",
        "job_id": job_id,
        "image_id": image_id(folder),
        "category": category,                                 # the required reject taxonomy (12)
        "image_category": _image_category(folder),            # the image's content category (context)
        "profile": _profile(folder),
        "reject_reason": str(reason).strip(),
        "reject_notes": {
            "what_is_wrong": notes.get("what_is_wrong"),
            "why_not_useful": notes.get("why_not_useful"),
            "which_area_failed": which_area,
            "what_next_time": notes.get("what_next_time"),
        },
        "uploaded_screenshot_path": screenshot_path,
        "affected_area_description": affected_area_description or which_area,
        "metrics_at_rejection": _metrics_snapshot(folder),
        "reports_snapshot": _reports_snapshot(folder),
        "created_at": _now(),
    }
    (REJECTIONS_DIR / f"{record_id}.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
    # Per-job copy (the sprint's rejection_feedback.json).
    (folder / "rejection_feedback.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
    rebuild_patterns()
    return record


def record_approval(job_id: str, folder: Path) -> dict:
    """Persist a positive experience record on approval (balances the learning data)."""
    folder = Path(folder)
    record_id = "app_" + uuid.uuid4().hex[:12]
    record = {
        "record_id": record_id,
        "kind": "approval",
        "job_id": job_id,
        "image_id": image_id(folder),
        "image_category": _image_category(folder),
        "profile": _profile(folder),
        "metrics_at_approval": _metrics_snapshot(folder),
        "created_at": _now(),
    }
    (APPROVED_DIR / f"{record_id}.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
    rebuild_patterns()
    return record


def _iter_records(directory: Path):
    if not directory.exists():
        return
    for p in sorted(directory.glob("*.json")):
        rec = _load(p)
        if rec:
            yield rec


def rebuild_patterns() -> dict:
    """Roll up the recorded feedback into experience/patterns/patterns.json — the raw
    material a future learning policy consumes. Pure aggregation; no algorithm reads it yet."""
    rejections = list(_iter_records(REJECTIONS_DIR))
    approvals = list(_iter_records(APPROVED_DIR))

    by_reject_cat: Counter = Counter()
    by_profile: Counter = Counter()
    by_image_cat: Counter = Counter()
    signal_sums: dict = defaultdict(lambda: defaultdict(float))
    signal_counts: dict = defaultdict(lambda: defaultdict(int))

    for r in rejections:
        rc = r.get("category", "other")
        by_reject_cat[rc] += 1
        by_profile[r.get("profile") or "unknown"] += 1
        ic = (r.get("image_category") or {})
        by_image_cat[ic.get("name") if isinstance(ic, dict) else "uncategorized"] += 1
        stats = (r.get("metrics_at_rejection") or {}).get("statistics", {})
        for k in _SIGNAL_KEYS:
            v = stats.get(k)
            if isinstance(v, (int, float)):
                signal_sums[rc][k] += v
                signal_counts[rc][k] += 1

    metric_signals = {
        rc: {k: round(signal_sums[rc][k] / signal_counts[rc][k], 2)
             for k in _SIGNAL_KEYS if signal_counts[rc].get(k)}
        for rc in signal_sums
    }

    patterns = {
        "version": 1,
        "note": "Rolled-up admin feedback for future learning. Collection only — no engine "
                "reads this yet; a learning policy will consume it via core.decision.",
        "generated_at": _now(),
        "totals": {"rejections": len(rejections), "approvals": len(approvals),
                   "reject_rate": round(len(rejections) / (len(rejections) + len(approvals)), 3)
                   if (rejections or approvals) else 0.0},
        "by_reject_category": dict(by_reject_cat.most_common()),
        "by_profile": dict(by_profile.most_common()),
        "by_image_category": dict(by_image_cat.most_common()),
        "metric_signals_by_reject_category": metric_signals,
    }
    (PATTERNS_DIR / "patterns.json").write_text(json.dumps(patterns, indent=2), encoding="utf-8")
    return patterns


def summary() -> dict:
    """Current experience-store summary (counts + patterns), for the admin dashboard."""
    p = PATTERNS_DIR / "patterns.json"
    patterns = _load(p) if p.exists() else rebuild_patterns()
    return {"categories": REJECT_CATEGORIES, "patterns": patterns}


def list_rejections(limit: int = 100) -> list:
    recs = list(_iter_records(REJECTIONS_DIR))
    recs.sort(key=lambda r: r.get("created_at", ""), reverse=True)
    return recs[:limit]
