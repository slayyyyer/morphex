#!/usr/bin/env bash
# Record the release's immutable inputs and measured outputs.
# Usage: scripts/release-evidence.sh [output.tsv]
# The manifest deliberately contains no timestamp so a clean rerun is byte-identical.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="${1:-results/release-1.2.0/EVIDENCE.tsv}"
TMP="${OUT}.tmp"
DIRTY="$(git status --porcelain | wc -l | tr -d ' ')"
mkdir -p "$(dirname "$OUT")"
printf 'field\tvalue\n' > "$TMP"

row() {
  local field="$1" value="$2"
  printf '%s\t%s\n' "$field" "$value" >> "$TMP"
}

sha() {
  sha256sum "$1" | awk '{print $1}'
}

row source_revision "$(git rev-parse HEAD)"
row source_branch "$(git rev-parse --abbrev-ref HEAD)"
row source_dirty "$DIRTY"
row release_version "1.2.0"
row dataset_version "nouns-v2"
row extraction_version "extract-v3"
row artifact_version "lexicon-v2"
row split_version "split-v1"
row p1_primary_denominator "36241"
row p2_test_primary_denominator "36241"
row p2_dev_primary_denominator "36473"
row external_vocabulary_lemmas "340741"

for path in \
  data/derived/nouns-v2.tsv.gz \
  inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv \
  inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-membership-v2.bin \
  results/release-1.2.0/eval/p1-test-gold.tsv \
  results/release-1.2.0/eval/p1-test-preds.tsv \
  results/release-1.2.0/eval/p2-dev-gold.tsv \
  results/release-1.2.0/eval/p2-dev-preds.tsv \
  results/release-1.2.0/eval/p2-test-gold.tsv \
  results/release-1.2.0/eval/p2-test-preds.tsv \
  results/release-1.2.0/eval/p1-report.json \
  results/release-1.2.0/eval/p2-dev-report.json \
  results/release-1.2.0/eval/p2-test-report.json \
  results/release-1.2.0/external/ewt-predictions.tsv \
  results/release-1.2.0/external/ewt-report.json \
  results/release-1.2.0/external/agid-manifest.json \
  results/release-1.2.0/external/agid-gold.tsv \
  results/release-1.2.0/external/agid-predictions.tsv \
  results/release-1.2.0/external/agid-report.json \
  inflector-benchmarks/src/main/resources/benchmark-workload-v1.tsv \
  results/release-1.2.0/benchmarks/jmh-workload-v1.json \
  site/catalog.json; do
  if [[ ! -f "$path" ]]; then
    echo "missing release input: $path" >&2
    exit 1
  fi
  row "sha256:${path}" "$(sha "$path")"
done

# JARs are produced by the build, not by this script; include them when present.
for jar in inflector-core/target/english-inflector-core-1.2.0.jar \
           inflector-benchmarks/target/benchmarks.jar; do
  if [[ -f "$jar" ]]; then
    row "sha256:${jar}" "$(sha "$jar")"
  fi
done

mv "$TMP" "$OUT"
echo "wrote $OUT"
