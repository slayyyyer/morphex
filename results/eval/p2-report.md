# Evaluation report (test split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.882% | 15851 | 96.473% | 1278 |
| lexicon | 64.259% | 18206 | 88.714% | 4090 |
| rules | 64.259% | 18206 | 88.714% | 4090 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions |
| --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | -7.7031 | [-8.7735, -6.8541] | 479 | 3291 |
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

