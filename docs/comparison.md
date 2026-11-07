# Comparison with evo-inflector 2.0

Head-to-head against stock `org.atteo:evo-inflector:2.0` on the same frozen labels.
Nothing below is projected. Historical audited-commit results are labelled separately.

| evidence | location |
| --- | --- |
| stock outputs | `docs/upstream-probe.md`, `results/upstream-probe.tsv` |
| P1/P2 results | `docs/evaluation.md`, `results/release-1.2.0/eval/` |
| external EWT | `results/release-1.2.0/external/ewt-report.md` |
| external AGID stress test | `results/release-1.2.0/external/agid-report.md` |
| latency / footprint | `docs/benchmarks.md`, `results/release-1.2.0/benchmarks/` |
| API and migration | `docs/compatibility.md` |

## Headline numbers

| dimension | stock evo-inflector 2.0 | this library |
| --- | --- | --- |
| P1 primary accepted-form accuracy (36,241) | 96.474% (1,278) | **100.000%** (0) |
| P1 overall (50,978) | 68.832% (15,889) | **100.000%** (0) |
| P1 paired gain | - | **+3.5264 pp** `[+3.3318, +3.7132]`, 1,278/0 |
| P2 family-holdout primary (36,241) | 96.474% (1,278) | **97.365%** (955) |
| P2 paired gain | - | **+0.8913 pp** `[+0.7355, +1.0531]`, 538/215 |
| External EWT selected overall (2,184) | 98.12% | **98.76%** |
| External EWT full-vocab seen (2,148) | 98.23% | **98.88%** |
| External EWT artifact hit (363) | 94.21% | **96.69%** |
| External EWT alternative coverage | n/a | **98.99%** |
| External AGID full-vocab unseen (261) | 95.02% | **95.40%** |
| External AGID selected overall (34,642) | 97.48% | 93.84%* |
| Lexicon artifact | small built-in table | 147,338 entries, 7.55 MB TSV, 1.18 MB gzip |
| Retained lexicon heap | n/a | ~18.2 MiB (19.08 MB, packed) |
| Runtime dependencies | none | none |

P1 is dictionary coverage, not generalization. P2 is the leakage-free measurement and is
ahead of evo2 despite forbidding test families from the artifact. Current P2 is **4 errors
above the historical v1 measurement** (955 vs 951) on two more primary records; that change
is reported rather than hidden. The strict-family sensitivity experiment is historical and
is not part of the current headline.

\* AGID 2016.01.19 is an automatically generated, permissively licensed lexical stress test,
not a hand-adjudicated corpus. Its 34,642 lemmas include 261 full-vocabulary-unseen lemmas,
where the library is tied with rules-only at 95.40% and one lemma ahead of evo2. The lower
overall AGID score is caused by 1,722 `MASS`/invariant identity returns against generated
plurals; it is not evidence of a natural-text regression.

## Why the difference

Stock 2.0 conflates lookup normalization with output casing (`German -> Germen`,
`CHILD -> CHILDren`, `CITY -> CITIes`) and expresses lexical exceptions as suffix rules
(`carp` makes `scarp` invariant; `louse` makes `blouse -> blice`). This library separates
lookup from rendering and stores exceptions as whole-word entries. It also adds:

* bounded head-final noun compounds, including multi-hyphen chains and productive head respellings (`credit-card -> credit-cards`, `great-great-grandfather -> great-great-grandfathers`, `ice-shelf -> ice-shelves`, `on-ramp -> on-ramps`);
* verb-particle compounds (`bake-off -> bake-offs`, `blow-in -> blow-ins`, `warm-up -> warm-ups`);
* documented `-woman -> -women`, `-wife -> -wives`, Greek `-sis -> -ses`, and guarded
  noun+`man -> men` backoffs;
* unknown-uppercase inference, default `UppercasePolicy.INFER_INITIALISM`, with
  `UppercasePolicy.LITERAL` as an opt-out.

## Behaviour table

### Correctness repairs

| input | stock 2.0 | this library |
| --- | --- | --- |
| `German` | `Germen` | `Germans` |
| `blouse` | `blice` | `blouses` |
| `scarp` | `scarp` | `scarps` |
| `CHILD` | `CHILDren` | `CHILDREN` |
| `CITY` | `CITIes` | `CITIES` |
| `mother-in-law` | `mother-in-laws` | `mothers-in-law` |
| `passer-by` | `passer-bies` | `passers-by` |
| `drive-by` | `drive-bies` | `drive-bys` |
| `bake-off` | `bake-offs` (stock often unchanged) | `bake-offs` |
| `credit-card` | unchanged in the stock probe | `credit-cards` |

### Intentional policy choices

| input | stock 2.0 | this library | reason |
| --- | --- | --- | --- |
| `information` | `informations` | `information` | mass-noun policy, `MASS` |
| `scissors` | `scissors` | `scissors` | plural-only, `PLURAL_ONLY` |
| `person` | `persons` | `people` (accepts `persons`) | preferred form; both accepted |
| `die` | `dies` | `dice` (accepts `dies`) | sense cannot be chosen from spelling |
| `SMS` | `SMSes` | `SMSes` | attested final-S initialism |
| `API` | `APIS` | `APIs` | inferred initialism, documented policy |
| `CAT` | `CATS` | `CATS` | ordinary uppercase noun, not an initialism |
| `well-known` | guessed/passed | unchanged, `FALLBACK` | unsupported compound |
| `user_id`, `3.14`, `mother's` | variously rewritten | unchanged, `FALLBACK` | out of scope |

## Qualifications

* **P2 is still a dictionary-lemma protocol.** It is not application-call accuracy.
* **P1 is coverage.** The 100% is not evidence that unseen compounds or irregulars are
  solved; P2 is the generalization number.
* **External labels were corrected.** The old 368/1,816 split was compact-artifact presence,
  not vocabulary membership. Full-vocabulary membership gives 2,148 seen EWT lemmas (98.88%
  for the lexicon) and only 36 genuinely unseen EWT lemmas (91.67%, tied with both baselines).
  Compact-artifact hit is 363 lemmas at 96.69%. Overall selected accuracy is 98.76% vs
  98.12%; alternative coverage is 98.99%. AGID adds 261 unseen lemmas at 95.40% for the
  lexicon and rules-only, but it is a generated lexicon and its overall score is not a
  corpus-accuracy claim.
* **Latency is mixed, not universally improved.** Repeated and exact lexical lookups are
  faster in the fixed-workload run. Targeted optimization improved unknown tokens from
  906.1 to 608.6 ns, compounds from 499.7 to 442.7 ns, and compacted words from 456.4 to
  187.3 ns, then to 105.0 ns after the ASCII fast path and 79.4 ns after flattening the rule
  dispatch. They remain 7.57x, 4.62x and 1.33x slower than evo2; uppercase (7.92x) and
  unsupported (4.89x) remain release-baseline figures. Four-thread throughput is 4.34x lower.
  The after figures are separate targeted reruns, not the full release run. Retained heap is
  lower.
* **Unknown-uppercase inference is a default policy change.** It applies only after lexical
  lookup, only to 2-6 upper-case Latin letters, and can be disabled with
  `UppercasePolicy.LITERAL`.

## Reproduce

```bash
./mvnw -q -o -pl inflector-eval -am install -DskipTests
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/release-1.2.0/eval/p1-test-records.tsv --out results/release-1.2.0/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/release-1.2.0/eval/p1-test-gold.tsv \
  --predictions results/release-1.2.0/eval/p1-test-preds.tsv \
  --out results/release-1.2.0/eval/p1-report.json --split test --lexicon-split all --iterations 2000
```

See `docs/evaluation.md` for P2, external validation and the full driver.
