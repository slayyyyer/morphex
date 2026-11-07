# External validation: UD English EWT (strict families)

Source: [UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), CC BY-SA 4.0. Independent of the Kaikki/Wiktionary snapshot.
Filtered pairs: 2184 lemmas, 2221 attested forms.
Full-vocabulary membership: 2148 seen / 36 unseen. Compact-artifact membership: 363 hit / 1821 miss. These labels are deliberately separate: a regular word omitted by compaction is still a seen vocabulary lemma.
Family membership uses the full vocabulary (2163 family-seen / 21 family-unseen); the compact artifact's family labels are reported only as an artifact diagnostic.
Selected-output accuracy: the selected plural matches an attested form. Alternative-set coverage is reported separately and is not compared against another system's selected output.
The artifact was not retrained or tuned on this corpus.

| system | selected overall | vocab seen | vocab unseen | family seen | family unseen | artifact hit | artifact miss | alternative coverage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evo2 | 98.12% [97.51, 98.67] | 98.23% [97.65, 98.78] | 91.67% [80.56, 100.00] | 98.24% [97.72, 98.79] | 85.71% [71.43, 100.00] | 94.21% [91.71, 96.44] | 98.90% [98.36, 99.34] | 0.00% [0.00, 0.00] |
| lexicon | 98.76% [98.26, 99.21] | 98.88% [98.43, 99.30] | 91.67% [80.56, 100.00] | 98.89% [98.43, 99.31] | 85.71% [71.43, 100.00] | 96.69% [94.77, 98.36] | 99.18% [98.70, 99.56] | 98.99% [98.53, 99.36] |
| rules | 98.26% [97.69, 98.77] | 98.37% [97.82, 98.89] | 91.67% [80.56, 100.00] | 98.38% [97.83, 98.89] | 85.71% [71.43, 100.00] | 93.66% [91.16, 95.91] | 99.18% [98.70, 99.56] | 98.26% [97.69, 98.77] |

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
