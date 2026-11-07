"""Turn the pinned Kaikki/Wiktextract noun dump into a validated normalized dataset.

Design notes
------------
* Source templates/expansions are preserved in their own column and are never treated as resolved forms.
* Invalid or unsupported records are quarantined with a reason; they are never relabelled from a prediction.
* Wiktionary's ``++`` (consonant doubling) and ``p`` (plural only) directives are data, not expected strings,
  so ``quiz`` gets ``quizzes`` and ``scissors`` is classified plural-only instead of producing a literal ``p``.
"""

from __future__ import annotations

import gzip
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field

from . import dataset

RESTRICTED_TAGS = {
    "obsolete",
    "archaic",
    "nonstandard",
    "rare",
    "informal",
    "slang",
    "dialectal",
    "poetic",
    "humorous",
    "childish",
    "colloquial",
    "pronunciation-spelling",
    "misspelling",
    "uncommon",
    "dated",
}

TEMPLATE_JUNK = ("{{", "}}", "++", "|", "<", ">", "=")

_SAFE_FORM = re.compile(r"^[^\W\d_][^\W\d_'’]*$", re.UNICODE)


@dataclass
class Record:
    lemma: str
    forms: list[str] = field(default_factory=list)
    restricted: list[tuple[str, tuple[str, ...]]] = field(default_factory=list)
    countability: str = "UNKNOWN"
    invariant: bool = False
    plural_only: bool = False
    head_expansion: str = ""
    record_ids: list[str] = field(default_factory=list)
    senses: int = 0


def is_latin_letter(ch: str) -> bool:
    if not ch.isalpha():
        return False
    name = unicodedata.name(ch, "")
    return name.startswith("LATIN")


def valid_surface(word: str, allow_hyphen: bool = True, allow_space: bool = False) -> bool:
    if not word or len(word) > 60:
        return False
    seen_letter = False
    for ch in word:
        if ch.isalpha():
            if not is_latin_letter(ch):
                return False
            seen_letter = True
        elif ch in ("-",) and allow_hyphen:
            continue
        elif ch == " " and allow_space:
            continue
        else:
            return False
    return seen_letter


def valid_form(form: str) -> bool:
    if not form or form == "-" or len(form) > 60:
        return False
    if any(token in form for token in TEMPLATE_JUNK):
        return False
    if form.lower() in ("p", "s", "es", "pl", "plural"):
        return False
    return valid_surface(form)


def classify_countability(expansion: str, sense_tags: list[list[str]],
                          has_attested_plural: bool = False) -> str:
    low = expansion.lower()
    if "countable and uncountable" in low:
        return "MIXED"
    # Strip "uncountable" before testing for "countable": the former contains the latter.
    stripped = low.replace("uncountable", "")
    if "uncountable" in low and "countable" not in stripped:
        # "usually uncountable" admits countable use; with an attested plural the
        # countable sense wins (MIXED pluralizes in that sense, like water). A bare
        # "uncountable" head, or no attested plural at all, stays MASS.
        if "usually uncountable" in low and has_attested_plural:
            return "MIXED"
        return "MASS"
    if "countable" in stripped:
        return "COUNTABLE"
    has_uncountable = any("uncountable" in tags for tags in sense_tags)
    has_countable = any("countable" in tags for tags in sense_tags)
    if has_uncountable and has_countable:
        return "MIXED"
    if has_uncountable:
        # A subordinate sense marked uncountable (often archaic or figurative) must not
        # outweigh attested plural forms: "account (plural accounts)" is countable even
        # though one archaic sense is uncountable. Without an attested plural the
        # mass reading stands.
        return "MASS" if not has_attested_plural else "COUNTABLE"
    if has_countable:
        return "COUNTABLE"
    return "COUNTABLE"


def head_marks_plural_only(expansion: str, args: dict) -> bool:
    """True when one head template describes the whole lemma as plural-only.

    A record may carry several templates (for example a plain noun-form head plus a
    reviewed ``... pl (plural only)`` head). Every template is evidence; inspecting only
    the first one dropped legitimate plural-only headwords such as *times* and *skins*.
    """
    low = (expansion or "").lower()
    if "plural only" in low or "plural-only" in low or re.search(r"\bpl\b", low):
        return True
    args = args or {}
    return any(str(args.get(key, "")).strip().lower() == "p" for key in ("1", "g"))


def is_plural_only(heads: list[dict], sense_tags: list[list[str]],
                   has_attested_plural: bool) -> bool:
    """Whole-lemma plural-only evidence, never a single subordinate sense.

    A sense tagged ``plural-only`` (for example the "expressions in general" sense of
    *accent*, or the drug-equipment sense of *work*) restricts that sense. It does not
    make the lemma plural-only while an ordinary plural is attested. Head-level evidence
    always wins; sense-level evidence is used only when no non-lemma plural exists.
    """
    if any(head_marks_plural_only(head.get("expansion", ""), head.get("args") or {})
           for head in heads):
        return True
    if has_attested_plural:
        return False
    return any("plural-only" in tags for tags in sense_tags)


def reconcile_countability(votes: list[str], forms: list[str], lemma: str,
                           all_plural_only: bool) -> str:
    """One spelling, several etymologies: any etymology licensing a plural wins.

    A spelling-keyed pluralizer cannot do sense disambiguation (cf. die → dice), so
    if any entry attests a non-lemma plural the lemma pluralizes; the label records
    whether mass-only uses were also seen (MIXED) or not (COUNTABLE). With no
    attested plural the identity label stands, preferring an explicit plural-only
    marking over a bare mass reading. This replaces first-in-dump-order wins, which
    made the label depend on raw record order (e.g. plate).
    """
    if all_plural_only:
        return "PLURAL_ONLY"
    has_countable = any(v in ("COUNTABLE", "MIXED") for v in votes)
    has_mass = any(v in ("MASS", "PLURAL_ONLY") for v in votes)
    has_plural_form = any(f.lower() != lemma.lower() for f in forms)
    if has_countable and has_plural_form:
        if has_mass or "MIXED" in votes:
            return "MIXED"
        return "COUNTABLE"
    if any(v == "PLURAL_ONLY" for v in votes):
        return "PLURAL_ONLY"
    if has_mass:
        return "MASS"
    return "COUNTABLE"


def normalize(obj: dict) -> tuple[Record | None, str]:
    if obj.get("pos") != "noun":
        return None, "not-noun"
    lemma = obj.get("word") or ""
    if not lemma:
        return None, "empty-lemma"
    if not valid_surface(lemma, allow_hyphen=True, allow_space=False):
        return None, "lemma-surface"

    heads = obj.get("head_templates") or []
    head = heads[0] if heads else {}
    expansion = head.get("expansion") or ""
    args = head.get("args") or {}
    sense_tags = [list(sense.get("tags") or []) for sense in obj.get("senses") or []]

    accepted: list[str] = []
    restricted: list[tuple[str, tuple[str, ...]]] = []
    for form in obj.get("forms") or []:
        surface = form.get("form") or ""
        tags = [str(t).lower() for t in (form.get("tags") or [])]
        raw_tags = [str(t).lower() for t in (form.get("raw_tags") or [])]
        all_tags = tags + raw_tags
        if "plural" not in all_tags or "singular" in all_tags:
            continue
        if not valid_form(surface):
            if surface:
                restricted.append((surface, ("invalid-surface",)))
            continue
        if any(tag in RESTRICTED_TAGS for tag in all_tags):
            restricted.append((surface, tuple(sorted(set(all_tags)))))
            continue
        if surface not in accepted:
            accepted.append(surface)

    plural_only = is_plural_only(heads, sense_tags, has_attested_plural=bool(accepted))
    countability = classify_countability(expansion, sense_tags,
                                         has_attested_plural=bool(accepted))

    if plural_only:
        accepted = [lemma]
    elif not accepted:
        # Attested plural missing. Mass nouns are still usable; everything else is unattested for gold.
        if countability == "MASS":
            accepted = [lemma]
        else:
            return None, "no-attested-plural"

    invariant = len(accepted) == 1 and accepted[0].lower() == lemma.lower()

    record_ids = [sense["id"] for sense in (obj.get("senses") or []) if sense.get("id")]
    record = Record(
        lemma=lemma,
        forms=accepted,
        restricted=restricted,
        countability="PLURAL_ONLY" if plural_only else countability,
        invariant=invariant,
        plural_only=plural_only,
        head_expansion=expansion,
        record_ids=record_ids[:3],
        senses=len(sense_tags),
    )
    return record, ""


def merge(records: dict[str, Record]) -> list[Record]:
    return [records[key] for key in sorted(records, key=lambda w: (w.lower(), w))]


def run(limit: int | None = None) -> dict:
    source = dataset.require_raw()
    merged: dict[str, Record] = {}
    votes: dict[str, list[str]] = {}
    quarantine: Counter[str] = Counter()
    quarantine_samples: list[tuple[str, str, str]] = []
    raw_records = 0
    kept_records = 0

    with gzip.open(source, "rt", encoding="utf-8") as handle:
        for line in handle:
            raw_records += 1
            if limit and raw_records > limit:
                break
            if raw_records % 100_000 == 0:
                print(f"  read {raw_records:,} raw records", file=sys.stderr)
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                quarantine["invalid-json"] += 1
                continue
            record, reason = normalize(obj)
            if record is None:
                quarantine[reason] += 1
                if len(quarantine_samples) < 40:
                    quarantine_samples.append((obj.get("word", ""), reason, (obj.get("head_templates") or [{}])[0].get("expansion", "")))
                continue
            kept_records += 1
            key = record.lemma.lower()
            existing = merged.get(key)
            if existing is None:
                merged[key] = record
                votes[key] = [record.countability]
            else:
                for form in record.forms:
                    if form not in existing.forms:
                        existing.forms.append(form)
                existing.restricted.extend(record.restricted)
                existing.record_ids.extend(rid for rid in record.record_ids if rid not in existing.record_ids)
                existing.senses += record.senses
                existing.plural_only = existing.plural_only and record.plural_only
                existing.invariant = existing.invariant and record.invariant
                votes[key].append(record.countability)

    for key, record in merged.items():
        record.countability = reconcile_countability(
            votes[key], record.forms, record.lemma, record.plural_only)

    records = merge(merged)
    rows = []
    for record in records:
        restricted = ";".join(f"{form}<{','.join(tags)}>" for form, tags in record.restricted)
        rows.append([
            record.lemma,
            record.countability,
            ",".join(record.forms),
            restricted,
            "1" if record.invariant else "0",
            "1" if record.plural_only else "0",
            record.head_expansion,
            ",".join(record.record_ids),
            f"kaikki-en-noun/{dataset.EXTRACTION_VERSION}",
        ])

    dataset.write_tsv(
        dataset.NORMALIZED,
        dataset.NORMALIZED_COLUMNS,
        rows,
        comments=[
            f"english-inflector normalized noun dataset {dataset.DATASET_VERSION}",
            f"extractor {dataset.EXTRACTION_VERSION}",
            f"source {dataset.SOURCE['url']}",
            f"source sha256 {dataset.SOURCE['sha256']}",
            "license: Wiktionary-derived data, CC BY-SA 3.0 (see data/LICENSE-DATA.md)",
            "forms are validated accepted plurals; restricted forms are kept separately and never used as gold",
        ],
    )

    quarantine_rows = [[word, reason, expansion] for word, reason, expansion in quarantine_samples]
    dataset.write_tsv(
        dataset.QUARANTINE,
        ["lemma", "reason", "head_expansion"],
        quarantine_rows,
        comments=["sample of quarantined records (reason counts are in manifest-v1.json)"],
    )

    manifest = dataset.load_manifest()
    manifest.update(
        {
            "dataset_version": dataset.DATASET_VERSION,
            "source": dataset.SOURCE,
            "extraction": {
                "version": dataset.EXTRACTION_VERSION,
                "raw_records": raw_records,
                "kept_records": kept_records,
                "unique_lemmas": len(records),
                "quarantine_reasons": dict(sorted(quarantine.items())),
            },
            "checksums": {
                **(manifest.get("checksums") or {}),
                dataset.rel(dataset.NORMALIZED): dataset.sha256_file(dataset.NORMALIZED),
            },
            "countability": dict(sorted(Counter(record.countability for record in records).items())),
        }
    )
    dataset.save_manifest(manifest)
    print(f"extracted {len(records):,} lemmas from {kept_records:,} noun records "
          f"({raw_records:,} raw, {sum(quarantine.values()):,} quarantined)", file=sys.stderr)
    return manifest
