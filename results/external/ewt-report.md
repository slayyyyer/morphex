# External validation: UD English EWT

Source: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0. Independent of the Kaikki/Wiktionary snapshot.
Filtered pairs: 2184 lemmas, 2221 attested forms; 376 lemmas are present in the shipped lexicon, 1808 are unseen.
Accepted-form accuracy: selected plural or any reported alternative matches an attested form.
The artifact was not retrained or tuned on this corpus.

| system | overall | seen | unseen |
| --- | --- | --- | --- |
| evo2 | 98.12% [97.53, 98.67] | 94.41% [92.02, 96.54] | 98.89% [98.40, 99.34] |
| lexicon | 98.76% [98.26, 99.22] | 96.81% [94.95, 98.40] | 99.17% [98.73, 99.56] |
| rules | 97.71% [97.07, 98.31] | 90.69% [87.50, 93.62] | 99.17% [98.73, 99.56] |

## Error diagnosis

27 lexicon errors; 8 are identity returns. By artifact class: PLURAL_ONLY/I 4, MASS/I 3, MASS/- 1.

## Lexicon errors (27)

| lemma | gold | selected | source | seen | artifact |
| --- | --- | --- | --- | --- | --- |
| accent | accents | accent | lexicon | yes | PLURAL_ONLY/I |
| amenity | amenities, amnesties | amenity | lexicon | yes | MASS/I |
| aquarium | aquiriums | aquariums | lexicon | yes | COUNTABLE/- |
| atty | attys | atties | rule | no | NOT_IN_ARTIFACT/- |
| belonging | belongings | belonging | lexicon | yes | PLURAL_ONLY/I |
| breeder | bredders | breeders | rule | no | NOT_IN_ARTIFACT/- |
| burrito | borritos | burritos | rule | no | NOT_IN_ARTIFACT/- |
| cpy | cpys | cpies | rule | no | NOT_IN_ARTIFACT/- |
| dinosaur | dinasaurs | dinosaurs | rule | no | NOT_IN_ARTIFACT/- |
| gardnerus | gardneri | gardneruses | rule | no | NOT_IN_ARTIFACT/- |
| laser | lazers | lasers | lexicon | yes | MIXED/- |
| luggage | luggages | luggage | lexicon | yes | MASS/- |
| mulla | mullahs | mullas | rule | no | NOT_IN_ARTIFACT/- |
| off | offs | off | lexicon | yes | MASS/I |
| old | olds | old | lexicon | yes | PLURAL_ONLY/I |
| original | orginals | originals | rule | no | NOT_IN_ARTIFACT/- |
| osteo | osteos | osteo | lexicon | yes | MASS/I |
| parade | paraded | parades | rule | no | NOT_IN_ARTIFACT/- |
| pedal | peddles | pedals | rule | no | NOT_IN_ARTIFACT/- |
| pinscher | pinchers | pinschers | rule | no | NOT_IN_ARTIFACT/- |
| shyster | sheisters | shysters | rule | no | NOT_IN_ARTIFACT/- |
| sussex | sussexs | sussexes | rule | yes | COUNTABLE/C |
| thank | thanx | thanks | rule | no | NOT_IN_ARTIFACT/- |
| tit | titts | tits | rule | no | NOT_IN_ARTIFACT/- |
| tomato | tomatos | tomatoes | lexicon | yes | COUNTABLE/- |
| villain | villians | villains | rule | no | NOT_IN_ARTIFACT/- |
| work | works | work | lexicon | yes | PLURAL_ONLY/I |
