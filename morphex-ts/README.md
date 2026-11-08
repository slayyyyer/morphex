# morphex (TypeScript)

Deterministic English noun pluralization for TypeScript and JavaScript. This is a native port of
the Java [morphex](../README.md) library: same precedence pipeline, same lexicon, same compound
table. No runtime dependencies, no network calls, no model downloads.

```ts
import { EnglishInflector } from 'morphex';

const inflector = EnglishInflector.standard();

inflector.pluralize('child'); // 'children'
inflector.pluralize('CHILD'); // 'CHILDREN'
inflector.pluralize('UFO'); // 'UFOs'
inflector.pluralize('mother-in-law'); // 'mothers-in-law'
inflector.pluralize('cat', 1); // 'cat'  (count === 1 keeps the source)
inflector.pluralize('cat', 3); // 'cats'

const result = inflector.analyze('die');
result.plural; // 'dice'
result.alternatives; // ['dies']
result.ambiguous; // true
result.stage; // 'LEXICON'
result.source; // 'lex:die'
result.countability; // 'COUNTABLE'
```

Node 18+ and browsers. Zero runtime dependencies. Immutable instances: configuration is fixed at
build time, so an instance can be shared freely.

## Install

```bash
npm install morphex
```

To build from a checkout instead: `cd morphex-ts && npm install && npm run build`.

Entry points:

| Import          | Lexicon                      | Use                                                                           |
| --------------- | ---------------------------- | ----------------------------------------------------------------------------- |
| `morphex`       | 124,883-entry delta artifact | `pluralize()`; `analyze()` reports rule/compound stages without full metadata |
| `morphex/full`  | 147,338-entry full artifact  | Java-identical `analyze()`: alternatives, ambiguity, countability             |
| `morphex/rules` | none                         | rules-only build with no data files and no CC BY-SA material                  |

The full artifact is opt-in so the common case does not pay for diagnostics it never reads:

```ts
import { EnglishInflector, createBundledFullLexicon } from 'morphex/full';

const diagnostics = EnglishInflector.builder().withLexicon(createBundledFullLexicon()).build();

diagnostics.analyze('die').alternatives; // ['dies']
```

## API

### `pluralize(word, count?)`

`pluralize(word, 1)` returns the input unchanged, including its original casing. Any other count
pluralizes. Both are pure functions of the configured instance.

### `analyze(word): Inflection`

Every field is always present; nothing is optional.

```ts
interface Inflection {
  readonly input: string;
  readonly plural: string;
  readonly alternatives: readonly string[];
  readonly ambiguous: boolean;
  readonly stage: 'OVERRIDE' | 'LEXICON' | 'INFERRED' | 'COMPOUND' | 'RULE' | 'FALLBACK';
  readonly source: string; // stable id, e.g. 'lex:child', 'rule:default-s'
  readonly countability: 'COUNTABLE' | 'MASS' | 'MIXED' | 'PLURAL_ONLY' | 'UNKNOWN';
  readonly invariant: boolean;
  readonly note: string;
  readonly diagnostics: boolean; // false when the provider cannot supply full metadata
  isAccepted(form: string): boolean;
}
```

`stage` follows the Java `DecisionSource`; `source` is the Java `Inflection.id()`.

### Configuration

```ts
const custom = EnglishInflector.builder()
  .withOverride('goose', 'geese') // exact, case-sensitive whole-input match
  .withOverride('API', 'APIs')
  .withInitialism('ABC') // suffix policy for a declared acronym
  .withMode('CLASSICAL') // prefer attested classical forms
  .withCasePolicy('LOWERCASE') // render the whole result in one casing
  .withoutBundledLexicon() // rules-only: no data files at all
  .build();
```

Overrides win over everything else and are returned literally. The builder is fluent and typed;
`build()` returns an `EnglishInflector`. Instances are immutable.

### Custom lexicons

Every data source implements the same provider interface, so bundled, opt-in, rules-only and
caller-supplied data are interchangeable:

```ts
import { EnglishInflector, createLexicon } from 'morphex';

const provider = createLexicon([
  { lemma: 'cactus', plural: 'cactuses', classical: 'cacti', alternatives: ['cacti'] },
]);

const inflector = EnglishInflector.builder().withLexicon(provider).build();
```

`createFullLexicon`, `createDeltaLexicon` and `RULES_ONLY_LEXICON` are exported for the same
purpose. A malformed artifact, duplicate key, unsorted file or unknown flag throws at construction
time; the loaders never guess.

## Precedence

First match wins, exactly as in the Java library:

1. **Caller override** — exact, case-sensitive whole-input match.
2. **Exact lexicon entry** — case-insensitive lookup, with case-sensitive entries where needed.
3. **Declared initialism** — configured acronym suffix policy.
4. **Supported compound** — bounded table: exact entries, free tails and curated postposed heads.
   Unmatched input is left alone, not guessed.
5. **Three productive rules** — `+es` after sibilants, consonant + `y` → `ies`, default `+s`.
6. **Documented fallback** — unsupported shapes are returned unchanged and reported by `analyze`.

Lookup normalisation is separate from output case restoration, so `CHILD → CHILDREN` and
`UFO → UFOs` are both correct.

## Rules-only vs bundled lexicon

| Build           |                     Data shipped | `pluralize('child')`  | `analyze()` metadata                            |
| --------------- | -------------------------------: | --------------------- | ----------------------------------------------- |
| `morphex/rules` |                              0 B | `childs` (rules only) | rules/compound stages, `diagnostics: false`     |
| `morphex`       | 1,962,900 B raw / 562,893 B gzip | `children`            | rule/compound stages; partial lexical metadata  |
| `morphex/full`  | 3,707,556 B raw / 785,131 B gzip | `children`            | complete: alternatives, ambiguity, countability |

A rules-only build is a plain `EnglishInflector.builder().withoutBundledLexicon().build()` and
needs no data files. The delta engine parses its four artifact sections and builds a 1 MiB
open-addressed hash index at construction (roughly 50 ms end to end on the measured machine,
with 17–19 ms of that building the index), so lookups do not scan the sorted keys; nothing is
built at module import. The full artifact also has a
1,817,208 B (1,333,152 B gzip) membership sidecar used only for uppercase-initialism inference.

## Building data

The lexicons are generated from the repository's authoritative artifact, not copied:

```bash
npm run build:data
```

Reads `inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv`
and `lexicon-membership-v2.bin`, and writes gitignored modules under `src/generated/` plus
`dist/lexicon-delta.txt`, `dist/lexicon-full.txt` and `dist/sizes.json`.

The delta artifact stores only entries whose answer differs from the productive rules, plus
sorted key lists for mass, plural-only and invariant nouns. Hyphenated and spaced lemmas are kept
even when the rules would reproduce them, because the rules are unreachable behind the compound
policy.

## Conformance

Both ports are tested against fixtures exported from the Java engine:

- `test-fixtures/p1.json` — all 50,978 frozen P1 cases, tuple-typed in
  `test-fixtures/schema.ts`.
- `test-fixtures/upstream-probe.json` — the `docs/upstream-probe.md` table.
- `site/catalog.json` — the verified catalog used by the Java site demo.

Vitest runs every case through both the delta and full engines and compares the plural, accepted
forms, primary flag, countability, invariant flag, stage and source id. CI regenerates the
fixtures with Java and fails on any diff, so the ports cannot drift silently.

```bash
npm test             # typecheck-friendly Vitest suite
npm run typecheck    # strict tsc, no emit
npm run lint         # typescript-eslint + Prettier
npm run build        # ESM + CJS + .d.ts/.d.cts via tsup
npm run check:package # publint + arethetypeswrong
```

## Releasing

The npm release runs through `.github/workflows/morphex-publish.yml`. Set the package version, commit it, and push a matching tag:

```bash
cd morphex-ts
npm version 1.0.1 --no-git-tag-version
git add package.json package-lock.json
git commit -m "Release morphex 1.0.1"
git tag morphex-v1.0.1
git push origin main --tags
```

The workflow typechecks, lints, tests, builds, validates the package, checks the tag against `package.json`, and publishes with npm trusted publishing. Configure the npm trusted publisher with workflow filename `morphex-publish.yml` and allow **npm publish**; no `NPM_TOKEN` secret is used.

## Benchmarks

See [BENCHMARKS.md](BENCHMARKS.md) for measured Node timings and bundle sizes. The numbers are
Node/V8 micro-benchmarks and are deliberately separate from the Java JMH results.

## Licence

Code: MIT (see `LICENSE`). Bundled lexicon data: CC BY-SA 3.0, Wiktionary contributors via
Kaikki.org / Wiktextract — see `NOTICE` and the repository's root `NOTICE` and
`data/LICENSE-DATA.md`. The `morphex/rules` entry point contains no lexicon data.
