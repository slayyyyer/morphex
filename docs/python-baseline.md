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

## Headline (P1, 34,933 primary records)

| system | primary accuracy | errors | overall accuracy | overall errors / 50,938 |
| --- | --- | --- | --- | --- |
| this library (rules + lexicon) | **100.000%** | **0** | **100.000%** | **0** |
| evo-inflector 2.0 | 96.502% | 1,222 | 68.825% | 15,880 |
| **PyPI `inflect` 7.6.0** | **94.157%** | **2,041** | **67.080%** | **16,769** |
| repaired rules only | 88.618% | 3,976 | 64.239% | 18,216 |

Paired vs evo2: **−2.3241 pp** `[-2.4966, -2.1504]`, 100 corrections, 918 regressions —
`inflect` sits between the rules-only floor and evo2 on this noun dictionary.

## Where it differs

Bucket breakdown for `pyinflect` (accepted-form accuracy):

| slice | accuracy | errors / total |
| --- | --- | --- |
| `simple` | 68.24% | 14,889 / 46,882 |
| `compound` | 74.19% | 671 / 2,600 |
| `initialism` | **0.00%** | **1,110 / 1,110** |
| `unicode` | 71.39% | 99 / 346 |
| `COUNTABLE` | 93.46% | 1,944 / 29,736 |
| `MASS` | 8.26% | 13,717 / 14,952 |
| `MIXED` | 93.87% | 335 / 5,461 |
| `PLURAL_ONLY` | 2.03% | 773 / 789 |

Three patterns explain most of it:

1. **Initialism casing.** `inflect` re-cases the whole word plus suffix
   (`AABNCP → AABNCPS` where `AABNCPs` is attested; `UFO → UFOS`, `SMS → SMSS`) —
   every one of the 1,110 initialism records misses. Same bug family as evo2's
   `CHILD → CHILDren`, different direction.
2. **Classical `-us` forms.** `acardiacus → acardiacuses` (attested `acardiaci`),
   `accidens → accidenss` (`accidentia`), `accusatrix → accusatrixes`
   (`accusatrices`). This library carries these as lexical entries; `inflect`'s
   modern-mode rules don't.
3. **Mass nouns pluralized.** Like evo2, `inflect` pluralizes most mass records, hence
   67.08% overall — slightly below evo2's 68.825% for the same structural reason.

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
* P1 caveats apply unchanged: dictionary coverage, and the external EWT countability
  defect affects all systems' interpretation (see `docs/evaluation.md`).
