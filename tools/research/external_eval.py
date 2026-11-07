#!/usr/bin/env python3
"""External validation against UD English EWT and the AGID lexicon.

Sources are downloaded separately (see docs/evaluation.md); EWT is CC BY-SA 4.0 and AGID is
published under its own permissive terms. This script only prepares the filtered pairs and
aggregates predictions; it never touches the shipped lexicon or trains anything.

Membership labels have two separate meanings:

* ``seen`` / ``unseen`` use the full validated vocabulary (every lemma permitted by the
  dataset, including regular words omitted by compact artifact generation);
* ``artifact_hit`` / ``artifact_miss`` describe only the compact runtime artifact.

Family membership is likewise computed from the full vocabulary's family assignments, never
from the compact artifact.

AGID is a large automatically generated inflection lexicon, not a hand-adjudicated corpus.
It is reported as an independent lexical stress test and separately from EWT.

Usage:
  py -3 tools/research/external_eval.py prepare --conllu-dir build/external/ud-ewt \
      --out-dir results/external
  py -3 tools/research/external_eval.py prepare --source agid \
      --agid build/external/agid/agid-2016.01.19/infl.txt --out-dir results/external
  py -3 tools/research/external_eval.py score --gold results/external/ewt-gold.tsv \
      --predictions results/external/ewt-predictions.tsv \
      --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv \
      --out-dir results/external
"""

from __future__ import annotations

import argparse
import collections
import glob
import hashlib
import json
import os
import random
import re
import sys
from pathlib import Path

try:
    from . import dataset, splits as splitter
except ImportError:  # allow `py -3 tools/research/external_eval.py ...`
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from tools.research import dataset, splits as splitter

WORD = re.compile(r"[a-z]{2,}")
AGID_WORD = re.compile(r"[a-z]{3,}")

SOURCES = {
    "ewt": {
        "name": "UD English EWT",
        "url": "https://github.com/UniversalDependencies/UD_English-EWT",
        "license": "CC BY-SA 4.0",
        "revision": "4a4d77f599ea53cc405f85d0cec4b2f14f81d42b",
        "kind": "corpus",
        "prefix": "ewt",
    },
    "agid": {
        "name": "AGID 2016.01.19",
        "url": "https://sourceforge.net/projects/wordlist/files/AGID/2016.01.19/",
        "license": "AGID permissive terms (WordNet 1.6-style)",
        "revision": "2016.01.19",
        "archive_sha256": "15d2d792d309d2dc838bf75d8abcd3feb36708c219dc5158d4fff70b89e601d1",
        "kind": "lexicon",
        "prefix": "agid",
    },
}


def source_config(source: str) -> dict:
    try:
        return SOURCES[source]
    except KeyError as error:
        raise SystemExit(f"unknown external source: {source}") from error


def _write_gold(pairs: dict[str, collections.Counter[str]], out_dir: str, prefix: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    gold_path = os.path.join(out_dir, f"{prefix}-gold.tsv")
    with open(gold_path, "w", encoding="utf-8", newline="\n") as out:
        out.write("lemma\tforms\tcounts\n")
        for lemma in sorted(pairs):
            forms = sorted(pairs[lemma])
            counts = [str(pairs[lemma][form]) for form in forms]
            out.write(f"{lemma}\t{','.join(forms)}\t{','.join(counts)}\n")


def _write_manifest(manifest: dict, out_dir: str, prefix: str) -> None:
    with open(os.path.join(out_dir, f"{prefix}-manifest.json"), "w", encoding="utf-8") as out:
        json.dump(manifest, out, indent=2)
        out.write("\n")
    print(json.dumps(manifest, indent=2))


def prepare(conllu_dir: str | None, out_dir: str, source: str = "ewt",
            agid_path: str | None = None) -> None:
    config = source_config(source)
    if source == "agid":
        prepare_agid(agid_path, out_dir, config)
        return
    if conllu_dir is None:
        raise SystemExit("--conllu-dir is required for the ewt source")

    pairs: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    tokens = 0
    files = sorted(glob.glob(os.path.join(conllu_dir, "*.conllu")))
    if not files:
        raise SystemExit(f"no .conllu files under {conllu_dir}")
    for path in files:
        for line in open(path, encoding="utf-8"):
            if not line.strip() or line.startswith("#"):
                continue
            columns = line.rstrip("\n").split("\t")
            if len(columns) != 10:
                continue
            if columns[3] != "NOUN" or "Number=Plur" not in columns[5]:
                continue
            tokens += 1
            if "-" in columns[0] or "." in columns[0]:
                continue
            lemma = columns[2].lower()
            form = columns[1].lower()
            if lemma == form:
                continue
            if not WORD.fullmatch(lemma) or not WORD.fullmatch(form):
                continue
            pairs[lemma][form] += 1

    _write_gold(pairs, out_dir, config["prefix"])
    manifest = {
        "source": config["name"],
        "source_url": config["url"],
        "license": config["license"],
        "source_kind": config["kind"],
        "source_revision": config["revision"],
        "files": [os.path.basename(path) for path in files],
        "plural_noun_tokens": tokens,
        "lemmas": len(pairs),
        "forms": sum(len(forms) for forms in pairs.values()),
        "filters": [
            "UPOS=NOUN and Number=Plur",
            "single-token word lines only",
            "lemma and form match [a-z]{2,}",
            "form != lemma",
            "lower-cased and de-duplicated by lemma",
        ],
    }
    _write_manifest(manifest, out_dir, config["prefix"])


def prepare_agid(agid_path: str | None, out_dir: str, config: dict) -> None:
    if agid_path is None:
        raise SystemExit("--agid is required for the agid source")
    path = Path(agid_path)
    if not path.is_file():
        raise SystemExit(f"no AGID infl.txt at {agid_path}")

    pairs: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    noun_entries = 0
    skipped: collections.Counter[str] = collections.Counter()
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"^([A-Za-z']+) N:(.*)$", line.rstrip("\r\n"))
        if not match:
            continue
        noun_entries += 1
        original, rest = match.group(1), match.group(2).strip()
        if original != original.lower():
            skipped["capitalized_or_initialism"] += 1
            continue
        lemma = original.lower()
        chosen = None
        for candidate in (part.strip() for part in rest.split(",")):
            if not candidate:
                continue
            if re.search(r"[<~!?{}]", candidate):
                skipped["uncertain_marker"] += 1
                continue
            parts = candidate.split()
            if len(parts) > 1 and parts[1].isdigit() and parts[1] != "0":
                skipped["nonpreferred_variant"] += 1
                continue
            form = parts[0].lower()
            if form == lemma:
                skipped["identity"] += 1
                continue
            if not AGID_WORD.fullmatch(lemma) or not AGID_WORD.fullmatch(form):
                skipped["unsupported_shape"] += 1
                continue
            chosen = form
            break
        if chosen is None:
            skipped["no_accepted_form"] += 1
            continue
        pairs[lemma][chosen] += 1

    _write_gold(pairs, out_dir, config["prefix"])
    manifest = {
        "source": config["name"],
        "source_url": config["url"],
        "license": config["license"],
        "source_kind": config["kind"],
        "source_revision": config["revision"],
        "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "archive_sha256": config["archive_sha256"],
        "files": [path.name],
        "noun_entries": noun_entries,
        "lemmas": len(pairs),
        "forms": sum(len(forms) for forms in pairs.values()),
        "skipped": dict(skipped),
        "filters": [
            "AGID noun entries (N:) only",
            "source lemma must be lowercase (removes capitalized proper names and initialisms)",
            "preferred plural with no uncertainty marker or non-zero variant level",
            "single-token alphabetic lemma and form matching [a-z]{3,}",
            "form != lemma; lower-cased and de-duplicated by lemma",
        ],
    }
    _write_manifest(manifest, out_dir, config["prefix"])


def load_gold(path: str) -> dict[str, list[str]]:
    gold = {}
    with open(path, encoding="utf-8") as source:
        next(source, None)
        for line in source:
            if not line.strip() or line.startswith("#"):
                continue
            columns = line.rstrip("\n").split("\t")
            gold[columns[0]] = columns[1].split(",")
    return gold


def load_predictions(path: str) -> dict[str, dict[str, tuple[bool, bool, str, str]]]:
    predictions: dict[str, dict[str, tuple[bool, bool, str, str]]] = collections.defaultdict(dict)
    with open(path, encoding="utf-8") as source:
        header = next(source, None)
        if header is None:
            raise SystemExit(f"empty predictions file: {path}")
        for line in source:
            columns = line.rstrip("\n").split("\t")
            if len(columns) != 6:
                continue
            lemma, system, selected, selected_accepted, alternative_coverage, source_name = columns
            predictions[system][lemma] = (selected_accepted == "true", alternative_coverage == "true",
                                           selected, source_name)
    return predictions


def load_lexicon_metadata(path: str) -> dict[str, tuple[str, str]]:
    """Compact artifact rows only: countability/flags for diagnosis and artifact-hit labels."""
    metadata = {}
    with open(path, encoding="utf-8") as source:
        for line in source:
            if not line.strip() or line.startswith("#") or line.startswith("lemma\t"):
                continue
            columns = line.split("\t")
            if len(columns) == 7:
                metadata[columns[0].lower()] = (columns[4], columns[5])
    return metadata


def load_vocabulary(path: str | None = None, curated_path: str | None = None) -> set[str]:
    """Every validated lemma, not just the compact artifact rows."""
    vocabulary = {row["lemma"].lower() for row in dataset.read_tsv(Path(path or dataset.NORMALIZED))}
    curated = Path(curated_path) if curated_path else (dataset.CURATED / "core-nouns.tsv")
    if curated.exists():
        vocabulary.update(row["lemma"].lower() for row in dataset.read_tsv(curated))
    return vocabulary


def load_manifest(gold_path: str, prefix: str) -> dict:
    path = Path(gold_path).with_name(f"{prefix}-manifest.json")
    if not path.is_file():
        return {}
    with open(path, encoding="utf-8") as source:
        return json.load(source)


def membership_sets(lemmas: list[str], vocabulary: set[str], artifact: set[str],
                    family_key) -> dict[str, set[str]]:
    """Separate full-vocabulary labels from compact-artifact labels.

    The full-vocabulary family set is authoritative for family-seen/unseen. The compact
    artifact's family set is retained only as an additional artifact-hit diagnostic.
    """
    lemma_set = {lemma.lower() for lemma in lemmas}
    vocabulary_families = {family_key(lemma) for lemma in vocabulary}
    artifact_families = {family_key(lemma) for lemma in artifact}
    seen = {lemma for lemma in lemma_set if lemma in vocabulary}
    artifact_hit = {lemma for lemma in lemma_set if lemma in artifact}
    family_seen = {lemma for lemma in lemma_set if family_key(lemma) in vocabulary_families}
    artifact_family_hit = {lemma for lemma in lemma_set if family_key(lemma) in artifact_families}
    return {
        "seen": seen,
        "unseen": lemma_set - seen,
        "artifact_hit": artifact_hit,
        "artifact_miss": lemma_set - artifact_hit,
        "family_seen": family_seen,
        "family_unseen": lemma_set - family_seen,
        "artifact_family_hit": artifact_family_hit,
        "artifact_family_miss": lemma_set - artifact_family_hit,
    }


def rate(values: list[bool]) -> float:
    return sum(values) / len(values) if values else 0.0


def bootstrap(values: list[bool], seed: int = 20261107, rounds: int = 2_000) -> tuple[float, float]:
    """Percentile bootstrap for Bernoulli values.

    Drawing the resample's success count from Binomial(n, observed_rate) is exactly the
    distribution induced by resampling n binary values with replacement, without rebuilding
    the list for every round. This keeps the 34k-lemma AGID bootstrap tractable.
    """
    rng = random.Random(seed)
    n = len(values)
    observed = sum(values) / n
    if hasattr(rng, "binomialvariate"):
        samples = [rng.binomialvariate(n, observed) / n for _ in range(rounds)]
    else:  # Python < 3.12 fallback; same distribution, slower for large samples
        samples = []
        for _ in range(rounds):
            accepted = sum(values[rng.randrange(n)] for _ in range(n))
            samples.append(accepted / n)
    samples.sort()
    return samples[int(rounds * 0.025)], samples[int(rounds * 0.975) - 1]


def bootstrap_groups(groups: dict[str, list[bool]], seed: int = 20261107,
                     rounds: int = 2_000) -> tuple[float, float]:
    """Family-clustered bootstrap: resample families, retaining every lemma in a draw."""
    keys = sorted(groups)
    if not keys:
        return 0.0, 0.0
    rng = random.Random(seed)
    samples = []
    for _ in range(rounds):
        accepted = total = 0
        for _ in range(len(keys)):
            values = groups[keys[rng.randrange(len(keys))]]
            accepted += sum(values)
            total += len(values)
        samples.append(accepted / total if total else 0.0)
    samples.sort()
    return samples[int(rounds * 0.025)], samples[int(rounds * 0.975) - 1]


def metric(values: list[bool], families: list[str] | None = None) -> dict:
    grouped: dict[str, list[bool]] = collections.defaultdict(list)
    if families is not None:
        for family, accepted in zip(families, values):
            grouped[family].append(accepted)
    ci = bootstrap_groups(grouped) if grouped else bootstrap(values)
    return {
        "accepted": sum(values),
        "total": len(values),
        "accuracy": rate(values),
        "ci95": ci,
    }


def score(gold_path: str, predictions_path: str, lexicon_path: str, out_dir: str,
          family_mode: str = "lexical", vocabulary_path: str | None = None,
          curated_path: str | None = None, source: str = "ewt") -> None:
    config = source_config(source)
    prefix = config["prefix"]
    manifest = load_manifest(gold_path, prefix)
    source_name = manifest.get("source", config["name"])
    source_url = manifest.get("source_url", config["url"])
    license_name = manifest.get("license", config["license"])
    source_kind = manifest.get("source_kind", config["kind"])
    source_revision = manifest.get("source_revision", config["revision"])
    gold = load_gold(gold_path)
    predictions = load_predictions(predictions_path)
    metadata = load_lexicon_metadata(lexicon_path)
    artifact = set(metadata)
    lemmas = sorted(gold)
    vocabulary = load_vocabulary(vocabulary_path, curated_path)
    family_key = splitter.strict_family_key if family_mode == "strict" else splitter.family_key
    family_of = {lemma: family_key(lemma) for lemma in lemmas}

    memberships = membership_sets(lemmas, vocabulary, artifact, family_key)
    seen = sorted(memberships["seen"])
    unseen = sorted(memberships["unseen"])
    artifact_hit = sorted(memberships["artifact_hit"])
    artifact_miss = sorted(memberships["artifact_miss"])
    family_seen = sorted(memberships["family_seen"])
    family_unseen = sorted(memberships["family_unseen"])
    artifact_family_hit = sorted(memberships["artifact_family_hit"])
    artifact_family_miss = sorted(memberships["artifact_family_miss"])

    cluster = family_mode == "strict"

    def system_metric(system: str, subset: list[str], *,
                      coverage: bool = False) -> dict:
        if coverage:
            values = [predictions[system].get(lemma, (False, False, "", ""))[1] for lemma in subset]
        else:
            values = [predictions[system].get(lemma, (False, False, "", ""))[0] for lemma in subset]
        families = [family_of[lemma] for lemma in subset] if cluster else None
        return metric(values, families)

    systems = {}
    for system in sorted(predictions):
        systems[system] = {
            "overall": system_metric(system, lemmas),
            "seen": system_metric(system, seen),
            "unseen": system_metric(system, unseen),
            "family_seen": system_metric(system, family_seen),
            "family_unseen": system_metric(system, family_unseen),
            "artifact_hit": system_metric(system, artifact_hit),
            "artifact_miss": system_metric(system, artifact_miss),
            "artifact_family_hit": system_metric(system, artifact_family_hit),
            "artifact_family_miss": system_metric(system, artifact_family_miss),
            "alternative_coverage": system_metric(system, lemmas, coverage=True),
        }

    errors = []
    for lemma in lemmas:
        accepted, _, selected, source = predictions["lexicon"].get(lemma, (False, False, "", ""))
        if not accepted:
            countability, flags = metadata.get(lemma, ("NOT_IN_ARTIFACT", "-"))
            errors.append({
                "lemma": lemma,
                "gold": gold[lemma],
                "selected": selected,
                "source": source,
                "seen": lemma in memberships["seen"],
                "family_seen": lemma in memberships["family_seen"],
                "artifact_hit": lemma in memberships["artifact_hit"],
                "family": family_of.get(lemma),
                "artifact_countability": countability,
                "artifact_flags": flags,
                "identity": selected == lemma,
            })

    identity_errors = [error for error in errors if error["identity"]]
    by_class = collections.Counter(
        f"{error['artifact_countability']}/{error['artifact_flags']}" for error in identity_errors
    )
    report = {
        "source": source_name,
        "source_url": source_url,
        "license": license_name,
        "source_kind": source_kind,
        "source_revision": source_revision,
        "source_sha256": manifest.get("source_sha256"),
        "archive_sha256": manifest.get("archive_sha256"),
        "lemmas": len(lemmas),
        "vocabulary": {
            "path": dataset.rel(Path(vocabulary_path or dataset.NORMALIZED)),
            "lemmas": len(vocabulary),
            "artifact_path": dataset.rel(Path(lexicon_path)),
            "artifact_entries": len(artifact),
        },
        "seen": len(seen),
        "unseen": len(unseen),
        "artifact_hit": len(artifact_hit),
        "artifact_miss": len(artifact_miss),
        "family_mode": family_mode,
        "families": len(set(family_of.values())),
        "family_seen": len(family_seen),
        "family_unseen": len(family_unseen),
        "artifact_family_hit": len(artifact_family_hit),
        "artifact_family_miss": len(artifact_family_miss),
        "systems": systems,
        "lexicon_errors": errors,
        "error_diagnosis": {
            "errors": len(errors),
            "identity_returns": len(identity_errors),
            "identity_by_artifact_class": dict(by_class.most_common()),
            "vocabulary_seen": sum(1 for error in errors if error["seen"]),
            "artifact_hit": sum(1 for error in errors if error["artifact_hit"]),
            "by_source": dict(collections.Counter(error["source"] for error in errors).most_common()),
        },
    }
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f"{prefix}-report.json"), "w", encoding="utf-8") as out:
        json.dump(report, out, indent=2)
        out.write("\n")

    def pct(metric_value: dict) -> str:
        low, high = metric_value["ci95"]
        return f"{metric_value['accuracy'] * 100:.2f}% [{low * 100:.2f}, {high * 100:.2f}]"

    if source_kind == "lexicon":
        title = f"# External stress test: {source_name} ({family_mode} families)"
        provenance = (
            f"Source: [{source_name}]({source_url}), {license_name}. This is an automatically "
            "generated inflection lexicon, not a hand-adjudicated corpus; it is independent of "
            "the shipped vocabulary."
        )
        tuning_note = "The artifact was not trained or tuned on this source."
    else:
        title = f"# External validation: {source_name} ({family_mode} families)"
        provenance = (
            f"Source: [{source_name}]({source_url}), {license_name}. Independent of the "
            "Kaikki/Wiktionary snapshot."
        )
        tuning_note = "The artifact was not retrained or tuned on this corpus."
    lines = [
        title,
        "",
        provenance,
        f"Filtered pairs: {len(lemmas)} lemmas, {sum(len(v) for v in gold.values())} "
        f"{'forms' if source_kind == 'lexicon' else 'attested forms'}.",
    ]
    if source_kind == "lexicon":
        lines += [
            "AGID has no countability labels; many mass/abstract nouns receive generated plurals. "
            "The library's MASS/invariant policy therefore returns identity against this gold. "
            "The overall row is a lexical stress test, not a corpus-accuracy estimate; the "
            "full-vocabulary unseen slice is the primary new denominator.",
        ]
    lines += [
        f"Full-vocabulary membership: {len(seen)} seen / {len(unseen)} unseen. "
        f"Compact-artifact membership: {len(artifact_hit)} hit / {len(artifact_miss)} miss. "
        "These labels are deliberately separate: a regular word omitted by compaction is still "
        "a seen vocabulary lemma.",
        f"Family membership uses the full vocabulary ({len(family_seen)} family-seen / "
        f"{len(family_unseen)} family-unseen); the compact artifact's family labels are reported "
        "only as an artifact diagnostic.",
        "Selected-output accuracy: the selected plural matches the gold form. Alternative-set coverage is "
        "reported separately and is not compared against another system's selected output.",
        tuning_note,
        "",
        "| system | selected overall | vocab seen | vocab unseen | family seen | family unseen | "
        "artifact hit | artifact miss | alternative coverage |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for system, metrics in systems.items():
        lines.append(
            f"| {system} | {pct(metrics['overall'])} | {pct(metrics['seen'])} | "
            f"{pct(metrics['unseen'])} | {pct(metrics['family_seen'])} | "
            f"{pct(metrics['family_unseen'])} | {pct(metrics['artifact_hit'])} | "
            f"{pct(metrics['artifact_miss'])} | {pct(metrics['alternative_coverage'])} |"
        )
    lines += [
        "",
        "## Error diagnosis",
        "",
        f"{len(errors)} lexicon errors; {len(identity_errors)} are identity returns; "
        f"{report['error_diagnosis']['vocabulary_seen']} are full-vocabulary-seen and "
        f"{report['error_diagnosis']['artifact_hit']} are compact-artifact hits. "
        "By artifact class: "
        + ", ".join(f"{name} {count}" for name, count in by_class.most_common())
        + ".",
        "",
        f"## Lexicon errors ({len(errors)})",
        "",
        "| lemma | gold | selected | source | vocab seen | family seen | artifact | artifact hit |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for error in errors[:40]:
        lines.append(
            f"| {error['lemma']} | {', '.join(error['gold'])} | {error['selected']} | "
            f"{error['source']} | {'yes' if error['seen'] else 'no'} | "
            f"{'yes' if error['family_seen'] else 'no'} | "
            f"{error['artifact_countability']}/{error['artifact_flags']} | "
            f"{'yes' if error['artifact_hit'] else 'no'} |"
        )
    if len(errors) > 40:
        lines.append(f"| ... | ({len(errors) - 40} more) | | | | | | |")
    with open(os.path.join(out_dir, f"{prefix}-report.md"), "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--source", choices=sorted(SOURCES), default="ewt")
    p.add_argument("--conllu-dir", default="build/external/ud-ewt",
                   help="UD .conllu directory (ewt source)")
    p.add_argument("--agid", default=None, help="AGID infl.txt path (agid source)")
    p.add_argument("--out-dir", required=True)
    s = sub.add_parser("score")
    s.add_argument("--source", choices=sorted(SOURCES), default="ewt")
    s.add_argument("--gold", required=True)
    s.add_argument("--predictions", required=True)
    s.add_argument("--lexicon", required=True)
    s.add_argument("--out-dir", required=True)
    s.add_argument("--family-mode", choices=["lexical", "strict"], default="lexical")
    s.add_argument("--vocabulary", default=None,
                   help="full validated vocabulary TSV (default: data/derived/nouns-v2.tsv.gz)")
    s.add_argument("--curated", default=None,
                   help="curated rows added to the full vocabulary (default: data/curated/core-nouns.tsv)")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.conllu_dir, args.out_dir, source=args.source, agid_path=args.agid)
    else:
        score(args.gold, args.predictions, args.lexicon, args.out_dir,
              family_mode=args.family_mode, vocabulary_path=args.vocabulary,
              curated_path=args.curated, source=args.source)


if __name__ == "__main__":
    main()
