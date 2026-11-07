"""Controlled candidate inventory and the single renderer used by both learned selectors.

A candidate is a (cut, append) edit applied to the caller's lemma, plus the deterministic base result. The
inventory is fixed before training and is not expanded after seeing test results. Both the non-neural and the
neural selector score exactly these candidates with exactly this renderer.

The base result and a rule that happens to render the same form are kept as separate candidates: the base
identity is what the routing decision keeps or replaces, while the rule identity is the supervision signal for
the corrective transformation. De-duplicating them, as the earlier experiment did, hid the transformation on
records where the lexical base was already correct and taught the selector to keep it.
"""

from __future__ import annotations

from dataclasses import dataclass

# edit id, cut, append, required lower-case suffix (None means shape-only)
EDITS: list[tuple[str, int, str, str | None]] = [
    ("s", 0, "s", None),
    ("es", 0, "es", None),
    ("ies", 1, "ies", "y"),
    ("ves", 1, "ves", "f"),
    ("fe->ves", 2, "ves", "fe"),
    ("oes", 1, "oes", "o"),
    ("us->i", 2, "i", "us"),
    ("um->a", 2, "a", "um"),
    ("a->ae", 1, "ae", "a"),
    ("ix->ices", 2, "ices", "ix"),
    ("is->es", 2, "es", "is"),
    ("man->men", 3, "men", "man"),
    ("child->ren", 0, "ren", "child"),
    ("mouse->ice", 4, "ice", "mouse"),
    ("person->people", 5, "eople", "person"),
    ("quiz->zzes", 1, "zzes", "z"),
    ("lemma", 0, "", None),
]

BASE_EDIT_IDS = [
    "base:same", "base:s", "base:es", "base:ies", "base:ves", "base:oes",
    "base:us-i", "base:um-a", "base:ix-ices", "base:is-es", "base:man-men", "base:other",
]

EDIT_IDS = [edit[0] for edit in EDITS] + BASE_EDIT_IDS + ["initialism-s", "initialism-es"]
EDIT_INDEX = {edit_id: index for index, edit_id in enumerate(EDIT_IDS)}


@dataclass(frozen=True)
class Candidate:
    edit_id: str
    form: str
    cut: int
    append: str
    is_base: bool = False
    origin: str = "rule"

    def __iter__(self):
        # Keeps the historical (edit_id, form) unpacking used by the learners working.
        yield self.edit_id
        yield self.form


def base_edit_id(lemma: str, base: str) -> str:
    """Describe what the deterministic base did, including edits that are not a plain prefix append."""
    if base == lemma:
        return "base:same"
    lb = lemma.lower()
    bb = base.lower()
    if bb.startswith(lb):
        suffix = bb[len(lb):]
        mapping = {"s": "base:s", "es": "base:es", "ies": "base:ies", "ves": "base:ves", "oes": "base:oes"}
        if suffix in mapping:
            return mapping[suffix]
    if len(lb) >= 2 and bb == lb[:-1] + "ies":
        return "base:ies"
    if len(lb) >= 2 and bb == lb[:-2] + "i" and lb.endswith("us"):
        return "base:us-i"
    if len(lb) >= 2 and bb == lb[:-2] + "a" and lb.endswith("um"):
        return "base:um-a"
    if len(lb) >= 2 and bb == lb[:-2] + "ices" and lb.endswith("ix"):
        return "base:ix-ices"
    if len(lb) >= 2 and bb == lb[:-2] + "es" and lb.endswith("is"):
        return "base:is-es"
    if len(lb) >= 3 and bb == lb[:-3] + "men" and lb.endswith("man"):
        return "base:man-men"
    return "base:other"


def apply_edit(lemma: str, cut: int, append: str) -> str:
    if cut <= 0:
        return lemma + append
    if cut >= len(lemma):
        return append
    return lemma[: len(lemma) - cut] + append


def candidates(lemma: str, base: str, base_source: str = "") -> list[Candidate]:
    """Ordered candidates. The base identity is first and is never de-duplicated against a rule."""
    lower = lemma.lower()
    result: list[Candidate] = [
        Candidate(base_edit_id(lemma, base), base, 0, "", True, "base")
    ]
    seen: set[tuple[str, str]] = {(result[0].edit_id, base)}

    def push(edit_id: str, cut: int, append: str, origin: str = "rule") -> None:
        form = apply_edit(lemma, cut, append)
        key = (edit_id, form)
        if key in seen:
            return
        seen.add(key)
        result.append(Candidate(edit_id, form, cut, append, False, origin))

    for edit_id, cut, append, requires in EDITS:
        if requires is not None and not lower.endswith(requires):
            continue
        push(edit_id, cut, append)

    # Unknown upper-case tokens need their own rendering candidates: the Java policy appends a
    # lower-case suffix (APIs) while the ordinary upper-case rendering appends an upper-case one.
    if lemma.isupper() and 2 <= len(lemma) <= 8:
        push("initialism-s", 0, "s", "initialism")
        push("initialism-es", 0, "es", "initialism")
    return result


def oracle(candidates_list: list[Candidate], accepted: set[str]) -> str | None:
    """Returns the first candidate that is an accepted gold form, or None when the inventory misses."""
    for candidate in candidates_list:
        if candidate.form in accepted:
            return candidate.edit_id
    return None
