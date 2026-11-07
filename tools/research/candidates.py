"""Controlled candidate inventory and the single renderer used by both learned selectors.

A candidate is a (cut, append) edit applied to the caller's lemma, plus the deterministic base result. The
inventory is fixed before training and is not expanded after seeing test results. Both the non-neural and the
neural selector score exactly these candidates with exactly this renderer.
"""

from __future__ import annotations

EDITS: list[tuple[str, int, str]] = [
    ("s", 0, "s"),
    ("es", 0, "es"),
    ("ies", 1, "ies"),
    ("ves", 1, "ves"),
    ("fe->ves", 2, "ves"),
    ("oes", 1, "oes"),
    ("us->i", 1, "i"),
    ("um->a", 2, "a"),
    ("a->ae", 1, "ae"),
    ("ix->ices", 2, "ices"),
    ("is->es", 2, "es"),
    ("man->men", 3, "men"),
    ("child->ren", 0, "ren"),
    ("mouse->ice", 4, "ice"),
    ("person->people", 5, "eople"),
    ("quiz->zzes", 1, "zzes"),
    ("lemma", 0, ""),
]

BASE_EDIT_IDS = ["base:same", "base:s", "base:es", "base:ies", "base:other"]

EDIT_IDS = [edit[0] for edit in EDITS] + BASE_EDIT_IDS
EDIT_INDEX = {edit_id: index for index, edit_id in enumerate(EDIT_IDS)}


def base_edit_id(lemma: str, base: str) -> str:
    """Describe what the deterministic base did, so a ranker can learn when it is likely wrong."""
    if base == lemma:
        return "base:same"
    if base.startswith(lemma):
        suffix = base[len(lemma):]
        if suffix == "s":
            return "base:s"
        if suffix == "es":
            return "base:es"
        if suffix == "ies":
            return "base:ies"
    return "base:other"


def apply_edit(lemma: str, cut: int, append: str) -> str:
    if cut <= 0:
        return lemma + append
    if cut >= len(lemma):
        return append
    return lemma[: len(lemma) - cut] + append


def candidates(lemma: str, base: str) -> list[tuple[str, str]]:
    """Ordered, de-duplicated (edit_id, form) candidates. The deterministic base is always present."""
    seen: set[str] = set()
    result: list[tuple[str, str]] = []

    def push(edit_id: str, form: str) -> None:
        if form and form not in seen:
            seen.add(form)
            result.append((edit_id, form))

    push(base_edit_id(lemma, base), base)
    for edit_id, cut, append in EDITS:
        push(edit_id, apply_edit(lemma, cut, append))
    return result


def oracle(candidates_list: list[tuple[str, str]], accepted: set[str]) -> str | None:
    """Returns the first candidate that is an accepted gold form, or None when the inventory misses."""
    for edit_id, form in candidates_list:
        if form in accepted:
            return edit_id
    return None
