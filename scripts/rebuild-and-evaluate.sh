#!/usr/bin/env bash
# Offline rebuild of the data artifacts, predictions, evaluation reports and learning run.
# Requires data/raw/kaikki-english-nouns.jsonl.gz (see docs/evaluation.md) and the bootstrapped JDK.
set -euo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(pwd)/.tools/jdk-21.0.13+9}"
export PATH="$JAVA_HOME/bin:$PATH"
MVN=".tools/apache-maven-3.9.9/bin/mvn"

echo "== extract / splits / artifacts =="
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v1.tsv

echo "== install core + evaluator =="
$MVN -q -pl inflector-eval -am install -DskipTests

echo "== protocol P1 (frozen shipped lexicon) =="
py -3 -m tools.research.cli records --split test \
  --out results/eval/test-records.tsv --gold results/eval/test-gold.tsv --lexicon-split all
$MVN -q -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds.tsv \
  --out results/eval/p1-report.json --split test --lexicon-split all

echo "== protocol P2 (family holdout, train-only artifact) =="
for split in train dev test; do
  py -3 -m tools.research.cli records --split "$split" \
    --out "results/eval/p2-$split-records.tsv" --gold "results/eval/p2-$split-gold.tsv" \
    --lexicon-split train
  $MVN -q -pl inflector-eval exec:java \
    -Denglish.inflector.lexicon=build/p2/lexicon-v1.tsv \
    -Dexec.args="--input results/eval/p2-$split-records.tsv --out results/eval/p2-$split-preds.tsv"
done
py -3 -m tools.research.cli evaluate \
  --gold results/eval/p2-test-gold.tsv --predictions results/eval/p2-test-preds.tsv \
  --out results/eval/p2-report.json --split test --lexicon-split train

echo "== learning experiment (P2 test, 3 seeds) =="
py -3 -m tools.research.cli learn --split test --out results/learning

echo "== done =="
