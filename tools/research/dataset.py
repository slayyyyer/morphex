"""Shared paths, checksums, TSV helpers and manifest handling."""

from __future__ import annotations

import gzip
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data"
RAW = DATA / "raw"
DERIVED = DATA / "derived"
CURATED = DATA / "curated"
REGRESSION = DATA / "regression"
RESULTS = ROOT / "results"

CORE_RESOURCE = ROOT / "inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v1.tsv"

DATASET_VERSION = "nouns-v1"
EXTRACTION_VERSION = "extract-v1"
ARTIFACT_VERSION = "lexicon-v1"
SPLIT_VERSION = "split-v1"

RAW_NOUNS = RAW / "kaikki-english-nouns.jsonl.gz"
NORMALIZED = DERIVED / "nouns-v1.tsv.gz"
QUARANTINE = DERIVED / "quarantine-v1.tsv"
MANIFEST = DERIVED / "manifest-v1.json"
ARTIFACT = DERIVED / "lexicon-v1.tsv"
SPLITS = DERIVED / "splits-v1.tsv"

# Pinned snapshot metadata. The checksum is verified when the raw dump is present.
SOURCE = {
    "name": "Kaikki.org English dictionary, by part of speech: noun",
    "url": "https://kaikki.org/dictionary/English/pos-noun/kaikki.org-dictionary-English-by-pos-noun.jsonl.gz",
    "sha256": "9a47500cabdb5d6c918a4b16abeefb55d32e2c1a2f29d260080c02329278ab43",
    "bytes": 298543101,
    "last_modified": "Sat, 31 Oct 2026 03:12:47 GMT",
    "via": "Wiktextract (https://github.com/tatuylonen/wiktextract)",
    "wiktionary_license": "CC BY-SA 3.0",
}

NORMALIZED_COLUMNS = [
    "lemma",
    "countability",
    "forms",
    "restricted",
    "invariant",
    "plural_only",
    "head_expansion",
    "record_id",
    "provenance",
]

SPLIT_COLUMNS = ["lemma", "family", "split", "bucket"]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _open_write(path: Path):
    if str(path).endswith(".gz"):
        return gzip.open(path, "wt", encoding="utf-8", newline="\n")
    return path.open("w", encoding="utf-8", newline="\n")


def _open_read(path: Path):
    if str(path).endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", newline="")
    return path.open("r", encoding="utf-8", newline="")


def write_tsv(path: Path, header: list[str], rows: list[list[str]], comments: list[str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with _open_write(path) as handle:
        for comment in comments or []:
            handle.write("# " + comment.replace("\n", " ") + "\n")
        handle.write("\t".join(header) + "\n")
        for row in rows:
            handle.write("\t".join(cell.replace("\t", " ") for cell in row) + "\n")


def read_tsv(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with _open_read(path) as handle:
        header: list[str] | None = None
        for line in handle:
            line = line.rstrip("\n").rstrip("\r")
            if not line or line.startswith("#"):
                continue
            columns = line.split("\t")
            if header is None:
                header = columns
                continue
            if len(columns) != len(header):
                raise ValueError(f"{path}: expected {len(header)} columns, found {len(columns)}: {line[:120]}")
            rows.append(dict(zip(header, columns)))
    return rows


def load_manifest() -> dict:
    if not MANIFEST.exists():
        return {}
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def save_manifest(manifest: dict) -> None:
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def require_raw() -> Path:
    if not RAW_NOUNS.exists():
        raise SystemExit(
            f"raw snapshot missing: {RAW_NOUNS}\n"
            "Run `python tools/research/cli.py fetch` (network, explicit) or place the pinned dump there."
        )
    return RAW_NOUNS


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT)).replace(os.sep, "/")
    except ValueError:
        return str(path)
