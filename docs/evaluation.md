# Evaluation

Two protocols are reported separately. Both use fixed gold labels written **before** any
prediction is read, fixed denominators, and no relabelling from engine output.

The dataset is a pinned Kaikki/Wiktionary extract (`data/LICENSE-DATA.md`). Records are
normalized by `tools/research/extract.py` and never by the engine under test.

## Primary accuracy definition

`primary` = every test record whose normalized countability is `COUNTABLE` or `MIXED`, scored
on **accepted-form accuracy**: the engine's output must equal any independently attested
plural. Because Wiktionary attests alternatives, `persons` and `people` are both accepted for
`person`; the engine is not rewarded for matching the preferred form by luck.

`overall` additionally includes `MASS`, `PLURAL_ONLY` and invariant records. Those are policy
outcomes, not singular→plural predictions, so they are reported separately and are not used
for the headline claim.

## Protocol 1 — frozen shipped lexicon (lexical coverage)

The shipped artifact (all splits, 157,344 entries) is frozen, then evaluated on the test split.
This measures **coverage of a frozen dictionary**, not generalization to unseen lemmas.

| system | primary accuracy | errors / 34,933 | overall accuracy | overall errors / 50,938 |
| --- | --- | --- | --- | --- |
| A. stock evo-inflector 2.0 | 96.502% | 1,222 | 68.825% | 15,880 |
| C. rules + curated + Wiktionary artifact | **99.997%** | **1** | **99.996%** | **2** |
| B. repaired rules only (no lexicon) | 88.618% | 3,976 | 64.239% | 18,216 |

Paired comparison, clustered bootstrap by lemma family (95% CI):

* evo2 → this library: **+4.1481 pp** `[+3.9231, +4.3629]`, 1,461 corrections, 1 regression.
* evo2 → rules only: `-7.8274 pp` `[-8.9596, -6.9535]`, 467 corrections, 3,222 regressions.

Rule source usage on the test split: 27,421 rule decisions, 23,517 lexical decisions, 1 fallback.
Mass nouns explain the overall gap: stock 2.0 pluralizes all 14,952 mass records
(`informations`), while this library returns the lemma and reports `MASS`.

### Residual errors (2)

| lemma | output | gold accepted | classification |
| --- | --- | --- | --- |
| `meet-her-in-the-entry-and-kiss-her-in-the-buttery` (51 chars) | lemma | `...butteries` | artifact skips lemmas longer than 40 characters |
| `pneumonoultramicroscopicsilicovolcanokoniosis` (45 chars, MASS) | rules pluralized it | lemma | same 40-character skip; outside the primary slice |

Both residual errors are the same documented limitation (an arbitrary 40-character cap on
lexical entries), not a wrong morphological decision. They are reported rather than relabelled.

## Protocol 2 — family holdout (no lexical leakage)

Whole lemma families are assigned to train/dev/test by a pinned hash (`splits.py`). Casing
variants, compounds and related records stay in one family. The experimental artifact is
built **from the train split only** (110,627 entries); dev and test lemmas are absent from
every lexical resource and from all training, mining and calibration inputs.

| system | primary accuracy | errors / 34,933 | note |
| --- | --- | --- | --- |
| A. stock evo-inflector 2.0 | 96.502% | 1,222 | unchanged baseline |
| C. train-only artifact | 88.618% | 3,976 | held-out families have almost no entries |
| B. repaired rules | 88.618% | 3,976 | identical by construction |

The equality is the expected result, not a bug: on unseen families the train-only artifact
contributes almost nothing, so the system reduces to the three productive rules. P2 is the
honest measure of generalization, and it is **worse than evo2** because evo2 carries a large
built-in exception table while P2 forbids using the test families' lexical knowledge.

P2 is the primary evaluation for the learning experiment: a learner must improve the
rules-only baseline using only train/dev data.

## External validation — UD English EWT

A fresh, independently annotated corpus check: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT),
CC BY-SA 4.0, 10,106 plural noun tokens, filtered to 2,184 single-token alphabetic
lemma→plural pairs (2,221 attested forms). The shipped artifact was **not retrained or tuned**
on this corpus; 454 of the 2,184 lemmas happen to be present in the lexicon, and 1,730 are
unseen.

| system | overall | seen (454) | unseen (1,730) |
| --- | --- | --- | --- |
| stock evo-inflector 2.0 | 98.12% [97.53, 98.67] | 95.37% [93.39, 97.14] | 98.84% [98.32, 99.31] |
| shipped lexicon | **94.55%** [93.59, 95.47] | **77.09%** [73.13, 80.84] | **99.13%** [98.67, 99.54] |
| repaired rules only | 97.71% [97.07, 98.31] | 92.29% [89.65, 94.49] | 99.13% [98.67, 99.54] |

Accepted-form accuracy; 95% percentile bootstrap over lemmas. The unseen lexicon number
equals the rules number by construction: unseen lemmas fall through to the productive rules.

The seen-subset failure is a **data defect, not a morphology result**: 100 of the 119 lexicon
errors are identity returns, and the artifact classifies common count nouns as mass or
plural-only — `book` is `PLURAL_ONLY`, `account` is `MASS`, `bag` is `PLURAL_ONLY`, so they
are returned unchanged (`book`, not `books`). The identity errors break down as `MASS` 44,
`PLURAL_ONLY` 53, `MASS/I` 3.

P1 did not expose this because its overall accepted-form slice counts identity as correct for
`MASS`/`PLURAL_ONLY` rows, and its primary slice excludes those rows entirely. The external
corpus supplies count contexts and shows that the artifact's countability classification is
over-broad. Fixing it requires correcting extraction/classification and re-running P1/P2; it
was deliberately **not** tuned against this external set. Until then, treat P1 as dictionary
coverage, not production accuracy, and see `results/external/ewt-report.md` for the error list.

## Candidate-oracle ceiling (P2 test)

Candidates are generated from lemma suffix and shape only; the deterministic base result is
always a candidate. An oracle picks a valid candidate whenever one exists.

| quantity | value |
| --- | --- |
| records | 35,197 |
| base accuracy | 88.022% |
| oracle accuracy | 99.182% |
| oracle gain | **+11.16 pp** |
| required gain (20% fewer errors + 0.05 pp) | 2.396 pp |
| ceiling clears required gain | yes |

Oracle hits by edit: `base:s` 28,340, `s` 2,612, `base:other` 1,586, `base:es` 1,019,
`man->men` 293, `is->es` 254, `um->a` 236, `lemma` 162, `es` 112, `us->i` 95, `a->ae` 51,
`ies` 45, `base:same` 36, `ix->ices` 18, `fe->ves` 17, `ves` 14, `child->ren` 8,
`mouse->ice` 6, `person->people` 3, `quiz->zzes` 2.

Because the ceiling clears the required gain, a learned candidate selector is worth training
(no pretraining stop applies). The learner's actual result is in `docs/learning.md`.

## What is not measured

* Application-call accuracy on real text: the corpus is dictionary lemmas, not sampled usage.
* Preferred-form accuracy: Wiktionary preferences are not independently labelled here.
* Singularization, verbs, sentence context: out of scope.
* Human adjudication: no new records were adjudicated, so no claim of human review is made.
* External generalization: UD EWT is one corpus with its own annotation noise (typos and
  abbreviations such as `aquiriums` and `bredders` appear in the gold). The 119-error list is
  published rather than hand-cleaned, and the defect pattern is far larger than that noise.

## Reproduction

```bash
# one-off (network): fetch and verify the pinned dump
py -3 -m tools.research.cli fetch

# offline from data/raw: normalize, split, build artifacts
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all  --selection compact

# predictions from the Java engine
./mvnw -q -pl inflector-eval -am install -DskipTests
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds.tsv"

# score
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds.tsv \
  --out results/eval/p1-report.json --split test --lexicon-split all

# external validation (network: UD English EWT, CC BY-SA 4.0)
git clone --depth 1 https://github.com/UniversalDependencies/UD_English-EWT.git \
  build/external/ewt-repo
mkdir -p build/external/ud-ewt
cp build/external/ewt-repo/*.conllu build/external/ud-ewt/
py -3 tools/research/external_eval.py prepare --conllu-dir build/external/ud-ewt \
  --out-dir results/external
./mvnw -q -pl inflector-eval -am package -DskipTests
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.mainClass=io.github.morphex.inflector.eval.ExternalEval \
  -Dexec.args="--gold results/external/ewt-gold.tsv --out results/external/ewt-predictions.tsv"
py -3 tools/research/external_eval.py score --gold results/external/ewt-gold.tsv \
  --predictions results/external/ewt-predictions.tsv \
  --lexicon inflector-core/src/main/resources/io/github/morphex/inflector/lexicon-v1.tsv \
  --out-dir results/external
```

The committed `results/eval/*.json` are the outputs of these commands. `data/raw/` is
gitignored; a missing dump makes the corpus evaluation unavailable, never silently successful.
