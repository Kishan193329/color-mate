"""End-to-end test of the Experience Learning rejection-feedback flow: the reject-feedback
endpoint (category + reason + 4 notes + screenshot) -> experience record + rejection_feedback.json
+ patterns rollup + status rejected; plus approval capture. Data collection only."""
from __future__ import annotations
import asyncio, io, json, shutil, sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa
from services import experience_store as E, publishing, review_engine  # noqa
from job_store import job_dir  # noqa
from starlette.datastructures import UploadFile  # noqa
from fastapi import HTTPException  # noqa

OUT = REPO / "research" / "08_reports" / "rejection_feedback"
OUT.mkdir(parents=True, exist_ok=True)
JOB = "cat_publish_test"
f = job_dir(JOB)

# clean the experience store for a deterministic run
for d in (E.REJECTIONS_DIR, E.APPROVED_DIR, E.PATTERNS_DIR):
    for p in d.glob("*"):
        p.unlink()
(f / "rejection_feedback.json").unlink(missing_ok=True)

print("1) reject-categories endpoint:", len(main.reject_categories()["categories"]), "categories")

review_engine.build_review(f, JOB)  # refresh review.json so metrics include the Decisions

print("2) REJECT with category + reason + notes + screenshot (multipart)")
shot = UploadFile(io.BytesIO(b"\\x89PNG\\r\\n_fake_screenshot_bytes"), filename="tree_area.png")
res = asyncio.run(main.reject_feedback(
    JOB, category="too_many_tiny_regions",
    reason="the tree canopy and hair fracture into 1-2px specks; tedious to fill",
    what_is_wrong="dozens of sub-pixel regions in foliage",
    why_not_useful="not relaxing; a colourist can't see the numbers",
    which_area_failed="tree canopy + hair bun",
    what_next_time="merge tiny same-colour background regions more aggressively",
    affected_area="tree canopy (top-right) and hair", screenshot=shot))
rec = res["record"]
print("   status:", res["status"], "| record_id:", rec["record_id"])
print("   required fields present:",
      all(k in rec for k in ["job_id", "image_id", "category", "profile", "reject_reason",
                             "reject_notes", "uploaded_screenshot_path", "affected_area_description",
                             "metrics_at_rejection", "reports_snapshot", "created_at"]))
print("   image_id:", rec["image_id"], "| profile:", rec["profile"], "| reject category:", rec["category"])
print("   screenshot saved:", rec["uploaded_screenshot_path"],
      "exists:", (REPO / rec["uploaded_screenshot_path"]).exists())
print("   reports referenced:", len(rec["reports_snapshot"]),
      "| metrics decisions:", len(rec["metrics_at_rejection"]["decisions"]))
print("   rejection_feedback.json in job:", (f / "rejection_feedback.json").exists())
print("   publish status:", publishing.status(f), "| review status:",
      json.loads((f / "review.json").read_text())["status"])

print("3) validation")
try:
    asyncio.run(main.reject_feedback(JOB, category="not_a_category", reason="x", screenshot=None))
except HTTPException as e:
    print("   bad category ->", e.status_code, e.detail[:45])
try:
    asyncio.run(main.reject_feedback(JOB, category="other", reason="   ", screenshot=None))
except HTTPException as e:
    print("   empty reason ->", e.status_code, e.detail[:45])

print("4) APPROVE another job (br_14) -> approval experience record")
main.review_approve("br_14")
print("   approvals now:", len(list(E.APPROVED_DIR.glob('*.json'))))

print("5) PATTERNS rollup + experience summary")
summ = main.admin_experience()
pat = summ["patterns"]
print("   totals:", pat["totals"])
print("   by_reject_category:", pat["by_reject_category"])
print("   signal (too_many_tiny_regions):", pat["metric_signals_by_reject_category"].get("too_many_tiny_regions"))

# copy evidence for the report (sanitised — the record + patterns)
shutil.copyfile(E.REJECTIONS_DIR / f"{rec['record_id']}.json", OUT / "sample_rejection.json")
shutil.copyfile(E.PATTERNS_DIR / "patterns.json", OUT / "sample_patterns.json")
print("\nDONE — evidence copied to", OUT)
