"""Line-Art Fragmentation Reduction — dissolve tiny non-structural contour fragments
at the source, before the SVG / render stage.

The Artistic Border Render rejects heavily-fragmented images because hundreds of tiny
non-structural sub-contours (background specks, filled-in holes, disconnected crumbs)
each get simplified and, summed, shift shape past the fidelity gate. This engine removes
that explosion at its source by DISSOLVING each such fragment into the region that
already surrounds it — producing a cleaned label map the border chain (QF14 + artistic
border) renders from.

Hard invariants (this is why it is safe):
  * Union preserved EXACTLY — a fragment is only ever reassigned to a NON-ZERO neighbour
    label, never to the line skeleton (label 0). `labels > 0` is byte-identical before
    and after, so the completed reveal still equals the original and every region mask
    stays closed. No line art is touched, so no leak can appear.
  * Every numbered region survives — only NON-PRIMARY components (a label's smaller,
    disconnected crumbs) are dissolved; each label keeps its largest component, so the
    palette and the numbers are unchanged.
  * Features preserved — labels carrying a facial / flower / leaf / fur / decorative role
    (or an explicit protect flag) are never dissolved, so eyes, petals, hair strands and
    accessories keep every one of their pieces. Thin long strands are additionally spared
    by the compact-only (small area AND short perimeter) test.

The cleaned map is written to a SEPARATE file; `labels.npy`, `region_mask.png`, the
palette, the numbers and every reveal asset are left exactly as they were.
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import cv2
import numpy as np

# A dissolvable fragment is small in BOTH area and perimeter (compact) — the perimeter
# floor spares thin long strands (small area, long perimeter) even in a non-protected
# region. Matched a little more generously than the render's speck floor because merging
# is lossless (union preserved) whereas the render's REMOVE actually drops the contour.
FRAG_AREA = 300
FRAG_LEN = 250

# Roles whose regions are structural / features — never dissolved (any component).
PROTECTED_ROLES = {
    "FACIAL_FEATURE", "FLOWER_DETAIL", "LEAF_DETAIL", "FUR_PATTERN", "DECORATIVE_OBJECT",
}
_NEIGH = np.ones((3, 3), np.uint8)


def _protected_labels(artistic_report: dict) -> set:
    prot = set()
    for r in artistic_report.get("regions", []):
        lbl = r.get("label")
        if lbl is None:
            continue
        if r.get("artistic_role") in PROTECTED_ROLES \
                or r.get("protect_shape") or r.get("protect_highlight"):
            prot.add(lbl)
    return prot


def _perimeter(comp: np.ndarray) -> float:
    cnts, _ = cv2.findContours(comp.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    return float(sum(cv2.arcLength(c, True) for c in cnts))


def _count_contours(labels: np.ndarray, labels_set) -> int:
    total = 0
    for lbl in labels_set:
        m = np.uint8(labels == lbl) * 255
        cnts, _ = cv2.findContours(m, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
        total += sum(1 for c in cnts if len(c) >= 4)
    return total


def clean(labels_path: Path, artistic_report_path: Path,
          out_labels_path: Path, out_report_path: Path,
          frag_area: int = FRAG_AREA, frag_len: int = FRAG_LEN) -> dict:
    """Dissolve non-primary compact fragments into their dominant neighbour. Writes the
    cleaned label map and a report. `labels.npy` itself is NOT modified."""
    labels = np.load(labels_path).astype(np.int32)
    try:
        artistic = json.loads(Path(artistic_report_path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        artistic = {}
    protected = _protected_labels(artistic)

    union_before = int((labels > 0).sum())
    labels_present = [int(l) for l in np.unique(labels) if l != 0]
    frags_before = _count_contours(labels, labels_present)

    dissolved = 0
    dissolved_area = 0
    protected_skipped = 0
    for lbl in labels_present:
        if lbl in protected:
            continue
        mask = np.uint8(labels == lbl)
        n, cc, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
        if n <= 2:                                   # 0 or 1 component -> nothing to dissolve
            continue
        areas = stats[1:, cv2.CC_STAT_AREA]
        primary = int(np.argmax(areas)) + 1         # keep the label's largest component
        for idx in range(1, n):
            if idx == primary:
                continue
            area = int(stats[idx, cv2.CC_STAT_AREA])
            if area > frag_area:
                continue
            comp = cc == idx
            if _perimeter(comp) > frag_len:          # spare thin long strands
                continue
            ring = (cv2.dilate(comp.astype(np.uint8), _NEIGH) > 0) & (~comp)
            nb = labels[ring]
            nb = nb[(nb != 0) & (nb != lbl)]
            if nb.size == 0:                         # only bordered by line skeleton -> keep (union)
                continue
            neighbor = int(Counter(nb.tolist()).most_common(1)[0][0])
            labels[comp] = neighbor                  # reassign to a real neighbouring region
            dissolved += 1
            dissolved_area += area

    union_after = int((labels > 0).sum())
    frags_after = _count_contours(labels, [int(l) for l in np.unique(labels) if l != 0])
    labels_after = set(int(l) for l in np.unique(labels) if l != 0)
    labels_lost = sorted(set(labels_present) - labels_after)   # must be empty (palette/numbers)

    np.save(out_labels_path, labels)
    report = {
        "version": 1,
        "note": "Line-art fragmentation reduction — dissolves non-primary compact fragments "
                "into their dominant neighbour to shrink the tiny-contour explosion before "
                "the SVG/render stage. Union (labels>0) preserved exactly, so reveal==original "
                "and masks stay closed; every numbered region survives (palette/numbers "
                "unchanged); facial/flower/leaf/fur/decorative labels never dissolved.",
        "sources": ["labels.npy", "artistic_decision_report.json"],
        "frag_area_max": frag_area, "frag_len_max": frag_len,
        "protected_label_count": len(protected),
        "labels_before": len(labels_present),
        "labels_after": len(labels_after),
        "labels_lost": labels_lost,               # expected [] — a lost label would drop a number
        "fragments_before": frags_before,
        "fragments_after": frags_after,
        "fragments_removed": frags_before - frags_after,
        "components_dissolved": dissolved,
        "dissolved_area_px": dissolved_area,
        "dissolved_area_percent": round(100 * dissolved_area / max(1, union_before), 3),
        "union_before": union_before,
        "union_after": union_after,
        "union_preserved": bool(union_before == union_after),
    }
    Path(out_report_path).write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
