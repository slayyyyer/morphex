# Evaluation report (test split, lexicon split all)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.832% | 15889 | 96.474% | 1278 |
| lexicon | 100.000% | 0 | 100.000% | 0 |
| rules | 67.798% | 16416 | 95.163% | 1753 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +3.5264 | [+3.3318, +3.7132] | 1278 | 0 | 1278 | 0 | +100.000% |
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
| by_bucket:compound | 2601 | 2601 | 100.00% |
| by_bucket:initialism | 1110 | 1110 | 100.00% |
| by_bucket:simple | 46916 | 46916 | 100.00% |
| by_bucket:unicode | 351 | 351 | 100.00% |
| by_countability:COUNTABLE | 29592 | 29592 | 100.00% |
| by_countability:MASS | 13687 | 13687 | 100.00% |
| by_countability:MIXED | 6915 | 6915 | 100.00% |
| by_countability:PLURAL_ONLY | 784 | 784 | 100.00% |
| by_length:short | 3757 | 3757 | 100.00% |
| by_length:medium | 17226 | 17226 | 100.00% |
| by_length:long | 15258 | 15258 | 100.00% |

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
