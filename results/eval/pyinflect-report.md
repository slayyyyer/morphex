# Evaluation report (test split, lexicon split all)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.882% | 15851 | 96.473% | 1278 |
| lexicon | 100.000% | 0 | 100.000% | 0 |
| pyinflect | 67.152% | 16732 | 94.169% | 2113 |
| rules | 64.259% | 18206 | 88.714% | 4090 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions |
| --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +4.1611 | [+3.9597, +4.3727] | 1519 | 0 |
| evo2 | pyinflect | -2.2846 | [-2.4513, -2.1104] | 103 | 937 |
| evo2 | rules | -7.7031 | [-8.7735, -6.8541] | 479 | 3291 |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 1954 | 75.15% |
| by_bucket:initialism | 1110 | 729 | 65.68% |
| by_bucket:simple | 46882 | 32157 | 68.59% |
| by_bucket:unicode | 346 | 247 | 71.39% |
| by_countability:COUNTABLE | 29593 | 28243 | 95.44% |
| by_countability:MASS | 13687 | 96 | 0.70% |
| by_countability:MIXED | 6912 | 6743 | 97.55% |
| by_countability:PLURAL_ONLY | 746 | 5 | 0.67% |
| by_length:short | 3757 | 3469 | 92.33% |
| by_length:medium | 17224 | 16635 | 96.58% |
| by_length:long | 15258 | 14857 | 97.37% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 2600 | 100.00% |
| by_bucket:initialism | 1110 | 1110 | 100.00% |
| by_bucket:simple | 46882 | 46882 | 100.00% |
| by_bucket:unicode | 346 | 346 | 100.00% |
| by_countability:COUNTABLE | 29593 | 29593 | 100.00% |
| by_countability:MASS | 13687 | 13687 | 100.00% |
| by_countability:MIXED | 6912 | 6912 | 100.00% |
| by_countability:PLURAL_ONLY | 746 | 746 | 100.00% |
| by_length:short | 3757 | 3757 | 100.00% |
| by_length:medium | 17224 | 17224 | 100.00% |
| by_length:long | 15258 | 15258 | 100.00% |

### pyinflect

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 1930 | 74.23% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46882 | 32029 | 68.32% |
| by_bucket:unicode | 346 | 247 | 71.39% |
| by_countability:COUNTABLE | 29593 | 27659 | 93.46% |
| by_countability:MASS | 13687 | 45 | 0.33% |
| by_countability:MIXED | 6912 | 6493 | 93.94% |
| by_countability:PLURAL_ONLY | 746 | 9 | 1.21% |
| by_length:short | 3757 | 2720 | 72.40% |
| by_length:medium | 17224 | 16584 | 96.28% |
| by_length:long | 15258 | 14822 | 97.14% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 606 | 23.31% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46882 | 31877 | 67.99% |
| by_bucket:unicode | 346 | 249 | 71.97% |
| by_countability:COUNTABLE | 29593 | 25906 | 87.54% |
| by_countability:MASS | 13687 | 529 | 3.86% |
| by_countability:MIXED | 6912 | 6268 | 90.68% |
| by_countability:PLURAL_ONLY | 746 | 29 | 3.89% |
| by_length:short | 3757 | 2853 | 75.94% |
| by_length:medium | 17224 | 15837 | 91.95% |
| by_length:long | 15258 | 13459 | 88.21% |

