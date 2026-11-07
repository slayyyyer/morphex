#!/usr/bin/env python3
"""External validation against UD English EWT.

The corpus is downloaded separately (see docs/evaluation.md) and is licensed CC BY-SA 4.0.
This script only prepares the filtered pairs and aggregates predictions; it never touches the
shipped lexicon or trains anything.

Usage:
  py -3 tools/research/external_eval.py prepare --conllu-dir build/external/ud-ewt \
      --out-dir results/external
  py -3 tools/research/external_eval.py score --gold results/external/ewt-gold.tsv \
      --predictions results/external/ewt-predictions.tsv \
      --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v1.tsv \
      --out-dir results/external
"""

from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import random
import re
import sys
from pathlib import Path

try:
    from . import splits as splitter
except ImportError:  # allow `py -3 tools/research/external_eval.py ...`
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from tools.research import splits as splitter

WORD = re.compile(r"[a-z]{2,}")
SOURCE = "UD English EWT"
SOURCE_URL = "https://github.com/UniversalDependencies/UD_English-EWT"
LICENSE = "CC BY-SA 4.0"


def prepare(conllu_dir: str, out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
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

    gold_path = os.path.join(out_dir, "ewt-gold.tsv")
    with open(gold_path, "w", encoding="utf-8", newline="\n") as out:
        out.write("lemma\tforms\tcounts\n")
        for lemma in sorted(pairs):
            forms = sorted(pairs[lemma])
            counts = [str(pairs[lemma][form]) for form in forms]
            out.write(f"{lemma}\t{','.join(forms)}\t{','.join(counts)}\n")

    manifest = {
        "source": SOURCE,
        "source_url": SOURCE_URL,
        "license": LICENSE,
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
    with open(os.path.join(out_dir, "ewt-manifest.json"), "w", encoding="utf-8") as out:
        json.dump(manifest, out, indent=2)
        out.write("\n")
    print(json.dumps(manifest, indent=2))


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
    metadata = {}
    with open(path, encoding="utf-8") as source:
        for line in source:
            if not line.strip() or line.startswith("#") or line.startswith("lemma\t"):
                continue
            columns = line.split("\t")
            if len(columns) == 7:
                metadata[columns[0].lower()] = (columns[4], columns[5])
    return metadata


def rate(values: list[bool]) -> float:
    return sum(values) / len(values) if values else 0.0


def bootstrap(values: list[bool], seed: int = 20261107, rounds: int = 10_000) -> tuple[float, float]:
    rng = random.Random(seed)
    n = len(values)
    samples = []
    for _ in range(rounds):
        accepted = sum(values[rng.randrange(n)] for _ in range(n))
        samples.append(accepted / n)
    samples.sort()
    return samples[int(rounds * 0.025)], samples[int(rounds * 0.975) - 1]


def bootstrap_groups(groups: dict[str, list[bool]], seed: int = 20261107,
                     rounds: int = 10_000) -> tuple[float, float]:
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
          family_mode: str = "lexical") -> None:
    gold = load_gold(gold_path)
    predictions = load_predictions(predictions_path)
    metadata = load_lexicon_metadata(lexicon_path)
    lexicon = set(metadata)
    lemmas = sorted(gold)
    seen = [lemma for lemma in lemmas if lemma in lexicon]
    unseen = [lemma for lemma in lemmas if lemma not in lexicon]

    family_of = {
        lemma: splitter.strict_family_key(lemma) if family_mode == "strict"
        else splitter.family_key(lemma)
        for lemma in lemmas
    }
    lexicon_families = {
        splitter.strict_family_key(lemma) if family_mode == "strict"
        else splitter.family_key(lemma)
        for lemma in lexicon
    }
    family_seen = [lemma for lemma in lemmas if family_of[lemma] in lexicon_families]
    family_unseen = [lemma for lemma in lemmas if family_of[lemma] not in lexicon_families]

    systems = {}
    for system in sorted(predictions):
        accepted = {lemma: predictions[system].get(lemma, (False, False, "", ""))[0] for lemma in lemmas}
        coverage = {lemma: predictions[system].get(lemma, (False, False, "", ""))[1] for lemma in lemmas}
        overall = [accepted[lemma] for lemma in lemmas]
        seen_values = [accepted[lemma] for lemma in seen]
        unseen_values = [accepted[lemma] for lemma in unseen]
        family_seen_values = [accepted[lemma] for lemma in family_seen]
        family_unseen_values = [accepted[lemma] for lemma in family_unseen]
        cluster = family_mode == "strict"
        families = [family_of[lemma] for lemma in lemmas] if cluster else None
        seen_families = [family_of[lemma] for lemma in seen] if cluster else None
        unseen_families = [family_of[lemma] for lemma in unseen] if cluster else None
        family_seen_families = [family_of[lemma] for lemma in family_seen] if cluster else None
        family_unseen_families = [family_of[lemma] for lemma in family_unseen] if cluster else None
        systems[system] = {
            "overall": metric(overall, families),
            "seen": metric(seen_values, seen_families),
            "unseen": metric(unseen_values, unseen_families),
            "family_seen": metric(family_seen_values, family_seen_families),
            "family_unseen": metric(family_unseen_values, family_unseen_families),
            "alternative_coverage": metric(list(coverage.values()), families),
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
                "seen": lemma in lexicon,
                "family": family_of.get(lemma),
                "family_seen": lemma in family_seen,
                "artifact_countability": countability,
                "artifact_flags": flags,
                "identity": selected == lemma,
            })

    identity_errors = [error for error in errors if error["identity"]]
    by_class = collections.Counter(
        f"{error['artifact_countability']}/{error['artifact_flags']}" for error in identity_errors
    )
    report = {
        "source": SOURCE,
        "source_url": SOURCE_URL,
        "license": LICENSE,
        "lemmas": len(lemmas),
        "seen": len(seen),
        "unseen": len(unseen),
        "family_mode": family_mode,
        "families": len(set(family_of.values())),
        "family_seen": len(family_seen),
        "family_unseen": len(family_unseen),
        "systems": systems,
        "lexicon_errors": errors,
        "error_diagnosis": {
            "errors": len(errors),
            "identity_returns": len(identity_errors),
            "identity_by_artifact_class": dict(by_class.most_common()),
            "by_source": dict(collections.Counter(error["source"] for error in errors).most_common()),
        },
    }
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "ewt-report.json"), "w", encoding="utf-8") as out:
        json.dump(report, out, indent=2)
        out.write("\n")

    def pct(metric: dict) -> str:
        low, high = metric["ci95"]
        return f"{metric['accuracy'] * 100:.2f}% [{low * 100:.2f}, {high * 100:.2f}]"

    lines = [
        f"# External validation: UD English EWT ({family_mode} families)",
        "",
        f"Source: [{SOURCE}]({SOURCE_URL}), {LICENSE}. Independent of the Kaikki/Wiktionary snapshot.",
        f"Filtered pairs: {len(lemmas)} lemmas, {sum(len(v) for v in gold.values())} attested forms; "
        f"{len(seen)} lemmas are present in the shipped lexicon, {len(unseen)} are unseen.",
        f"Strict-family check: {len(family_seen)} lemmas share a family with an artifact lemma; "
        f"{len(family_unseen)} are family-unseen.",
        "Selected-output accuracy: the selected plural matches an attested form. Alternative-set coverage is "
        "reported separately and is not compared against another system's selected output.",
        "The artifact was not retrained or tuned on this corpus.",
        "",
        "| system | selected overall | exact seen | exact unseen | family seen | family unseen | alternative coverage |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for system, metrics in systems.items():
        lines.append(
            f"| {system} | {pct(metrics['overall'])} | {pct(metrics['seen'])} | {pct(metrics['unseen'])} | "
            f"{pct(metrics['family_seen'])} | {pct(metrics['family_unseen'])} | "
            f"{pct(metrics['alternative_coverage'])} |"
        )
    lines += [
        "",
        "## Error diagnosis",
        "",
        f"{len(errors)} lexicon errors; {len(identity_errors)} are identity returns. "
        "By artifact class: "
        + ", ".join(f"{name} {count}" for name, count in by_class.most_common())
        + ".",
        "",
        f"## Lexicon errors ({len(errors)})",
        "",
        "| lemma | gold | selected | source | seen | artifact |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for error in errors[:40]:
        lines.append(
            f"| {error['lemma']} | {', '.join(error['gold'])} | {error['selected']} | "
            f"{error['source']} | {'yes' if error['seen'] else 'no'} | "
            f"{error['artifact_countability']}/{error['artifact_flags']} |"
        )
    if len(errors) > 40:
        lines.append(f"| ... | ({len(errors) - 40} more) | | | | |")
    with open(os.path.join(out_dir, "ewt-report.md"), "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--conllu-dir", required=True)
    p.add_argument("--out-dir", required=True)
    s = sub.add_parser("score")
    s.add_argument("--gold", required=True)
    s.add_argument("--predictions", required=True)
    s.add_argument("--lexicon", required=True)
    s.add_argument("--out-dir", required=True)
    s.add_argument("--family-mode", choices=["lexical", "strict"], default="lexical")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.conllu_dir, args.out_dir)
    else:
        score(args.gold, args.predictions, args.lexicon, args.out_dir, family_mode=args.family_mode)


if __name__ == "__main__":
    main()
