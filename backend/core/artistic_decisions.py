"""Read-only bridge to the QF13 artistic decision report — the single authority.

Engineering Sprint 1 made QF13 (`artistic_decision_engine`) the one place every
processing decision lives. Instead of re-deriving policy from QF9, downstream
engines consult THIS bridge, which loads `artistic_decision_report.json` and
exposes the per-region decisions keyed by the geometry `label` the region tools
use (and by `region_id`).

The split engine (QF12) already obeys QF13 via the report's `split_plan`. The
palette / SVG / numbering engines are *prepared* to obey QF13 through this bridge
(see their `OBEY_QF13` seams) — Sprint 1 wires the read path without changing any
behaviour. This module only READS; it never writes or recomputes anything.
"""
from __future__ import annotations

import json
from pathlib import Path


class ArtisticDecisions:
    """Per-region QF13 decisions, indexed by geometry `label` and `region_id`.

    Accessors take a region label and return the authoritative field with a
    conservative default when a region is absent from the report, so callers can
    consult the brain uniformly without existence checks.
    """

    def __init__(self, report: dict):
        regions = report.get("regions", []) or []
        self._by_id = {r.get("region_id"): r for r in regions}
        self._by_label = {r.get("label"): r for r in regions
                          if r.get("label") is not None}
        self.split_plan: list[dict] = report.get("split_plan", []) or []
        self.split_selected_region_ids = set(report.get("split_selected_region_ids", []) or [])

    # -- lookups ----------------------------------------------------------- #
    def for_label(self, label) -> dict:
        return self._by_label.get(label, {})

    def for_region(self, region_id) -> dict:
        return self._by_id.get(region_id, {})

    # -- actuator-facing decisions (label-keyed) --------------------------- #
    def palette_merge_permission(self, label, default: str = "WITHIN_MATERIAL") -> str:
        """LOCKED (keep own colour) | WITHIN_MATERIAL | FREE — obeyed by the palette engine."""
        return self.for_label(label).get("palette_merge_permission", default)

    def svg_precision(self, label, default: str = "MEDIUM") -> str:
        """HIGH (crisp) | MEDIUM | LOW — obeyed by the SVG generator."""
        return self.for_label(label).get("svg_precision", default)

    def numbering_sensitivity(self, label, default: str = "MEDIUM") -> str:
        """HIGH (protect small numbers) | MEDIUM | LOW — obeyed by the numbering engine."""
        return self.for_label(label).get("numbering_sensitivity", default)

    def protect_shape(self, label, default: bool = False) -> bool:
        return bool(self.for_label(label).get("protect_shape", default))

    def artistic_role(self, label, default: str = "UNKNOWN_SUPPORT") -> str:
        return self.for_label(label).get("artistic_role", default)

    def visual_importance(self, label, default: float = 0.0) -> float:
        return float(self.for_label(label).get("visual_importance", default))

    def is_split_selected(self, region_id) -> bool:
        return region_id in self.split_selected_region_ids

    def __len__(self) -> int:
        return len(self._by_label)


def load(report_path: Path | str | None) -> ArtisticDecisions | None:
    """Load the QF13 report, or None if it is absent/unreadable.

    Best-effort by design: a missing report (e.g. QF13 not yet run in this pass)
    yields None so callers transparently keep their current behaviour.
    """
    if report_path is None:
        return None
    p = Path(report_path)
    if not p.exists():
        return None
    try:
        return ArtisticDecisions(json.loads(p.read_text(encoding="utf-8")))
    except (ValueError, OSError):
        return None
