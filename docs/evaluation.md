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

The shipped artifact (all splits, 150,267 entries) is frozen, then evaluated on the test split.
This measures **coverage of a frozen dictionary**, not generalization to unseen lemmas.

| system | primary accuracy | errors / 36,239 | overall accuracy | overall errors / 50,938 |
| --- | --- | --- | --- | --- |
| A. stock evo-inflector 2.0 | 96.473% | 1,278 | 68.882% | 15,851 |
| C. rules + curated + Wiktionary artifact | **100.000%** | **0** | **100.000%** | **0** |
| B. repaired rules only (no lexicon) | 88.714% | 4,090 | 64.259% | 18,206 |

Paired comparison, clustered bootstrap by lemma family (95% CI):

* evo2 → this library: **+4.1611 pp** `[+3.9597, +4.3727]`, 1,519 corrections, 0 regressions.
* evo2 → rules only: `-7.7031 pp` `[-8.7735, -6.8541]`, 479 corrections, 3,291 regressions.

Rule source usage on the test split: 28,512 rule decisions, 22,426 lexical decisions, no fallback.
Mass nouns explain the overall gap: stock 2.0 pluralizes all 14,952 mass records
(`informations`), while this library returns the lemma and reports `MASS`.

### Residual errors (0)

No test record is missed. The two former residuals — the 51-character
`meet-her-in-the-entry-and-kiss-her-in-the-buttery` and the 45-character MASS
`pneumonoultramicroscopicsilicovolcanokoniosis` — were an arbitrary 40-character cap on
lexical entries, not wrong morphological decisions. The cap is lifted (the longest
validated single-token lemma is 53 characters and the packed columns impose no such
limit); the artifact now covers all of them.

## Protocol 2 — family holdout (no lexical leakage)

Whole lemma families are assigned to train/dev/test by a pinned hash (`splits.py`). Casing
variants, compounds and related records stay in one family. The experimental artifact is
built **from the train split only** (105,688 entries); dev and test lemmas are absent from
every lexical resource and from all training, mining and calibration inputs.

| system | primary accuracy | errors / 36,239 | note |
| --- | --- | --- | --- |
| A. stock evo-inflector 2.0 | 96.473% | 1,278 | unchanged baseline |
| C. train-only artifact | 88.714% | 4,090 | held-out families have almost no entries |
| B. repaired rules | 88.714% | 4,090 | identical by construction |

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

| system | overall | seen (376) | unseen (1,808) |
| --- | --- | --- | --- |
| stock evo-inflector 2.0 | 98.12% [97.53, 98.67] | 94.41% [92.02, 96.54] | 98.89% [98.40, 99.34] |
| shipped lexicon | **98.76%** [98.26, 99.22] | **96.81%** [94.95, 98.40] | **99.17%** [98.73, 99.56] |
| repaired rules only | 97.71% [97.07, 98.31] | 90.69% [87.50, 93.62] | 99.17% [98.73, 99.56] |

Accepted-form accuracy; 95% percentile bootstrap over lemmas. The unseen lexicon number
equals the rules number by construction: unseen lemmas fall through to the productive rules.

The earlier seen-subset failure (77.09% on 454 lemmas) is fixed. Three extraction defects
caused it, all corrected and re-measured with a full P1/P2 rerun:

1. Sense-level `plural-normally` tags promoted whole lemmas to `PLURAL_ONLY`
   (`book`, `bag`) — now only head-level plural-only marking counts.
2. A single subordinate `uncountable` sense outweighed attested plurals (`account`)
   — now attested plurals win unless the head itself says uncountable.
3. Multi-etymology labels took the first record in dump order (`plate`) — now reconciled
   by votes, and `usually uncountable` with an attested plural maps to `MIXED`
   (`practice`, `reality`, `sculpture`, `truth`, `stuff`, `furniture`).
4. `money`/`music`/`traffic` moved from curated `MASS` to `MIXED` (`monies`, `musics`,
   `traffics`), matching the `water`/`coffee` precedent.

Of the remaining 27 lexicon errors, 16 are rule-source misses on EWT annotation noise
(`aquiriums`, `bredders`, `dinasaurs`, `paraded`, `tomatos`, `thanx`, `titts`, `villians`)
or known rule limits (`gardneri`, `mullahs`, `attys`/`cpys`); 8 are identity returns on
disputed subordinate uses (`works`, `olds`, `offs`, `osteos`, `luggage`, `accent`,
`amenity`, `belonging`); 3 are correct outputs scored wrong against gold typos
(`tomatoes`, `aquariums`, `lasers`). Nothing here was tuned against the external set;
see `results/external/ewt-report.md` for the full list.

## Candidate-oracle ceiling (P2 test)

Candidates are generated from lemma suffix and shape only; the deterministic base result is
always a candidate. An oracle picks a valid candidate whenever one exists.

| quantity | value |
| --- | --- |
| records | 36,505 |
| base accuracy | 88.136% |
| oracle accuracy | 99.173% |
| oracle gain | **+11.04 pp** |
| required gain (20% fewer errors + 0.05 pp) | 2.373 pp |
| ceiling clears required gain | yes |

Oracle hits by edit: `base:s` 29,181, `s` 2,651, `base:other` 1,774, `base:es` 1,181,
`man->men` 293, `is->es` 285, `um->a` 236, `lemma` 186, `es` 115, `us->i` 97, `a->ae` 51,
`ies` 46, `base:same` 38, `ix->ices` 18, `fe->ves` 18, `ves` 14, `child->ren` 8,
`mouse->ice` 6, `person->people` 3, `quiz->zzes` 2.

Because the ceiling clears the required gain, a learned candidate selector is worth training
(no pretraining stop applies). The learner's actual result is in `docs/learning.md`.

## What is not measured

* Application-call accuracy on real text: the corpus is dictionary lemmas, not sampled usage.
* Preferred-form accuracy: Wiktionary preferences are not independently labelled here.
* Singularization, verbs, sentence context: out of scope.
* Human adjudication: no new records were adjudicated, so no claim of human review is made.
* External generalization: UD EWT is one corpus with its own annotation noise (typos and
  abbreviations such as `aquiriums` and `bredders` appear in the gold). The 27-error list is
  published rather than hand-cleaned; most of it is that noise plus disputed subordinate
  uses (`works`, `olds`).

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
  -Dexec.mainClass=io.github.slayyyyer.morphex.inflector.eval.ExternalEval \
  -Dexec.args="--gold results/external/ewt-gold.tsv --out results/external/ewt-predictions.tsv"
py -3 tools/research/external_eval.py score --gold results/external/ewt-gold.tsv \
  --predictions results/external/ewt-predictions.tsv \
  --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v1.tsv \
  --out-dir results/external
```

The committed `results/eval/*.json` are the outputs of these commands. `data/raw/` is
gitignored; a missing dump makes the corpus evaluation unavailable, never silently successful.
