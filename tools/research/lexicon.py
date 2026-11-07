"""Generate the runtime lexicon artifact from the normalized dataset plus the curated core.

Two artefacts can be produced:

* ``--split all``  -> the shipped artifact (protocol P1: frozen lexicon, coverage evaluation)
* ``--split train``-> the P2 experimental artifact (only families assigned to train, for unseen-lemma tests)

Nothing here invents a form. Extracted entries take their forms from the validated dataset; the curated file
only supplies reviewed preferences and countability that the source does not express reliably.
"""

from __future__ import annotations

import sys

from . import dataset

FLAGS_ORDER = "INCA"
CLASSICAL_SUFFIXES = (
    (("us",), ("i",)),
    (("um",), ("a",)),
    (("on",), ("a",)),
    (("a",), ("ae",)),
    (("ix", "ex"), ("ices",)),
    (("is",), ("es",)),
)


def productive_form(lemma: str) -> str:
    """Mirror of the three productive rules in the Java engine (documented in docs/compatibility.md)."""
    low = lemma.lower()
    if low.endswith(("s", "x", "z", "ch", "sh")):
        return lemma + "es"
    if len(low) >= 2 and low.endswith("y"):
        before = low[-2]
        if before not in "aeiou" or (before == "u" and len(low) >= 3 and low[-3] == "q"):
            return lemma[:-1] + "ies"
    return lemma + "s"


def classical_like(lemma: str, form: str) -> bool:
    low_lemma = lemma.lower()
    low_form = form.lower()
    for lemma_endings, form_endings in CLASSICAL_SUFFIXES:
        if low_lemma.endswith(lemma_endings) and low_form.endswith(form_endings):
            if "is" in lemma_endings and len(low_form) != len(low_lemma):
                continue
            if "a" in lemma_endings and not low_lemma.endswith("a"):
                continue
            return True
    return False


def derive_preferences(lemma: str, forms: list[str]) -> tuple[str, str | None, list[str]]:
    """Anglicized preference, classical preference (or None), remaining accepted alternatives."""
    classical_forms = [form for form in forms if form.lower() != lemma.lower() and classical_like(lemma, form)]
    if not classical_forms:
        return forms[0], None, forms[1:]
    anglicized = productive_form(lemma)
    if anglicized.lower() not in {form.lower() for form in forms}:
        anglicized = next((form for form in forms if form not in classical_forms), forms[0])
    classical = classical_forms[0]
    alternatives = [form for form in forms if form != anglicized and form != classical]
    if classical == anglicized:
        classical = None
    return anglicized, classical, alternatives


def _split_allowed(lemma: str, split_filter: str, assignments: dict[str, dict[str, str]]) -> bool:
    if split_filter == "all":
        return True
    row = assignments.get(lemma)
    return bool(row) and row["split"] == split_filter


def read_curated(path=None) -> dict[str, dict[str, str]]:
    path = path or (dataset.CURATED / "core-nouns.tsv")
    rows = dataset.read_tsv(path)
    curated: dict[str, dict[str, str]] = {}
    for row in rows:
        key = row["lemma"].lower()
        if key in curated:
            raise SystemExit(f"curated lexicon has duplicate key {key!r}")
        curated[key] = row
    return curated


def is_rule_reproducible(lemma: str, forms: list[str]) -> bool:
    """True when the engine's productive rules already produce the preferred form.

    Hyphenated lemmas are never rule-reproducible: the engine deliberately refuses to guess morpheme
    boundaries, so dropping them would turn every compound into the unchanged-input fallback.
    """
    if len(forms) != 1 or "-" in lemma:
        return False
    preferred, classical, alternatives = derive_preferences(lemma, forms)
    return (preferred == productive_form(lemma) and classical is None and not alternatives
            and lemma == lemma.lower())


def build(split_filter: str = "all", selection: str = "compact") -> list[list[str]]:
    curated = read_curated()
    assignments = {row["lemma"]: row for row in dataset.read_tsv(dataset.SPLITS)} if split_filter != "all" else {}

    rows: list[list[str]] = []
    seen: set[str] = set()

    for key, row in sorted(curated.items()):
        if split_filter != "all" and not _split_allowed(row["lemma"], split_filter, assignments):
            continue
        rows.append([
            row["lemma"],
            row["plural"],
            row["classical"],
            row["alternatives"],
            row["countability"],
            row["flags"],
            row["source"],
        ])
        seen.add(key)

    extracted = dataset.read_tsv(dataset.NORMALIZED)
    skipped = 0
    excluded = 0
    for record in extracted:
        lemma = record["lemma"]
        key = lemma.lower()
        if key in seen:
            continue
        # Space-containing lemmas stay out: they belong to compound handling or the
        # documented fallback, never to whole-word lexicon lookup. Length is not capped:
        # the longest validated single-token lemma is 53 characters and the packed
        # columns impose no 40-character limit.
        if " " in lemma:
            skipped += 1
            continue
        if split_filter != "all" and not _split_allowed(lemma, split_filter, assignments):
            continue
        forms = [form for form in record["forms"].split(",") if form]
        countability = record["countability"]
        invariant = record["invariant"] == "1"
        plural_only = record["plural_only"] == "1"

        flags = []
        if invariant:
            flags.append("I")
        initialism = (
            lemma.isupper()
            and 1 <= len(lemma) <= 10
            and any(form in (lemma + "s", lemma + "es") for form in forms)
        )
        if initialism:
            flags.append("N")
        if lemma != lemma.lower() and not lemma.islower():
            flags.append("C")

        if selection == "compact" and countability not in ("MASS", "PLURAL_ONLY") and not invariant \
                and not initialism and is_rule_reproducible(lemma, forms):
            excluded += 1
            continue
        if plural_only or countability in ("MASS", "PLURAL_ONLY"):
            plural = lemma
            classical = "-"
            alternatives = "-"
        elif not forms:
            skipped += 1
            continue
        else:
            plural, classical, alternatives = derive_preferences(lemma, forms)
            if invariant:
                plural = lemma
                alternatives = [form for form in forms if form.lower() != lemma.lower()] + alternatives
        if not plural:
            skipped += 1
            continue

        flags_text = ",".join(flag for flag in FLAGS_ORDER if flag in flags) or "-"
        rows.append([
            lemma,
            plural,
            classical or "-",
            ",".join(dict.fromkeys(alternatives)) or "-",
            countability if countability in ("COUNTABLE", "MASS", "MIXED", "PLURAL_ONLY") else "COUNTABLE",
            flags_text,
            "kaikki:en-noun",
        ])
        seen.add(key)

    print(f"artifact: selection={selection} entries={len(rows)} excluded_rule_reproducible={excluded} "
          f"skipped={skipped}", file=sys.stderr)
    return rows


def write(rows: list[list[str]], target, split_filter: str, selection: str, dataset_sha: str) -> None:
    dataset.write_tsv(
        target,
        ["lemma", "plural", "classical", "alternatives", "countability", "flags", "source"],
        rows,
        comments=[
            f"english-inflector runtime lexicon {dataset.ARTIFACT_VERSION} "
            f"(split={split_filter}, selection={selection})",
            f"generated by tools/research/lexicon.py from {dataset.rel(dataset.NORMALIZED)}",
            f"normalized dataset sha256 {dataset_sha}",
            f"source dump sha256 {dataset.SOURCE['sha256']}",
            "code license MIT; this data file is derived from Wiktionary via Kaikki and is CC BY-SA 3.0",
            "see data/LICENSE-DATA.md and NOTICE",
        ],
    )


def run(split_filter: str = "all", target=None, selection: str = "compact") -> dict:
    target = target or dataset.CORE_RESOURCE
    rows = build(split_filter, selection)
    normalized_sha = dataset.sha256_file(dataset.NORMALIZED) if dataset.NORMALIZED.exists() else "missing"
    write(rows, target, split_filter, selection, normalized_sha)
    if split_filter == "all":
        dataset.write_tsv(
            dataset.ARTIFACT,
            ["lemma", "plural", "classical", "alternatives", "countability", "flags", "source"],
            rows,
            comments=[
                f"english-inflector artifact {dataset.ARTIFACT_VERSION}",
                f"runtime copy: {dataset.rel(dataset.CORE_RESOURCE)}",
            ],
        )
    manifest = dataset.load_manifest()
    checks = manifest.setdefault("checksums", {})
    checks[dataset.rel(target)] = dataset.sha256_file(target)
    if split_filter == "all":
        checks[dataset.rel(dataset.ARTIFACT)] = dataset.sha256_file(dataset.ARTIFACT)
    manifest["artifact"] = {
        "version": dataset.ARTIFACT_VERSION,
        "split": split_filter,
        "selection": selection,
        "entries": len(rows),
        "bytes": target.stat().st_size,
    }
    dataset.save_manifest(manifest)
    return manifest
