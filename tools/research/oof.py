"""Build inner-fold out-of-fold base predictions for the residual-learning experiment.

The earlier experiment trained on predictions from a lexicon that contained the training words themselves.
Its base was therefore usually correct by lookup, and the selector mostly learned to keep a lexical answer
that would not exist on an unseen word. This script removes that mismatch:

1. training families are split into deterministic inner folds;
2. each fold's artifact is built from the other training folds only;
3. the excluded fold is predicted with that artifact using the real Java engine;
4. the combined out-of-fold predictions are the training input for the selector.

Outer dev/test predictions still come from the train-only P2 artifact. Inner-fold predictions are never used
to select a threshold on test data.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

from . import dataset, lexicon, splits


def fold_for(family: str, folds: int) -> int:
    digest = hashlib.blake2b(f"oof-v1:{family}".encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, "big") % folds


def java_command(args: list[str]) -> list[str]:
    maven = Path(".tools/apache-maven-3.9.9/bin/mvn")
    if os.name == "nt":
        return ["cmd", "/c", str(maven.with_suffix(".cmd")), *args]
    return [str(maven), *args]


def run_eval(input_path: Path, output_path: Path, artifact: Path) -> None:
    command = java_command([
        "-q", "-o", "-pl", "inflector-eval", "exec:java",
        f"-Denglish.inflector.lexicon={artifact.resolve()}",
        f"-Dexec.args=--input {input_path} --out {output_path} --systems lexicon",
    ])
    subprocess.run(command, check=True, cwd=dataset.ROOT)


def run(folds: int = 5, out_dir: str = "results/learning/oof") -> dict:
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    normalized = dataset.read_tsv(dataset.NORMALIZED)
    assignments = splits.load()
    train = [row for row in normalized if assignments.get(row["lemma"], {}).get("split") == "train"]
    if not train:
        raise SystemExit("no training records; run splits first")

    by_fold: dict[int, list[dict]] = {index: [] for index in range(folds)}
    for row in train:
        family = assignments[row["lemma"]]["family"]
        by_fold[fold_for(family, folds)].append(row)

    all_predictions: dict[str, tuple[str, str]] = {}
    fold_manifest = []
    for index in range(folds):
        held = by_fold[index]
        held_families = {assignments[row["lemma"]]["family"] for row in held}
        allowed = {row["lemma"] for row in train
                   if assignments[row["lemma"]]["family"] not in held_families}
        fold_dir = out / f"fold-{index}"
        fold_dir.mkdir(parents=True, exist_ok=True)
        artifact = fold_dir / dataset.CORE_RESOURCE.name
        membership: set[str] = set()
        rows = lexicon.build("train", "compact", membership, allowed)
        lexicon.write(rows, artifact, "train", "compact", "oof-inner-fold")
        lexicon.write_membership(fold_dir / dataset.CORE_MEMBERSHIP.name, membership)

        records = fold_dir / "records.tsv"
        predictions = fold_dir / "predictions.tsv"
        dataset.write_tsv(records, ["id", "lemma"], [[row["lemma"], row["lemma"]] for row in held])
        run_eval(records, predictions, artifact)
        for row in dataset.read_tsv(predictions):
            all_predictions[row["id"]] = (row["prediction"], row["source"])
        fold_manifest.append({
            "fold": index,
            "held_records": len(held),
            "held_families": len(held_families),
            "allowed_lemmas": len(allowed),
            "artifact_entries": len(rows),
        })
        print(f"fold {index}: held={len(held)} families={len(held_families)} "
              f"artifact={len(rows)}", file=sys.stderr)

    combined = out / "train-preds.tsv"
    dataset.write_tsv(
        combined,
        ["id", "system", "prediction", "source"],
        [[lemma, "lexicon", prediction, source]
         for lemma, (prediction, source) in sorted(all_predictions.items())],
        comments=[
            "out-of-fold P2 training predictions (inner folds, real Java engine)",
            f"folds={folds}; each row was predicted by an artifact excluding its own family fold",
        ],
    )
    manifest = {
        "version": "oof-v1",
        "folds": folds,
        "train_records": len(train),
        "predicted_records": len(all_predictions),
        "fold_manifest": fold_manifest,
        "predictions": dataset.rel(combined),
        "predictions_sha256": dataset.sha256_file(combined),
    }
    (out / "manifest.json").write_text(__import__("json").dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(manifest, file=sys.stderr)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--folds", type=int, default=5)
    parser.add_argument("--out", default="results/learning/oof")
    args = parser.parse_args()
    run(args.folds, args.out)


if __name__ == "__main__":
    main()
