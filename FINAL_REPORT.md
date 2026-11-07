# Final report

Date: 2026-11-07. Current numbers come from the recorded v2 runs under
`results/release-1.2.0/`. Historical audited-commit results are labelled separately.

## What shipped

| item | state |
| --- | --- |
| `inflector-core` | Java 17+, immutable, thread-safe, no runtime dependencies |
| lexical artifact | 147,338 entries / 7,553,688 bytes TSV |
| membership table | 340,741 lemmas / 2,725,940 bytes |
| rules | three productive rules plus documented woman/wife/man/sis backoffs |
| compounds | provenance-aware heads, bounded depth 4, coherent whole-compound casing |
| uppercase | lexical membership checked before bounded inference; explicit/LITERAL policies |
| diagnostics | immutable `Inflection` with source, id, countability, alternatives, ambiguity, note |
| evaluation | P1/P2 with 2,000-round family bootstrap, corrected external membership labels |
| website | `site/catalog.json` generated from the Java engine; no browser pluralizer |
| benchmark | fixed `benchmark-workload-v1.tsv` covering all required categories |
| release evidence | `results/release-1.2.0/EVIDENCE.md` and checksum manifest |

No model, training dependency or network call is in the shipped runtime.

## P1 - frozen shipped lexicon

| system | primary (36,241) | overall (50,978) |
| --- | ---: | ---: |
| stock evo-inflector 2.0 | 96.474% (1,278 errors) | 68.832% (15,889) |
| this library | **100.000% (0)** | **100.000% (0)** |
| rules only | 95.163% (1,753) | 67.798% (16,416) |

Paired evo2 -> library: **+3.5264 pp**, 95% CI `[+3.3318, +3.7132]`, 1,278 corrections,
0 regressions. Source usage: 7,245 `lexicon:lexicon`, 28,996 `lexicon:rule`. P1 is
dictionary coverage, not generalization.

Historical audited-commit P1 was also 100% (0/36,239 primary, 0/50,938 overall), with a
150,267-entry artifact and rules-only 88.714% (4,090). It remains preserved as historical
evidence and is not mixed into the current denominator.

## P2 - family holdout

| system | test primary (36,241) | dev primary (36,473) |
| --- | ---: | ---: |
| stock evo-inflector 2.0 | 96.474% (1,278) | 96.400% (1,313) |
| this library, train-only artifact | **97.365% (955)** | **97.382% (955)** |
| rules only | 95.163% (1,753) | 95.257% (1,730) |

Test paired gain: **+0.8913 pp** `[+0.7355, +1.0531]`, 538 corrections, 215 regressions,
**25.274%** relative error reduction. Dev gain is +0.9815 pp `[+0.8079, +1.1374]`, with 543
corrections and 185 regressions. The historical 1,278-error parity target is cleared by
**323 errors**.

**Baseline disclosure.** The previous v1 P2 test result was 951 errors on 36,239 primary
records (97.376%). The current result is 955 errors on 36,241 records (97.365%): **4 more
errors** and two more primary records. Simple errors fell from 827 to 783; compound errors
rose from 92 to 134. The change is reported, not hidden.

Test primary buckets:

| bucket | records | correct | errors | accuracy |
| --- | ---: | ---: | ---: | ---: |
| compound | 2,018 | 1,884 | 134 | 93.360% |
| initialism | 806 | 783 | 23 | 97.146% |
| simple | 33,149 | 32,366 | 783 | 97.638% |
| unicode | 268 | 253 | 15 | 94.403% |

Decision sources: `lexicon:compound` 31/1,915 errors; `lexicon:rule` 798/33,417;
`lexicon:inferred` 23/806; `lexicon:fallback` 103/103. Fallback records are unsupported
shapes, not silently rewritten.

## Artifact preservation

The compact artifact omits a known entry only when the final resolver reproduces its
required output and no lexical metadata is needed. The Java artifact audit and Python mirror
are checked against the P1 records, including policy cases. Current P1 is 0 primary and 0
overall errors.

## External validation

UD English EWT, CC BY-SA 4.0, 2,184 lemmas. **Full-vocabulary membership**, not compact
artifact presence, now defines seen/unseen. 2,148 lemmas are seen and only 36 are genuinely
unseen; 363 are compact-artifact hits and 1,821 are misses.

| system | selected overall | full-vocab seen | full-vocab unseen | artifact hit | artifact miss | alternative coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 98.12% | 98.23% | 91.67% | 94.21% | 98.90% | n/a |
| shipped lexicon | **98.76%** | **98.88%** | 91.67% | **96.69%** | **99.18%** | **98.99%** |
| rules only | 98.26% | 98.37% | 91.67% | 93.66% | **99.18%** | 98.26% |

The 27 remaining lexicon errors: 4 identity returns on disputed countability, 24
full-vocabulary-seen lemmas, 3 genuinely unseen lemmas, 12 artifact hits; 16 rule decisions
and 11 lexical decisions. Nothing was tuned on this corpus. Full list:
`results/release-1.2.0/external/ewt-report.md`.

## Benchmarks

The fixed workload is `inflector-benchmarks/src/main/resources/benchmark-workload-v1.tsv`,
covering lexical hits, compacted regular words, unknown inputs, uppercase inference,
compounds, and unsupported input. JMH reports and startup/memory/concurrency details are in
`docs/benchmarks.md` and `results/release-1.2.0/benchmarks/`. No speedup is claimed beyond the
same-run measurements.

## Validation

* `mvn -o -pl inflector-core test` passed after the final resource removal.
* All 44 Python extract/lexicon/split/external tests passed.
* P1, P2 dev and P2 test reports used frozen gold files and 2,000-round family bootstrap.
* `scripts/release-evidence.sh` emits a checksum manifest for inputs, predictions, reports,
  the Java catalog, and benchmark data.
* `data/raw/` is gitignored; without the pinned dump, corpus evaluation is unavailable and
  is never reported as passing.
* The benchmark workload is fixed and versioned; it is not sampled at run time.

## Limitations

* P2 remains dictionary-lemma generalization, not application-call accuracy.
* 103 P2 fallback records are unsupported and returned unchanged.
* Current P2 is 4 errors above the historical v1 951-error measurement, though far below
  evo2’s 1,278.
* The genuinely unseen external slice is only 36 lemmas; no strong unseen-generalization
  claim is made.
* Compound misses remain concentrated in unsupported fixed phrases and non-productive heads.
* No human adjudication was performed.

## Bottom line

The deterministic library preserves P1 at 100%, scores 97.365% on P2 (955 errors), and clears
the 1,278-error evo2 parity target by 323 errors. The 1.2.0 work corrects compound
provenance/casing, plural-only extraction, preferred plural stability, external membership
labelling, and website provenance. The measured P2 change from 951 to 955 errors is reported
explicitly rather than hidden.
