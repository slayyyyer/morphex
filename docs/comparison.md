# Comparison with evo-inflector 2.0

Head-to-head of this library against stock `org.atteo:evo-inflector:2.0` (Maven Central),
measured in this repository. Nothing below is projected; see the linked reports and raw
outputs for reproduction.

| detail source | location |
| --- | --- |
| stock outputs reproduced here | `docs/upstream-probe.md`, `results/upstream-probe.tsv` |
| accuracy protocols P1/P2 + external UD EWT | `docs/evaluation.md`, `results/eval/`, `results/external/` |
| latency / allocation / footprint | `docs/benchmarks.md`, `results/benchmarks/jmh-packed.json` |
| API and migration | `docs/compatibility.md` |
| engine internals | `docs/design.md` |
| Python baseline (`inflect`) | `docs/python-baseline.md` |

## Headline numbers

| dimension | stock evo-inflector 2.0 | this library |
| --- | --- | --- |
| P1 frozen-lexicon accepted-form accuracy (36,239 primary records) | 96.473% (1,278 errors) | **100.000%** (0 errors) |
| P1 paired gain (family-clustered bootstrap 95% CI) | — | **+4.1611 pp** `[+3.9597, +4.3727]`, 1,519 corrections, 0 regressions |
| P2 family-holdout accuracy (unseen families, train-only artifact) | 96.473% (carries its exception table) | 88.714% — honest unseen-family number, worse by construction (see below) |
| External UD English EWT overall (2,184 lemmas, no retraining) | 98.12% | **98.76%** |
| External EWT unseen lemmas (1,808) | 98.89% | **99.17%** |
| External EWT seen lemmas (376) | 94.41% | **96.81%** — former defect fixed (see §3) |
| Repeated-word lookup (JMH, JDK 21) | 98.2 ns/op, 113 B/op | **81.7 ns/op**, 35 B/op (0.83×, faster) |
| Distinct-word lookup | 150.7 ns/op, 137 B/op | 201.1 ns/op, 29 B/op (**1.34× slower**, lower allocation) |
| Retained lexicon heap | n/a (small built-in table) | 24.1 MB for 150,267 entries (was 55.5 MB before packing) |
| Runtime dependencies | none | none |

## 1. Why the accuracy differs

Two structural root causes in stock 2.0, both reproduced in `docs/upstream-probe.md`:

1. **Lookup/casing conflation.** The exception table is reached lower-cased but the result
   is re-cased as a suffix edit: `German → Germen`, `CHILD → CHILDren`, `CITY → CITIes`.
   This library separates lookup normalization (`WordShape.lower`) from output case
   restoration (`render`/`renderFull`), so `CHILD → CHILDREN`, `CITY → CITIES`,
   `German → Germans`.
2. **Lexical exceptions expressed as suffix rules.** `carp` (invariant) makes `scarp`
   invariant; `louse → lice` reaches `blouse → blice`. This library stores lexical
   exceptions as whole-word edits and allows only three productive suffix rules
   (`+es` after sibilants, consonant-`y → ies`, default `+s`), so `scarp → scarps` and
   `blouse → blouses`.

Compounds are the third systematic gap: stock 2.0 inflects the wrong constituent
(`mother-in-laws`, `passer-bies`, `drive-bies`, `attorney generals`). This library uses a
bounded, reviewed compound table (exact entries, free tails, curated postposed heads) and
refuses to guess otherwise (`well-known`, `jack in the box` returned unchanged).

P1's 100.000% is **dictionary coverage**, not generalization: the shipped artifact covers the
test families. P2 (family holdout, test families excluded from lexicon, training, mining
and calibration) is the honest unseen-family number at 88.714%, where stock 2.0's built-in
exception table still applies. That inversion is expected and documented, not hidden.

## 2. Behaviour table

### Correctness repairs (stock output wrong, fixed here)

| input | stock 2.0 | this library |
| --- | --- | --- |
| `German` | `Germen` | `Germans` |
| `blouse` | `blice` | `blouses` |
| `scarp` | `scarp` | `scarps` |
| `UFO` | `UFOes` | `UFOs` |
| `mother-in-law` | `mother-in-laws` | `mothers-in-law` |
| `passer-by` | `passer-bies` | `passers-by` |
| `CHILD` | `CHILDren` | `CHILDREN` |
| `CITY` | `CITIes` | `CITIES` |
| `drive-by` | `drive-bies` | `drive-bys` |
| `attorney general` | `attorney generals` | `attorneys general` |

### Intentional changes (not stock bugs; scope or policy choices)

| input | stock 2.0 | this library | reason |
| --- | --- | --- | --- |
| `information` | `informations` | `information` | mass-noun policy, reported as `MASS` |
| `scissors` | `scissors` | `scissors` | plural-only, reported as `PLURAL_ONLY` |
| `person` | `persons` | `people` (accepts `persons`) | preferred form modelled; both accepted |
| `die` | `dies` | `dice` (accepts `dies`, `ambiguous`) | spelling cannot choose the sense |
| `SMS` | `SMSes` | `SMSes` | deliberate: final-`S` initialism takes `es` |
| `user_id`, `3.14`, `mother's` | variously rewritten | unchanged, `FALLBACK` | identifiers/numbers/possessives out of scope |
| `well-known`, `jack in the box` | guessed or passed | unchanged, `FALLBACK` | unmatched compounds not guessed |

Full migration recipe (static `English.plural` → shared `EnglishInflector`, caller
overrides, case policy, initialisms, rules-only build) is in `docs/compatibility.md`.
There is deliberately no `org.atteo.evo.inflector` API shim: a shim would put two copies
of the same API on the benchmark classpath.

## 3. Qualifications (read before citing the headline)

* **P2 is worse than stock.** 88.714% vs 96.473%. P1 measures a frozen dictionary; P2
  measures unseen families. Both are reported; only P1 is the headline.
* **External seen-subset failure — fixed.** Was 77.09% on 454 present lemmas because common
  count nouns (`book`, `bag`, `account`) were misclassified as `MASS`/`PLURAL_ONLY` and
  returned unchanged. Three extraction defects caused it (sense-level `plural-normally`,
  subordinate `uncountable` senses, first-in-dump-order multi-etymology labels) plus curated
  `MASS` rows for `money`/`music`/`traffic`. Now 96.81% on 376 present lemmas with 27 errors
  left, mostly EWT annotation noise and disputed subordinate uses; see `docs/evaluation.md`.
  Nothing was tuned on the external set.
* **Distinct-word latency.** 1.34× slower than stock 2.0 (improved from 2.4× before the
  packed layout). Repeated-word lookup is faster (0.83×) at lower allocation in all
  workloads. Concurrent throughput was not captured in the final run — no claim is made.
* **Residual P1 errors:** none — the former 40-character cap is lifted, so the artifact
  covers all validated lemmas.

## 4. When to choose which

* Stay on stock 2.0 if you need a tiny built-in table, a static API, and can tolerate the
  rows in §2.
* Choose this library if you need the §2 repairs, explicit casing/countability policies,
  compound handling, a diagnostic API (`analyze`: source, id, alternatives, ambiguity),
  per-instance overrides/initialisms/modes, and are willing to carry a 7.7 MB TSV
  (1.21 MB gzip, ~25 MB retained) — or use the rules-only build to drop the data entirely
  at the cost of irregular forms.

## Reproduce

```bash
# stock probe
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.mainClass=io.github.slayyyyer.morphex.inflector.eval.UpstreamProbe

# accuracy (offline from the pinned dump; see docs/evaluation.md)
./mvnw -q -pl inflector-eval -am install -DskipTests
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds.tsv \
  --out results/eval/p1-report.json --split test --lexicon-split all

# benchmarks
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 -w 1s -r 1s -prof gc \
     -rf json -rff results/benchmarks/jmh.json
```
