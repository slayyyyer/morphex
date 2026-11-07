# External stress test: AGID 2016.01.19 (lexical families)

Source: [AGID 2016.01.19](https://sourceforge.net/projects/wordlist/files/AGID/2016.01.19/), AGID permissive terms (WordNet 1.6-style). This is an automatically generated inflection lexicon, not a hand-adjudicated corpus; it is independent of the shipped vocabulary.
Filtered pairs: 34642 lemmas, 34642 forms.
AGID has no countability labels; many mass/abstract nouns receive generated plurals. The library's MASS/invariant policy therefore returns identity against this gold. The overall row is a lexical stress test, not a corpus-accuracy estimate; the full-vocabulary unseen slice is the primary new denominator.
Full-vocabulary membership: 34381 seen / 261 unseen. Compact-artifact membership: 5463 hit / 29179 miss. These labels are deliberately separate: a regular word omitted by compaction is still a seen vocabulary lemma.
Family membership uses the full vocabulary (34395 family-seen / 247 family-unseen); the compact artifact's family labels are reported only as an artifact diagnostic.
Selected-output accuracy: the selected plural matches the gold form. Alternative-set coverage is reported separately and is not compared against another system's selected output.
The artifact was not trained or tuned on this source.

| system | selected overall | vocab seen | vocab unseen | family seen | family unseen | artifact hit | artifact miss | alternative coverage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | 97.48% [97.32, 97.64] | 97.50% [97.33, 97.66] | 95.02% [92.34, 97.32] | 97.50% [97.33, 97.66] | 94.74% [91.90, 97.17] | 87.50% [86.62, 88.34] | 99.35% [99.26, 99.44] | 0.00% [0.00, 0.00] |
| lexicon | 93.84% [93.59, 94.09] | 93.83% [93.57, 94.08] | 95.40% [92.72, 97.70] | 93.83% [93.58, 94.08] | 95.14% [92.31, 97.57] | 61.54% [60.24, 62.79] | 99.89% [99.85, 99.92] | 94.67% [94.43, 94.89] |
| rules | 98.20% [98.06, 98.33] | 98.22% [98.08, 98.35] | 95.40% [92.72, 97.70] | 98.22% [98.08, 98.35] | 95.14% [92.31, 97.57] | 89.16% [88.34, 89.95] | 99.89% [99.85, 99.92] | 98.20% [98.06, 98.33] |

## Error diagnosis

2133 lexicon errors; 1722 are identity returns; 2121 are full-vocabulary-seen and 2101 are compact-artifact hits. By artifact class: MASS/I 1685, PLURAL_ONLY/I 17, MASS/- 12, COUNTABLE/I 8.

## Lexicon errors (2133)

| lemma | gold | selected | source | vocab seen | family seen | artifact | artifact hit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| abomasum | abomasa | abomasums | lexicon | yes | yes | COUNTABLE/- | yes |
| above | aboves | above | lexicon | yes | yes | MASS/I | yes |
| absentmindedness | absentmindednesses | absentmindedness | lexicon | yes | yes | MASS/I | yes |
| abstemiousness | abstemiousnesses | abstemiousness | lexicon | yes | yes | MASS/I | yes |
| abstractedness | abstractednesses | abstractedness | lexicon | yes | yes | MASS/I | yes |
| abusiveness | abusivenesses | abusiveness | lexicon | yes | yes | MASS/I | yes |
| academia | academias | academia | lexicon | yes | yes | MASS/I | yes |
| acapnia | acapnias | acapnia | lexicon | yes | yes | MASS/I | yes |
| acarophobia | acarophobias | acarophobia | lexicon | yes | yes | MASS/I | yes |
| acarus | acari | acarina | lexicon | yes | yes | COUNTABLE/- | yes |
| acceptableness | acceptablenesses | acceptableness | lexicon | yes | yes | MASS/I | yes |
| accurateness | accuratenesses | accurateness | lexicon | yes | yes | MASS/I | yes |
| accursedness | accursednesses | accursedness | lexicon | yes | yes | MASS/I | yes |
| acedia | acedias | acedia | lexicon | yes | yes | MASS/I | yes |
| acetophenetidin | acetophenetidins | acetophenetidin | lexicon | yes | yes | MASS/I | yes |
| achira | achiras | achira | lexicon | yes | yes | MASS/I | yes |
| acholia | acholias | acholia | lexicon | yes | yes | MASS/I | yes |
| achromatism | achromatisms | achromatism | lexicon | yes | yes | MASS/I | yes |
| acidimetry | acidimetries | acidimetry | lexicon | yes | yes | MASS/I | yes |
| acridness | acridnesses | acridness | lexicon | yes | yes | MASS/I | yes |
| acrimoniousness | acrimoniousnesses | acrimoniousness | lexicon | yes | yes | MASS/I | yes |
| adamance | adamances | adamance | lexicon | yes | yes | MASS/I | yes |
| addendum | addenda | addendums | lexicon | yes | yes | COUNTABLE/- | yes |
| adipose | adiposes | adipose | lexicon | yes | yes | MASS/I | yes |
| adiposeness | adiposenesses | adiposeness | lexicon | yes | yes | MASS/I | yes |
| admass | admasses | admass | lexicon | yes | yes | MASS/I | yes |
| admirableness | admirablenesses | admirableness | lexicon | yes | yes | MASS/I | yes |
| ado | ados | ado | lexicon | yes | yes | MASS/I | yes |
| adrenocorticotrophin | adrenocorticotrophins | adrenocorticotrophin | lexicon | yes | yes | MASS/I | yes |
| adrenocorticotropin | adrenocorticotropins | adrenocorticotropin | lexicon | yes | yes | MASS/I | yes |
| advantageousness | advantageousnesses | advantageousness | lexicon | yes | yes | MASS/I | yes |
| adventurousness | adventurousnesses | adventurousness | lexicon | yes | yes | MASS/I | yes |
| advice | advices | advice | lexicon | yes | yes | MASS/- | yes |
| aecium | aecia | aeciums | lexicon | yes | yes | COUNTABLE/- | yes |
| aeromedicine | aeromedicines | aeromedicine | lexicon | yes | yes | MASS/I | yes |
| aerophagia | aerophagias | aerophagia | lexicon | yes | yes | MASS/I | yes |
| affectionateness | affectionatenesses | affectionateness | lexicon | yes | yes | MASS/I | yes |
| aftercare | aftercares | aftercare | lexicon | yes | yes | MASS/I | yes |
| agamogenesis | agamogeneses | agamogenesis | lexicon | yes | yes | MASS/I | yes |
| agedness | agednesses | agedness | lexicon | yes | yes | MASS/I | yes |
| ... | (2093 more) | | | | | | |
