# english-inflector

Deterministic English noun pluralization for Java 17+, inspired by and built on the ideas of [evo-inflector](https://github.com/atteo/evo-inflector). It aims to keep evo-inflector's predictable, dependency-free runtime behaviour while extending coverage of irregular forms, compounds, and casing.

- No runtime dependencies, no network calls, no model downloads.
- Immutable per-instance configuration; thread-safe by construction.
- A 150,267-entry lexical artifact (7.7 MB TSV, 1.21 MB gzip) plus three productive rules, with explicit precedence and a documented fallback.
- A diagnostic API (`analyze`) that says *why* a form was chosen.
- Compared against evo-inflector 2.0 on the frozen lexical-coverage protocol: **100.000%** vs **96.473%** accepted-form accuracy, mostly from the added lexicon. On the leakage-free family-holdout protocol this library scores **88.714%** (see `docs/evaluation.md`).
- A neural-selector investigation was run and **not adopted** based on measured gates; the shipped artifact contains no model and no training dependency (`docs/learning.md`).

Status: `0.1.0-SNAPSHOT`. Publication coordinates are provisional: `io.github.slayyyyer.morphex:english-inflector:0.1.0-SNAPSHOT` (not published to Maven Central).

Website: https://slayyyyer.github.io/morphex/ (install, quickstart, comparison, benchmarks, design, Javadoc).

## Why

evo-inflector is a compact, well-loved library, and this project grew out of wanting a few more edge cases covered: mixed and upper-case input, hyphenated compounds, and irregulars that share a suffix with regular words. `docs/upstream-probe.md` lists the specific inputs we compared (for example `German`, `blouse`, `UFO`, `mother-in-law`, `CHILD`).

Two design choices handle most of those cases:

1. lookup normalization is kept separate from output case restoration;
2. lexical exceptions are stored as whole-word edits rather than suffix rules, so `carp` cannot affect `scarp` and `louse` cannot affect `blouse`.

Those choices trade a larger data file for fewer surprises. evo-inflector's rule-based approach is smaller and simpler, and may be the better fit if you don't need the extra coverage.

## Install

From this repository:

```
./mvnw install          # JDK 17+; the wrapper pins Maven 3.9.9
```

Consumer dependency (after `install`):

```xml
<dependency>
  <groupId>io.github.slayyyyer.morphex</groupId>
  <artifactId>inflector-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Rules-only build (no bundled Wiktionary data, no CC BY-SA obligation):

```java
EnglishInflector rulesOnly = EnglishInflector.builder().withoutBundledLexicon().build();
```

That instance uses only the three productive rules and caller overrides. It loses irregular forms by design.

## API

```java
EnglishInflector inflector = EnglishInflector.standard();

inflector.pluralize("child");          // "children"
inflector.pluralize("CHILD");          // "CHILDREN"
inflector.pluralize("UFO");            // "UFOs"
inflector.pluralize("mother-in-law");  // "mothers-in-law"
inflector.pluralize("cat", 1);         // "cat"  (the source instance is preserved)
inflector.pluralize("cat", 3);         // "cats"

Inflection result = inflector.analyze("die");
result.plural();          // "dice"
result.alternatives();    // ["dies"]
result.ambiguous();       // true
result.source();           // LEXICON
result.id();               // "lex:die"
result.countability();     // COUNTABLE
```

Configuration is immutable and per instance:

```java
EnglishInflector custom = EnglishInflector.builder()
        .mode(Mode.CLASSICAL)            // indices, cacti, formulae where attested
        .casePolicy(CasePolicy.PRESERVE) // default; LOWERCASE / UPPERCASE also available
        .override("information", "informations")
        .initialism("XYZ")
        .build();
```

`builder()` also accepts `overrides(Map)` and `initialisms(Collection)`. `standard()` is a shared instance; construct your own only when you need different policies.

### Contract

| input                                                                           | behaviour                                               |
|---------------------------------------------------------------------------------|---------------------------------------------------------|
| `""`                                                                            | returns `""`                                            |
| `null`                                                                          | `NullPointerException` with a message                   |
| unsupported shape (identifier, digits, possessive, non-Latin, unmatched hyphen) | returned unchanged, `analyze(...).source() == FALLBACK` |
| `count == 1`                                                                    | the exact source instance, unmodified                   |
| any other `count`                                                               | plural behaviour                                        |
| already-plural / mass / invariant                                               | documented policy; see below                            |

No trimming, case folding or rewriting of the caller's input happens beyond the selected output.

## Policies

### Precedence

1. caller override (exact, case-sensitive spelling)
2. exact lexical entry (case-insensitive lookup, with case-sensitive entries where needed)
3. supported compound
4. productive spelling rule
5. documented fallback

### Productive rules

Only three, and they are the only suffix rules in the engine:

| id                     | condition                                       | change      |
|------------------------|-------------------------------------------------|-------------|
| `rule:sibilant-es`     | ends `s`, `x`, `z`, `ch`, `sh`                  | append `es` |
| `rule:consonant-y-ies` | consonant before final `y` (including `qu + y`) | `y -> ies`  |
| `rule:default-s`       | otherwise                                       | append `s`  |

Everything else irregular is lexical. Adding a word to the lexicon cannot change an unrelated word that shares its suffix.

### Casing

`CasePolicy.PRESERVE` (default) restores the input's shape: `CHILD -> CHILDREN`, `Child -> Children`, `iPhone -> iPhones`. `LOWERCASE` and `UPPERCASE` force the output shape.

Initialisms are explicit metadata, never an uppercase guess. A lexical entry is flagged when its attested plural is exactly `lemma + s`/`lemma + es`; it then appends that suffix without whole-token re-casing (`UFO -> UFOs`, `SMS -> SMSes`, `ABC -> ABCs`). Declare your own with `builder().initialism("XYZ")`.

### Countability

Kept separate from singular→plural accuracy:

| status        | returned                                   | example           |
|---------------|--------------------------------------------|-------------------|
| `MASS`        | the lemma unchanged                        | `information`     |
| `PLURAL_ONLY` | the lemma unchanged                        | `scissors`        |
| invariant     | the lemma unchanged, `invariant() == true` | `sheep`           |
| `MIXED`       | the countable plural                       | `water -> waters` |
| `COUNTABLE`   | the plural                                 | `cat -> cats`     |

`analyze` reports the status and a note. Alternatives are preserved: `person` prefers `people` and accepts `persons`; `die` prefers `dice` and reports `dies` as an alternative with `ambiguous() == true`.

### Compounds

Bounded and tested, not suffix-guessed:

- exact entries: `passer-by`, `stand-in`, `drive-by`, `hanger-on`, `runner-up`, `close-up`, `go-between`, `jack-in-the-box`, `man-of-war`, `major general`;
- recognized free tails: `-in-law`, `-in-chief`, `-of-war`, `-at-arms`, `-at-law`, `-of-arms`;
- postposed modifiers only after curated heads: `attorney general`, `court-martial`, `heir apparent`, `governor-elect`;
- space-separated compounds inflect the final token, unless the input contains a preposition (`jack in the box` is returned unchanged rather than guessed).

The head inherits irregular forms: `child-in-law -> children-in-law`, `man-of-war -> men-of-war`.

### Modes

`Mode.ANGLICIZED` (default) prefers `indexes`, `cactuses`, `formulas`; `Mode.CLASSICAL` prefers the attested classical form (`indices`, `cacti`, `formulae`). Classicism is never invented by a rule, so an unseen `blouse` can never become `blice`.

## What is not supported

Singularization, verb conjugation, sentence parsing, arbitrary multilingual morphology, online learning, and general multi-word rewriting. Unsupported input is returned unchanged and identified by `analyze`, not silently fixed.

## Data

The lexical artifact is generated offline from a pinned Kaikki/Wiktionary dump (SHA-256 `9a47500c…8ab43`, 298,543,101 bytes) plus a small hand-authored curated table.

- **Code**: MIT (`LICENSE`).
- **Data**: CC BY-SA 3.0 (Wiktionary contributors, via Kaikki.org and Wiktextract); see `NOTICE` and `data/LICENSE-DATA.md`.
- The data is a separate, redistributable file; the rules-only build avoids it entirely.

## Evidence

| document                 | contents                                                                    |
|--------------------------|-----------------------------------------------------------------------------|
| `docs/evaluation.md`     | protocols P1/P2, fixed labels/denominators, actual results, residual errors |
| `docs/upstream-probe.md` | evo-inflector 2.0 outputs reproduced on JDK 21                              |
| `docs/benchmarks.md`     | JMH methodology, raw numbers, honest interpretation                         |
| `docs/learning.md`       | candidate-oracle ceiling, learners, routing, seeds, decision                |
| `docs/compatibility.md`  | differences from evo-inflector, intentional changes, migration recipe       |
| `docs/python-baseline.md`  | PyPI `inflect` 7.6.0 scored on the same frozen P1 protocol                   |
| `docs/design.md`         | engine architecture, precedence, lexicon layout, casing, compounds           |
| `docs/comparison.md`     | head-to-head vs evo-inflector 2.0, with qualifications                      |

Headline numbers (JDK 21, recorded runs):

- P1 frozen lexicon: **100.000%** accepted-form accuracy (0/36,239 errors) vs evo-inflector 2.0 at **96.473%**; paired +4.16 pp, 95% CI `[+3.96, +4.37]`. Most of the gain comes from the bundled lexicon, so this measures dictionary coverage more than a fundamental difference in approach.
- P2 family holdout: **88.714%** for both rules and train-only lexicon (no leakage possible), which is the honest unseen-family number.
- External check (UD English EWT, CC BY-SA 4.0; 2,184 lemmas, no retraining): **98.76%** overall, **96.81%** on lemmas present in the shipped lexicon, **99.17%** on unseen lemmas — ahead of evo-inflector 2.0's 98.12% overall. The earlier countability defect (`book`/`bag`/`account` returned unchanged) is fixed; remaining errors are mostly EWT annotation noise plus disputed subordinate uses — see `docs/evaluation.md` and `results/external/ewt-report.md`.
- JMH: 82 ns/op repeated (vs 98 ns evo-inflector), 201 ns/op distinct-word (vs 151 ns; 1.34× slower), 29-51 B/op allocated. The packed lexicon retains 24.1 MB after load. Concurrent throughput was not captured in the final run.

## Reproduce

```
# build + all unit/regression tests (offline, no corpus)
./mvnw verify

# the consumer example
./mvnw -q -pl inflector-example exec:java

# offline data pipeline (requires data/raw dump; see docs/evaluation.md)
py -3 -m tools.research.cli extract
py -3 -m tools.research.cli splits
py -3 -m tools.research.cli artifact --split all --selection compact

# evaluation
./mvnw -q -pl inflector-eval -am install -DskipTests
./mvnw -q -pl inflector-eval exec:java \
  -Dexec.args="--input results/eval/test-records.tsv --out results/eval/p1-test-preds.tsv"
py -3 -m tools.research.cli evaluate \
  --gold results/eval/test-gold.tsv --predictions results/eval/p1-test-preds.tsv \
  --out results/eval/p1-report.json --split test --lexicon-split all

# benchmarks
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 -prof gc \
     -rf json -rff results/benchmarks/jmh.json
```

The normal test run uses only `data/regression/nouns-regression.tsv` and the shipped artifact; it never downloads a corpus. `data/raw/` is gitignored and its absence makes the corpus evaluation unavailable, never silently passing.

## Layout

```
inflector-core        published library (no runtime dependencies)
inflector-example     runnable consumer example
inflector-eval        baselines (evo-inflector 2.0, rules, lexicon) and upstream probe
inflector-benchmarks  JMH benchmarks; evo-inflector is benchmark-scope only
tools/research        stdlib-only Python pipeline, evaluator and learning experiment
data/                 curated table, regression suite, derived manifests
results/              recorded evaluation, benchmark and learning outputs
docs/                 evaluation, benchmarks, learning, compatibility, provenance
```

## Acknowledgements

Thanks to the evo-inflector authors. Their work and test cases shaped this project.
