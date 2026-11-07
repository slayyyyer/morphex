# Learning experiment

This is the required neural-learning investigation, not a production dependency. The shipped
library contains no model, no training code and no Python. Everything here is offline
research code in `tools/research/`.

Question: can a learned candidate selector beat the repaired deterministic system **on the
leakage-free protocol P2**, within a declared budget, by enough to justify a runtime
dependency?

## Design

* **Protocol P2 only.** Test-fraction lemma families never appear in the train-only artifact
  (110,627 entries), the training records, the threshold sweep, or feature design.
* **Identical D and E.** The non-neural selector (D) and the neural selector (E) share the
  same candidate inventory, the same multi-positive softmax objective, the same training
  records, and the same renderer. Neither gets a larger dictionary.
* **Accepted alternatives are multi-positive targets**, so predicting `persons` where
  `people` is preferred is not punished.
* **Routing gate is pre-inference**: only records whose deterministic source is a rule or
  fallback are scored. Lexical hits never invoke the model, so an override can never damage a
  trusted lexical answer.
* **Threshold selection on dev only**, then frozen and applied once to test.
* **Candidates are generated from lemma shape and suffix**, plus the deterministic base
  result, which is always present.

### Learners

| id | model | configuration |
| --- | --- | --- |
| D | sparse linear ranker | 4,096 hashed features (`edit`, suffix×edit, length×edit), SGD, lr 0.35, L2 1e-6 |
| E | compact MLP | 1,024 hashed character trigrams + edit embedding (22 ids), width 12, tanh, SGD, lr 0.08 |

Parameters: D = 4,096 weights; E = 1,024×12 + 22×12 + 12×3 + 1 = **12,589** — about one
eighth of the proposed ~100k-parameter budget, deliberately small.

### Declared budget (actual)

| item | budget | actual |
| --- | --- | --- |
| configurations per learner | ≤ 2 | 2 screened on dev; 1 final |
| seeds | 3 | 3 for the MLP; linear seed 0 (deterministic init) |
| CPU-hours of fitting | ≤ 8 | see report (`train_seconds` per selector) |
| newly adjudicated records | ≤ 2,000 | 0 (no human adjudication was performed) |

Configuration screening used **dev only**: 6,000 records × 3 epochs gave +2.60 pp on dev;
20,000 × 5 gave +1.08 pp (figures from the original dev screening; the frozen 6,000×3
configuration was re-applied unchanged after the extraction fix). No test
label was used for any choice.

## Candidate ceiling (P2 test)

| quantity | value |
| --- | --- |
| records | 36,505 |
| deterministic C accuracy | 88.136% |
| candidate-oracle accuracy | 99.173% |
| oracle gain | +11.037 pp |
| required gain | 2.373 pp |
| decision | ceiling clears the gate; train the selector |

The ceiling clears the required gain, so no pretraining stop applies. (A ranker cannot
recover a missing irregular or compound transform; the oracle inventory includes the base
result, three suffix edits, and a curated set of irregular edits.)

## Results (P2 test, thresholds frozen on dev)

| selector | seed | params | dev threshold | test accuracy | errors | corrections | regressions | 95% CI (pp) | overrides |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D. linear | 0 | 4,096 | 0.30 | 88.218% | 4,301 | 48 | 18 | [+0.038, +0.128] | 0.3% |
| E. MLP | 0 | 12,589 | 0.00 | 88.136% | 4,331 | 0 | 0 | [0.000, 0.000] | 0.0% |
| E. MLP | 1 | 12,589 | 0.00 | 88.136% | 4,331 | 0 | 0 | [0.000, 0.000] | 0.0% |
| E. MLP | 2 | 12,589 | 0.00 | 88.136% | 4,331 | 0 | 0 | [0.000, 0.000] | 0.0% |

Baseline C on the same records: 88.136% (4,331 errors of 36,505). Invocation rate is 100% of
the gated rule/fallback records (the gate excludes lexical hits; no lexical answer was ever
overridden).

### What happened

* **D (linear)** learned a small number of real corrections (+48, -18), a net +0.082 pp. That
  is far below the required 2.373 pp and the 11.04 pp oracle ceiling.
* **E (MLP)** collapsed to the majority action: it ranked the deterministic base first for
  every gated record, at every dev threshold, so it made **zero overrides**. A model that
  never deviates cannot correct anything. Both 12,589-parameter configurations tested during
  dev screening (lr 0.08/3 epochs; lr 0.20/5 epochs, width 16) showed the same behaviour.
* The result is a genuine negative outcome for this compact ranker and budget, not evidence
  that neural selection is impossible: the candidate oracle shows +11.01 pp is reachable if a
  selector could choose among the same 20 candidates. The gap is a modelling/optimisation
  failure at this scale, and is reported as such rather than smoothed over.

### Decision: reject runtime neural inference

The frozen gates require 20% fewer residual errors **and** +0.05 pp, with a CI excluding
zero. D gives +0.082 pp and 0.7% fewer errors; E gives 0. Neither comes close. In addition,
no Java scorer exists, so the microsecond p99, memory, artifact-size and init gates are not
demonstrated at all.

The deterministic library ships unchanged. No model, training dependency or Python code is
on the consumer classpath. An offline export was not attempted because there is no validated
neural gain to export.

## What this does and does not show

* It measures selector quality on dictionary lemmas under a family holdout. It is not an
  application-call accuracy claim.
* No Java inference path exists, so the microsecond, memory, size and initialization gates
  are **not demonstrated**. That alone prevents a runtime `ship` decision.
* No new records were adjudicated, so no human-review claim is made.
* The deterministic core is unaffected by this result and ships regardless.
