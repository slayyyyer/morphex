# External validation: UD English EWT

Source: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0. Independent of the Kaikki/Wiktionary snapshot.
Filtered pairs: 2184 lemmas, 2221 attested forms; 454 lemmas are present in the shipped lexicon, 1730 are unseen.
Accepted-form accuracy: selected plural or any reported alternative matches an attested form.
The artifact was not retrained or tuned on this corpus.

| system | overall | seen | unseen |
| --- | --- | --- | --- |
| evo2 | 98.12% [97.53, 98.67] | 95.37% [93.39, 97.14] | 98.84% [98.32, 99.31] |
| lexicon | 94.55% [93.59, 95.47] | 77.09% [73.13, 80.84] | 99.13% [98.67, 99.54] |
| rules | 97.71% [97.07, 98.31] | 92.29% [89.65, 94.49] | 99.13% [98.67, 99.54] |

## Error diagnosis

119 lexicon errors; 100 are identity returns. By artifact class: MASS/- 44, PLURAL_ONLY/I 40, PLURAL_ONLY/- 13, MASS/I 3.

## Lexicon errors (119)

| lemma | gold | selected | source | seen | artifact |
| --- | --- | --- | --- | --- | --- |
| accent | accents | accent | lexicon | yes | PLURAL_ONLY/I |
| account | accounts | account | lexicon | yes | MASS/- |
| accountability | accountabilities | accountability | lexicon | yes | MASS/- |
| alternative | alternatives | alternative | lexicon | yes | MASS/- |
| amenity | amenities, amnesties | amenity | lexicon | yes | MASS/I |
| aquarium | aquiriums | aquariums | lexicon | yes | COUNTABLE/- |
| asset | assets | asset | lexicon | yes | PLURAL_ONLY/I |
| atty | attys | atties | rule | no | NOT_IN_ARTIFACT/- |
| bag | bags | bag | lexicon | yes | PLURAL_ONLY/I |
| ball | balls | ball | lexicon | yes | PLURAL_ONLY/- |
| behavior | behaviors | behavior | lexicon | yes | MASS/- |
| belonging | belongings | belonging | lexicon | yes | PLURAL_ONLY/I |
| blade | blades | blade | lexicon | yes | MASS/- |
| bog | bogs | bog | lexicon | yes | MASS/- |
| book | books | book | lexicon | yes | PLURAL_ONLY/I |
| boot | boots | boot | lexicon | yes | MASS/- |
| breeder | bredders | breeders | rule | no | NOT_IN_ARTIFACT/- |
| burrito | borritos | burritos | rule | no | NOT_IN_ARTIFACT/- |
| case | cases | case | lexicon | yes | MASS/- |
| chamber | chambers | chamber | lexicon | yes | PLURAL_ONLY/I |
| cheek | cheeks | cheek | lexicon | yes | PLURAL_ONLY/I |
| chip | chips | chip | lexicon | yes | PLURAL_ONLY/- |
| collection | collections | collection | lexicon | yes | PLURAL_ONLY/I |
| compliment | compliments | compliment | lexicon | yes | MASS/- |
| connection | connections | connection | lexicon | yes | PLURAL_ONLY/I |
| copy | copies | copy | lexicon | yes | MASS/- |
| corn | corns | corn | lexicon | yes | MASS/- |
| cpy | cpys | cpies | rule | no | NOT_IN_ARTIFACT/- |
| crew | crews | crew | lexicon | yes | MASS/- |
| curve | curves | curve | lexicon | yes | PLURAL_ONLY/I |
| dinosaur | dinasaurs | dinosaurs | rule | no | NOT_IN_ARTIFACT/- |
| dislike | dislikes | dislike | lexicon | yes | PLURAL_ONLY/I |
| dispute | disputes | dispute | lexicon | yes | MASS/- |
| effect | effects | effect | lexicon | yes | PLURAL_ONLY/I |
| element | elements | element | lexicon | yes | PLURAL_ONLY/I |
| eye | eyes | eye | lexicon | yes | PLURAL_ONLY/- |
| face | faces | face | lexicon | yes | MASS/- |
| farmland | farmlands | farmland | lexicon | yes | MASS/- |
| feeling | feelings | feeling | lexicon | yes | PLURAL_ONLY/I |
| field | fields | field | lexicon | yes | PLURAL_ONLY/I |
| ... | (79 more) | | | | |
