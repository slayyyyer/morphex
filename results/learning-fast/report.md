# Learning experiment (test split)

Generated 2026-11-07T03:15:23+00:00. Runtime: python research code only; no Java inference path.

## Candidate ceiling

* records: 36445
* deterministic C accuracy: 88.157% (4316 errors)
* candidate-oracle accuracy: 99.169% (+11.011pp)
* required gain: 2.369pp -> cleared

| edit | oracle hits |
| --- | --- |
| base | 32129 |
| s | 2655 |
| man->men | 293 |
| is->es | 285 |
| um->a | 236 |
| lemma | 165 |
| es | 116 |
| us->i | 97 |
| a->ae | 51 |
| ies | 46 |
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
| linear | 0 | 4096 | 0.65 | 88.157% | 4316 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |
| mlp-seed0 | 0 | 12541 | 0.00 | 88.157% | 4316 | 0 | 0 | [+0.000, +0.000] | 100.0% | 0.0% |

## Decision

**reject** - learned selector did not clear the frozen gates; deterministic library ships

* gain 0.0000pp below required 2.3685pp
* residual errors reduced by only 0.0% (need 20%)
* runtime microsecond/size gates require a Java scorer and were not demonstrated

