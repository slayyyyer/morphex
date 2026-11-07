# External validation: UD English EWT (strict families)

Source: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0. Independent of the Kaikki/Wiktionary snapshot.
Filtered pairs: 2184 lemmas, 2221 attested forms; 368 lemmas are present in the shipped lexicon, 1816 are unseen.
Strict-family check: 1677 lemmas share a family with an artifact lemma; 507 are family-unseen.
Selected-output accuracy: the selected plural matches an attested form. Alternative-set coverage is reported separately and is not compared against another system's selected output.
The artifact was not retrained or tuned on this corpus.

| system | selected overall | exact seen | exact unseen | family seen | family unseen | alternative coverage |
| --- | --- | --- | --- | --- | --- | --- |
| evo2 | 98.12% [97.53, 98.67] | 94.29% [91.80, 96.49] | 98.90% [98.39, 99.34] | 98.09% [97.42, 98.73] | 98.22% [97.02, 99.22] | 0.00% [0.00, 0.00] |
| lexicon | 98.21% [97.64, 98.76] | 93.48% [90.96, 95.91] | 99.17% [98.73, 99.56] | 98.03% [97.35, 98.64] | 98.82% [97.82, 99.61] | 98.76% [98.27, 99.18] |
| rules | 98.26% [97.69, 98.80] | 93.75% [91.26, 96.19] | 99.17% [98.73, 99.56] | 98.09% [97.43, 98.73] | 98.82% [97.82, 99.61] | 98.26% [97.69, 98.80] |

## Error diagnosis

39 lexicon errors; 16 are identity returns. By artifact class: MIXED/- 6, PLURAL_ONLY/I 4, MASS/I 3, COUNTABLE/I 1, MASS/- 1, COUNTABLE/- 1.

## Lexicon errors (39)

| lemma | gold | selected | source | seen | artifact |
| --- | --- | --- | --- | --- | --- |
| accent | accents | accent | lexicon | yes | PLURAL_ONLY/I |
| alumnus | alumni | alumnuses | lexicon | yes | COUNTABLE/- |
| amenity | amenities, amnesties | amenity | lexicon | yes | MASS/I |
| antipasto | antipasti | antipastos | lexicon | yes | COUNTABLE/- |
| aquarium | aquiriums | aquariums | lexicon | yes | COUNTABLE/- |
| atty | attys | atties | rule | no | NOT_IN_ARTIFACT/- |
| belonging | belongings | belonging | lexicon | yes | PLURAL_ONLY/I |
| breeder | bredders | breeders | rule | no | NOT_IN_ARTIFACT/- |
| burrito | borritos | burritos | rule | no | NOT_IN_ARTIFACT/- |
| cpy | cpys | cpies | rule | no | NOT_IN_ARTIFACT/- |
| craft | crafts | craft | lexicon | yes | MIXED/- |
| dinosaur | dinasaurs | dinosaurs | rule | no | NOT_IN_ARTIFACT/- |
| fish | fishes | fish | lexicon | yes | COUNTABLE/I |
| gardnerus | gardneri | gardneruses | rule | no | NOT_IN_ARTIFACT/- |
| index | indices | indexes | lexicon | yes | COUNTABLE/- |
| laser | lazers | lasers | lexicon | yes | MIXED/- |
| luggage | luggages | luggage | lexicon | yes | MASS/- |
| mulla | mullahs | mullas | rule | no | NOT_IN_ARTIFACT/- |
| off | offs | off | lexicon | yes | MASS/I |
| old | olds | old | lexicon | yes | PLURAL_ONLY/I |
| original | orginals | originals | rule | no | NOT_IN_ARTIFACT/- |
| osteo | osteos | osteo | lexicon | yes | MASS/I |
| oz | ozs | oz | lexicon | yes | MIXED/- |
| parade | paraded | parades | rule | no | NOT_IN_ARTIFACT/- |
| pedal | peddles | pedals | rule | no | NOT_IN_ARTIFACT/- |
| person | persons | people | lexicon | yes | COUNTABLE/- |
| pinscher | pinchers | pinschers | rule | no | NOT_IN_ARTIFACT/- |
| plate | plates | plate | lexicon | yes | MIXED/- |
| prawn | prawns | prawn | lexicon | yes | MIXED/- |
| robot | robots | robot | lexicon | yes | MIXED/- |
| shyster | sheisters | shysters | rule | no | NOT_IN_ARTIFACT/- |
| sussex | sussexs | sussexes | rule | yes | COUNTABLE/C |
| thank | thanx | thanks | rule | no | NOT_IN_ARTIFACT/- |
| tit | titts | tits | rule | no | NOT_IN_ARTIFACT/- |
| tomato | tomatos | tomatoes | lexicon | yes | COUNTABLE/- |
| villain | villians | villains | rule | no | NOT_IN_ARTIFACT/- |
| way | ways | way | lexicon | yes | MIXED/- |
| wonton | wantons, wontons | wonton | lexicon | yes | COUNTABLE/- |
| work | works | work | lexicon | yes | PLURAL_ONLY/I |
