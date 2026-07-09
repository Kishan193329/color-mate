"""Validation for Experience Learning Policy v1:
  1. no experience data     -> identical output, every decision source=rule
  2. seeded rejection data   -> the four review decisions flip to source=experience, with reason
  3. processing outputs      -> game_config / labels / masks / reveal byte-identical throughout
  4. far-from-centroid data  -> no override (proximity gating)
Only the four whitelisted review decisions may change; border_quality (not whitelisted) stays rule."""
from __future__ import annotations
import hashlib, io, json, sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
REPO = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai")
sys.path.insert(0, str(REPO / "backend"))
import main  # noqa (activates the policy)
from services import review_engine as R, experience_store as E, experience_policy as POL  # noqa
from job_store import job_dir  # noqa
from config import PATTERNS_DIR  # noqa

JOB = "br_14"
f = job_dir(JOB)
CORE = ["game_config.json", "labels.npy", "region_mask.png", "reveal_completed.png", "reveal_master.png"]


def hsh(p: Path):
    return hashlib.sha1(p.read_bytes()).hexdigest() if p.exists() else None


def clean():
    for d in (E.REJECTIONS_DIR, E.APPROVED_DIR, PATTERNS_DIR):
        for p in d.glob("*"):
            if p.name != ".gitkeep":
                p.unlink()
    (f / "rejection_feedback.json").unlink(missing_ok=True)
    POL._cache["mtime"] = None


def scores(rev):
    s = rev["statistics"]
    return (s["complexity_score"], s["artistic_score"], s["estimated_play_time_min"])


def srcs(rev):
    return {d["key"].split(".")[-1]: d["source"] for d in rev["decisions"]}


clean()

print("BASELINE (policy deactivated)")
POL.deactivate()
base = R.build_review(f, JOB)
base_core = {c: hsh(f / c) for c in CORE}
print("   scores:", scores(base), "| recommendation:", base["recommendation"]["verdict"])

print("\n1) POLICY ACTIVE + NO DATA -> identical")
POL.activate()
POL._cache["mtime"] = None
nod = R.build_review(f, JOB)
print("   identical stats:", nod["statistics"] == base["statistics"],
      "| identical recommendation:", nod["recommendation"] == base["recommendation"],
      "| all rule:", all(v == "rule" for v in srcs(nod).values()))
print("   processing outputs identical:", {c: hsh(f / c) for c in CORE} == base_core)

print("\n2) SEED repeated rejections near this image's metrics")
for _ in range(3):
    E.record_rejection(JOB, f, category="over_complicated", reason="too busy to enjoy")
for _ in range(3):
    E.record_rejection(JOB, f, category="too_many_tiny_regions", reason="speckle everywhere")
for _ in range(2):
    E.record_rejection(JOB, f, category="not_fun_to_color", reason="tedious")
POL._cache["mtime"] = None
seed = R.build_review(f, JOB)
print("   sources:", srcs(seed))
print("   scores rule -> experience:", scores(base), "->", scores(seed))
print("   recommendation:", seed["recommendation"]["verdict"],
      "| matched:", [m["category"] for m in seed["recommendation"]["matched_patterns"]])
print("   OVERRIDE REASONS:")
for d in seed["decisions"]:
    if d["source"] == "experience":
        print(f"      {d['key']}: {d['reason']}  (conf {d['confidence']})")

OUT = REPO / "research" / "08_reports" / "experience_policy"
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "sample_experience_decisions.json").write_text(json.dumps({
    "note": "review.json excerpt with the experience policy active over seeded rejection patterns",
    "statistics": {k: seed["statistics"][k] for k in
                   ("complexity_score", "artistic_score", "estimated_play_time_min")},
    "recommendation": seed["recommendation"],
    "experience_overridden_decisions": [d for d in seed["decisions"] if d["source"] == "experience"],
}, indent=2), encoding="utf-8")

print("\n3) PROCESSING OUTPUTS unchanged by the override")
print("   game_config/labels/masks/reveal identical:", {c: hsh(f / c) for c in CORE} == base_core)
print("   border_quality (not whitelisted) still rule:", srcs(seed)["border_quality_score"] == "rule")

print("\n4) FAR-FROM-CENTROID data -> no override")
(PATTERNS_DIR / "patterns.json").write_text(json.dumps({
    "totals": {"rejections": 5, "approvals": 0},
    "by_reject_category": {"over_complicated": 5},
    "metric_signals_by_reject_category": {"over_complicated": {"complexity_score": 10,
                                                               "estimated_play_time_min": 2}},
}), encoding="utf-8")
POL._cache["mtime"] = None
far = R.build_review(f, JOB)
print("   sources:", srcs(far), "| scores:", scores(far), "(== baseline:", scores(far) == scores(base), ")")

clean()
R.build_review(f, JOB)  # restore a clean rule-only review.json
print("\nDONE — experience store cleaned, rule-only review restored")
