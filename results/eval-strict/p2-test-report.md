# Evaluation report (test split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 69.200% | 16144 | 96.379% | 1358 |
| lexicon | 69.715% | 15874 | 97.238% | 1036 |
| rules | 68.235% | 16650 | 95.174% | 1810 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +0.8585 | [+0.6868, +1.0287] | 536 | 214 | 1358 | 1036 | +23.711% |
| evo2 | rules | -1.2051 | [-1.4160, -0.9865] | 478 | 930 | 1358 | 1810 | -33.284% |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 3528 | 2581 | 73.16% |
| by_bucket:initialism | 1101 | 717 | 65.12% |
| by_bucket:simple | 47472 | 32780 | 69.05% |
| by_bucket:unicode | 315 | 194 | 61.59% |
| by_countability:COUNTABLE | 30454 | 29006 | 95.25% |
| by_countability:MASS | 13906 | 103 | 0.74% |
| by_countability:MIXED | 7333 | 7160 | 97.64% |
| by_countability:PLURAL_ONLY | 723 | 3 | 0.41% |
| by_length:short | 3709 | 3441 | 92.77% |
| by_length:medium | 17616 | 16954 | 96.24% |
| by_length:long | 16182 | 15754 | 97.36% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 3528 | 2584 | 73.24% |
| by_bucket:initialism | 1101 | 769 | 69.85% |
| by_bucket:simple | 47472 | 32983 | 69.48% |
| by_bucket:unicode | 315 | 206 | 65.40% |
| by_countability:COUNTABLE | 30454 | 29250 | 96.05% |
| by_countability:MASS | 13906 | 68 | 0.49% |
| by_countability:MIXED | 7333 | 7221 | 98.47% |
| by_countability:PLURAL_ONLY | 723 | 3 | 0.41% |
| by_length:short | 3709 | 3588 | 96.74% |
| by_length:medium | 17616 | 17178 | 97.51% |
| by_length:long | 16182 | 15705 | 97.05% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 3528 | 2577 | 73.04% |
| by_bucket:initialism | 1101 | 0 | 0.00% |
| by_bucket:simple | 47472 | 32983 | 69.48% |
| by_bucket:unicode | 315 | 206 | 65.40% |
| by_countability:COUNTABLE | 30454 | 28703 | 94.25% |
| by_countability:MASS | 13906 | 66 | 0.47% |
| by_countability:MIXED | 7333 | 6994 | 95.38% |
| by_countability:PLURAL_ONLY | 723 | 3 | 0.41% |
| by_length:short | 3709 | 2828 | 76.25% |
| by_length:medium | 17616 | 17169 | 97.46% |
| by_length:long | 16182 | 15700 | 97.02% |
