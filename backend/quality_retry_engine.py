"""Quality-retry engine (Product Sprint 2).

The auto-processing entry point for an upload. It runs the balanced **Medium**
profile first, assesses the result (`auto_profile_selector`), and — only when the
quality signals say so — retries a better-suited profile: escalate when the output
is too flat / under-detailed, step down when it is too complex or leaks. It then
picks the best-scoring attempt and leaves that as the job's final output.

It orchestrates the existing pipeline unchanged (`pipeline.run(job_id, profile)`);
no algorithm is redesigned. A finished run already writes every artifact, so each
attempt is assessed straight off disk. A compact `auto_profile_report.json` records
the decision trace for transparency (and the frontend, later).
"""
from __future__ import annotations

import json
from pathlib import Path

import auto_profile_selector as selector
import pipeline
from job_store import finish_job, get_job, job_dir, update_job

DEFAULT_START = "medium"
MAX_ATTEMPTS = 3          # Medium + up to two targeted retries (bounded cost)


def _run_profile(job_id: str, profile: str) -> str:
    """Run the full pipeline for one profile, then immediately hold the job in
    `processing` (pipeline.run finishes each run as `done`, but auto-selection is
    not finished until the winning attempt is chosen). Returns the run's status."""
    pipeline.run(job_id, profile)
    status = (get_job(job_id) or {}).get("status", "error")
    if status == "done":
        update_job(job_id, status="processing")   # keep polling clients waiting
    return status


def process_upload(job_id: str, start: str = DEFAULT_START,
                   max_attempts: int = MAX_ATTEMPTS) -> dict:
    """Auto-select the best output profile for this upload, with quality retry.

    Runs Medium first, retries a better-suited profile only when the quality
    signals demand it, keeps the best-scoring attempt as the final output, and
    finishes the job exactly once. Returns the decision record (also written to
    `auto_profile_report.json`)."""
    folder = job_dir(job_id)
    update_job(job_id, status="processing")
    tried: dict[str, dict] = {}
    trace: list[dict] = []
    profile = start if start in selector.PROFILE_ORDER else DEFAULT_START
    last_run: str | None = None

    for attempt in range(1, max_attempts + 1):
        status = _run_profile(job_id, profile)
        last_run = profile
        if status != "done":
            trace.append({"attempt": attempt, "profile": profile,
                          "verdict": "RUN_FAILED", "direction": "none"})
            return {"version": 1, "chosen_profile": profile,
                    "chosen_reason": "processing failed", "attempts": trace,
                    "reran_winner": False, "ranking": []}
        m = selector.assess(folder, profile)
        tried[profile] = m
        verdict, direction = selector.classify(m)
        trace.append({
            "attempt": attempt, "profile": profile,
            "verdict": verdict, "direction": direction,
            "rendered_deltaE": m["rendered_deltaE"], "palette_count": m["palette_count"],
            "region_count": m["region_count"], "leak_count": m["leak_count"],
            "tiny_region_count": m["tiny_region_count"],
            "protected_highlight_loss": m["protected_highlight_loss"],
            "score": selector.score(m),
        })
        if verdict == "OK":
            break
        nxt = selector.next_profile(profile, direction, set(tried))
        if nxt is None:            # nowhere better to go / already tried
            break
        profile = nxt

    # Pick the best-scoring attempt; make sure it is the final on-disk output.
    ranking = selector.rank(list(tried.values())) if tried else []
    chosen = ranking[0]["profile"] if ranking else last_run
    rerun = False
    if chosen and last_run != chosen:
        _run_profile(job_id, chosen)
        rerun = True

    result = {
        "version": 1,
        "note": "Auto profile selection + quality retry. Medium first, then targeted "
                "retries; best-scoring attempt kept as the final output.",
        "chosen_profile": chosen,
        "chosen_reason": _reason(trace, chosen, ranking),
        "attempts": trace,
        "reran_winner": rerun,
        "ranking": [{"profile": r["profile"], "score": r["score"],
                     "rendered_deltaE": r["rendered_deltaE"],
                     "palette_count": r["palette_count"],
                     "region_count": r["region_count"]} for r in ranking],
    }
    try:
        (folder / "auto_profile_report.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8")
        update_job(job_id, auto_profile=chosen)
    except (OSError, ValueError):
        pass
    finish_job(job_id)   # single final 'done', with the winner's artifacts
    return result


def _reason(trace: list, chosen: str, ranking: list) -> str:
    if not trace:
        return "no attempts"
    first = trace[0]
    if len(trace) == 1 and first["verdict"] == "OK":
        return f"Medium met quality targets (rendered ΔE {first['rendered_deltaE']})."
    verdicts = " → ".join(f"{t['profile']}:{t['verdict']}" for t in trace)
    top = ranking[0] if ranking else {}
    return (f"{verdicts}. Chose {chosen} (best score {top.get('score')}, "
            f"rendered ΔE {top.get('rendered_deltaE')}, {top.get('palette_count')} colours).")
