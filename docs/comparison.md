# Comparison with evo-inflector 2.0

Head-to-head against stock `org.atteo:evo-inflector:2.0` on the same frozen labels.
Nothing below is projected. Historical audited-commit results are labelled separately.

| evidence | location |
| --- | --- |
| stock outputs | `docs/upstream-probe.md`, `results/upstream-probe.tsv` |
| P1/P2 results | `docs/evaluation.md`, `results/eval/*-final.json` |
| external EWT | `results/external-v2/ewt-report.md` |
| latency / footprint | `docs/benchmarks.md`, `results/benchmarks/` |
| API and migration | `docs/compatibility.md` |

## Headline numbers

| dimension | stock evo-inflector 2.0 | this library |
| --- | --- | --- |
| P1 primary accepted-form accuracy (36,239) | 96.473% (1,278) | **100.000%** (0) |
| P1 overall (50,938) | 68.882% (15,851) | **100.000%** (0) |
| P1 paired gain | - | **+3.5266 pp** `[+3.3323, +3.7159]`, 1,278/0 |
| P2 family-holdout primary (36,239) | 96.473% (1,278) | **97.376%** (951) |
| P2 paired gain | - | **+0.9023 pp** `[+0.7309, +1.0718]`, 540/213 |
| External EWT selected output (2,184) | 98.12% | **98.21%** |
| External EWT alternative coverage | n/a | **98.76%** |
| P2 strict-family test (37,507) | 96.379% (1,358) | **97.238%** (1,036) |
| External EWT strict-family unseen (507) | 98.22% (498) | **98.82%** (501) |
| Lexicon artifact | small built-in table | 147,193 entries, 7.5 MB TSV, 1.18 MB gzip |
| Retained lexicon heap | n/a | 24-25 MB (packed) |
| Runtime dependencies | none | none |

P1 is dictionary coverage, not generalization. P2 is the leakage-free measurement and is
now ahead of evo2 despite forbidding test families from the artifact.

The two strict-family rows are a supplementary robustness check using `split-v2`; its
derivational grouping changes the P2 denominators, so they are not directly comparable to
the `split-v1` rows above. The default protocol and headline remain `split-v1`.

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
* **External seen subset is mixed.** Lexicon selected accuracy is 93.48% on 368 lemmas;
  rules are 93.75% and evo2 94.29%. Overall selected accuracy is 98.21% vs 98.12%, a small
  lead, while alternative coverage is 98.76%.
* **Distinct-word latency remains slower than stock** (about 1.38x in the fresh JMH run;
  1.34x in the earlier profiled run).
  Repeated-word lookup is faster; retained heap is lower.
* **Unknown-uppercase inference is a default policy change.** It applies only after lexical
  lookup, only to 2-6 upper-case Latin letters, and can be disabled with
  `UppercasePolicy.LITERAL`.

## Reproduce

```bash
./mvnw -q -o -pl inflector-eval -am install -DskipTests
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds-v4.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds-v4.tsv \
  --out results/eval/p1-report-final.json --split test --lexicon-split all --iterations 2000
```

See `docs/evaluation.md` for P2, external validation and the full driver.
