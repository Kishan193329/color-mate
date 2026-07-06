"""Shared engine primitives.

`core` holds cross-engine helpers that are NOT a pipeline stage of their own —
currently the gradient/split math reused by QF10/QF11/QF12 (see
`gradient_common`). Keeping them here means no engine has to import another
engine just to borrow a function.
"""
