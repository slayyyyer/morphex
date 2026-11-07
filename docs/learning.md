# Learning experiment

This is an offline research experiment. The shipped library contains no model, no training
code and no Python. The question is whether a learned candidate selector can beat the
deterministic P2 system by enough to justify a runtime dependency.

A dependency-free Java mirror of the corrected linear selector lives in `inflector-eval` and is
evaluation-only; it is not on the consumer classpath.

## Corrected design

The first experiment trained on predictions from a lexicon that already contained the
training words. Its base was usually correct by lookup, so it mostly learned to keep a
lexical answer that would not exist on an unseen word. This version fixes that mismatch:

1. training families are split into five deterministic inner folds;
2. each fold's artifact is built from the other folds only;
3. the excluded fold is predicted with the real Java engine;
4. the combined out-of-fold predictions are the training input;
5. thresholds and configurations are selected on outer dev data only;
6. the frozen selector is scored once on test.

The OOF run covers 238,810 training records. After the primary selector and the production
eligibility gate, 170,695 records are primary and 156,825 are eligible rule/fallback
records. The gate excludes lexical hits, so a selector can never override a trusted lexical
answer. The candidate inventory is generated from lemma shape/suffix plus the deterministic
base; base identity and a rule that renders the same form remain separate candidates.

## Candidate ceiling (P2 test, pre-compound run)

| quantity | value |
| --- | ---: |
| primary records | 36,239 |
| base accuracy (pre-compound run) | 97.238% (1,001 errors) |
| candidate-oracle accuracy | 99.230% |
| oracle gain | +1.992 pp |
| required gain (20% fewer errors, >=0.05 pp) | 0.552 pp |
| ceiling clears | yes |

## Results

The corrected linear selector was trained and selected on dev only. The final bounded run
(20,000 OOF records, three epochs, seed 0, no MLP) selected threshold 0.90 and produced:

| selector | dev accuracy | dev overrides | test accuracy | test errors | corrections | regressions | 95% CI (pp) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| keep-base (pre-compound run) | 97.165% | 0 | 97.238% | 1,001 | 0 | 0 | [0.0000, 0.0000] |
| linear, final | **97.702%** | 545 | **97.790%** | **801** | **310** | **110** | **[+0.4282, +0.6769]** |

An earlier 3,000-record/one-epoch screening run selected threshold 0.40 and reached
97.917% test (755 errors, 423/177); the larger run is the reported result. The screening MLP
(97.243%, 2 corrections, 0 regressions) added no material gain.

These learning artifacts predate the compound resolver changes; the selector was not
re-fit, and the rejection decision is unchanged.

The linear selector reduces test errors by **20.0%**, but the measured gain is **0.5519 pp**
against a required **0.5524 pp** - it misses the frozen threshold by 0.0005 pp. The MLP
screening collapsed to keeping the base and added no material gain. These numbers are from
the bounded 20,000-record budget, not an exhaustive hyperparameter search.

## Java scorer and runtime gates

The Java scorer in `inflector-eval` mirrors the candidate inventory, hashed features,
softmax and residual gate without external dependencies. Loading the same float32 artifact
and scoring the full P2 test population gives **0 prediction mismatches**: 801 errors,
310 corrections, 110 regressions and 537 overrides, exactly the Python result above.

Five fresh JVMs (`-Xms256m -Xmx256m`) measured 200,000 selector calls each after 50,000
warm-up calls:

| gate | median | observed range | threshold | verdict |
| --- | ---: | ---: | ---: | --- |
| p99 latency | 14.1 µs | 13.2-17.7 µs | <= 10 µs | **FAIL** |
| artifact size | 16,412 B | fixed | <= 32 KiB | **PASS** |
| cold initialization | 14.709 ms | 11.844-19.326 ms | <= 10 ms | **FAIL** |

Initialization is model load plus the first selection; the model load alone was 12.062 ms
median. Size is 16.027 KiB. The repository does not preserve numeric gate values, so the
runner uses explicit CLI-overridable defaults; `results/learning/java-gate.md` records the
strict-default evaluation and full command.

## Decision

**Reject runtime inference.** The Java scorer removes the implementation blocker and
reproduces the offline result exactly, but the selector still misses the offline gain gate by
0.0005 pp and fails the strict-default p99 and initialization gates. Its artifact-size gate
passes. The deterministic library ships unchanged. No model, training dependency or Python
code is on the consumer classpath.

## Historical result (audited commit)

The original experiment used train-on-lexicon predictions and the retired 36,505-record
population. It reported linear 88.218% (+48 corrections, 18 regressions) and an MLP that
made zero overrides, and rejected runtime inference. Those numbers are preserved here as
historical evidence; they are not the corrected OOF result.

## What this does and does not show

* It measures selector quality on dictionary lemmas under a family holdout, not application
  accuracy on natural text.
* The OOF construction removes the train-on-the-answer leakage, but it cannot create
  corrections the candidate inventory does not contain.
* The Java scorer is an evaluation-only mirror; it is not part of the shipped runtime.
* No new records were adjudicated; no human-review claim is made.
