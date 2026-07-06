"""Shared gradient / split primitives (Engineering Sprint 1).

These constants and functions were originally defined inside the QF10
(`gradient_preservation_engine`) and QF11 (`flow_gradient_engine`) engines and
imported across QF11/QF12. The audit noted QF10/QF11 are no longer live engines
(their `run()` is never called) — only their helpers are reused. This module is
the single home for those reusable pieces so no engine imports another engine
just to borrow math.

Behaviour is unchanged: every definition here is moved verbatim from QF10/QF11.
QF10/QF11 now re-import these names (backward compatible), and QF12 imports them
directly.
"""
from __future__ import annotations

import cv2
import numpy as np

# --- selection / banding constants (moved verbatim from QF10) --------------- #
SPLIT_MIN_STRENGTH = 0.5      # policy future_split_strength gate
MIN_LUMA_RANGE = 16.0         # Lab-L span (p5..p95) needed before splitting
LUMA_STEP = 20.0              # ~1 band per this much L range
MAX_STEPS = 3                 # never more than 3 tonal steps (avoid over-split)
SELECT_POLICY = {"GRADIENT_PRESERVE", "FLOW_PROTECT"}

# --- flow constants (moved verbatim from QF11) ------------------------------ #
MIN_COHERENCE = 0.25   # edge_flow_score below this -> isotropic fallback
FLOW_MAX_STEPS = 4     # QF11 allows one more tonal step than QF10 (finer gradient)
BLUR_LEN = 15          # oriented band-boundary smoothing kernel length

# --- split protection set (moved verbatim from QF12 PROTECT_CLASSES) -------- #
# Never split these (small / fragile / structural detail), even if policy selects.
SPLIT_PROTECT_CLASSES = {"FLOWER", "GLASS", "EYE", "SKY"}


def _mean_fill(labels: np.ndarray, bgr: np.ndarray) -> np.ndarray:
    """Flat fill: every region painted with the mean original colour (label 0 = line)."""
    num = int(labels.max()) + 1
    flat = labels.ravel()
    counts = np.bincount(flat, minlength=num).astype(np.float64)
    counts[counts == 0] = 1
    out = np.zeros_like(bgr)
    for ch in range(3):
        s = np.bincount(flat, weights=bgr[:, :, ch].astype(np.float64).ravel(), minlength=num)
        m = (s / counts).astype(np.uint8)
        out[:, :, ch] = m[labels]
    out[labels == 0] = (35, 35, 35)
    return out


def _delta_e(fill_bgr: np.ndarray, orig_bgr: np.ndarray, mask: np.ndarray) -> float:
    """Mean CIE76 Lab distance between a flat fill and the original, over `mask`."""
    a = cv2.cvtColor(fill_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    b = cv2.cvtColor(orig_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    d = np.sqrt(((a - b) ** 2).sum(axis=2))
    return float(d[mask].mean()) if mask.any() else 0.0


def _boundaries(labels: np.ndarray) -> np.ndarray:
    edge = np.zeros(labels.shape, bool)
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    return edge


def ssim(a_gray: np.ndarray, b_gray: np.ndarray, mask: np.ndarray | None = None) -> float:
    a = a_gray.astype(np.float64)
    b = b_gray.astype(np.float64)
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    mu_a = cv2.GaussianBlur(a, (11, 11), 1.5)
    mu_b = cv2.GaussianBlur(b, (11, 11), 1.5)
    sa = cv2.GaussianBlur(a * a, (11, 11), 1.5) - mu_a * mu_a
    sb = cv2.GaussianBlur(b * b, (11, 11), 1.5) - mu_b * mu_b
    sab = cv2.GaussianBlur(a * b, (11, 11), 1.5) - mu_a * mu_b
    m = ((2 * mu_a * mu_b + c1) * (2 * sab + c2)) / \
        ((mu_a * mu_a + mu_b * mu_b + c1) * (sa + sb + c2) + 1e-9)
    return float(m[mask].mean()) if mask is not None and mask.any() else float(m.mean())


def _oriented_blur(img: np.ndarray, angle_deg: float, length: int = BLUR_LEN, thick: int = 3) -> np.ndarray:
    k = np.zeros((length, length), np.float32)
    cv2.line(k, (0, length // 2), (length - 1, length // 2), 1.0, thick)
    rot = cv2.getRotationMatrix2D((length / 2 - 0.5, length / 2 - 0.5), -angle_deg, 1.0)
    k = cv2.warpAffine(k, rot, (length, length))
    s = k.sum()
    if s > 0:
        k /= s
    return cv2.filter2D(img, -1, k)


def legacy_split_selected(policy_region: dict, semantic_class: str) -> bool:
    """The exact QF12 split-selection predicate, in one place.

    Equals the historical `intelligent_split_engine` selection gate:
    a GRADIENT_PRESERVE/FLOW_PROTECT region that preserves gradient, whose
    policy split strength clears the threshold, and whose material is not in the
    protected set. QF13 evaluates this to publish the authoritative selection so
    QF12 no longer re-derives policy itself. Behaviour is identical to the
    pre-Sprint-1 QF12 filter.
    """
    return bool(
        policy_region.get("policy_class") in SELECT_POLICY
        and policy_region.get("preserve_gradient")
        and float(policy_region.get("future_split_strength", 0.0)) >= SPLIT_MIN_STRENGTH
        and semantic_class not in SPLIT_PROTECT_CLASSES
    )
