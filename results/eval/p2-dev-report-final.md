# Evaluation report (dev split, lexicon split train)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 69.412% | 15542 | 96.397% | 1314 |
| lexicon | 70.045% | 15220 | 97.398% | 949 |
| rules | 68.524% | 15993 | 95.281% | 1721 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions | baseline errors | candidate errors | relative error reduction |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +1.0008 | [+0.8317, +1.1672] | 545 | 180 | 1314 | 949 | +27.778% |
| evo2 | rules | -1.1160 | [-1.3359, -0.8942] | 469 | 876 | 1314 | 1721 | -30.974% |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2620 | 1988 | 75.88% |
| by_bucket:initialism | 1113 | 701 | 62.98% |
| by_bucket:simple | 46733 | 32342 | 69.21% |
| by_bucket:unicode | 344 | 237 | 68.90% |
| by_countability:COUNTABLE | 29765 | 28365 | 95.30% |
| by_countability:MASS | 13383 | 87 | 0.65% |
| by_countability:MIXED | 6989 | 6815 | 97.51% |
| by_countability:PLURAL_ONLY | 673 | 1 | 0.15% |
| by_length:short | 3640 | 3366 | 92.47% |
| by_length:medium | 17284 | 16643 | 96.29% |
| by_length:long | 15546 | 15147 | 97.43% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2620 | 2016 | 76.95% |
| by_bucket:initialism | 1113 | 762 | 68.46% |
| by_bucket:simple | 46733 | 32567 | 69.69% |
| by_bucket:unicode | 344 | 245 | 71.22% |
| by_countability:COUNTABLE | 29765 | 28629 | 96.18% |
| by_countability:MASS | 13383 | 67 | 0.50% |
| by_countability:MIXED | 6989 | 6892 | 98.61% |
| by_countability:PLURAL_ONLY | 673 | 2 | 0.30% |
| by_length:short | 3640 | 3528 | 96.92% |
| by_length:medium | 17284 | 16849 | 97.48% |
| by_length:long | 15546 | 15144 | 97.41% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2620 | 2004 | 76.49% |
| by_bucket:initialism | 1113 | 1 | 0.09% |
| by_bucket:simple | 46733 | 32567 | 69.69% |
| by_bucket:unicode | 344 | 245 | 71.22% |
| by_countability:COUNTABLE | 29765 | 28073 | 94.32% |
| by_countability:MASS | 13383 | 66 | 0.49% |
| by_countability:MIXED | 6989 | 6676 | 95.52% |
| by_countability:PLURAL_ONLY | 673 | 2 | 0.30% |
| by_length:short | 3640 | 2773 | 76.18% |
| by_length:medium | 17284 | 16837 | 97.41% |
| by_length:long | 15546 | 15139 | 97.38% |
