# Strict morphological-family rerun

Date: 2026-11-07. Supplementary robustness check, not a replacement for the primary
`split-v1` P2 table or the existing external report.

## Protocol

`split-v2` uses the same deterministic hash threshold (`<70` train, `<85` dev, otherwise
test) but groups conservative derivational relatives before hashing. The key lower-cases
the lemma, retains the existing first-token rule for compounds, strips up to two productive
nominal suffixes (for example `-ation`, `-ment`, `-ness`, `-ity`, `-er`, `-ing`), and
normalizes a final `e`/`y` and doubled consonant. It is an approximation, not a lemmatizer.

The P2 artifact was rebuilt from strict train families only: **102,273 entries**. Strict
P2 primary denominators are 36,520 (dev) and 37,507 (test); they differ from the primary
split because families moved between train/dev/test. External EWT is unchanged (2,184
lemmas, 2,221 forms), but its family-seen/family-unseen split now uses the same strict key.

## P2, evo2 and this library

Primary accepted-form accuracy, countable/mixed non-invariant records:

| split | evo2 | this library | paired delta (95% CI) | corrections / regressions |
| --- | ---: | ---: | ---: | ---: |
| dev (36,520) | 96.358% (1,330) | **97.125% (1,050)** | **+0.7667 pp** `[+0.4742, +1.0107]` | 553 / 273 |
| test (37,507) | 96.379% (1,358) | **97.238% (1,036)** | **+0.8585 pp** `[+0.6868, +1.0287]` | 536 / 214 |

Rules-only reference: dev 94.932% (1,851), test 95.174% (1,810). The strict test
result is 322 fewer errors than evo2 (23.711% relative error reduction).

## External EWT, evo2 and this library

Selected-output accuracy, with family-clustered bootstrap intervals. Exact seen/unseen is
the original lemma-membership split; family seen/unseen uses the strict family key.

| slice | evo2 | this library |
| --- | ---: | ---: |
| overall (2,184) | 98.12% (2,143) `[97.53, 98.67]` | **98.21% (2,145)** `[97.64, 98.76]` |
| exact seen (368) | **94.29% (347)** `[91.80, 96.49]` | 93.48% (344) `[90.96, 95.91]` |
| exact unseen (1,816) | 98.90% (1,796) `[98.39, 99.34]` | **99.17% (1,801)** `[98.73, 99.56]` |
| family seen (1,677) | **98.09% (1,645)** `[97.42, 98.73]` | 98.03% (1,644) `[97.35, 98.64]` |
| family unseen (507) | 98.22% (498) `[97.02, 99.22]` | **98.82% (501)** `[97.82, 99.61]` |

The library's alternative-set coverage is 98.76% (2,157/2,184). The external family-unseen
slice is where the library's advantage is largest (+0.60 pp); exact-seen remains slightly
below evo2 because the shipped artifact intentionally preserves its dictionary policy
choices.

## Interpretation

The stricter grouping lowers both systems' P2 accuracy relative to their original splits
(evo2 96.473% → 96.379% test; this library 97.376% → 97.238%), which is expected when
related derivational forms are forced into the same split. The ranking and positive paired
gain remain. External family-unseen accuracy also remains ahead (98.82% vs 98.22%).
