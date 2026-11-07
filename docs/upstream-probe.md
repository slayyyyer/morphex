# Stock evo-inflector 2.0 behaviour probe

Reproduced in this repository, not copied from a review.

* Artifact: `org.atteo:evo-inflector:2.0` resolved from Maven Central to
  `~/.m2/repository/org/atteo/evo-inflector/2.0/evo-inflector-2.0.jar`
* JDK: Eclipse Temurin 21.0.13 (21.0.13+9-LTS)
* Source: `inflector-eval/src/main/java/io/github/morphex/inflector/eval/UpstreamProbe.java`
* Command:
  `./mvnw -q -pl inflector-eval -am install -DskipTests && ./mvnw -q -pl inflector-eval exec:java -Dexec.mainClass=io.github.morphex.inflector.eval.UpstreamProbe`
* Raw output: `results/upstream-probe.tsv` (captured from the run below)

## Results

| input | `plural(word)` | `plural(word, 2)` | `plural(word, 1)` |
| --- | --- | --- | --- |
| German | Germen | Germen | German |
| blouse | blice | blice | blouse |
| scarp | scarp | scarp | scarp |
| UFO | UFOes | UFOes | UFO |
| mother-in-law | mother-in-laws | mother-in-laws | mother-in-law |
| passer-by | passer-bies | passer-bies | passer-by |
| child | children | children | child |
| CHILD | CHILDren | CHILDren | CHILD |
| city | cities | cities | city |
| CITY | CITIes | CITIes | CITY |
| mouse | mice | mice | mouse |
| MOUSE | MICE | MICE | MOUSE |
| SMS | SMSes | SMSes | SMS |
| scissors | scissors | scissors | scissors |
| information | informations | informations | information |
| analysis | analyses | analyses | analysis |
| quiz | quizzes | quizzes | quiz |
| die | dies | dies | die |
| person | persons | persons | person |
| sheep | sheep | sheep | sheep |
| cactus | cactuses | cactuses | cactus |
| stand-in | stand-ins | stand-ins | stand-in |
| drive-by | drive-bies | drive-bies | drive-by |
| attorney general | attorney generals | attorney generals | attorney general |
| ice cream | ice creams | ice creams | ice cream |
| café | cafés | cafés | café |

Every finding supplied in the brief is reproduced exactly, including the two mechanisms:

1. **Lookup/insertion casing mismatch.** `plural("German")` yields `Germen`, while the
   exception table only reaches the lower-cased key; `CHILD -> CHILDren` and
   `CITY -> CITIes` show that whole-token re-casing is applied as a suffix edit rather than
   as a case restoration policy.
2. **Lexical exceptions conflated with suffix rules.** `plurals("carp") = carp` makes
   `scarp` invariant, and the `-ouse -> -ice` behaviour reaches `blouse`.

Additional observations recorded here for scope decisions:

* `drive-by -> drive-bies` and `attorney general -> attorney generals` are also wrong, showing
  that the engine inflects the wrong constituent of compounds rather than refusing to guess.
* `person -> persons` is not wrong (`persons` is a legitimate alternative) but shows that
  preference is not modelled; this project keeps `people` preferred and `persons` accepted.
* `die -> dies` is defensible for the stamping-tool sense; spelling alone cannot choose
  between `dice` and `dies`, so this project marks the entry ambiguous.
* `plural(word, 1)` returns the input, matching the required count contract.

## Status of prior claims

* Reproduced here: the six table rows, `CHILD/CITY/MOUSE`, `SMS -> SMSes`,
  `scarp`/`blouse` mechanisms, `information -> informations`, `quiz -> quizzes`.
* Not independently measured: upstream throughput claims. This repository measures its own
  performance against stock 2.0 on identical JMH workloads (see `docs/benchmarks.md`).
* Not claimed: byte-for-byte or behavioural drop-in compatibility. See
  `docs/compatibility.md` for the intentional differences.
