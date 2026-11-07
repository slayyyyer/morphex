# Compatibility and migration

This project is **not** a drop-in replacement for evo-inflector. It fixes several of its
outputs, intentionally changes others, and does not ship the `org.atteo.evo.inflector`
package, so it cannot be swapped in without changing imports.

Stock `org.atteo:evo-inflector:2.0` is used only in non-published test/benchmark modules.
No upstream classes are on a consumer classpath, so there is no duplicate-class conflict.

## What is different at the API level

| evo-inflector 2.0 | english-inflector |
| --- | --- |
| static `English.plural(String)` | `EnglishInflector.standard().pluralize(String)` |
| static `English.plural(String, int)` | `pluralize(String, int)` |
| no configuration object | immutable `EnglishInflector` built once, safe to share |
| no diagnostic result | `analyze(String)` returns immutable `Inflection` |
| global static state | per-instance overrides, lexicon and mode |
| one built-in table | anglicized/classical modes, explicit case policy, initialism metadata |

The count contract is preserved exactly: `pluralize(noun, 1)` returns the source instance
unchanged, and any other integer selects the plural.

## Behaviour that is a correctness repair

These are defects in stock 2.0, reproduced in `docs/upstream-probe.md` and fixed here:

| input | stock 2.0 | this library | why |
| --- | --- | --- | --- |
| `German` | `Germen` | `Germans` | exception lookup is case-insensitive; output case is restored, not framework-inferred |
| `blouse` | `blice` | `blouses` | `louse -> lice` is lexical, not a `-ouse` rule |
| `scarp` | `scarp` | `scarps` | `carp` is lexical, not a suffix rule |
| `UFO` | `UFOes` | `UFOs` | initialisms append `s` (or `es` only after a final sibilant S) |
| `mother-in-law` | `mother-in-laws` | `mothers-in-law` | the head is `mother`, not the final token |
| `passer-by` | `passer-bies` | `passers-by` | `-by` is not a productive head transform |
| `CHILD` | `CHILDren` | `CHILDREN` | whole-token case policy |
| `CITY` | `CITIes` | `CITIES` | whole-token case policy |
| `drive-by` | `drive-bies` | `drive-bys` | counterexample to suffix-based compound handling |
| `attorney general` | `attorney generals` | `attorneys general` | postposed modifier |

## Behaviour that is an intentional change

These are not bugs in stock 2.0; they are scope or policy choices. Each is covered by the
regression suite.

| input | stock 2.0 | this library | reason |
| --- | --- | --- | --- |
| `information` | `informations` | `information` | mass-noun policy returns the lemma; `analyze` reports `MASS` |
| `scissors` | `scissors` | `scissors` | plural-only lemma returned unchanged, reported as `PLURAL_ONLY` |
| `person` | `persons` | `people` | preferred form is `people`; `persons` remains an accepted alternative |
| `die` | `dies` | `dice` | selected for the game-piece sense and flagged `ambiguous`; `dies` is an accepted alternative |
| `cactus` (anglicized) | `cactuses` | `cactuses` | same, but `classical` mode selects `cacti` |
| `SMS` | `SMSes` | `SMSes` | preserved deliberately: final `S` takes `es`; `UFO`/`SMS` are explicit metadata, not uppercase guessing |
| `analysis` | `analyses` | `analyses` | same result; now lexical rather than a global `-is` rule |
| `sheep` | `sheep` | `sheep` | same, now reported as `INVARIANT` |
| unmatched hyphenated token (`well-known`) | `well-knowns` | `well-known` | fallback: a hyphen is not proof of a compound |
| `jack in the box` (spaces) | `jack in the box` | `jack in the box` | fallback: prepositional multi-word input is not guessed; use `jack-in-the-box` |
| `user_id`, `3.14`, `mother's` | variously rewritten | unchanged `FALLBACK` | identifiers, numbers and possessives are out of scope and reported by `analyze` |

## Casing policy

`CasePolicy.PRESERVE` (default) restores the input's case shape onto the selected form:

* all-upper input -> all-upper output (`CHILD -> CHILDREN`, `CITY -> CITIES`)
* capitalized input -> capitalized output (`Child -> Children`)
* mixed-case input -> internal capitals preserved (`iPhone -> iPhones`)
* `LOWERCASE` / `UPPERCASE` force the output shape for callers that need it

Initialisms are explicit data, not an uppercase heuristic. A lexical entry carries an
`initialism` flag when its attested plural is exactly `lemma + s` or `lemma + es`; an
initialism appends its attested suffix in the requested case (so `SMS -> SMSes`,
`UFO -> UFOs`). Callers can declare additional initialisms with `builder().initialism("XYZ")`.

## Migration recipe

```java
// before
String plural = org.atteo.evo.inflector.English.plural(word);

// after
private static final EnglishInflector INFLECTOR = EnglishInflector.standard();
String plural = INFLECTOR.pluralize(word);
```

Build the inflector once (construction loads the lexical artifact). If startup latency
matters, construct it eagerly during application start-up rather than per call, or use the
rules-only builder (see README) to skip loading the artifact.

If you relied on `information -> informations` or on arbitrary hyphenated rewriting, add
caller overrides, which take precedence over everything else:

```java
EnglishInflector custom = EnglishInflector.builder()
        .override("information", "informations")
        .build();
```

## Not supported

Singularization, verb conjugation, sentence parsing, arbitrary multilingual morphology and
online learning are out of scope. Multi-word inputs are unchanged unless they match a curated
compound pattern; use a hyphenated form or an override.
