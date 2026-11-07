# Release evidence — 1.2.0

This directory holds the frozen evidence for the runtime-correctness release. The
machine-readable checksums are in `EVIDENCE.tsv`, produced by `scripts/release-evidence.sh`.
That manifest records the source revision and branch, dataset/extraction/artifact/split
versions, denominators, SHA-256 hashes for every evaluation gold/prediction file, the
external predictions and report, the benchmark workload and JMH JSON, the Java-generated
site catalog, and the core/benchmark JARs.

## Versions and denominators

| field | value |
| --- | --- |
| release | `1.2.0` |
| dataset | `nouns-v2` (340,734 lemmas) |
| extraction | `extract-v3` |
| runtime artifact | `lexicon-v2` (147,338 entries) |
| membership | 340,741 lemmas |
| P2 train artifact | 103,668 entries; 238,927 membership lemmas |
| split | `split-v1` |
| P1 primary / overall | 36,241 / 50,978 |
| P2 test primary / overall | 36,241 / 50,978 |
| P2 dev primary / overall | 36,473 / 50,829 |
| external EWT | 2,184 lemmas; 2,148 full-vocabulary seen / 36 unseen; 363 artifact hit / 1,821 miss |

## Commands

Run from the repository root with JDK 21 (JDK 17+ is supported) and the checked-in wrapper:

```bash
source scripts/dev-env.sh

# data and both runtime artifacts
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v2.tsv

# Java evaluator and P1/P2 predictions
./mvnw -q -o -pl inflector-eval -am install -DskipTests
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/release-1.2.0/eval/p1-test-records.tsv --out results/release-1.2.0/eval/p1-test-preds.tsv"
./mvnw -q -o -pl inflector-eval exec:java \
  -Denglish.inflector.lexicon="$(cygpath -w "$(pwd)/build/p2/lexicon-v2.tsv")" \
  -Dexec.args="--input results/release-1.2.0/eval/p2-test-records.tsv --out results/release-1.2.0/eval/p2-test-preds.tsv"
# dev uses the same command with p2-dev-* paths

# scores (2,000 family-clustered bootstrap rounds)
py -3 -m tools.research.cli evaluate \
  --gold results/release-1.2.0/eval/p1-test-gold.tsv \
  --predictions results/release-1.2.0/eval/p1-test-preds.tsv \
  --out results/release-1.2.0/eval/p1-report.json --split test --lexicon-split all --iterations 2000
# P2 uses --split dev|test --lexicon-split train

# external full-vocabulary labels
py -3 tools/research/external_eval.py score \
  --gold results/external/ewt-gold.tsv \
  --predictions results/release-1.2.0/external/ewt-predictions.tsv \
  --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv \
  --out-dir results/release-1.2.0/external --family-mode lexical

# fixed workload benchmark
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 \
  -w 500ms -r 500ms -prof gc -rf json \
  -rff results/release-1.2.0/benchmarks/jmh-workload-v1.json

# verified website catalog
./mvnw -q -pl inflector-example -am compile
java -cp "inflector-core/target/classes;inflector-example/target/classes" \
  io.github.slayyyyer.morphex.inflector.example.SiteCatalog site/catalog.json

# checksum manifest
scripts/release-evidence.sh results/release-1.2.0/EVIDENCE.tsv
```

## Recorded outcomes

| check | result |
| --- | --- |
| P1 primary | 0 / 36,241 errors (100.000%) |
| P1 overall | 0 / 50,978 errors (100.000%) |
| P2 test primary | 955 / 36,241 errors (97.365%); +0.8913 pp vs evo2, CI `[+0.7355, +1.0531]` |
| P2 dev primary | 955 / 36,473 errors (97.382%); +0.9815 pp vs evo2 |
| P2 change vs v1 | +4 errors vs the historical 951-error baseline; explicitly retained |
| external selected overall | 98.76% lexicon / 98.12% evo2 |
| external full-vocabulary seen | 98.88% lexicon over 2,148 lemmas |
| external genuinely unseen | 91.67% over only 36 lemmas |
| external artifact hit | 96.69% over 363 lemmas |
| external alternative coverage | 98.99% |
| JMH repeated lookup | 81.4 ns vs evo2 110.9 ns (1.36x faster) |
| JMH compacted regular | 456.4 ns vs evo2 104.7 ns (slower) |
| JMH four-thread throughput | 4.82M vs evo2 20.91M ops/s (slower) |
| retained heap | 19.08 MB |
| tests | Java core suite + 44 Python tests passed |

The historical v1 P2 measurement (951 errors / 36,239) and the old external artifact-miss
labels are preserved in the historical reports; they are not substituted into the current
tables. No benchmark number is claimed without the recorded JMH JSON.
