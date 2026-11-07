# Learning experiment (test split)

Generated 2026-11-07T06:28:59+00:00. Runtime: python research code only; no Java inference path.

## Candidate ceiling

* records: 36505
* deterministic C accuracy: 88.136% (4331 errors)
* candidate-oracle accuracy: 99.173% (+11.037pp)
* required gain: 2.373pp -> cleared

| edit | oracle hits |
| --- | --- |
| base:s | 29181 |
| s | 2651 |
| base:other | 1774 |
| base:es | 1181 |
| man->men | 293 |
| is->es | 285 |
| um->a | 236 |
| lemma | 186 |
| es | 115 |
| us->i | 97 |
| a->ae | 51 |
| ies | 46 |
| base:same | 38 |
| ix->ices | 18 |
| fe->ves | 18 |
| ves | 14 |
| child->ren | 8 |
| mouse->ice | 6 |
| person->people | 3 |
| quiz->zzes | 2 |

## Selectors

| selector | seed | params | dev threshold | test accuracy | errors | corrections | regressions | CI95 pp | invocation | overrides |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| linear | 0 | 4096 | 0.30 | 88.218% | 4301 | 48 | 18 | [+0.038, +0.128] | 100.0% | 0.3% |
| mlp-seed0 | 0 | 12589 | 0.00 | 88.136% | 4331 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |
| mlp-seed1 | 1 | 12589 | 0.00 | 88.136% | 4331 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |
| mlp-seed2 | 2 | 12589 | 0.00 | 88.136% | 4331 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |

## Decision

**reject** - learned selector did not clear the frozen gates; deterministic library ships

* gain 0.0822pp below required 2.3728pp
* residual errors reduced by only 0.7% (need 20%)
* runtime microsecond/size gates require a Java scorer and were not demonstrated

