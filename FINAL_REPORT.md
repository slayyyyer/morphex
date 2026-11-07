# Final report

Date: 2026-11-07. Every number below comes from a recorded run in this repository. Historical
audited-commit results are labelled separately and are not mixed into the current tables.

## What shipped

| item | state |
| --- | --- |
| `inflector-core` | Java 17+, immutable, thread-safe, no runtime dependencies |
| lexical artifact | 147,193 entries / 7,545,760 bytes TSV (1,176,616 bytes gzip) |
| membership table | 340,565 entries / 2,724,532 bytes, exact FNV-1a hashes |
| rules | three productive rules plus documented woman/wife/man/sis backoffs |
| compounds | exact entries, extended free tails, curated postposed heads, guarded head-final and verb-particle paths, multi-hyphen chains |
| uppercase | explicit metadata and overrides first; bounded inference for unknown 2-6 letter uppercase tokens |
| diagnostics | immutable `Inflection` with source, id, countability, alternatives, ambiguity, note |
| evaluation | eval-v2, exact ids, one primary selector, family-clustered boostrap, 2,000 iterations |
| offline pipeline | pinned dump -> validated data -> splits -> artifacts/membership -> predictions -> reports |
| learning | corrected five-fold OOF experiment; offline result rejected for runtime |
| example | runnable consumer in `inflector-example` |

No model, training dependency or network call is in the shipped runtime.

## P1 - frozen shipped lexicon

| system | primary (36,239) | overall (50,938) |
| --- | ---: | ---: |
| stock evo-inflector 2.0 | 96.473% (1,278 errors) | 68.882% (15,851) |
| this library | **100.000% (0)** | **100.000% (0)** |
| rules only | 95.168% (1,751) | 67.851% (16,376) |

Paired evo2 -> library: **+3.5266 pp**, 95% CI `[+3.3323, +3.7159]`, 1,278 corrections,
0 regressions. Source usage: 7,244 `lexicon:lexicon`, 28,995 `lexicon:rule`. P1 is
dictionary coverage, not generalization.

Historical audited-commit P1 was also 100% (0/36,239 primary, 0/50,938 overall), but with a
150,267-entry artifact and rules-only 88.714% (4,090). The old evaluator counted the TSV
header; current prediction files contain exactly 50,938 records.

## P2 - family holdout

| system | test primary (36,239) | dev primary (36,470) |
| --- | ---: | ---: |
| stock evo-inflector 2.0 | 96.473% (1,278) | 96.397% (1,314) |
| this library, train-only artifact | **97.376% (951)** | **97.398% (949)** |
| rules only | 95.168% (1,751) | 95.281% (1,721) |

Test paired gain: **+0.9023 pp** `[+0.7309, +1.0718]`, 540 corrections, 213 regressions,
**25.587%** relative error reduction. The historical parity target (<=1,278 errors) is
cleared by 327 errors. The compound slice fell from 180 to 130 test errors (50 corrected,
0 regressed) and from 177 to 92 dev errors (85 corrected, 0 regressed).

Test primary buckets:

| bucket | records | correct | errors | accuracy |
| --- | ---: | ---: | ---: | ---: |
| compound | 2,018 | 1,888 | 130 | 93.558% |
| initialism | 806 | 783 | 23 | 97.146% |
| simple | 33,147 | 32,364 | 783 | 97.638% |
| unicode | 268 | 253 | 15 | 94.403% |

Decision sources: `lexicon:compound` 27/1,915 errors; `lexicon:rule` 798/33,415;
`lexicon:inferred` 23/806; `lexicon:fallback` 103/103. Fallback records are unsupported
shapes, not silently rewritten.

Historical audited P2 was **88.714%** (4,090 errors), with 31,876/33,147 simple
non-invariant correct (96.166%) and 273/3,092 in the remainder (8.829%). Those numbers are
retained in `results/eval/p2-report.json` and are not comparable to the current engine,
artifact and contract.

## Artifact preservation

The compact artifact omits a known entry only when the final resolver reproduces its
required output and no lexical metadata is needed. A Python mirror is checked by the Java
`ArtifactAuditTest` over the full P1 evaluation, including policy cases. Current P1 is
0 primary and 0 overall errors; the audit fixture is green.

## External validation

UD English EWT, CC BY-SA 4.0, 2,184 lemmas / 2,221 forms; 368 lemmas present in the shipped
artifact, 1,816 unseen. Selected-output accuracy and alternative-set coverage are separate
metrics.

| system | selected overall | selected seen | selected unseen | alternative coverage |
| --- | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 98.12% | 94.29% | 98.90% | n/a |
| shipped lexicon | **98.21%** | 93.48% | **99.17%** | **98.76%** |
| rules only | **98.26%** | 93.75% | **99.17%** | 98.26% |

The 39 remaining lexicon errors include disputed countability identity returns, EWT typos
and rule limits. Nothing was tuned on this corpus. Full list:
`results/external-v2/ewt-report.md`.

## Strict-family sensitivity check

A supplementary `split-v2` run groups conservative derivational relatives before splitting
(for example `teacher`/`teaching`/`teach` and `runner`/`running`/`run`). The stem key is an
approximation, not a lemmatizer. The strict train-only artifact has 102,273 entries, and the
P2 primary denominators change to 36,520 dev and 37,507 test; these results are not
directly comparable to the primary `split-v1` table.

| split | evo-inflector 2.0 | this library |
| --- | ---: | ---: |
| P2 dev strict families (36,520) | 96.358% (1,330) | **97.125% (1,050)** |
| P2 test strict families (37,507) | 96.379% (1,358) | **97.238% (1,036)** |
| EWT family seen (1,677) | **98.09% (1,645)** | 98.03% (1,644) |
| EWT family unseen (507) | 98.22% (498) | **98.82% (501)** |

The strict test paired gain is +0.8585 pp `[+0.6868, +1.0287]`, with 536 corrections and
214 regressions. The default `split-v1` results remain the headline evaluation; full
details are in `results/strict-family-report.md`.

## Learning experiment

The corrected experiment uses five inner folds: each training family is predicted by an
artifact built without it, then the outer dev threshold is selected and test is scored once.
The OOF set has 238,810 records (170,695 primary, 156,825 eligible).

| quantity | value |
| --- | ---: |
| base accuracy (pre-compound run) | 97.238% (1,001 errors) |
| candidate-oracle accuracy | 99.230% |
| oracle gain | +1.992 pp |
| required gain | 0.552 pp |
| corrected linear selector | 97.790% (801 errors, 310 corrections / 110 regressions) |
| screening MLP | 97.243% (2 corrections, 0 regressions) |
| decision | **reject runtime inference** |

The linear selector misses the offline +0.5524 pp gain gate by 0.0005 pp. A dependency-free
Java scorer in `inflector-eval` now reproduces the selector exactly (0 prediction
mismatches; 801 errors, 310 corrections, 110 regressions). Against the strict-default
runtime gates, its 16,412-byte artifact passes size, but p99 latency (14.1 µs vs 10 µs) and
cold initialization (14.709 ms vs 10 ms) fail. The learning artifacts predate the compound
resolver changes; the selector was not re-fit, and the rejection decision is unchanged. The deterministic library ships. The original train-on-lexicon experiment
(linear 88.218%, MLP zero overrides) is preserved as historical evidence in
`docs/learning.md`.

## Validation

* `mvn verify` is the required JDK 17/21 gate; Java and Python regression tests cover
  parsing, casing, countability, compounds, uppercase inference, membership, candidate
  renderers and compaction.
* P1 and P2 reports use the frozen gold files and 2,000-iteration family bootstrap.
* `scripts/rebuild-and-evaluate.sh` reproduces the full pipeline, including `oof`.
* `data/raw/` is gitignored; without the pinned dump, corpus evaluation is unavailable and
  is never reported as passing.
* The recorded JMH run is kept in `docs/benchmarks.md`; it predates the final rule additions
  and is not claimed as a fresh benchmark.
* No full 20,000-record, three-epoch learning run completed in the available window; the
  bounded OOF run is reported as such.

## Limitations

* P2 remains dictionary-lemma generalization, not application-call accuracy.
* 103 P2 fallback records are unsupported and returned unchanged.
* External selected accuracy is only 0.09 pp ahead of evo2; the seen subset is lower.
* Distinct-word latency remains slower than stock; retained heap is higher than a small
  built-in table but lower than the earlier packed map.
* The default unknown-uppercase inference is a policy change; `UppercasePolicy.LITERAL`
  restores literal rendering.
* No human adjudication was performed.

## Bottom line

The deterministic library preserves P1 at 100% and improves P2 from 88.714% to **97.376%**
(951 errors), clearing the 1,278-error parity target by 327 errors and beating evo2 by
0.902 pp with a CI excluding zero. The corrected learning experiment shows additional offline headroom but
does not justify a runtime model. The main remaining gaps are unsupported fallback shapes,
head-final compounds with non-productive heads, and a small external-validation deficit on
seen lemmas.
