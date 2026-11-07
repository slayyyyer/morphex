"""Leakage-safe, deterministic train/dev/test splits.

The unit of splitting is the lemma family, never an individual spelling variant:

* casing variants are grouped by the lower-cased lemma,
* hyphenated or spaced compounds are grouped with their first head token,
* every record of one lemma (all etymologies/senses) is in one split.

The default ``split-v1`` family key is deliberately lexical. ``split-v2`` adds a
conservative derivational-stem key for the stricter morphological-family run: productive
nominal suffixes are stripped (with a small minimum stem length), so ``teacher``,
``teaching`` and ``teach`` share a split instead of leaking across train/test. Both modes
are pure functions of the version and family key, so no random seed or dataset order is
needed for reproduction.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

from . import dataset

TRAIN = "train"
DEV = "dev"
TEST = "test"

SPLIT_VERSION = dataset.SPLIT_VERSION
STRICT_VERSION = "split-v2"
STRICT_SPLITS = dataset.DERIVED / "splits-v2.tsv"

# Longest suffix first. Values are the minimum remaining stem length; short suffixes are
# allowed to leave a three-letter stem so actor/act and artist/art stay together, while
# productive nominal suffixes require a four-letter stem.
STRICT_SUFFIXES = (
    ("ization", 4), ("isation", 4), ("ification", 4), ("ability", 4), ("ibility", 4),
    ("ivity", 4), ("ality", 4), ("ation", 4), ("ition", 4), ("ician", 4), ("ian", 4),
    ("ion", 4), ("ment", 4), ("ness", 4), ("ity", 4), ("ance", 4), ("ence", 4),
    ("ship", 4), ("hood", 4), ("dom", 4), ("ism", 3), ("ist", 3), ("er", 4),
    ("or", 3), ("ing", 3), ("age", 4), ("ery", 4), ("ry", 4), ("ical", 4),
    ("al", 4), ("ial", 4), ("ic", 4), ("ous", 4), ("ive", 4), ("able", 4),
    ("ible", 4), ("ful", 3), ("less", 4), ("ly", 3), ("ed", 3),
)
STRICT_SUFFIXES = tuple(sorted(STRICT_SUFFIXES, key=lambda item: -len(item[0])))


def family_key(lemma: str) -> str:
    low = lemma.strip().lower()
    for separator in ("-", " "):
        if separator in low:
            head = low.split(separator, 1)[0]
            if head:
                return head
    return low


def _normalize_stem(root: str, *, collapse_double: bool) -> str:
    if len(root) >= 5 and root.endswith("e"):
        root = root[:-1]
    if collapse_double and len(root) >= 4 and root[-1] == root[-2] and root[-1] not in "aeiou":
        root = root[:-1]
    if len(root) >= 5 and root.endswith("y"):
        root = root[:-1] + "i"
    return root


def strict_family_key(lemma: str) -> str:
    """Group conservative derivational relatives for the strict holdout.

    This is intentionally an approximation, not a lemmatizer: it strips at most two
    productive nominal suffixes, then normalizes a final ``e``/``y`` and doubled
    consonant. The minimum stem lengths are the guard against collapsing unrelated short
    words; compound inputs retain the existing first-token rule.
    """
    low = family_key(lemma)
    root = low
    for _ in range(2):
        changed = False
        for suffix, minimum in STRICT_SUFFIXES:
            if root.endswith(suffix) and len(root) - len(suffix) >= minimum:
                root = _normalize_stem(root[:-len(suffix)], collapse_double=True)
                changed = True
                break
        if not changed:
            break
    return _normalize_stem(root, collapse_double=False)


def split_for(family: str, version: str = SPLIT_VERSION) -> str:
    digest = hashlib.blake2b(f"{version}:{family}".encode("utf-8"), digest_size=8).digest()
    bucket = int.from_bytes(digest, "big") % 100
    if bucket < 70:
        return TRAIN
    if bucket < 85:
        return DEV
    return TEST


def record_bucket(lemma: str) -> str:
    if "-" in lemma or " " in lemma:
        return "compound"
    if lemma.isupper() and 2 <= len(lemma) <= 6:
        return "initialism"
    if any(ord(ch) > 127 for ch in lemma):
        return "unicode"
    return "simple"


def assign(lemmas: list[str], mode: str = "lexical") -> list[list[str]]:
    if mode not in ("lexical", "strict"):
        raise ValueError(f"unknown split mode {mode!r}")
    version = STRICT_VERSION if mode == "strict" else SPLIT_VERSION
    key = strict_family_key if mode == "strict" else family_key
    rows: list[list[str]] = []
    for lemma in sorted(set(lemmas), key=lambda value: (value.lower(), value)):
        family = key(lemma)
        rows.append([lemma, family, split_for(family, version), record_bucket(lemma)])
    return rows


def run(mode: str = "lexical") -> dict:
    if mode not in ("lexical", "strict"):
        raise ValueError(f"unknown split mode {mode!r}")
    target = STRICT_SPLITS if mode == "strict" else dataset.SPLITS
    rows = dataset.read_tsv(dataset.NORMALIZED)
    lemmas = [row["lemma"] for row in rows]
    assignments = assign(lemmas, mode=mode)
    dataset.write_tsv(
        target,
        dataset.SPLIT_COLUMNS,
        assignments,
        comments=[
            f"family-aware split {STRICT_VERSION if mode == 'strict' else SPLIT_VERSION}",
            "family = lower-cased compound first token, otherwise the lower-cased lemma",
            "strict mode additionally strips productive derivational suffixes",
            "hash(version:family) mod 100 -> <70 train, <85 dev, else test",
        ],
    )
    counts: dict[str, int] = {}
    for _, _, split, _ in assignments:
        counts[split] = counts.get(split, 0) + 1
    manifest = dataset.load_manifest()
    manifest.setdefault("checksums", {})[dataset.rel(target)] = dataset.sha256_file(target)
    entry = {"version": STRICT_VERSION if mode == "strict" else SPLIT_VERSION, "lemmas": counts}
    if mode == "strict":
        manifest["strict_split"] = entry
    else:
        manifest["split"] = entry
    dataset.save_manifest(manifest)
    return manifest


def load(path=None) -> dict[str, dict[str, str]]:
    return {row["lemma"]: row for row in dataset.read_tsv(Path(path or dataset.SPLITS))}
