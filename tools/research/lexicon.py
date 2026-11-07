"""Generate the runtime lexicon artifact from the normalized dataset plus the curated core.

Two artefacts can be produced:

* ``--split all``  -> the shipped artifact (protocol P1: frozen lexicon, coverage evaluation)
* ``--split train``-> the P2 experimental artifact (only families assigned to train, for unseen-lemma tests)

Nothing here invents a form. Extracted entries take their forms from the validated dataset; the curated file
only supplies reviewed preferences and countability that the source does not express reliably.
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

from . import dataset

MEMBERSHIP_MAGIC = b"MORPHEX1"
FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASK64 = (1 << 64) - 1

# Must stay in sync with CompoundTable.manExceptions and EnglishInflector.RULES.
MAN_EXCEPTIONS = {
    "human", "german", "roman", "shaman", "talisman", "caiman", "ottoman", "brahman", "atman",
    "liman", "chroman", "hooman", "kundiman", "laraman", "oralman", "abhuman", "metahuman",
    "paleohuman", "parahuman", "dragoman", "firman", "harman", "tarman", "turkman", "touman",
    "tramman", "vataman", "velociman", "zygentoman",
}

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
    """Mirror of the final spelling path in the Java engine, including its two documented backoffs.

    Compaction is only safe when this function still reproduces the attested form: the artifact deliberately
    omits regular words, so a later rule change must not silently alter an omitted word. Keeping this mirror in
    the same order as ``EnglishInflector.RULES`` is what prevents that class of P1 regression.
    """
    low = lemma.lower()
    if low.endswith("woman"):
        return lemma[:-5] + "women"
    if low.endswith("wife"):
        return lemma[:-4] + "wives"
    # Java's MAN rule is lowercase-only: proper names such as Cartman take the default
    # productive suffix, not "cartmen". The mirror must match that guard or a proper
    # name can be selected as a default that the engine would never produce itself.
    if (len(low) >= 6 and low.endswith("man") and not low.endswith("woman")
            and lemma == lemma.lower()
            and low not in MAN_EXCEPTIONS and not low.endswith("human")):
        return lemma[:-3] + "men"
    if len(low) >= 5 and low.endswith("sis"):
        return lemma[:-2] + "es"
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


def form_sort_key(form: str) -> tuple[str, str]:
    """Deterministic form order, independent of the order records appear in the dump."""
    return (form.lower(), form)


def unique_forms(forms: list[str]) -> list[str]:
    return sorted(dict.fromkeys(forms), key=form_sort_key)


def casing(form: str) -> str:
    """Coarse casing shape, matching the runtime's LOWER/UPPER/CAPITALIZED/MIXED distinction."""
    if form.islower():
        return "LOWER"
    if form.isupper():
        return "UPPER"
    if form[:1].isupper() and form[1:].islower():
        return "CAPITALIZED"
    return "MIXED"


def derive_preferences(lemma: str, forms: list[str]) -> tuple[str, str | None, list[str]]:
    """Stable anglicized/classical preferences and remaining accepted alternatives.

    Source records are merged in dump order, so taking ``forms[0]`` made the selected
    default depend on record order. Selection is now a pure function of the form set:

    * an independently attested productive plural (``robots``) beats identity for an
      eligible countable/mixed entry;
    * if the productive form is not attested, an attested overt plural beats identity;
    * an overt form is preferred over identity among the remaining forms;
    * classical forms remain a separate preference selected only by ``Mode.CLASSICAL``.

    Curated rows bypass this function and are authoritative.
    """
    ordered = unique_forms(forms)
    identity = [form for form in ordered if form.lower() == lemma.lower()]
    overt = [form for form in ordered if form.lower() != lemma.lower()]
    classical_forms = [form for form in overt if classical_like(lemma, form)]
    productive = productive_form(lemma)
    lemma_case = casing(lemma)

    if lemma_case == "UPPER":
        # Uppercase entries are case-sensitive in the runtime. Prefer a suffix that preserves
        # the lemma's case (LES -> LESs), then an attested identity (FOSS -> FOSS); otherwise
        # a lower-case source variant would be re-cased as FOSSES/GOES and lose the accepted
        # identity form. Only fall through to ordinary selection when neither exists.
        exact_suffix = next((form for form in ordered if form in (lemma + "s", lemma + "es")), None)
        if exact_suffix is not None:
            anglicized = exact_suffix
        elif identity:
            anglicized = identity[0]
        else:
            candidates = [form for form in overt if casing(form) == "UPPER"] or overt
            anglicized = _preferred_from(candidates, productive, classical_forms) or productive
    else:
        # A merge of case variants can put "Bombs" next to "bombs" under the same lower-case
        # key. Select a form with the lemma's own casing before any case-mismatched variant;
        # otherwise a lowercase head such as bomb is stored as "Bombs" and every compound
        # using it inherits the wrong case (A-bomb -> A-Bombs).
        candidates = [form for form in overt if casing(form) == lemma_case] or overt
        anglicized = _preferred_from(candidates, productive, classical_forms)
        if anglicized is None:
            anglicized = identity[0] if identity else productive

    classical = classical_forms[0] if classical_forms else None
    if classical == anglicized:
        classical = None
    alternatives = [form for form in ordered if form != anglicized and form != classical]
    return anglicized, classical, alternatives


def _preferred_from(candidates: list[str], productive: str,
                    classical_forms: list[str]) -> str | None:
    """Prefer an attested productive form, then a non-classical overt form."""
    attested_productive = next(
        (form for form in candidates if form.lower() == productive.lower()), None)
    if attested_productive is not None:
        return attested_productive
    non_classical = [form for form in candidates if form not in classical_forms]
    if non_classical:
        return non_classical[0]
    return candidates[0] if candidates else None


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
    """True when the engine's final spelling path already produces the preferred form.

    Hyphenated lemmas are never rule-reproducible: the engine deliberately refuses to guess morpheme
    boundaries, so dropping them would turn every compound into the unchanged-input fallback.
    """
    if len(forms) != 1 or "-" in lemma:
        return False
    preferred, classical, alternatives = derive_preferences(lemma, forms)
    return (preferred == productive_form(lemma) and classical is None and not alternatives
            and lemma == lemma.lower())


def membership_hash(value: str) -> int:
    """FNV-1a 64 over UTF-8, matching Membership.hash in the Java runtime."""
    result = FNV_OFFSET
    for byte in value.encode("utf-8"):
        result ^= byte
        result = (result * FNV_PRIME) & MASK64
    return result


def _signed(value: int) -> int:
    return value - (1 << 64) if value >= (1 << 63) else value


def write_membership(path, lemmas) -> None:
    """Write the sorted 64-bit membership table consumed by Membership.load.

    The table is generated from every lemma in the permitted corpus scope, not from the compact rows, so
    regular words omitted by compaction still count as ordinary words for the uppercase policy.
    """
    hashes = sorted({_signed(membership_hash(lemma.lower())) for lemma in lemmas if lemma})
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as handle:
        handle.write(MEMBERSHIP_MAGIC)
        handle.write(struct.pack(">i", len(hashes)))
        for value in hashes:
            handle.write(struct.pack(">q", value))


def build(split_filter: str = "all", selection: str = "compact",
          membership: set[str] | None = None,
          allowed_lemmas: set[str] | None = None,
          splits_path=None) -> list[list[str]]:
    curated = read_curated()
    assignments = {row["lemma"]: row for row in dataset.read_tsv(Path(splits_path or dataset.SPLITS))} \
        if split_filter != "all" else {}

    rows: list[list[str]] = []
    seen: set[str] = set()

    for key, row in sorted(curated.items()):
        if split_filter != "all" and not _split_allowed(row["lemma"], split_filter, assignments):
            continue
        if allowed_lemmas is not None and row["lemma"] not in allowed_lemmas:
            continue
        if membership is not None:
            membership.add(row["lemma"].lower())
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
        if allowed_lemmas is not None and lemma not in allowed_lemmas:
            continue
        if membership is not None:
            membership.add(lemma.lower())
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
                classical = None
                alternatives = sorted(
                    {*alternatives, *(form for form in forms if form.lower() != lemma.lower())},
                    key=form_sort_key,
                )
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


def run(split_filter: str = "all", target=None, selection: str = "compact", splits_path=None) -> dict:
    target = target or dataset.CORE_RESOURCE
    membership: set[str] = set()
    rows = build(split_filter, selection, membership, splits_path=splits_path)
    normalized_sha = dataset.sha256_file(dataset.NORMALIZED) if dataset.NORMALIZED.exists() else "missing"
    write(rows, target, split_filter, selection, normalized_sha)
    target_membership = target.parent / dataset.CORE_MEMBERSHIP.name
    write_membership(target_membership, membership)
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
        write_membership(dataset.MEMBERSHIP, membership)
    manifest = dataset.load_manifest()
    checks = manifest.setdefault("checksums", {})
    checks[dataset.rel(target)] = dataset.sha256_file(target)
    checks[dataset.rel(target_membership)] = dataset.sha256_file(target_membership)
    if split_filter == "all":
        checks[dataset.rel(dataset.ARTIFACT)] = dataset.sha256_file(dataset.ARTIFACT)
        checks[dataset.rel(dataset.MEMBERSHIP)] = dataset.sha256_file(dataset.MEMBERSHIP)
    manifest["artifact"] = {
        "version": dataset.ARTIFACT_VERSION,
        "split": split_filter,
        "selection": selection,
        "entries": len(rows),
        "membership_entries": len(membership),
        "membership_bytes": target_membership.stat().st_size,
        "bytes": target.stat().st_size,
    }
    dataset.save_manifest(manifest)
    return manifest
