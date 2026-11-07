# Python baseline: `inflect`

A third-party reference point outside the Java ecosystem: PyPI
[`inflect`](https://pypi.org/project/inflect/) 7.6.0 (`jaraco/inflect`), the most widely
used Python singular→plural engine, scored on the same frozen P1 protocol as everything
else in this repository. Nothing here was tuned; `inflect` runs in its default (modern)
mode, matching this library's anglicized default.

## Method

* `tools/research/python_baseline.py` (needs `pip install inflect`) writes
  `results/eval/pyinflect-test-preds.tsv`: one row per test record,
  `engine.plural_noun(lemma, 2)`. A non-string result falls back to the lemma unchanged;
  that never triggered (0 fallbacks in 50,938 records, ~5 s).
* Scored with the unchanged fixed evaluator, precisely:
  ```bash
  py -m tools.research.cli evaluate \
    --gold results/eval/test-gold.tsv \
    --predictions results/eval/p1-test-preds.tsv results/eval/pyinflect-test-preds.tsv \
    --out results/eval/pyinflect-report.json --split test --lexicon-split all
  ```
* Report: `results/eval/pyinflect-report.json` (plus the generated `.md`). Like P1, this
  measures dictionary coverage on the frozen test families, not unseen generalization.

## Headline (P1, 36,239 primary records)

| system | primary accuracy | errors | overall accuracy | overall errors / 50,938 |
| --- | --- | --- | --- | --- |
| this library (rules + lexicon) | **100.000%** | **0** | **100.000%** | **0** |
| evo-inflector 2.0 | 96.473% | 1,278 | 68.882% | 15,851 |
| **PyPI `inflect` 7.6.0** | **94.169%** | **2,113** | **67.152%** | **16,732** |
| repaired rules only | 88.714% | 4,090 | 64.259% | 18,206 |

Paired vs evo2: **−2.2846 pp** `[-2.4513, -2.1104]`, 103 corrections, 937 regressions —
`inflect` sits between the rules-only floor and evo2 on this noun dictionary.

## Where it differs

Bucket breakdown for `pyinflect` (accepted-form accuracy):

| slice | accuracy | errors / total |
| --- | --- | --- |
| `simple` | 68.32% | 14,853 / 46,882 |
| `compound` | 74.23% | 670 / 2,600 |
| `initialism` | **0.00%** | **1,110 / 1,110** |
| `unicode` | 71.39% | 99 / 346 |
| `COUNTABLE` | 93.46% | 1,934 / 29,593 |
| `MASS` | 0.33% | 13,642 / 13,687 |
| `MIXED` | 93.94% | 419 / 6,912 |
| `PLURAL_ONLY` | 1.21% | 737 / 746 |

Three patterns explain most of it:

1. **Initialism casing.** `inflect` re-cases the whole word plus suffix
   (`AABNCP → AABNCPS` where `AABNCPs` is attested; `UFO → UFOS`, `SMS → SMSS`) —
   every one of the 1,110 initialism records misses. Same bug family as evo2's
   `CHILD → CHILDren`, different direction.
2. **Classical `-us` forms.** `acardiacus → acardiacuses` (attested `acardiaci`),
   `accidens → accidenss` (`accidentia`), `accusatrix → accusatrixes`
   (`accusatrices`). This library carries these as lexical entries; `inflect`'s
   modern-mode rules don't.
3. **Mass nouns pluralized.** Like evo2, `inflect` pluralizes almost all mass records
   (0.33% on the MASS slice — the few it gets right are identities like `information`), hence
   67.15% overall — slightly below evo2's 68.88% for the same structural reason.

## Showcase probe (observed outputs, not scored)

| input | evo-inflector 2.0 | `inflect` 7.6.0 | this library |
| --- | --- | --- | --- |
| `German` | `Germen` | `Germans` | `Germans` |
| `blouse` | `blice` | `blouses` | `blouses` |
| `scarp` | `scarp` | `scarps` | `scarps` |
| `UFO` | `UFOes` | `UFOS` | `UFOs` |
| `SMS` | `SMSes` | `SMSS` | `SMSes` |
| `CHILD` | `CHILDren` | `CHILDREN` | `CHILDREN` |
| `CITY` | `CITIes` | `CITYS` | `CITIES` |
| `mother-in-law` | `mother-in-laws` | `mothers-in-law` | `mothers-in-law` |
| `passer-by` | `passer-bies` | `passer-bies` | `passers-by` |
| `drive-by` | `drive-bies` | `drive-bies` | `drive-bys` |
| `stand-in` | `stand-ins` | `stand-ins` | `stand-ins` |
| `attorney general` | `attorney generals` | `attorneys general` | `attorneys general` |
| `man-of-war` | *(not probed)* | `men-of-war` | `men-of-war` |
| `jack-in-the-box` | *(not probed)* | `jacks-in-the box` | `jack-in-the-boxes` |
| `information` | `informations` | `information` | `information` |
| `person` / `die` | `persons` / `dies` | `people` / `dice` | `people` / `dice` |
| `user_id` | rewritten | `user_ids` | unchanged (`FALLBACK`) |
| `well-known` | *(guessed)* | `well-knowns` | unchanged (`FALLBACK`) |

`inflect` agrees with this library on the headline irregulars (`German`, `blouse`,
`person → people`, `die → dice`) and shares evo2's `passer-by`/`drive-by` weakness —
plus its own compound mangling (`jacks-in-the box`). It is a strong general-purpose
engine; on this dictionary its misses concentrate in casing, classical Latin/Greek
forms, and compounds.

## Limitations

* One library, one version, default mode — not a survey of Python pluralization.
* The `.classical()` mode was not enabled (would change classical-form preferences both
  ways); worth a follow-up run.
* P1 caveats apply unchanged: dictionary coverage, not production accuracy
  (see `docs/evaluation.md`).
