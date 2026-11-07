# Design

How `inflector-core` is put together, why it looks that way, and what was deliberately
left out. Code references are to `inflector-core/src/main/java/io/github/slayyyyer/morphex/inflector/`.

## Goals and constraints

* Deterministic English noun pluralization for Java 17+, no runtime dependencies.
* More correct than stock evo-inflector 2.0 on the cases in `docs/upstream-probe.md`
  without becoming a suffix-guessing engine.
* Predictable behaviour: immutable per-instance configuration, thread-safe by construction,
  no global mutable state, no network, no model, no trimming or rewriting of caller input.
* Lexical knowledge is data (a versioned TSV artifact), not code. Productive morphology is
  exactly three suffix rules; everything else irregular is lexical.

## Public surface

| type | role |
| --- | --- |
| `EnglishInflector` | immutable engine; `standard()`, `builder()`, `pluralize(String)`, `pluralize(String,int)`, `analyze(String)`, `lexiconSize()` |
| `Builder` | `mode()`, `casePolicy()`, `override()` / `overrides()`, `initialism()` / `initialisms()`, `withoutBundledLexicon()`, `build()` |
| `Mode` | `ANGLICIZED` (default) vs `CLASSICAL` preference for attested classical plurals only |
| `CasePolicy` | `PRESERVE` (default), `LOWERCASE`, `UPPERCASE` |
| `Inflection` | immutable diagnostic: `plural()`, `alternatives()`, `source()`, `id()`, `countability()`, `invariant()`, `ambiguous()`, `note()`; no numeric confidence |
| `DecisionSource` | `OVERRIDE`, `LEXICON`, `COMPOUND`, `RULE`, `FALLBACK` |
| `Countability` | `COUNTABLE`, `MASS`, `PLURAL_ONLY`, `MIXED`, `INVARIANT`-adjacent flag, `UNKNOWN` |

The count contract is exact: `pluralize(noun, 1)` returns the source instance unmodified;
any other `count` selects the plural. `null` throws `NullPointerException`; `""` returns `""`
with `FALLBACK`. Unsupported shapes (identifiers, digits, possessives, non-Latin,
unmatched hyphens) are returned unchanged with `FALLBACK` and identified by `analyze`.

## Resolution pipeline (`EnglishInflector.resolve`)

Fixed precedence, in order:

1. **Caller override** — exact, case-sensitive whole-input match (`Map.copyOf` snapshot).
2. **Exact lexical entry** — `Lexicon.find(WordShape.lower(noun))`, plus the `C`
   (case-sensitive) gate: entries flagged `C` must also equal the caller's exact spelling.
3. **Caller-declared initialism** — `initialisms` set (lower-cased at build time).
4. **Supported compound** — `CompoundTable` + `resolveCompound`, depth-guarded.
5. **Productive spelling rule** — only if `WordShape.isPlainLatinToken(noun)`; first match
   among the three rules wins.
6. **Documented fallback** — return the input unchanged, `fallback:unsupported`.

`MAX_COMPOUND_DEPTH = 1`: ordinary compound heads are pluralized with simple words only
(`pluralizeHead` calls `resolve(head, null, depth + 1)`). The trailing free-tail path may
inflect a hyphenated prefix as a whole (`great-grandfather-in-law`), but the depth guard
keeps that bounded; there is no unbounded recursion.

The fast `pluralize` path allocates nothing for identity results and never builds
diagnostic collections. `analyze` runs the same `resolve` with a `Sink` that records
`source`, `id`, countability, alternatives, ambiguity and a human-readable `note`;
the chosen plural string is identical in both paths.

## Lexicon (`Lexicon.java`)

The artifact is plain 7-column TSV (`lemma, plural, classical, alternatives, countability,
flags, ...` — see `data/LICENSE-DATA.md`), so it can be diffed, validated and regenerated
offline. Lookup keys are lower-cased via `WordShape.lower`; entries that must not be
reachable from a different casing carry the `C` flag and are compared against the exact
caller spelling (`matchesCase`).

In memory the lexicon is columnar, not an object graph:

* keys concatenated in one `char[]` with an `int[]` offset column;
* an open-addressed `int[]` table of entry indexes (linear probing, `stored - 1` encoding);
* metadata in primitive columns: `flags` (I/N/C/A bits), `countabilities`, `cuts`,
  `appendIds` into a deduplicated `appendPool`;
* full plural strings retained **only** for the ~14k entries whose diagnostics can expose
  them (an alternative or a classical form); everything else is a derived `(cut, append)`
  edit reconstructed from the artifact, which is sufficient for the fast path.

`deriveEdit` computes the edit as longest-common-prefix: `cut = lemma.length() - prefix`,
`append = plural.substring(prefix)`. Example: `mouse/mice` → cut 4, append `ice`;
`child/children` → cut 0, append `ren`.

That layout is the footprint win reported in `docs/benchmarks.md`: the historical object
graph retained 55.5 MB; the packed artifact measured **19.08 MB** retained in the v1.2.0
run after GC, at the cost of slower one-time load (column packing + index build, 718-726 ms
to first call on that run). No per-entry object exists.

## Rules (`Rule.java`, `WordShape.java`)

Three productive rules, with documented backoffs for known weak suffix families (the
backoffs are bounded lexical fallbacks, not general suffix rules):

| id | condition on the lower-cased token | edit |
| --- | --- | --- |
| `rule:sibilant-es` | ends `s`, `x`, `z`, `ch`, `sh` | append `es` |
| `rule:consonant-y-ies` | consonant before final `y`, including `qu + y` (`soliloquy → soliloquies`) | `y → ies` (cut 1) |
| `rule:default-s` | otherwise | append `s` |

Rules are separate from the lexicon by construction: a `Rule` is a suffix condition plus a
fixed end-of-token edit. Nothing word-specific is expressible as a rule, which removes the
upstream `carp/scarp` and `louse/blouse` failure structurally — `carp` and `louse` are
lexical rows, not patterns, so they cannot reach `scarp` or `blouse`.

Token gating: rules apply only to `isPlainLatinToken` inputs (Latin letters plus combining
marks; hyphens, spaces, digits, apostrophes and non-Latin scripts are excluded). Anything
else falls through to compound handling or the fallback.

## Casing (`WordShape`, `EnglishInflector.render*`)

Lookup normalization and output case restoration are separate operations — this is the fix
for the upstream `CHILD → CHILDren` / `CITY → CITIes` bug.

* Lookup: `WordShape.lower` (returns the same instance when no upper-case code point exists).
* Render: `casingOf` classifies the input as `LOWER`, `UPPER`, `CAPITALIZED` or `MIXED`;
  `render` applies the `(cut, append)` edit at the end of the token and re-cases the whole
  result; `renderFull` maps a complete lowercase form (lexical alternative, compound) onto
  the caller's shape; `renderInitialism` appends the suffix without whole-token re-casing.
* `LOWERCASE` / `UPPERCASE` policies force the output shape; `PRESERVE` (default) keeps
  `iPhone → iPhones` internal capitals via the `MIXED` path.
* Length-changing case mappings (e.g. Turkish dotted İ) are guarded: when
  `source.length() != lower.length()`, the lower-cased render is re-cased instead of
  editing the source in place.

Initialisms are explicit metadata, never an uppercase guess. A lexical entry is flagged `N`
when its attested plural is exactly `lemma + s`/`lemma + es`; it then appends that suffix
(`UFO → UFOs`, `SMS → SMSes`). Caller-declared initialisms (`builder().initialism("XYZ")`)
append `s`, or `es` after a final `s` by default.

## Compounds (`CompoundTable.java`)

Bounded and reviewed, not suffix-guessed — a hyphen or space is not proof of a morpheme
boundary:

* **Exact entries** (whole-compound → whole-plural): `passer-by`, `stand-in`, `drive-by`,
  `hanger-on`, `runner-up`, `close-up`, `go-between`, `jack-in-the-box`, `man-of-war`,
  `major general`, and a handful more.
* **Free tails** (cannot themselves be a noun, so any head is safe): `-in-law`, `-in-arms`,
  `-in-aid`, `-in-kind`, `-in-chief`, `-of-war`, `-of-arms`, `-of-spring`, `-of-all-trades`,
  `-out-law`, `-at-arms`, `-at-law`, `-in-crime`. The head inherits irregularity and may
  itself be hyphenated (`child-in-law → children-in-law`, `man-of-war → men-of-war`,
  `great-grandfather-in-law → great-grandfathers-in-law`).
* **Postposed modifiers** only after curated heads: `general`, `elect`, `apparent`,
  `martial`, … after `attorney`, `court`, `heir`, `governor`, … (`attorneys general`).
  `major general → major generals` is the counterexample that forces the head allow-list.
* **Head-final nouns**: inflect the final token when it is a plain head and neither a
  preposition, coordinator, free tail nor documented non-noun head. Heads that are absent
  or unchanged get productive respelling (`ice-shelf → ice-shelves`, `non-life → non-lives`),
  multi-hyphen chains are bounded (`great-great-grandfather → great-great-grandfathers`), and
  reviewed `on-` prefixes are allowed (`on-ramp → on-ramps`). Short initial prefixes keep a
  lower-case suffix (`G-PON → G-PONs`, `LV-ROM → LV-ROMs`).
* **Space-separated rule**: inflect the final token only when no preposition is present and
  the final token is a plain Latin token (`ice cream → ice creams`); `jack in the box` is
  returned unchanged rather than guessed.

Counterexamples are pinned as regression tests: `stand-in → stand-ins` (not `stands-in`),
`drive-by → drive-bys`, unmatched hyphenated tokens (`well-known`) fall through to the
fallback, and `CREDIT-CARD → CREDIT-CARDS` retains upper-case casing.

## Countability and alternatives

Kept separate from singular→plural accuracy in the evaluator (see `docs/evaluation.md`):

* `MASS` / `PLURAL_ONLY` / invariant inputs return the lemma unchanged and are flagged in
  `analyze`; `MIXED` pluralizes in the countable sense (`waters`); `COUNTABLE` pluralizes.
* Alternatives are preserved: `person` prefers `people` with `persons` accepted; `die`
  prefers `dice` with `dies` as an alternative and `ambiguous() == true` because spelling
  alone cannot choose the sense.
* `Mode.CLASSICAL` only changes *preferences* for entries carrying an attested classical
  form (`cacti`, `formulae`, `indices`); it never invents neo-classical forms by rule.

The external UD EWT check (`docs/evaluation.md`) once found the artifact over-classifying
common count nouns as `MASS`/`PLURAL_ONLY` (`book`, `bag`, `account`) — a data defect in
extraction/classification, not a rendering bug. It was fixed by correcting extraction
(sense-level tags no longer outweigh attested plurals; multi-etymology labels reconciled
by votes) and rerunning P1/P2; it was deliberately not tuned against the external set.

## Concurrency, startup and footprint

* Instances are immutable (`Map.copyOf` / `Set.copyOf` snapshots, `final` fields, no
  setters); the bundled `Lexicon` loads once into packed arrays and is shared via a holder.
  Safe to share across threads; there is no mutable global configuration.
* Startup is one-time artifact load + index build (718-726 ms to first call on the measured
  v1.2.0 run); per-call lookup is lock-free array/hash access.
* No model, no training dependency, no native bridge, no runtime download. The learning
  experiment (`docs/learning.md`) was rejected, so the shipped artifact is deterministic
  code + TSV only.

## What is out of scope, on purpose

Singularization, verb conjugation, sentence parsing, multilingual morphology, online
learning, general multi-word rewriting. Unsupported input is returned unchanged and
reported by `analyze`, not silently fixed. The rules-only runtime mode
(`builder().withoutBundledLexicon()`) does not load the packaged Wiktionary data. The data
still ships in the JAR, so the CC BY-SA 3.0 attribution and redistribution terms still
apply; it is not a data-free build.
