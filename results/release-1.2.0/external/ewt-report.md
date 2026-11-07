# External validation: UD English EWT (lexical families)

Source: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0. Independent of the Kaikki/Wiktionary snapshot.
Filtered pairs: 2184 lemmas, 2221 attested forms.
Full-vocabulary membership: 2148 seen / 36 unseen. Compact-artifact membership: 363 hit / 1821 miss. These labels are deliberately separate: a regular word omitted by compaction is still a seen vocabulary lemma.
Family membership uses the full vocabulary (2150 family-seen / 34 family-unseen); the compact artifact's family labels are reported only as an artifact diagnostic.
Selected-output accuracy: the selected plural matches an attested form. Alternative-set coverage is reported separately and is not compared against another system's selected output.
The artifact was not retrained or tuned on this corpus.

| system | selected overall | vocab seen | vocab unseen | family seen | family unseen | artifact hit | artifact miss | alternative coverage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | 98.12% [97.48, 98.67] | 98.23% [97.67, 98.74] | 91.67% [80.56, 100.00] | 98.23% [97.67, 98.74] | 91.18% [82.35, 100.00] | 94.21% [91.74, 96.42] | 98.90% [98.41, 99.34] | 0.00% [0.00, 0.00] |
| lexicon | 98.76% [98.26, 99.22] | 98.88% [98.42, 99.30] | 91.67% [80.56, 100.00] | 98.88% [98.42, 99.30] | 91.18% [82.35, 100.00] | 96.69% [94.77, 98.35] | 99.18% [98.74, 99.56] | 98.99% [98.53, 99.40] |
| rules | 98.26% [97.66, 98.76] | 98.37% [97.81, 98.88] | 91.67% [80.56, 100.00] | 98.37% [97.81, 98.88] | 91.18% [82.35, 100.00] | 93.66% [91.18, 96.14] | 99.18% [98.74, 99.56] | 98.26% [97.66, 98.76] |

## Error diagnosis

27 lexicon errors; 4 are identity returns; 24 are full-vocabulary-seen and 12 are compact-artifact hits. By artifact class: MASS/I 2, COUNTABLE/I 1, MASS/- 1.

## Lexicon errors (27)

| lemma | gold | selected | source | vocab seen | family seen | artifact | artifact hit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| alumnus | alumni | alumnuses | lexicon | yes | yes | COUNTABLE/- | yes |
| antipasto | antipasti | antipastos | lexicon | yes | yes | COUNTABLE/- | yes |
| aquarium | aquiriums | aquariums | lexicon | yes | yes | COUNTABLE/- | yes |
| atty | attys | atties | rule | no | no | NOT_IN_ARTIFACT/- | no |
| breeder | bredders | breeders | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| burrito | borritos | burritos | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| cpy | cpys | cpies | rule | no | no | NOT_IN_ARTIFACT/- | no |
| dinosaur | dinasaurs | dinosaurs | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| fish | fishes | fish | lexicon | yes | yes | COUNTABLE/I | yes |
| gardnerus | gardneri | gardneruses | rule | no | no | NOT_IN_ARTIFACT/- | no |
| index | indices | indexes | lexicon | yes | yes | COUNTABLE/- | yes |
| laser | lazers | lasers | lexicon | yes | yes | MIXED/- | yes |
| luggage | luggages | luggage | lexicon | yes | yes | MASS/- | yes |
| mulla | mullahs | mullas | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| off | offs | off | lexicon | yes | yes | MASS/I | yes |
| original | orginals | originals | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| osteo | osteos | osteo | lexicon | yes | yes | MASS/I | yes |
| parade | paraded | parades | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| pedal | peddles | pedals | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| person | persons | people | lexicon | yes | yes | COUNTABLE/- | yes |
| pinscher | pinchers | pinschers | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| shyster | sheisters | shysters | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| sussex | sussexs | sussexes | rule | yes | yes | COUNTABLE/C | yes |
| thank | thanx | thanks | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| tit | titts | tits | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
| tomato | tomatos | tomatoes | lexicon | yes | yes | COUNTABLE/- | yes |
| villain | villians | villains | rule | yes | yes | NOT_IN_ARTIFACT/- | no |
