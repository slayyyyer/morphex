# english-inflector

Deterministic English noun pluralization for Java 17+, inspired by and built on the ideas of [evo-inflector](https://github.com/atteo/evo-inflector). It aims to keep evo-inflector's predictable, dependency-free runtime behaviour while extending coverage of irregular forms, compounds, and casing.

- No runtime dependencies, no network calls, no model downloads.
- Immutable per-instance configuration; thread-safe by construction.
- A 147,193-entry lexical artifact (7.5 MB TSV, 1.18 MB gzip) plus three productive rules, documented backoffs, and a bounded compound resolver.
- A diagnostic API (`analyze`) that says *why* a form was chosen.
- Compared against evo-inflector 2.0 on the frozen lexical-coverage protocol: **100.000%** vs **96.473%** accepted-form accuracy. On the leakage-free family-holdout protocol this library scores **97.376%** vs **96.473%** (see `docs/evaluation.md`).
- A corrected out-of-fold selector investigation was run. Its dependency-free Java mirror reproduces the result exactly, but the selector misses the offline gain gate by 0.0005 pp and fails the strict-default p99/init gates; it is **not shipped** (`docs/learning.md`).

Status: `1.1.0` on Maven Central: `io.github.slayyyyer.morphex:english-inflector-core:1.1.0`.

Website: https://slayyyyer.github.io/morphex/ (install, quickstart, comparison, benchmarks, design, Javadoc).

## What's new in 1.1.0

This is a backwards-compatible minor release. It keeps the 1.0 API and zero-runtime-dependency constraints while tightening the runtime and evaluation story:

- a compact packed membership index, improved compound handling, and explicit uppercase/initialism policies;
- a supplementary strict derivational-family evaluation (`split-v2`); the headline `split-v1` protocol is unchanged;
- a dependency-free Java evaluator for the linear selector, which remains experimental and is **not** shipped;
- refreshed benchmarks, external validation, and reproducible reports.

## Why

evo-inflector is a compact, well-loved library, and this project grew out of wanting a few more edge cases covered: mixed and upper-case input, hyphenated compounds, and irregulars that share a suffix with regular words. `docs/upstream-probe.md` lists the specific inputs we compared (for example `German`, `blouse`, `UFO`, `mother-in-law`, `CHILD`).

Two design choices handle most of those cases:

1. lookup normalization is kept separate from output case restoration;
2. lexical exceptions are stored as whole-word edits rather than suffix rules, so `carp` cannot affect `scarp` and `louse` cannot affect `blouse`.

Those choices trade a larger data file for fewer surprises. evo-inflector's rule-based approach is smaller and simpler, and may be the better fit if you don't need the extra coverage.

## Install

From Maven Central:

```xml
<dependency>
  <groupId>io.github.slayyyyer.morphex</groupId>
  <artifactId>english-inflector-core</artifactId>
  <version>1.1.0</version>
</dependency>
```

From this repository:

```
./mvnw install          # JDK 17+; the wrapper pins Maven 3.9.9
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

Three productive rules, plus documented backoffs for known weak suffix families:

| id                     | condition                                       | change      |
|------------------------|-------------------------------------------------|-------------|
| `rule:sibilant-es`     | ends `s`, `x`, `z`, `ch`, `sh`                  | append `es` |
| `rule:consonant-y-ies` | consonant before final `y` (including `qu + y`) | `y -> ies`  |
| `rule:default-s`       | otherwise                                       | append `s`  |

Everything else irregular is lexical. Adding a word to the lexicon cannot change an unrelated word that shares its suffix.

| backoff | condition | change |
| --- | --- | --- |
| `backoff:woman->women` | ends `woman` | `woman -> women` |
| `backoff:wife->wives` | ends `wife` | `wife -> wives` |
| `backoff:man->men` | lower-case noun+`man`, length >= 6, not in the author-reviewed exception set | `man -> men` |
| `backoff:greek-sis` | ends `sis`, length >= 5 | `sis -> ses` |

### Casing

`CasePolicy.PRESERVE` (default) restores the input's shape: `CHILD -> CHILDREN`, `Child -> Children`, `iPhone -> iPhones`. `LOWERCASE` and `UPPERCASE` force the output shape.

Explicit metadata and caller configuration always win. A lexical entry is flagged when its attested plural is exactly `lemma + s`/`lemma + es` and then appends that suffix without whole-token re-casing (`UFO -> UFOs`, `SMS -> SMSes`). For an unknown 2-6 letter upper-case Latin token that is not in the membership table, the default `UppercasePolicy.INFER_INITIALISM` infers an initialism and appends lower-case `s` (`API -> APIs`). `builder().uppercasePolicy(UppercasePolicy.LITERAL)` restores literal rendering (`API -> APIS`); ordinary uppercase nouns and overrides are unaffected. Declare explicit initialisms with `builder().initialism("XYZ")`.

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
- recognized free tails: `-in-law`, `-in-arms`, `-in-aid`, `-in-kind`, `-in-chief`, `-of-war`, `-of-arms`, `-of-spring`, `-of-all-trades`, `-out-law`, `-at-arms`, `-at-law`, `-in-crime`;
- postposed modifiers only after curated heads: `attorney general`, `court-martial`, `heir apparent`, `governor-elect`;
- guarded head-final nouns, including multi-hyphen chains and productive head respellings: `credit-card -> credit-cards`, `great-great-grandfather -> great-great-grandfathers`, `ice-shelf -> ice-shelves`, `on-ramp -> on-ramps`;
- verb-particle nouns: `bake-off -> bake-offs`, `blow-in -> blow-ins`, `warm-up -> warm-ups`;
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

- P1 frozen lexicon: **100.000%** accepted-form accuracy (0/36,239 errors) vs evo-inflector 2.0 at **96.473%**; paired +3.5266 pp, 95% CI `[+3.3323, +3.7159]`. Most of the gain comes from the bundled lexicon, so this measures dictionary coverage more than a fundamental difference in approach.
- P2 family holdout: **97.376%** (951/36,239 errors) vs evo-inflector 2.0 at **96.473%**; paired gain +0.9023 pp, 95% CI `[+0.7309, +1.0718]`, 540 corrections and 213 regressions.
- Strict-family sensitivity check (`split-v2`, supplementary): P2 test **97.238%** (1,036/37,507) vs **96.379%** (1,358/37,507); external family-unseen **98.82%** (501/507) vs **98.22%** (498/507). The stemmer is approximate and the changed denominators make this non-comparable to the default `split-v1` row above.
- External check (UD English EWT, CC BY-SA 4.0; 2,184 lemmas, no retraining): **98.21% selected-output accuracy** overall, **93.48%** on lemmas present in the shipped lexicon, **99.17%** on unseen lemmas, with **98.76% alternative-set coverage**; evo-inflector 2.0 selects 98.12% overall. Remaining errors are mostly EWT annotation noise plus disputed subordinate uses — see `docs/evaluation.md` and `results/external-v2/ewt-report.md`.
- JMH fresh short run: 77.8 ns/op repeated (vs 81.5 evo-inflector), 226.0 ns/op distinct-word (vs 163.6; 1.38× slower); the older full run's allocation and retained-heap figures remain historical. Concurrent throughput was not captured.

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
