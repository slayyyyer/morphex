# Implementation plan (working title: english-inflector)

Repository state on start: empty (only `.freebuff/`), no git history, no JDK/Maven installed.
The build therefore uses the conventional Maven multi-module layout with a checked-in wrapper.

## Scope of release 1

In: singular English nouns -> plural, accepted alternatives, countability, casing policies,
documented bounded compounds, anglicized/classical preference modes, caller overrides,
immutable per-instance configuration, diagnostic result.

Out: singularization, verb conjugation, sentence parsing, general morphology, multilingual data,
online learning, runtime model download.

## Layout

| Module | Purpose | Ships to consumers |
| --- | --- | --- |
| `inflector-core` | API, engine, generated lexicon resource | yes (no runtime deps) |
| `inflector-example` | runnable consumer example | no |
| `inflector-eval` | evaluator CLI; stock evo-inflector 2.0 baseline (A), rules (B), rules+lexicon (C) | no |
| `inflector-benchmarks` | JMH forks, allocation/startup/footprint probes | no |
| `tools/research` | offline extraction, artifact generation, splits, metrics, learners (python, stdlib only) | no |
| `data/` | curated sources, normalized dataset, manifest, regression suite | data licenses apply |

Baselines D/E/F are produced by `tools/research` from the deterministic (C) prediction file, so D and E
share one candidate inventory and one renderer.

## Precedence (frozen)

1. caller override (exact, case-sensitive)
2. exact lexical entry (case-sensitive entries honoured; otherwise case-insensitive)
3. supported compound transformation
4. productive spelling rule (`+es` after sibilant, consonant+y -> `ies`, default `+s`)
5. documented unresolved-input fallback: return input unchanged, `DecisionSource.FALLBACK`

Lexical entries and rules are separate data. Nothing word-specific is expressed as a suffix rule,
which removes the upstream `carp`/`scarp` and `louse`/`blouse` failure mode structurally.

## Policy decisions

- **D1 Casing.** `CasePolicy.PRESERVE` infers from the input: all-lower -> lower, Capitalized -> capitalized,
  ALL-UPPER -> upper, mixed -> apply the same suffix edit to the original token so internal capitals survive
  (`iPhone -> iPhones`). `LOWERCASE`/`UPPERCASE` force the result case. The upstream `CHILD -> CHILDren`
  bug disappears because the edit is applied at the end of the token and the whole result is re-cased.
- **D2 Initialisms.** Uppercase alone is not evidence of an acronym. An entry is an initialism only via
  lexical metadata (`N` flag) or `Builder.initialisms(...)`. Initialisms append `s`, or `es` when the final
  letter is S/X/Z/C (`SMSes`, `UFOs`, `CDs`). This is why `SMS -> SMSes` is an intentional policy, not a bug fix.
- **D3 Identifiers.** Tokens with digits, `_`, `.`, `+`, `#`, `/`, `'`, or non-Latin letters are unsupported:
  returned unchanged with `FALLBACK`. No trimming or rewriting of caller input, ever.
- **D4 Compounds.** Only a bounded, tested set: exact entries (`passer-by`, `stand-in`, `drive-by`,
  `hanger-on`, `runner-up`, `close-up`, `jack-in-the-box`, `man-of-war`, ...), free-suffix patterns that cannot
  be a noun (`-in-law`, `-in-chief`, `-of-war`, `-at-arms`, `-at-law`, `-of-arms`), postposed modifiers
  restricted to curated heads (`attorney general`, `court-martial`, `governor-elect`), and a generic
  space-separated rule that inflects the final token when no preposition is present (`ice cream`).
  Counterexamples are regression tests: `stand-in -> stand-ins` (not `stands-in`), `drive-by -> drive-bys`,
  `major general -> major generals`, `jack in the box` stays unchanged rather than being guessed.
- **D5 Countability.** Mass-only and plural-only inputs are returned unchanged and flagged in `analyze`;
  mixed-use nouns pluralize normally (`waters`) with `MIXED`. Invariant nouns return the identical `String`
  instance (`sheep`). These are separate from singular-to-plural accuracy in the evaluator.
- **D6 Alternatives.** `people`/`persons` are both accepted; `die` surfaces preferred `dice` with alternative
  `dies` and `ambiguous=true` because spelling cannot distinguish the senses.
- **D7 Modes.** `CLASSICAL` changes *preferences* for entries that carry an attested classical plural
  (`cacti`, `formulae`, `indices`). It never invents neo-classical forms by suffix rule.
- **D8 No numeric confidence.** The deterministic path does not expose a probability; a raw model score is not
  a validated correctness probability, so confidence is omitted rather than faked.
- **D9 No evo API shim.** A class implementing `org.atteo.evo.inflector.English` would put two copies of the same
  API on the benchmark classpath. Migration is documented in `docs/compatibility.md` instead.
- **D10 Data license.** Code MIT; the generated lexicon is derived from Wiktionary via Kaikki (CC BY-SA 3.0),
  shipped as a separate resource with attribution and the option to build without it.

## Data protocols (frozen)

- **P1 lexical coverage.** Ship the artifact, evaluate every validated record it covers against independently
  extracted gold forms. Reported as coverage, never as unseen generalization.
- **P2 family holdout.** Group lemma families (compound head, casing variants, shared irregular family) before
  generating any experimental resource; holdout families are excluded from lexicon, mining, calibration and
  training. Report P1 and P2 separately.

Gold labels, eligibility and denominators are fixed before predictions. Unsupported/unknown/unattested targets
are reported in their own bucket and never relabelled from a prediction.

## Research budget (declared before training)

- at most 2 configurations per learner, 3 seeds for stochastic training
- 8 CPU-hours of model fitting, 2000 newly adjudicated records maximum
- runtime neural gate uses the numbers in the brief; gates are frozen before the blind test is scored

## Stop rule

Candidate-oracle ceiling is measured first. If the oracle cannot beat the strongest non-neural system by the
required margin, ranker training stops and the outcome is recorded as a pretraining stop. A negative neural
result with a correct, fast deterministic library is a successful deliverable.

## Milestones

1. Repository/baseline: build, upstream probes, decision log. (this file)
2. Deterministic core: API, precedence, casing, compounds, overrides, regression tests.
3. Data/evaluation: extraction, provenance, artifact, fixed scoring, leakage-safe splits, baseline report.
4. Performance: JMH, allocations, footprint, stock 2.0 comparison.
5. Learning: oracle ceiling, D, E, routing, decision.
6. Delivery: clean build, example, docs, final report.
