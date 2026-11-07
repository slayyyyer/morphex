# Evaluation report (test split, lexicon split all)

Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.

| system | accepted accuracy | errors | primary accuracy | primary errors |
| --- | --- | --- | --- | --- |
| evo2 | 68.825% | 15880 | 96.502% | 1222 |
| lexicon | 99.996% | 2 | 99.997% | 1 |
| pyinflect | 67.080% | 16769 | 94.157% | 2041 |
| rules | 64.239% | 18216 | 88.618% | 3976 |

## Paired comparisons (primary slice, family-clustered bootstrap)

| baseline | candidate | delta pp | 95% CI | corrections | regressions |
| --- | --- | --- | --- | --- | --- |
| evo2 | lexicon | +4.1481 | [+3.9231, +4.3629] | 1461 | 1 |
| evo2 | pyinflect | -2.3241 | [-2.4966, -2.1504] | 100 | 918 |
| evo2 | rules | -7.8274 | [-8.9596, -6.9535] | 467 | 3222 |

## Slice detail

### evo2

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 1952 | 75.08% |
| by_bucket:initialism | 1110 | 729 | 65.68% |
| by_bucket:simple | 46882 | 32130 | 68.53% |
| by_bucket:unicode | 346 | 247 | 71.39% |
| by_countability:COUNTABLE | 29736 | 28389 | 95.47% |
| by_countability:MASS | 14952 | 1309 | 8.75% |
| by_countability:MIXED | 5461 | 5347 | 97.91% |
| by_countability:PLURAL_ONLY | 789 | 13 | 1.65% |
| by_length:short | 3627 | 3359 | 92.61% |
| by_length:medium | 16773 | 16200 | 96.58% |
| by_length:long | 14533 | 14152 | 97.38% |

### lexicon

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 2599 | 99.96% |
| by_bucket:initialism | 1110 | 1110 | 100.00% |
| by_bucket:simple | 46882 | 46881 | 100.00% |
| by_bucket:unicode | 346 | 346 | 100.00% |
| by_countability:COUNTABLE | 29736 | 29735 | 100.00% |
| by_countability:MASS | 14952 | 14951 | 99.99% |
| by_countability:MIXED | 5461 | 5461 | 100.00% |
| by_countability:PLURAL_ONLY | 789 | 789 | 100.00% |
| by_length:short | 3627 | 3627 | 100.00% |
| by_length:medium | 16773 | 16773 | 100.00% |
| by_length:long | 14533 | 14532 | 99.99% |

### pyinflect

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 1929 | 74.19% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46882 | 31993 | 68.24% |
| by_bucket:unicode | 346 | 247 | 71.39% |
| by_countability:COUNTABLE | 29736 | 27792 | 93.46% |
| by_countability:MASS | 14952 | 1235 | 8.26% |
| by_countability:MIXED | 5461 | 5126 | 93.87% |
| by_countability:PLURAL_ONLY | 789 | 16 | 2.03% |
| by_length:short | 3627 | 2624 | 72.35% |
| by_length:medium | 16773 | 16147 | 96.27% |
| by_length:long | 14533 | 14121 | 97.17% |

### rules

| slice | total | correct | accuracy |
| --- | --- | --- | --- |
| by_bucket:compound | 2600 | 633 | 24.35% |
| by_bucket:initialism | 1110 | 0 | 0.00% |
| by_bucket:simple | 46882 | 31840 | 67.92% |
| by_bucket:unicode | 346 | 249 | 71.97% |
| by_countability:COUNTABLE | 29736 | 26050 | 87.60% |
| by_countability:MASS | 14952 | 1702 | 11.38% |
| by_countability:MIXED | 5461 | 4931 | 90.29% |
| by_countability:PLURAL_ONLY | 789 | 39 | 4.94% |
| by_length:short | 3627 | 2754 | 75.93% |
| by_length:medium | 16773 | 15412 | 91.89% |
| by_length:long | 14533 | 12791 | 88.01% |

