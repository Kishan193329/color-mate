# ColorForge Research Workspace

Research-only workspace for improving ColorForge image conversion quality. This
is **observation, analysis, and specification — no application code lives here**
and nothing in `backend/` or `frontend/` is changed from this folder.

The workspace exists to turn real reference images into **evidence → patterns →
engineering standards → algorithms → validated results**, one quality area at a
time. Day 1 focuses on **Borders / Line Art**.

## Folder structure

| Folder | Purpose |
| --- | --- |
| `01_reference_images/` | Source images we study (portraits, animals, fur, flowers, products). One conclusion per image should trace back here. |
| `02_observations/` | Structured, per-image findings (CSV). The raw evidence. |
| `03_patterns/` | Recurring findings distilled into named patterns. |
| `04_engineering_standards/` | Formal specs (CFES = ColorForge Engineering Standard) the engine must satisfy. |
| `05_decision_trees/` | "If X then Y" logic the engine/tools follow (keep vs merge, smooth vs preserve). |
| `06_algorithms/` | Candidate algorithms + pseudocode to meet the standards. |
| `07_validation/` | Test cases, pass/fail criteria, before/after checks. |
| `08_reports/` | Day summaries and decisions. |

## Workflow

1. Collect reference images → `01_reference_images/`.
2. Record what we see → `02_observations/*.csv` (one row per observation).
3. Cluster observations into patterns → `03_patterns/*.md`.
4. Write the standard the engine must meet → `04_engineering_standards/CFES-*.md`.
5. Design decision logic and algorithms → `05_*`, `06_*`.
6. Define validation → `07_validation/`.
7. Summarize → `08_reports/`.

## Guardrails

- Governed by [`../COLORFORGE_QUALITY_RULEBOOK.md`](../COLORFORGE_QUALITY_RULEBOOK.md).
- Original image must not be redesigned; line art (not color quantization) is the
  source of truth for regions.
- Research does not modify shipping code. Implementation happens later, on its own
  branch, referencing the CFES standards produced here.

## Observation CSV schema

`02_observations/border_observations.csv`

| Column | Meaning |
| --- | --- |
| `id` | Stable observation id (e.g. `BORD-001`). |
| `category` | Border sub-area (outer border, inner border, fur/hair border, eye border, mouth/nose border, organic curves, closed shapes, jagged edge control, texture noise vs real border). |
| `object` | Subject type (portrait, cat, dog, tiger, flower, product, general). |
| `location` | Where on the subject the observation applies. |
| `importance` | `critical` \| `high` \| `medium` \| `low`. |
| `observation` | What we actually see in the reference. |
| `reason` | Why it matters for a clean color-by-number conversion. |
| `possible_rule` | Candidate engine rule this observation suggests. |
| `reference_images` | `;`-separated image ids in `01_reference_images/`. |
| `status` | `proposed` \| `reviewed` \| `accepted` \| `rejected`. |

## Day 1 deliverables

- `02_observations/border_observations.csv` — first 25 border observations.
- `03_patterns/border_patterns.md` — patterns distilled from them.
- `04_engineering_standards/CFES-100-border-engineering.md` — the border standard.
