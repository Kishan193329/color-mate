"""End-to-end Admin Publishing Flow test with the existing cat.png:
admin upload -> process -> review -> approve -> publish -> catalog. Verifies the full
lifecycle, the approved-package manifest, and the user-facing catalog."""
from __future__ import annotations
import io, json, shutil, sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import pipeline  # noqa
pipeline.STEP_DELAY_SECONDS = 0.0
from services import ai_mode_router  # noqa
ai_mode_router.maybe_enhance = lambda *a, **k: {"source": "local"}
from services import publishing  # noqa
from job_store import create_job, job_dir, get_job  # noqa
import main  # noqa

JOB = "cat_publish_test"
OUT = REPO / "research" / "08_reports" / "admin_publishing"
OUT.mkdir(parents=True, exist_ok=True)
f = job_dir(JOB)
if f.exists():
    shutil.rmtree(f)

print("1) ADMIN UPLOAD (colour image, category)")
create_job(JOB)
shutil.copyfile(REPO / "cat.png", f / "original.png")
publishing.init(f, category="cats", title="Curious Cat", tags=["animal", "cat"], job_id=JOB)
print("   status:", publishing.status(f), "  (expect draft)")

print("2) PROCESS (pipeline; deterministic line art)")
pipeline.run(JOB, "medium")
print("   status:", publishing.status(f), " | job:", (get_job(JOB) or {}).get("status"),
      "| review.json:", (f / "review.json").exists(), "  (expect review / done)")

print("3) APPROVE")
main.review_approve(JOB)
print("   status:", publishing.status(f), "  (expect approved)")

print("4) PUBLISH")
res = main.publish_job(JOB)
print("   status:", res["status"], "| package complete:", res["manifest"]["complete"])
print("   package components:")
for k, v in res["manifest"]["components"].items():
    if isinstance(v, dict):
        mark = "OK" if v.get("complete") else "MISSING " + str(v.get("missing_required"))
        print(f"      {k:20} {mark}")

print("5) CATALOG (user-facing — published only)")
cat = main.catalog()["images"]
mine = [i for i in cat if i["job_id"] == JOB]
print("   in catalog:", bool(mine), "|", mine[0] if mine else None)

print("6) LIFECYCLE HISTORY")
data = publishing.load(f)
print("   ", " -> ".join([h["from"] for h in data["history"][:1]] + [h["to"] for h in data["history"]]))

print("7) UNPUBLISH round-trip")
main.unpublish_job(JOB)
print("   status after unpublish:", publishing.status(f), "| catalog now:",
      bool([i for i in main.catalog()["images"] if i["job_id"] == JOB]), " (expect approved / False)")
main.publish_job(JOB)
print("   re-published:", publishing.status(f))

# copy the published thumbnail + a manifest snapshot for the report
shutil.copyfile(f / "publish_thumbnail.png", OUT / "cat_thumbnail.png")
(OUT / "cat_publish_manifest.json").write_text((f / "publish_manifest.json").read_text(encoding="utf-8"), encoding="utf-8")
(OUT / "cat_publish.json").write_text((f / "publish.json").read_text(encoding="utf-8"), encoding="utf-8")
print("\nDONE — artifacts copied to", OUT)
