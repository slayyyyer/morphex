# Final report

Date: 2026-11-07. All numbers below come from runs recorded in this repository; nothing is
projected.

## What shipped

| item | state |
| --- | --- |
| `inflector-core` | working Java 17 library, no runtime dependencies, immutable, thread-safe |
| API | `builder()`, `mode()`, `casePolicy()`, `overrides()`, `initialisms()`, `build()`, `pluralize(String)`, `pluralize(String,int)`, `analyze(String)` |
| lexical artifact | 157,344 entries / 8,053,686 bytes TSV (1,253,478 bytes gzip), generated offline |
| rules | exactly three productive rules; all other irregularity is lexical |
| compounds | bounded exact entries, free tails, curated postposed heads, guarded space rule |
| diagnostics | immutable `Inflection` with source, id, countability, alternatives, ambiguity, note; no fake confidence |
| tests | 87 tests green on JDK 21 and JDK 17, including the pinned 100-row regression suite |
| offline pipeline | pinned dump → validated normalized data → splits → runtime artifact, stdlib Python |
| evaluator | three systems, fixed gold, paired family-clustered bootstrap, P1/P2 protocols |
| benchmarks | 12 JMH benchmarks with forks and GC profiler; raw JSON committed |
| learning experiment | candidate ceiling, linear + compact MLP selectors, calibration, seeds, explicit reject decision |
| example | runnable consumer (`inflector-example`) with recorded output |
| docs | README, compatibility/migration, data provenance/licensing, evaluation, benchmarks, learning, upstream probe |

Publication coordinates are provisional (`io.github.morphex:english-inflector:0.1.0-SNAPSHOT`)
and nothing was published.

## What was measured

### Correctness (protocol P1, frozen shipped lexicon, 34,933 primary records)

| system | accepted-form accuracy | errors |
| --- | --- | --- |
| stock evo-inflector 2.0 | 96.502% | 1,222 |
| repaired rules only | 88.618% | 3,976 |
| repaired rules + curated + Wiktionary artifact | **99.997%** | **1** |

Paired family-clustered bootstrap, evo2 → this library: **+4.1481 pp**, 95% CI
`[+3.9231, +4.3629]`, 1,461 corrections, 1 regression. Overall (including mass and
plural-only records) the library reaches 99.996% (2/50,938 errors); stock 2.0 reaches
68.825% because it pluralizes mass nouns. The two residual errors are both the documented
40-character lexical cap, not wrong morphology.

### Generalization (protocol P2, family holdout, train-only artifact)

88.618% for both rules and train-only lexicon, identical by construction: held-out families
have almost no lexical entries. This is the honest unseen-family number and is worse than
stock 2.0, which carries its exception table everywhere. No leakage: test families never
enter the artifact, training, threshold selection or feature design.

### Candidate ceiling and learning

| quantity | measured |
| --- | --- |
| candidate-oracle accuracy | 99.182% vs 88.022% base (+11.16 pp) |
| required gain | 2.396 pp (clears the ceiling test) |
| linear selector (D) | 88.110%, 42 corrections, 11 regressions, +0.088 pp |
| compact MLP (E, 3 seeds) | 88.022%, 0 overrides, 0 corrections |
| decision | **reject runtime neural inference** |

The ceiling says the inventory is sufficient; the trained selectors do not capture it. D
finds a few real corrections but misses the required gain by an order of magnitude. E
collapses to the deterministic base action in all three seeds and both screened
configurations. Runtime microsecond/memory/size gates were therefore not demonstrated. The
deterministic core ships; no model or training dependency is in the artifact.

### Performance (JDK 21, i5-12500H, JMH 1.37, 2 forks)

| workload | evo 2.0 | this library | allocation |
| --- | --- | --- | --- |
| repeated word | 109.6 ns/op | 83.5 ns/op | 35 B/op (vs 113) |
| distinct words | 163.2 ns/op | 395.2 ns/op | 29 B/op (vs 137) |
| mixed case | 184.1 ns/op | 404.2 ns/op | 51 B/op (vs 151) |
| count overload | 141.6 ns/op | 379.6 ns/op | 29 B/op (vs 137) |
| diagnostics | 246.1 ns/op (equivalent) | 480.8 ns/op | 189 B/op |

Repeated lookup is 0.76× (faster); distinct-word lookup is 2.4× slower because the shipped
structure is an object-heavy immutable `HashMap` rather than a packed trie. The final run did
not capture the concurrent benchmarks, so no concurrency number is claimed.

`StartupFootprint` (JDK 21) measured 309 ms to the first call, 1.64 ms for the first
pluralize, 0.69 µs/call over a steady 50k loop, and **55.5 MB retained heap** for 157,344
entries. The retained heap is the main footprint cost and the clearest follow-up work.

### Toolchain validation

* `./mvnw verify` green on Temurin **21.0.13** and **17.0.21**.
* Example consumer runs and its output is recorded in `results/example-run.txt`.
* The normal test run is offline: it reads the pinned regression TSV from
  `data/regression/` and the bundled artifact only.

## What remains hypothetical or blocked

* **Unseen-family accuracy**: 88.618%. P1's 99.997% is dictionary coverage, not
  generalization; the README says so.
* **Runtime neural inference**: no Java scorer, so latency/memory/artifact/init gates are
  undemonstrated. Rejected, not merely deferred.
* **Offline export**: not attempted, because there is no validated neural gain to export.
* **Performance parity on distinct-word workloads and retained heap**: 2.4× slower on
  distinct words and 55.5 MB retained, both traced to the object-per-entry map; not fixed.
* **Human review**: no records were externally adjudicated and no human reviewed the
  expectations; the docs say "author-reviewed", never "human-reviewed".
* **Corpus evaluation**: requires the 298 MB pinned dump, which is gitignored. Its absence
  makes that evaluation unavailable, never silently successful.
* **Git history**: the repository had no history at the start; no commit or push was made.

## Exact commands executed

```bash
# toolchain (checked-in wrapper uses the bootstrapped Maven)
export JAVA_HOME="$(pwd)/.tools/jdk-21.0.13+9"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -q verify
./mvnw -q -pl inflector-example exec:java

# data pipeline (offline from the pinned dump)
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v1.tsv

# evaluation
./mvnw -q -pl inflector-eval -am install -DskipTests
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds.tsv \
  --out results/eval/p1-report.json --split test --lexicon-split all

# stock probe
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.mainClass=io.github.morphex.inflector.eval.UpstreamProbe

# benchmarks
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 -w 1s -r 1s -prof gc \
     -rf json -rff results/benchmarks/jmh.json

# learning
py -3 -m tools.research.cli learn --split test --out results/learning
```

## Bottom line

A correct, compact, dependency-free pluralizer ships with a reproducible data pipeline and
measured evidence: 99.997% on frozen-lexicon coverage versus 96.502% for stock 2.0, and
88.618% honest unseen-family accuracy. The neural selector investigation is reproducible and
negative within its budget; the deterministic library is the deliverable, not a fallback.
