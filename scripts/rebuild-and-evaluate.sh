#!/usr/bin/env bash
# Offline rebuild of data artifacts, predictions, evaluation reports and the corrected learning run.
# Requires data/raw/kaikki-english-nouns.jsonl.gz (see docs/evaluation.md) and the bootstrapped JDK.
set -euo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(pwd)/.tools/jdk-21.0.13+9}"
export PATH="$JAVA_HOME/bin:$PATH"
MVN=".tools/apache-maven-3.9.9/bin/mvn"
ITERATIONS="${ITERATIONS:-2000}"

echo "== tests =="
$MVN -q -o -pl inflector-core test
py -3 -m unittest tools.research.test_lexicon tools.research.test_learning tools.research.test_splits

echo "== extract / splits / artifacts =="
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v1.tsv

echo "== install evaluator =="
$MVN -q -o -pl inflector-eval -am install -DskipTests

echo "== records and gold =="
py -3 -m tools.research.cli records --split test \
  --out results/eval/test-records.tsv --gold results/eval/test-gold.tsv --lexicon-split all
for split in train dev test; do
  py -3 -m tools.research.cli records --split "$split" \
    --out "results/eval/p2-$split-records.tsv" --gold "results/eval/p2-$split-gold.tsv" \
    --lexicon-split train
done

echo "== protocol P1 (frozen shipped lexicon) =="
$MVN -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds-v4.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds-v4.tsv \
  --out results/eval/p1-report-final.json --split test --lexicon-split all \
  --iterations "$ITERATIONS"

echo "== protocol P2 (family holdout, train-only artifact) =="
for split in train dev test; do
  $MVN -q -o -pl inflector-eval exec:java \
    -Denglish.inflector.lexicon="$(pwd)/build/p2/lexicon-v1.tsv" \
    -Dexec.args="--input results/eval/p2-$split-records.tsv --out results/eval/p2-$split-preds-v4.tsv"
done
for split in dev test; do
  py -3 -m tools.research.cli evaluate \
    --gold "results/eval/p2-$split-gold.tsv" \
    --predictions "results/eval/p2-$split-preds-v4.tsv" \
    --out "results/eval/p2-$split-report-final.json" --split "$split" \
    --lexicon-split train --iterations "$ITERATIONS"
done

echo "== corrected learning input (inner folds) =="
py -3 -m tools.research.cli oof --folds 5

echo "== corrected linear selector (offline research only) =="
py -3 -m tools.research.cli learn --linear-only --iterations 1000

echo "== done =="
