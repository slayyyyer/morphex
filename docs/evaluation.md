# Evaluation

All numbers below come from frozen, checksummed outputs in `results/eval/`. The current
contract is **eval-v2**. Historical results from the audited commit are kept separately and
labelled as such; they are not mixed into the current tables.

## Contract

* **Primary** means `countability in {COUNTABLE, MIXED}` and `invariant == false`. This one
  selector is used by every headline, paired comparison, bootstrap interval and learning gate.
* Accuracy is **accepted-form**: the prediction must equal any independently attested plural.
  The engine is not rewarded for matching the preferred form by luck.
* Gold labels are written before predictions. No prediction changes a label.
* The Java evaluator skips an explicit `id<TAB>lemma` header, asserts unique ids, and rejects
  any gold/prediction id mismatch. The former 50,939-vs-50,938 off-by-one is fixed.
* Relative error reduction is `(baseline_errors - candidate_errors) / baseline_errors`.
* `artifact_hit` / `artifact_miss` describe the compact artifact only. They are not claims
  about source-corpus membership.
* The historical 36,505-record learning population is retired. The current primary
  denominator is 36,239 on the P1/P2 test split and 36,470 on dev.

Source: pinned Kaikki/Wiktionary dump (`data/LICENSE-DATA.md`), normalized offline by
`tools/research/extract.py`; split families are assigned by the pinned hash in `splits.py`.

## P1 - frozen shipped lexicon (lexical coverage)

The shipped artifact is built from all permitted splits and frozen before evaluation. This
measures dictionary coverage, not generalization.

Artifact: **147,193 entries**, 7,545,760 bytes TSV (1,176,616 bytes gzip), plus a 340,565-entry
membership table (2,724,532 bytes). The membership table is generated from every permitted
lemma, not only the compact rows, so omitted regular words still count as ordinary words for
uppercase inference.

| system | primary accuracy | primary errors / 36,239 | overall accuracy | overall errors / 50,938 |
| --- | --- | --- | --- | --- |
| stock evo-inflector 2.0 | 96.473% | 1,278 | 68.882% | 15,851 |
| lexicon + rules + curated | **100.000%** | **0** | **100.000%** | **0** |
| rules only (no lexicon) | 95.168% | 1,751 | 67.851% | 16,376 |

Paired family-clustered bootstrap (2,000 iterations, 95% CI):

* evo2 -> lexicon: **+3.5266 pp** `[+3.3323, +3.7159]`, 1,278 corrections, 0 regressions.
* evo2 -> rules: `-1.3052 pp` `[-1.5035, -1.0974]`, 463 corrections, 936 regressions.

P1 primary source usage: 7,244 `lexicon:lexicon`, 28,995 `lexicon:rule`; every record is
correct. There is no fallback or inferred-uppercase decision in this coverage protocol.

### Historical results (audited commit)

The audited commit reported P1 100.000% primary (0/36,239) and 100.000% overall
(0/50,938), with a 150,267-entry artifact and rules-only 88.714% (4,090 errors). Those
numbers remain reproduced in `results/eval/p1-report.json` and are not the current v4
results. The old evaluator also counted the header, which is why historical prediction files
contain 50,939 rows; current files contain exactly 50,938.

## P2 - family holdout (unseen families, train-only artifact)

Whole lemma families are assigned to train/dev/test by the pinned hash. Casing variants,
compounds and related records stay in one family. The P2 artifact is built from train
families only: **103,576 entries** (5,312,723 bytes), with a 238,810-entry membership table.
Dev/test families are absent from the artifact and from all threshold selection.

| system | primary accuracy | primary errors / 36,239 | dev accuracy | dev errors / 36,470 |
| --- | --- | --- | --- | --- |
| stock evo-inflector 2.0 | 96.473% | 1,278 | 96.397% | 1,314 |
| train-only lexicon + rules | **97.376%** | **951** | **97.398%** | **949** |
| rules only | 95.168% | 1,751 | 95.281% | 1,721 |

Test paired comparison: **+0.9023 pp** `[+0.7309, +1.0718]`, 540 corrections, 213
regressions, **25.587%** relative error reduction. Dev: +1.0008 pp `[+0.8317, +1.1672]`,
545 corrections, 180 regressions. The compound slice fell from 180 to 130 test errors
(50 corrected, 0 regressed) and from 177 to 92 dev errors (85 corrected, 0 regressed).

### Test primary slices

| bucket | records | correct | errors | accuracy |
| --- | ---: | ---: | ---: | ---: |
| compound | 2,018 | 1,888 | 130 | 93.558% |
| initialism | 806 | 783 | 23 | 97.146% |
| simple | 33,147 | 32,364 | 783 | 97.638% |
| unicode | 268 | 253 | 15 | 94.403% |

| decision source | records | errors | accuracy |
| --- | ---: | ---: | ---: |
| `lexicon:compound` | 1,915 | 27 | 98.590% |
| `lexicon:rule` | 33,415 | 798 | 97.612% |
| `lexicon:inferred` | 806 | 23 | 97.146% |
| `lexicon:fallback` | 103 | 103 | 0.000% |

The 103 fallback records are unsupported shapes, not silently rewritten. The remaining
130 test compound errors are mostly head-final compounds whose head is absent or
non-productive in the train-only lexicon, plus unsupported fixed phrases; the remaining
simple errors are dominated by irregular suffix families that have no training-family entry.

### Historical results (audited commit)

The audited P2 result was **88.714%** (4,090 errors), with `rules only` identical by
construction. On the 33,147 simple non-invariant records it was 31,876 correct (96.166%);
the remaining 3,092 records were 273 correct (8.829%). Those numbers are retained in
`results/eval/p2-report.json` and summarized in `FINAL_REPORT.md` as historical evidence.
They are not comparable to the current P2 table because the engine, artifact, and evaluation
contract all changed.

## Artifact preservation

Compaction omits a known entry only when the final resolver reproduces the required output
and no lexical metadata is needed. The Python mirror is checked against the Java engine by
`ArtifactAuditTest` over the full pinned P1 evaluation, including policy cases. Current P1:
**0 errors / 0 overall**, and the audit fixture is green.

## External validation - UD English EWT

[UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0,
filtered to 2,184 single-token alphabetic lemma->plural pairs (2,221 forms); 368 lemmas are
present in the shipped artifact and 1,816 are unseen. The artifact was not retrained or tuned
on this corpus.

Selected-output accuracy and alternative-set coverage are separate. The former is what both
systems are compared on; the latter is not equivalent to another system's selected output.

| system | selected overall | selected seen (368) | selected unseen (1,816) | alternative coverage |
| --- | --- | --- | --- | --- |
| stock evo-inflector 2.0 | 98.12% [97.53, 98.67] | 94.29% [91.85, 96.47] | 98.90% [98.40, 99.34] | n/a |
| shipped lexicon | **98.21%** [97.62, 98.76] | 93.48% [90.76, 95.92] | **99.17%** [98.73, 99.56] | 98.76% [98.26, 99.22] |
| rules only | **98.26%** [97.66, 98.76] | 93.75% [91.30, 96.20] | **99.17%** [98.73, 99.56] | 98.26% [97.66, 98.76] |

The lexicon's selected-output advantage over evo2 is small (0.09 pp) and its seen-subset
score is lower; the alternative coverage is higher. The remaining 39 lexicon errors include
identity returns on disputed countability, EWT typos, and rule limits. Nothing was tuned on
this set. Full list: `results/external-v2/ewt-report.md`.

### Strict-family sensitivity check

A supplementary rerun groups conservative derivational relatives before splitting. The
`split-v2` key strips common nominal suffixes and normalizes some stem endings; it is an
approximation, not a lemmatizer. The train-only artifact for this run has **102,273
entries**, and the stricter grouping changes the P2 denominators to **36,520 dev** and
**37,507 test**. These numbers are therefore not directly comparable to the primary
`split-v1` table above and do not replace it.

| P2 split | evo-inflector 2.0 | train-only lexicon | paired delta (95% CI) |
| --- | ---: | ---: | ---: |
| dev strict families (36,520) | 96.358% (1,330) | **97.125% (1,050)** | **+0.7667 pp** `[+0.4742, +1.0107]` |
| test strict families (37,507) | 96.379% (1,358) | **97.238% (1,036)** | **+0.8585 pp** `[+0.6868, +1.0287]` |

The strict test gain is 536 corrections and 214 regressions; rules-only accuracy is
95.174% (1,810 errors). External EWT uses the same strict family key for its family slices:

| external slice | evo-inflector 2.0 | shipped lexicon |
| --- | ---: | ---: |
| family seen (1,677) | **98.09% (1,645)** | 98.03% (1,644) |
| family unseen (507) | 98.22% (498) | **98.82% (501)** |

Reports: `results/strict-family-report.md`, `results/eval-strict/*-report.json`, and
`results/external-strict/ewt-report.md`.

## Candidate ceiling (P2 test, pre-compound run)

Candidates are generated from lemma shape/suffix plus the deterministic base, which is always
present. The oracle picks any accepted candidate.

| quantity | value |
| --- | ---: |
| primary records | 36,239 |
| base accuracy (pre-compound run) | 97.238% (1,001 errors) |
| candidate-oracle accuracy | 99.230% |
| oracle gain | +1.992 pp |
| required gain (20% fewer errors, >=0.05 pp) | 0.552 pp |
| ceiling clears | yes |

The ceiling was computed before the compound resolver changes and is retained as the
selection evidence for the rejected selector; the learner result and its rejection are in
`docs/learning.md`.

## What is not measured

* Application-call accuracy on natural text; the corpus is dictionary lemmas.
* Preferred-form accuracy; Wiktionary preferences are not independently labelled.
* Singularization, verbs, sentence context, multilingual morphology.
* Human adjudication; no records were externally reviewed.
* The strict-family rerun is a sensitivity check with a deterministic approximate stemmer,
  not a second primary protocol; the `split-v1` results remain the headline evaluation.

## Reproduction

```bash
# one-off network fetch and checksum verification
py -3 -m tools.research.cli fetch

# offline: normalize, split, build both artifacts and membership tables
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all  --selection compact
py -3 -m tools.research.cli artifact --split train --selection compact --out build/p2/lexicon-v1.tsv

./mvnw -q -o -pl inflector-eval -am install -DskipTests

# P1
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds-v4.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds-v4.tsv \
  --out results/eval/p1-report-final.json --split test --lexicon-split all --iterations 2000

# P2 dev/test (train-only artifact; membership is loaded from its sibling file)
for split in dev test; do
  ./mvnw -q -o -pl inflector-eval exec:java \
    -Denglish.inflector.lexicon="$(pwd)/build/p2/lexicon-v1.tsv" \
    -Dexec.args="--input results/eval/p2-$split-records.tsv --out results/eval/p2-$split-preds-v4.tsv"
  py -3 -m tools.research.cli evaluate \
    --gold "results/eval/p2-$split-gold.tsv" \
    --predictions "results/eval/p2-$split-preds-v4.tsv" \
    --out "results/eval/p2-$split-report-final.json" --split "$split" \
    --lexicon-split train --iterations 2000
done

# corrected out-of-fold learning input and selector run
py -3 -m tools.research.cli oof --folds 5
py -3 -m tools.research.cli learn --linear-only --iterations 1000
```

The full driver is `scripts/rebuild-and-evaluate.sh`. `data/raw/` is gitignored; without it
the corpus evaluation is unavailable, never silently successful.
