# Learning experiment (test split)

Generated 2026-11-07T03:41:23+00:00. Runtime: python research code only; no Java inference path.

## Candidate ceiling

* records: 35197
* deterministic C accuracy: 88.022% (4216 errors)
* candidate-oracle accuracy: 99.182% (+11.160pp)
* required gain: 2.396pp -> cleared

| edit | oracle hits |
| --- | --- |
| base:s | 28340 |
| s | 2612 |
| base:other | 1586 |
| base:es | 1019 |
| man->men | 293 |
| is->es | 254 |
| um->a | 236 |
| lemma | 162 |
| es | 112 |
| us->i | 95 |
| a->ae | 51 |
| ies | 45 |
| base:same | 36 |
| ix->ices | 18 |
| fe->ves | 17 |
| ves | 14 |
| child->ren | 8 |
| mouse->ice | 6 |
| person->people | 3 |
| quiz->zzes | 2 |

## Selectors

| selector | seed | params | dev threshold | test accuracy | errors | corrections | regressions | CI95 pp | invocation | overrides |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| linear | 0 | 4096 | 0.40 | 88.110% | 4185 | 42 | 11 | [+0.040, +0.134] | 100.0% | 0.3% |
| mlp-seed0 | 0 | 12589 | 0.00 | 88.022% | 4216 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |
| mlp-seed1 | 1 | 12589 | 0.00 | 88.022% | 4216 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |
| mlp-seed2 | 2 | 12589 | 0.00 | 88.022% | 4216 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |

## Decision

**reject** - learned selector did not clear the frozen gates; deterministic library ships

* gain 0.0881pp below required 2.3957pp
* residual errors reduced by only 0.7% (need 20%)
* runtime microsecond/size gates require a Java scorer and were not demonstrated

