# Learning experiment (test split)

Generated 2026-11-07T15:53:56+00:00. Research code only; no runtime model is shipped.

## Protocol

* P2 family holdout; inner-fold OOF training; outer dev threshold selection; test scored once. Primary selector = COUNTABLE/MIXED, non-invariant.
* primary records: 36239
* training examples: 170695 out-of-fold records (156825 eligible rule/fallback records)
* OOF manifest: `results\learning\oof\manifest.json`

## Candidate ceiling

* base accuracy: 97.238% (1001 errors)
* candidate-oracle accuracy: 99.230% (+1.992pp)
* required gain: 0.552pp -> cleared

## Selectors (threshold chosen on dev; test scored once)

| selector | seed | params | threshold | dev accuracy | dev overrides | test accuracy | test errors | corrections | regressions | 95% CI pp |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| keep_base | 0 | 0 | 1.00 | 97.165% | 0 | 97.238% | 1001 | 0 | 0 | [+0.0000, +0.0000] |
| linear | 0 | 4096 | 0.90 | 97.702% | 545 | 97.790% | 801 | 310 | 110 | [+0.4282, +0.6769] |

## Runtime gates

* model bytes: 16412 (gate 32.0 KiB)
* p99: 14.1 us (gate 10.0 us)
* initialization: 14.709 ms (gate 10.0 ms)
* overall: FAIL

## Decision

**reject** - learned selector did not clear the frozen gates; deterministic library ships

* test gain 0.5519pp below required 0.5524pp
* residual errors reduced by only 20.0% (need 20%)
* runtime gates failed: p99=False, size=True, init=False

