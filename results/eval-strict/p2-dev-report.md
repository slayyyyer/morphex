# Evaluation report (dev split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 69.388% | 15587 | 96.358% | 1330 |
| lexicon | 69.759% | 15398 | 97.125% | 1050 |
| rules | 68.186% | 16199 | 94.932% | 1851 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +0.7667 | [+0.4742, +1.0107] | 553 | 273 | 1330 | 1050 | +21.053% |
| evo2 | rules | -1.4266 | [-1.7492, -1.1429] | 485 | 1006 | 1330 | 1851 | -39.173% |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2985 | 2266 | 75.91% |
| by_bucket:initialism | 1141 | 733 | 64.24% |
| by_bucket:simple | 46436 | 32094 | 69.11% |
| by_bucket:unicode | 356 | 238 | 66.85% |
| by_countability:COUNTABLE | 29917 | 28537 | 95.39% |
| by_countability:MASS | 13410 | 110 | 0.82% |
| by_countability:MIXED | 6865 | 6682 | 97.33% |
| by_countability:PLURAL_ONLY | 726 | 2 | 0.28% |
| by_length:short | 3778 | 3478 | 92.06% |
| by_length:medium | 17225 | 16613 | 96.45% |
| by_length:long | 15517 | 15099 | 97.31% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2985 | 2208 | 73.97% |
| by_bucket:initialism | 1141 | 789 | 69.15% |
| by_bucket:simple | 46436 | 32278 | 69.51% |
| by_bucket:unicode | 356 | 245 | 68.82% |
| by_countability:COUNTABLE | 29917 | 28721 | 96.00% |
| by_countability:MASS | 13410 | 47 | 0.35% |
| by_countability:MIXED | 6865 | 6750 | 98.32% |
| by_countability:PLURAL_ONLY | 726 | 2 | 0.28% |
| by_length:short | 3778 | 3651 | 96.64% |
| by_length:medium | 17225 | 16760 | 97.30% |
| by_length:long | 15517 | 15059 | 97.05% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2985 | 2195 | 73.53% |
| by_bucket:initialism | 1141 | 1 | 0.09% |
| by_bucket:simple | 46436 | 32278 | 69.51% |
| by_bucket:unicode | 356 | 245 | 68.82% |
| by_countability:COUNTABLE | 29917 | 28159 | 94.12% |
| by_countability:MASS | 13410 | 47 | 0.35% |
| by_countability:MIXED | 6865 | 6511 | 94.84% |
| by_countability:PLURAL_ONLY | 726 | 2 | 0.28% |
| by_length:short | 3778 | 2874 | 76.07% |
| by_length:medium | 17225 | 16744 | 97.21% |
| by_length:long | 15517 | 15051 | 97.00% |
