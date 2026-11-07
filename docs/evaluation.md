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

The EWT unseen slice is only 36 lemmas, so it cannot support a strong “unseen
generalization” claim. It remains the hand-adjudicated corpus result and is **not replaced**
by the AGID stress test below. The safer statement is that selected accuracy is 98.76%
overall, 98.88% on full-vocabulary-seen lemmas, and 96.69% on the 363 compact-artifact hits.

The 27 remaining lexicon errors include 4 identity returns on disputed countability, 24
full-vocabulary-seen lemmas, 3 genuinely unseen lemmas, and 12 artifact hits. By source:
16 rule decisions and 11 lexical decisions. Nothing was tuned on this set. Full list:
`results/release-1.2.0/external/ewt-report.md`.

## External stress test - AGID 2016.01.19

[AGID 2016.01.19](https://sourceforge.net/projects/wordlist/files/AGID/2016.01.19/),
AGID permissive terms (public-domain word lists plus WordNet 1.6-style terms), archive
SHA-256 `15d2d792d309d2dc838bf75d8abcd3feb36708c219dc5158d4fff70b89e601d1`,
`infl.txt` SHA-256 `df0c8f6edd4193d823abfad9e4ff6f42b4526211516649e1c73d4533aef749fa`.
It is an automatically generated inflection lexicon, **not a hand-adjudicated corpus**, and
is independent of the shipped Kaikki/Wiktionary vocabulary. It was not used for training or
tuning.

`infl.txt` has 48,500 `N:` entries. The filter keeps a lowercase lemma, one clean preferred
plural, and a single-token alphabetic lemma/form matching `[a-z]{3,}`; this yields **34,642
lemmas**. Full-vocabulary membership is **34,381 seen / 261 unseen** (7.25x the EWT unseen
denominator); compact-artifact membership is **5,463 hit / 29,179 miss**. Other permissive UD
English treebanks checked added only dozens of unseen lemmas each; GUM, LinES and ParTUT are
CC BY-NC-SA 4.0 and were excluded.

| system | selected overall | full-vocab seen | full-vocab unseen (261) | artifact hit (5,463) | artifact miss (29,179) | alternative coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 97.48% `[97.32, 97.64]` | 97.50% `[97.33, 97.66]` | 95.02% `[92.34, 97.32]` | 87.50% | 99.35% | n/a |
| shipped lexicon | 93.84% `[93.59, 94.09]` | 93.83% `[93.57, 94.08]` | **95.40%** `[92.72, 97.70]` | 61.54% | 99.89% | 94.67% |
| rules only | **98.20%** `[98.06, 98.33]` | **98.22%** `[98.08, 98.35]` | **95.40%** `[92.72, 97.70]` | **89.16%** | **99.89%** | **98.20%** |

**How to read the overall row.** AGID has no countability labels. The lexicon's `MASS` and
invariant policy returns identity for many mass/abstract nouns that AGID supplies with a
generated plural. Of 2,133 lexicon errors, **1,722 are identity returns** (1,685 `MASS/I`),
2,121 are full-vocabulary-seen, 2,101 are compact-artifact hits, and only 12 are in the
unseen slice. The fact that the lexicon scores below rules-only overall is therefore a
**lexical policy stress test**, not evidence of a natural-text regression. No stable
improvement is claimed.

On the 261 genuinely unseen lemmas, lexicon and rules are tied at **249/261 (95.40%)**;
evo2 is **248/261 (95.02%)**. The 12 lexicon errors are `calliopsis`, `corditis`,
`corneum`, `dalo`, `globigerina`, `marum`, `pizzaz`, `siris`, `stelis`, `stypsis`, `tabis`,
and `yourself`; the last is an AGID pronoun misclassified as a noun and illustrates the
source's label noise. Full output: `results/release-1.2.0/external/agid-report.md`.

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
* EWT's 36-lemma unseen slice is still too small for a strong claim. AGID's 261-lemma slice is larger, but AGID is automatically generated and has no countability labels; its overall figure is not a corpus-accuracy estimate.

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

# External EWT (existing) and AGID (new)
py -3 tools/research/external_eval.py prepare --source agid \
  --agid build/external/agid/agid-2016.01.19/infl.txt \
  --out-dir results/release-1.2.0/external
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.mainClass=io.github.slayyyyer.morphex.inflector.eval.ExternalEval \
  -Dexec.args="--gold results/release-1.2.0/external/agid-gold.tsv --out results/release-1.2.0/external/agid-predictions.tsv"
py -3 tools/research/external_eval.py score --source agid \
  --gold results/release-1.2.0/external/agid-gold.tsv \
  --predictions results/release-1.2.0/external/agid-predictions.tsv \
  --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv \
  --out-dir results/release-1.2.0/external --family-mode lexical

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
