"""Leakage-safe, deterministic train/dev/test splits.

The unit of splitting is the lemma family, never an individual spelling variant:

* casing variants are grouped by the lower-cased lemma,
* hyphenated or spaced compounds are grouped with their head lemma,
* every record of one lemma (all etymologies/senses) is in one split.

Assignments are a pure function of ``SPLIT_VERSION`` and the family key, so the split is reproducible without
storing a random seed or the dataset order.
"""

from __future__ import annotations

import hashlib

from . import dataset

TRAIN = "train"
DEV = "dev"
TEST = "test"


def family_key(lemma: str) -> str:
    low = lemma.strip().lower()
    for separator in ("-", " "):
        if separator in low:
            head = low.split(separator, 1)[0]
            if head:
                return head
    return low


def split_for(family: str) -> str:
    digest = hashlib.blake2b(f"{dataset.SPLIT_VERSION}:{family}".encode("utf-8"), digest_size=8).digest()
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


def assign(lemmas: list[str]) -> list[list[str]]:
    rows: list[list[str]] = []
    for lemma in sorted(set(lemmas), key=lambda value: (value.lower(), value)):
        family = family_key(lemma)
        rows.append([lemma, family, split_for(family), record_bucket(lemma)])
    return rows


def run() -> dict:
    rows = dataset.read_tsv(dataset.NORMALIZED)
    lemmas = [row["lemma"] for row in rows]
    assignments = assign(lemmas)
    dataset.write_tsv(
        dataset.SPLITS,
        dataset.SPLIT_COLUMNS,
        assignments,
        comments=[
            f"family-aware split {dataset.SPLIT_VERSION}",
            "family = lower-cased compound head, otherwise the lower-cased lemma",
            "hash(version:family) mod 100 -> <70 train, <85 dev, else test",
        ],
    )
    counts: dict[str, int] = {}
    for _, _, split, _ in assignments:
        counts[split] = counts.get(split, 0) + 1
    manifest = dataset.load_manifest()
    manifest.setdefault("checksums", {})[dataset.rel(dataset.SPLITS)] = dataset.sha256_file(dataset.SPLITS)
    manifest["split"] = {"version": dataset.SPLIT_VERSION, "lemmas": counts}
    dataset.save_manifest(manifest)
    return manifest


def load() -> dict[str, dict[str, str]]:
    return {row["lemma"]: row for row in dataset.read_tsv(dataset.SPLITS)}
