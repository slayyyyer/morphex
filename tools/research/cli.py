"""Command line entry point for the offline pipeline.

Run from the repository root:

    python tools/research/cli.py extract
    python tools/research/cli.py splits
    python tools/research/cli.py artifact --split all
    python tools/research/cli.py records --split test --out results/eval/test-records.tsv --gold results/eval/test-gold.tsv --lexicon-split all
    python tools/research/cli.py evaluate --gold ... --predictions ... --out ...
"""

from __future__ import annotations

import argparse
import hashlib
import sys
import urllib.request

from . import dataset, evaluate, extract, lexicon, splits


def cmd_fetch(args: argparse.Namespace) -> int:
    target = dataset.RAW_NOUNS
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        digest = dataset.sha256_file(target)
        if digest == dataset.SOURCE["sha256"]:
            print(f"already present and verified: {target}")
            return 0
        raise SystemExit(f"existing dump has unexpected sha256 {digest}; move it away first")

    print(f"downloading {dataset.SOURCE['url']}")
    digest = hashlib.sha256()
    with urllib.request.urlopen(dataset.SOURCE["url"]) as response, target.open("wb") as handle:
        while chunk := response.read(1 << 20):
            handle.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != dataset.SOURCE["sha256"]:
        raise SystemExit(f"checksum mismatch: {digest.hexdigest()} != {dataset.SOURCE['sha256']}")
    print(f"verified sha256 {digest.hexdigest()} ({target.stat().st_size} bytes)")
    return 0


def cmd_extract(args: argparse.Namespace) -> int:
    manifest = extract.run(limit=args.limit)
    print(f"normalized dataset: {dataset.rel(dataset.NORMALIZED)}")
    print(f"records: {manifest['extraction']['unique_lemmas']}")
    return 0


def cmd_splits(args: argparse.Namespace) -> int:
    manifest = splits.run(mode=args.mode)
    print(f"splits: {manifest['strict_split'] if args.mode == 'strict' else manifest['split']}")
    return 0


def cmd_artifact(args: argparse.Namespace) -> int:
    out = dataset.CORE_RESOURCE
    if args.out:
        from pathlib import Path
        out = Path(args.out)
    manifest = lexicon.run(split_filter=args.split, target=out, selection=args.selection,
                            splits_path=args.splits)
    print(f"artifact: {manifest['artifact']}")
    return 0


def cmd_records(args: argparse.Namespace) -> int:
    from pathlib import Path

    if not dataset.SPLITS.exists():
        raise SystemExit("splits missing: run `splits` first")
    assignments = splits.load(args.splits)
    normalized = dataset.read_tsv(dataset.NORMALIZED)
    records_rows: list[list[str]] = []
    gold_rows: list[list[str]] = []
    for record in normalized:
        assignment = assignments.get(record["lemma"])
        if not assignment or assignment["split"] != args.split:
            continue
        forms = [form for form in record["forms"].split(",") if form]
        accepted = list(forms)
        if record["invariant"] == "1" or record["plural_only"] == "1" or record["countability"] == "MASS":
            if record["lemma"] not in accepted:
                accepted.append(record["lemma"])
        records_rows.append([record["lemma"], record["lemma"]])
        gold_rows.append([
            record["lemma"],
            record["lemma"],
            assignment["family"],
            assignment["bucket"],
            record["countability"],
            record["invariant"],
            ",".join(accepted),
            str(len([form for form in record["restricted"].split(";") if form])),
        ])
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    dataset.write_tsv(Path(args.out), ["id", "lemma"], records_rows)
    dataset.write_tsv(
        Path(args.gold),
        ["id", "lemma", "family", "bucket", "countability", "invariant", "accepted", "restricted_count"],
        gold_rows,
        comments=[
            f"gold labels for split={args.split}, lexicon_split={args.lexicon_split}",
            "labels are fixed before predictions; accepted forms come from the validated normalized dataset",
        ],
    )
    print(f"records={len(records_rows)} -> {args.out}; gold -> {args.gold}")
    return 0


def cmd_evaluate(args: argparse.Namespace) -> int:
    from pathlib import Path

    report = evaluate.run(
        Path(args.gold),
        [Path(path) for path in args.predictions],
        baseline=args.baseline,
        out_path=Path(args.out),
        lexicon_split=args.lexicon_split,
        split_name=args.split,
        iterations=args.iterations,
    )
    for name, stats in report["systems"].items():
        primary = stats["primary"]
        accuracy = f"{primary['accuracy'] * 100:.3f}%" if primary["accuracy"] is not None else "n/a"
        print(f"{name:>12}: primary accepted accuracy {accuracy} ({primary['errors']} errors of {primary['total']})")
    for comparison in report["comparisons"]:
        ci = comparison["ci95_pp"]
        print(f"{comparison['baseline']} -> {comparison['candidate']}: "
              f"{comparison['delta_accuracy_pp']:+.4f}pp CI95 [{ci[0]:+.4f}, {ci[1]:+.4f}] "
              f"corrections={comparison['corrections']} regressions={comparison['regressions']}")
    return 0


def cmd_oof(args: argparse.Namespace) -> int:
    from . import oof
    manifest = oof.run(folds=args.folds, out_dir=args.out)
    print(f"out-of-fold predictions: {manifest['predictions']}")
    return 0


def cmd_learn(args: argparse.Namespace) -> int:
    from . import experiment
    experiment.run(args)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="research", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("fetch", help="download and verify the pinned Kaikki noun dump (network)")
    extract_parser = sub.add_parser("extract", help="normalize and validate the dump")
    extract_parser.add_argument("--limit", type=int, default=None)
    splits_parser = sub.add_parser("splits", help="write family-aware splits")
    splits_parser.add_argument("--mode", choices=["lexical", "strict"], default="lexical")
    artifact_parser = sub.add_parser("artifact", help="generate the runtime/jsonl lexicon artifact")
    artifact_parser.add_argument("--split", default="all", choices=["all", "train"])
    artifact_parser.add_argument("--selection", default="compact", choices=["compact", "full"])
    artifact_parser.add_argument("--out", default=None)
    artifact_parser.add_argument("--splits", default=None)
    records_parser = sub.add_parser("records", help="write evaluation records and frozen gold labels")
    records_parser.add_argument("--split", default="test", choices=["train", "dev", "test"])
    records_parser.add_argument("--out", required=True)
    records_parser.add_argument("--gold", required=True)
    records_parser.add_argument("--lexicon-split", default="all", choices=["all", "train"])
    records_parser.add_argument("--splits", default=None)
    evaluate_parser = sub.add_parser("evaluate", help="score prediction files against frozen gold")
    evaluate_parser.add_argument("--gold", required=True)
    evaluate_parser.add_argument("--predictions", nargs="+", required=True)
    evaluate_parser.add_argument("--baseline", default="evo2")
    evaluate_parser.add_argument("--out", required=True)
    evaluate_parser.add_argument("--split", default="test")
    evaluate_parser.add_argument("--lexicon-split", default="all")
    evaluate_parser.add_argument("--iterations", type=int, default=2000)
    learn_parser = sub.add_parser("learn", help="run the candidate/oracle and learner experiments")
    learn_parser.add_argument("--split", default="test", choices=["train", "dev", "test"])
    learn_parser.add_argument("--out", default="results/learning")
    learn_parser.add_argument("--iterations", type=int, default=2000)
    learn_parser.add_argument("--oof", default="results/learning/oof/train-preds.tsv")
    learn_parser.add_argument("--dev-preds", default="results/eval/p2-dev-preds-v4.tsv")
    learn_parser.add_argument("--test-preds", default="results/eval/p2-test-preds-v4.tsv")
    learn_parser.add_argument("--model-out", default="results/learning/linear-v1.bin",
                              help="write the corrected linear selector artifact for the Java scorer")
    learn_parser.add_argument("--selector-input", default="results/learning/java-test-input.tsv",
                              help="write the Java selector's id/lemma/base/source evaluation input")
    learn_parser.add_argument("--runtime", default="results/learning/java-gate.json",
                              help="optional Java gate JSON to include in the learning report")
    learn_parser.add_argument("--fast", action="store_true", help="smaller training budget for smoke checks")
    learn_parser.add_argument("--linear-only", action="store_true",
                              help="train the corrected linear selector without the MLP screening runs")
    oof_parser = sub.add_parser("oof", help="generate inner-fold out-of-fold training predictions")
    oof_parser.add_argument("--folds", type=int, default=5)
    oof_parser.add_argument("--out", default="results/learning/oof")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    handlers = {
        "fetch": cmd_fetch,
        "extract": cmd_extract,
        "splits": cmd_splits,
        "artifact": cmd_artifact,
        "records": cmd_records,
        "evaluate": cmd_evaluate,
        "oof": cmd_oof,
        "learn": cmd_learn,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
