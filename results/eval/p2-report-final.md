# Evaluation report (test split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.882% | 15851 | 96.473% | 1278 |
| lexicon | 69.428% | 15573 | 97.376% | 951 |
| rules | 67.851% | 16376 | 95.168% | 1751 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +0.9023 | [+0.7309, +1.0718] | 540 | 213 | 1278 | 951 | +25.587% |
| evo2 | rules | -1.3052 | [-1.5035, -1.0974] | 463 | 936 | 1278 | 1751 | -37.011% |

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
| by_bucket:compound | 2600 | 1964 | 75.54% |
| by_bucket:initialism | 1110 | 783 | 70.54% |
| by_bucket:simple | 46882 | 32365 | 69.04% |
| by_bucket:unicode | 346 | 253 | 73.12% |
| by_countability:COUNTABLE | 29593 | 28502 | 96.31% |
| by_countability:MASS | 13687 | 71 | 0.52% |
| by_countability:MIXED | 6912 | 6788 | 98.21% |
| by_countability:PLURAL_ONLY | 746 | 4 | 0.54% |
| by_length:short | 3757 | 3643 | 96.97% |
| by_length:medium | 17224 | 16823 | 97.67% |
| by_length:long | 15258 | 14822 | 97.14% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 1944 | 74.77% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46882 | 32365 | 69.04% |
| by_bucket:unicode | 346 | 253 | 73.12% |
| by_countability:COUNTABLE | 29593 | 27944 | 94.43% |
| by_countability:MASS | 13687 | 69 | 0.50% |
| by_countability:MIXED | 6912 | 6545 | 94.69% |
| by_countability:PLURAL_ONLY | 746 | 4 | 0.54% |
| by_length:short | 3757 | 2870 | 76.39% |
| by_length:medium | 17224 | 16804 | 97.56% |
| by_length:long | 15258 | 14814 | 97.09% |
