"""Admin Publishing Flow — the owner/admin lifecycle that prepares an image before it
appears in the user app.

Images are **admin-uploaded**, processed, reviewed, approved, and finally **published**;
only published images are exposed to users (the catalog / category grid). This module owns
the publish *lifecycle* and *publishing metadata* only. It changes no image algorithm, no
reveal mode, and no border engine — it reads the pipeline / review outputs and writes
`publish.json`, `publish_manifest.json`, and a grid thumbnail.

Lifecycle:  draft -> processing -> review -> approved -> published   (with -> rejected)
  draft       admin uploaded the colour image, not processed yet
  processing  the pipeline is running
  review      processed; awaiting the admin's approve/reject decision
  approved    admin approved; ready to publish
  rejected    admin rejected (with a reason)
  published   assembled into a package and exposed to users

The approved package bundles: original image, border image, numbered image,
labels/regions, palette/navigation colours, reveal masks, game_config, category
metadata, and the thumbnail/grid preview.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import cv2

from config import JOBS_DIR

STATES = ["draft", "processing", "review", "approved", "rejected", "published"]

# Transitions permitted for admin / API actions. Pipeline hooks force-set the automatic
# states (processing, review) with validate=False, so a regenerate cleanly resets an
# already-approved/published image back through the lifecycle.
TRANSITIONS = {
    "draft": {"processing"},
    "processing": {"review", "rejected", "draft"},
    "review": {"approved", "rejected", "processing"},
    "approved": {"processing", "rejected"},   # -> published only via publish() (builds the package)
    "rejected": {"processing", "review"},
    "published": {"approved"},                # unpublish
}

# Approved-package components: (logical name, required files, optional files).
# A component is "complete" when all its required files exist on disk.
PACKAGE = [
    ("original_image", ["original.png"], []),
    ("border_image", ["outline.svg"], ["review_border_preview.png"]),
    ("numbered_image", ["game_preview.png"], []),
    ("labels_regions", ["labels.npy", "regions.json"], []),
    ("palette_navigation", ["palette.json"], []),
    ("reveal_masks", ["reveal_master.png", "region_mask.png", "labels.npy"],
     ["reveal_blank.png", "reveal_partial.png", "reveal_highlight.png", "reveal_completed.png"]),
    ("game_config", ["game_config.json"], []),
    ("thumbnail_grid", ["publish_thumbnail.png"], []),
]

# Filenames the publishing / catalog surfaces are allowed to serve (whitelist).
SERVEABLE = {"publish_thumbnail.png", "reveal_partial.png", "reveal_completed.png",
             "review_border_preview.png", "game_preview.png", "original.png"}
for _n, _req, _opt in PACKAGE:
    SERVEABLE.update(_req)
    SERVEABLE.update(_opt)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _pub_path(folder: Path) -> Path:
    return Path(folder) / "publish.json"


def load(folder: Path) -> dict:
    p = _pub_path(folder)
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            pass
    return {}


def _save(folder: Path, data: dict) -> None:
    data["updated_at"] = _now()
    _pub_path(folder).write_text(json.dumps(data, indent=2), encoding="utf-8")


def _default_category() -> dict:
    return {"name": "uncategorized", "title": None, "tags": []}


def init(folder: Path, category: str | None = None, title: str | None = None,
         tags: list | None = None, job_id: str | None = None) -> dict:
    """Admin upload -> draft. Idempotent; only fills what's missing."""
    folder = Path(folder)
    data = load(folder)
    data.setdefault("job_id", job_id or folder.name)
    data.setdefault("status", "draft")
    data.setdefault("history", [])
    cat = data.get("category") or _default_category()
    if category is not None:
        cat["name"] = category
    if title is not None:
        cat["title"] = title
    if tags is not None:
        cat["tags"] = tags
    data["category"] = cat
    _save(folder, data)
    return data


def status(folder: Path) -> str:
    return (load(folder) or {}).get("status", "draft")


def set_status(folder: Path, new: str, *, validate: bool = True,
               reason: str | None = None, actor: str = "admin") -> dict:
    folder = Path(folder)
    data = load(folder) or init(folder)
    cur = data.get("status", "draft")
    if new not in STATES:
        raise ValueError(f"unknown status '{new}'")
    if validate and new != cur and new not in TRANSITIONS.get(cur, set()):
        raise ValueError(f"illegal transition {cur} -> {new}")
    if new != cur:
        data["history"].append({"from": cur, "to": new, "reason": reason, "actor": actor, "at": _now()})
    data["status"] = new
    if new == "rejected":
        data["reject_reason"] = reason
    _save(folder, data)
    return data


def set_category(folder: Path, name: str | None = None, title: str | None = None,
                 tags: list | None = None) -> dict:
    folder = Path(folder)
    data = load(folder) or init(folder)
    cat = data.get("category") or _default_category()
    if name is not None:
        cat["name"] = name
    if title is not None:
        cat["title"] = title
    if tags is not None:
        cat["tags"] = tags
    data["category"] = cat
    _save(folder, data)
    return data


def _thumbnail(folder: Path, size: int = 400) -> bool:
    """Square grid thumbnail from the completed reveal (falls back to numbered / original)."""
    folder = Path(folder)
    for src in ("reveal_completed.png", "game_preview.png", "preview.png", "original.png"):
        p = folder / src
        if not p.exists():
            continue
        img = cv2.imread(str(p))
        if img is None:
            continue
        h, w = img.shape[:2]
        s = min(h, w)
        y, x = (h - s) // 2, (w - s) // 2
        crop = img[y:y + s, x:x + s]
        cv2.imwrite(str(folder / "publish_thumbnail.png"),
                    cv2.resize(crop, (size, size), interpolation=cv2.INTER_AREA))
        return True
    return False


def ensure_thumbnail(folder: Path) -> bool:
    """Make sure the grid thumbnail exists (for the admin review's category-grid preview,
    which is available before publishing)."""
    if (Path(folder) / "publish_thumbnail.png").exists():
        return True
    return _thumbnail(folder)


def package_manifest(folder: Path) -> dict:
    """Describe (and verify) the approved-image package. `complete` is True when every
    required file of every component is present."""
    folder = Path(folder)
    comps: dict = {}
    complete = True
    for name, required, optional in PACKAGE:
        req_ok = all((folder / f).exists() for f in required)
        comps[name] = {
            "required": required,
            "optional": optional,
            "present": [f for f in required + optional if (folder / f).exists()],
            "missing_required": [f for f in required if not (folder / f).exists()],
            "complete": req_ok,
        }
        complete = complete and req_ok
    data = load(folder)
    cat = data.get("category")
    comps["category_metadata"] = {"category": cat, "complete": bool(cat and cat.get("name"))}
    complete = complete and comps["category_metadata"]["complete"]
    return {"complete": complete, "components": comps}


def publish(folder: Path) -> dict:
    """approved -> published. Generates the thumbnail, assembles + verifies the package,
    writes publish_manifest.json. Refuses unless status == approved and the package is
    complete."""
    folder = Path(folder)
    data = load(folder) or init(folder)
    if data.get("status") != "approved":
        raise ValueError(f"can only publish an approved image (status={data.get('status')})")
    _thumbnail(folder)
    manifest = package_manifest(folder)
    if not manifest["complete"]:
        missing = {k: v.get("missing_required")
                   for k, v in manifest["components"].items()
                   if isinstance(v, dict) and v.get("missing_required")}
        raise ValueError(f"package incomplete: {missing or 'category metadata missing'}")
    ts = _now()
    manifest.update({"published_at": ts, "job_id": data.get("job_id"), "category": data.get("category")})
    (folder / "publish_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    data["history"].append({"from": "approved", "to": "published", "at": ts, "actor": "admin"})
    data["status"] = "published"
    data["published_at"] = ts
    _save(folder, data)
    return {"status": "published", "manifest": manifest}


def unpublish(folder: Path) -> dict:
    return set_status(folder, "approved", reason="unpublished")


def _stats_brief(folder: Path) -> dict:
    p = Path(folder) / "game_config.json"
    if not p.exists():
        return {}
    try:
        gc = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    return {"regions": len(gc.get("regions", [])), "palette": len(gc.get("palette", [])),
            "difficulty": gc.get("difficulty") or (gc.get("stats") or {}).get("difficulty")}


def _row(folder: Path, data: dict) -> dict:
    return {
        "job_id": data.get("job_id", folder.name),
        "status": data.get("status"),
        "category": data.get("category"),
        "published_at": data.get("published_at"),
        "updated_at": data.get("updated_at"),
        "thumbnail": "publish_thumbnail.png" if (folder / "publish_thumbnail.png").exists() else None,
        "stats": _stats_brief(folder),
    }


def catalog(published_only: bool = True) -> list:
    """The user-facing catalog / category grid — published images with category + thumbnail.
    With published_only=False, the admin dashboard of every job and its publish status."""
    out = []
    if not JOBS_DIR.exists():
        return out
    for folder in sorted(JOBS_DIR.iterdir()):
        if not folder.is_dir():
            continue
        data = load(folder)
        if not data:
            continue
        if published_only and data.get("status") != "published":
            continue
        out.append(_row(folder, data))
    out.sort(key=lambda r: ((r.get("category") or {}).get("name") or "~", r.get("job_id") or ""))
    return out
