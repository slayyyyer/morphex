# Evaluation report (dev split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 69.393% | 15557 | 96.400% | 1313 |
| lexicon | 70.092% | 15202 | 97.382% | 955 |
| rules | 68.486% | 16018 | 95.257% | 1730 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +0.9815 | [+0.8079, +1.1374] | 543 | 185 | 1313 | 955 | +27.266% |
| evo2 | rules | -1.1433 | [-1.3594, -0.9387] | 469 | 886 | 1313 | 1730 | -31.759% |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2621 | 1988 | 75.85% |
| by_bucket:initialism | 1113 | 701 | 62.98% |
| by_bucket:simple | 46748 | 32346 | 69.19% |
| by_bucket:unicode | 347 | 237 | 68.30% |
| by_countability:COUNTABLE | 29764 | 28366 | 95.30% |
| by_countability:MASS | 13383 | 87 | 0.65% |
| by_countability:MIXED | 6992 | 6818 | 97.51% |
| by_countability:PLURAL_ONLY | 690 | 1 | 0.14% |
| by_length:short | 3642 | 3368 | 92.48% |
| by_length:medium | 17285 | 16645 | 96.30% |
| by_length:long | 15546 | 15147 | 97.43% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2621 | 2049 | 78.18% |
| by_bucket:initialism | 1113 | 762 | 68.46% |
| by_bucket:simple | 46748 | 32571 | 69.67% |
| by_bucket:unicode | 347 | 245 | 70.61% |
| by_countability:COUNTABLE | 29764 | 28625 | 96.17% |
| by_countability:MASS | 13383 | 99 | 0.74% |
| by_countability:MIXED | 6992 | 6894 | 98.60% |
| by_countability:PLURAL_ONLY | 690 | 9 | 1.30% |
| by_length:short | 3642 | 3530 | 96.92% |
| by_length:medium | 17285 | 16847 | 97.47% |
| by_length:long | 15546 | 15141 | 97.39% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2621 | 1994 | 76.08% |
| by_bucket:initialism | 1113 | 1 | 0.09% |
| by_bucket:simple | 46748 | 32571 | 69.67% |
| by_bucket:unicode | 347 | 245 | 70.61% |
| by_countability:COUNTABLE | 29764 | 28069 | 94.31% |
| by_countability:MASS | 13383 | 66 | 0.49% |
| by_countability:MIXED | 6992 | 6674 | 95.45% |
| by_countability:PLURAL_ONLY | 690 | 2 | 0.29% |
| by_length:short | 3642 | 2770 | 76.06% |
| by_length:medium | 17285 | 16834 | 97.39% |
| by_length:long | 15546 | 15139 | 97.38% |

