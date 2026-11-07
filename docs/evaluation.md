# Evaluation

All current numbers come from frozen outputs in `results/release-1.2.0/`. Historical
audited-commit measurements are labelled separately and are not mixed into the current
tables. The dataset is `nouns-v2`, extraction is `extract-v3`, the runtime artifact is
`lexicon-v2`, and the split algorithm remains `split-v1`.

## Contract

* **Primary** means `countability in {COUNTABLE, MIXED}` and `invariant == false`.
* Accuracy is **accepted-form**: the prediction must equal any independently attested plural.
* Gold labels were written before predictions; no prediction changes a label.
* The evaluator rejects duplicate or mismatched ids.
* `artifact_hit` / `artifact_miss` describe the compact artifact only. They are **not**
  claims about source-corpus membership.
* Full-vocabulary membership uses every permitted normalized lemma (340,741), not only the
  rows retained in the compact artifact.
* The current primary denominator is **36,241** on P1 and P2 test, and **36,473** on P2 dev.

## Data and artifacts

| item | value |
| --- | --- |
| normalized dataset | `nouns-v2`, 340,734 lemmas |
| extraction | `extract-v3` |
| P1 artifact | 147,338 entries, 7,553,688 bytes TSV |
| P1 membership | 340,741 lemmas, 2,725,940 bytes |
| P2 train artifact | 103,668 entries, 5,317,828 bytes |
| P2 train membership | 238,927 lemmas |
| split key | `split-v1` |

Changes from v1: 178 plural-only lemmas added, `PADS` and `PL` removed, `accent` and `work`
reclassified as `MIXED`, and `amenity -> amenities` added as a reviewed curated correction.

## P1 - frozen shipped lexicon

P1 measures coverage of the shipped dictionary, not generalization.

| system | primary accuracy | primary errors / 36,241 | overall accuracy | overall errors / 50,978 |
| --- | --- | --- | --- | --- |
| stock evo-inflector 2.0 | 96.474% | 1,278 | 68.832% | 15,889 |
| lexicon + rules + curated | **100.000%** | **0** | **100.000%** | **0** |
| rules only (no lexicon loaded) | 95.163% | 1,753 | 67.798% | 16,416 |

Paired family-clustered bootstrap (2,000 iterations, 95% CI):

* evo2 -> lexicon: **+3.5264 pp** `[+3.3318, +3.7132]`, 1,278 corrections, 0 regressions.
* evo2 -> rules: `-1.3107 pp` `[-1.5146, -1.1084]`, 463 corrections, 938 regressions.

P1 primary source usage: 7,245 `lexicon:lexicon`, 28,996 `lexicon:rule`; every record is
correct. There is no fallback or inferred-uppercase decision in this coverage protocol.

Historical audited-commit P1 was also 100% (0/36,239 primary, 0/50,938 overall), using a
150,267-entry artifact and rules-only 88.714% (4,090 errors). Those values remain historical
and are not used for the current claims.

## P2 - family holdout

Whole lemma families are assigned to train/dev/test by the pinned hash. The train-only
artifact excludes dev/test families. Dev and test family assignments do not overlap.

| system | test primary (36,241) | test errors | dev primary (36,473) | dev errors |
| --- | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 96.474% | 1,278 | 96.400% | 1,313 |
| train-only lexicon + rules | **97.365%** | **955** | **97.382%** | **955** |
| rules only | 95.163% | 1,753 | 95.257% | 1,730 |

Test paired comparison: **+0.8913 pp** `[+0.7355, +1.0531]`, 538 corrections, 215
regressions, **25.274%** relative error reduction. Dev: +0.9815 pp `[+0.8079, +1.1374]`,
543 corrections, 185 regressions.

**Change from the historical 951-error baseline.** The previous v1 P2 test measurement was
951 errors on 36,239 primary records (97.376%). The current result is 955 errors on 36,241
records (97.365%). That is **4 more errors** (and two more primary records), not an
improvement to hide. The error composition changed: simple errors fell from 827 to 783,
while compound errors rose from 92 to 134. It still clears the historical 1,278-error
evo2 parity target by **323 errors**.

### Test primary slices

| bucket | records | correct | errors | accuracy |
| --- | ---: | ---: | ---: | ---: |
| compound | 2,018 | 1,884 | 134 | 93.360% |
| initialism | 806 | 783 | 23 | 97.146% |
| simple | 33,149 | 32,366 | 783 | 97.638% |
| unicode | 268 | 253 | 15 | 94.403% |

| decision source | records | errors | accuracy |
| --- | ---: | ---: | ---: |
| `lexicon:compound` | 1,915 | 31 | 98.381% |
| `lexicon:rule` | 33,417 | 798 | 97.612% |
| `lexicon:inferred` | 806 | 23 | 97.146% |
| `lexicon:fallback` | 103 | 103 | 0.000% |

The 103 fallback records are unsupported shapes, not silently rewritten. Remaining compound
errors are dominated by unsupported fixed phrases and heads that are absent or not
productive in the train-only artifact. The extraction fix also changed which plural-only
records enter the primary denominator.

## External validation - UD English EWT

[UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0,
filtered to 2,184 single-token alphabetic lemma->plural pairs. The artifact was not trained
or tuned on this corpus.

**Membership correction.** Previous reports called a lemma “unseen” when it was absent from
the compact artifact. That was an artifact-hit/miss label, not vocabulary membership. Using
the full permitted vocabulary, **2,148** of the 2,184 EWT lemmas are seen and only **36** are
genuinely unseen. The compact artifact contains 363 of the 2,184 lemmas; 1,821 are artifact
misses. These labels are now reported separately.

Selected-output accuracy and alternative-set coverage are separate. The former is the
comparison between systems; the latter is not equivalent to another system's selected output.

| system | selected overall | full-vocab seen (2,148) | full-vocab unseen (36) | artifact hit (363) | artifact miss (1,821) | family seen (2,150) | family unseen (34) | alternative coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 98.12% | 98.23% | 91.67% | 94.21% | 98.90% | 98.23% | 91.18% | n/a |
| shipped lexicon | **98.76%** | **98.88%** | 91.67% | **96.69%** | **99.18%** | **98.88%** | 91.18% | **98.99%** |
| rules only | 98.26% | 98.37% | 91.67% | 93.66% | **99.18%** | 98.37% | 91.18% | 98.26% |

The genuinely unseen slice is only 36 lemmas, so it cannot support a strong “unseen
generalization” claim. The safer statement is that selected accuracy is 98.76% overall,
98.88% on full-vocabulary-seen lemmas, and 96.69% on the 363 compact-artifact hits.

The 27 remaining lexicon errors include 4 identity returns on disputed countability, 24
full-vocabulary-seen lemmas, 3 genuinely unseen lemmas, and 12 artifact hits. By source:
16 rule decisions and 11 lexical decisions. Nothing was tuned on this set. Full list:
`results/release-1.2.0/external/ewt-report.md`.

## Artifact preservation

Compaction omits a known entry only when the final resolver reproduces the required output
and no lexical metadata is needed. The Java artifact audit and Python mirror are checked
against the P1 records, including countability and policy cases. Current P1 is 0 primary and
0 overall errors.

## What is not measured

* Application-call accuracy on natural text; the corpus is dictionary lemmas.
* Preferred-form accuracy; Wiktionary preferences are not independently labelled.
* Singularization, verbs, sentence context, multilingual morphology.
* Human adjudication; no records were externally reviewed.
* The external unseen slice is too small for a strong generalization claim.

## Reproduction

```bash
# one-off network fetch and checksum verification
py -3 -m tools.research.cli fetch

# offline normalization, splits and artifacts
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all   --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v2.tsv

./mvnw -q -o -pl inflector-eval -am install -DskipTests

# P1
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/release-1.2.0/eval/p1-test-records.tsv --out results/release-1.2.0/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/release-1.2.0/eval/p1-test-gold.tsv \
  --predictions results/release-1.2.0/eval/p1-test-preds.tsv \
  --out results/release-1.2.0/eval/p1-report.json --split test --lexicon-split all --iterations 2000

# P2 (train-only artifact; membership is loaded from its sibling)
for split in dev test; do
  ./mvnw -q -o -pl inflector-eval exec:java \
    -Denglish.inflector.lexicon="$(cygpath -w "$(pwd)/build/p2/lexicon-v2.tsv")" \
    -Dexec.args="--input results/release-1.2.0/eval/p2-$split-records.tsv --out results/release-1.2.0/eval/p2-$split-preds.tsv"
  py -3 -m tools.research.cli evaluate \
    --gold "results/release-1.2.0/eval/p2-$split-gold.tsv" \
    --predictions "results/release-1.2.0/eval/p2-$split-preds.tsv" \
    --out "results/release-1.2.0/eval/p2-$split-report.json" --split "$split" --lexicon-split train --iterations 2000
done
```

The full driver is `scripts/rebuild-and-evaluate.sh`. `data/raw/` is gitignored; without it
the corpus evaluation is unavailable, never silently successful.
