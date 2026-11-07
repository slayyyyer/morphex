# Evaluation report (test split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.832% | 15889 | 96.474% | 1278 |
| lexicon | 69.455% | 15571 | 97.365% | 955 |
| rules | 67.798% | 16416 | 95.163% | 1753 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +0.8913 | [+0.7355, +1.0531] | 538 | 215 | 1278 | 955 | +25.274% |
| evo2 | rules | -1.3107 | [-1.5146, -1.1084] | 463 | 938 | 1278 | 1753 | -37.167% |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2601 | 1954 | 75.12% |
| by_bucket:initialism | 1110 | 729 | 65.68% |
| by_bucket:simple | 46916 | 32159 | 68.55% |
| by_bucket:unicode | 351 | 247 | 70.37% |
| by_countability:COUNTABLE | 29592 | 28242 | 95.44% |
| by_countability:MASS | 13687 | 96 | 0.70% |
| by_countability:MIXED | 6915 | 6746 | 97.56% |
| by_countability:PLURAL_ONLY | 784 | 5 | 0.64% |
| by_length:short | 3757 | 3469 | 92.33% |
| by_length:medium | 17226 | 16637 | 96.58% |
| by_length:long | 15258 | 14857 | 97.37% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2601 | 2004 | 77.05% |
| by_bucket:initialism | 1110 | 783 | 70.54% |
| by_bucket:simple | 46916 | 32367 | 68.99% |
| by_bucket:unicode | 351 | 253 | 72.08% |
| by_countability:COUNTABLE | 29592 | 28502 | 96.32% |
| by_countability:MASS | 13687 | 107 | 0.78% |
| by_countability:MIXED | 6915 | 6788 | 98.16% |
| by_countability:PLURAL_ONLY | 784 | 10 | 1.28% |
| by_length:short | 3757 | 3642 | 96.94% |
| by_length:medium | 17226 | 16823 | 97.66% |
| by_length:long | 15258 | 14821 | 97.14% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2601 | 1942 | 74.66% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46916 | 32367 | 68.99% |
| by_bucket:unicode | 351 | 253 | 72.08% |
| by_countability:COUNTABLE | 29592 | 27941 | 94.42% |
| by_countability:MASS | 13687 | 69 | 0.50% |
| by_countability:MIXED | 6915 | 6548 | 94.69% |
| by_countability:PLURAL_ONLY | 784 | 4 | 0.51% |
| by_length:short | 3757 | 2870 | 76.39% |
| by_length:medium | 17226 | 16804 | 97.55% |
| by_length:long | 15258 | 14814 | 97.09% |
