/**
 * Immutable English noun pluralizer, a TypeScript port of the Java `EnglishInflector`.
 *
 * Resolution order is fixed: caller override, exact lexical entry, caller-declared initialism,
 * supported compound, productive spelling rule, then the documented fallback that returns the
 * input unchanged. Instances are immutable and stateless.
 */

import {
  exactPlural,
  freeTailSuffix,
  isCompoundPrefix,
  isCoordinator,
  isCuratedHead,
  isFreeTail,
  isNonNounHead,
  isParticle,
  isPostposed,
  isPreposition,
} from './compound-table.js';
import { defaultLexiconProvider } from './defaults.js';
import { RULES_ONLY_LEXICON, deriveEdit } from './lexicon.js';
import { applyCasePolicy, render, renderFull, renderInitialism } from './render.js';
import { applyProductiveRule } from './rules.js';
import type {
  CasePolicy,
  Countability,
  Inflection,
  InflectionSource,
  LexiconEntry,
  LexiconProvider,
  Mode,
  UppercasePolicy,
} from './types.js';
import {
  codePointLength,
  isAllUpperLatinToken,
  isPlainLatinToken,
  lower as lowerCaseOf,
} from './word-shape.js';

/** Compound transformations are nested, never unbounded. */
const MAX_COMPOUND_DEPTH = 4;

const FALLBACK_ID = 'fallback:unsupported';

/** The `-f`/`-fe` respellings used only when a compound head is genuinely unresolved. */
const F_TO_VES = [
  ['life', 'lives'],
  ['shelf', 'shelves'],
  ['half', 'halves'],
  ['staff', 'staves'],
  ['wolf', 'wolves'],
  ['leaf', 'leaves'],
  ['loaf', 'loaves'],
  ['calf', 'calves'],
  ['elf', 'elves'],
  ['self', 'selves'],
  ['thief', 'thieves'],
  ['sheaf', 'sheaves'],
  ['hoof', 'hooves'],
] as const satisfies readonly (readonly [string, string])[];

interface Sink {
  stage: InflectionSource;
  id: string;
  countability: Countability;
  invariant: boolean;
  ambiguous: boolean;
  alternatives: readonly string[];
  note: string;
}

interface HeadResult {
  readonly text: string;
  readonly stage: InflectionSource;
  readonly id: string;
  readonly invariant: boolean;
}

interface Config {
  readonly mode: Mode;
  readonly casePolicy: CasePolicy;
  readonly overrides: ReadonlyMap<string, string>;
  readonly initialisms: ReadonlySet<string>;
  readonly uppercasePolicy: UppercasePolicy;
  readonly lexicon: LexiconProvider;
}

function createSink(): Sink {
  return {
    stage: 'FALLBACK',
    id: FALLBACK_ID,
    countability: 'UNKNOWN',
    invariant: false,
    ambiguous: false,
    alternatives: [],
    note: '',
  };
}

function record(
  sink: Sink,
  stage: InflectionSource,
  id: string,
  countability: Countability,
  invariant: boolean,
  ambiguous: boolean,
  alternatives: readonly string[],
  note: string,
): void {
  sink.stage = stage;
  sink.id = id;
  sink.countability = countability;
  sink.invariant = invariant;
  sink.ambiguous = ambiguous;
  sink.alternatives = alternatives;
  sink.note = note;
}

class DiagnosticInflection implements Inflection {
  readonly input: string;
  readonly plural: string;
  readonly alternatives: readonly string[];
  readonly ambiguous: boolean;
  readonly stage: InflectionSource;
  readonly source: string;
  readonly countability: Countability;
  readonly invariant: boolean;
  readonly note: string;
  readonly diagnostics: boolean;

  constructor(fields: {
    input: string;
    plural: string;
    alternatives: readonly string[];
    ambiguous: boolean;
    stage: InflectionSource;
    source: string;
    countability: Countability;
    invariant: boolean;
    note: string;
    diagnostics: boolean;
  }) {
    this.input = fields.input;
    this.plural = fields.plural;
    this.alternatives = Object.freeze([...fields.alternatives]);
    this.ambiguous = fields.ambiguous;
    this.stage = fields.stage;
    this.source = fields.source;
    this.countability = fields.countability;
    this.invariant = fields.invariant;
    this.note = fields.note;
    this.diagnostics = fields.diagnostics;
  }

  isAccepted(form: string): boolean {
    return this.plural === form || this.alternatives.includes(form);
  }
}

function entryNote(entry: LexiconEntry): string {
  switch (entry.countability) {
    case 'MASS':
      return 'mass noun: returned unchanged by policy';
    case 'PLURAL_ONLY':
      return 'plural-only lemma: returned unchanged by policy';
    case 'MIXED':
      return 'mixed mass/countable noun: pluralized in its countable sense';
    default:
      return entry.invariant ? 'invariant plural: singular and plural are identical' : '';
  }
}

function initialismSuffix(lowerCase: string): string {
  return lowerCase.charAt(lowerCase.length - 1) === 's' ? 'es' : 's';
}

let standardInstance: EnglishInflector | undefined;

export class EnglishInflector {
  readonly #mode: Mode;
  readonly #casePolicy: CasePolicy;
  readonly #overrides: ReadonlyMap<string, string>;
  readonly #initialisms: ReadonlySet<string>;
  readonly #uppercasePolicy: UppercasePolicy;
  readonly #lexicon: LexiconProvider;

  /** @internal Instances are created through `standard()` or `builder()`. */
  constructor(config: Config) {
    this.#mode = config.mode;
    this.#casePolicy = config.casePolicy;
    this.#overrides = config.overrides;
    this.#initialisms = config.initialisms;
    this.#uppercasePolicy = config.uppercasePolicy;
    this.#lexicon = config.lexicon;
  }

  /** Shared default instance: anglicized mode, `PRESERVE` casing, bundled delta lexicon. */
  static standard(): EnglishInflector {
    standardInstance ??= EnglishInflector.builder().build();
    return standardInstance;
  }

  static builder(): Builder {
    return new Builder();
  }

  get mode(): Mode {
    return this.#mode;
  }

  get casePolicy(): CasePolicy {
    return this.#casePolicy;
  }

  get uppercasePolicy(): UppercasePolicy {
    return this.#uppercasePolicy;
  }

  /** Number of lexical entries visible to this instance (0 for a rules-only build). */
  get lexiconSize(): number {
    return this.#lexicon.size;
  }

  /**
   * Returns the plural of a singular noun. With a `count`, returns `noun` unchanged when
   * `count === 1` and the plural otherwise. This is deliberately a plain count test, not an
   * absolute-value or locale rule.
   */
  pluralize(noun: string, count?: number): string {
    if (count === 1) {
      return noun;
    }
    return this.#resolve(noun, undefined, 0, this.#casePolicy);
  }

  /** Returns a full diagnostic result. The chosen form is identical to `pluralize(noun)`. */
  analyze(noun: string): Inflection {
    const sink = createSink();
    const plural = this.#resolve(noun, sink, 0, this.#casePolicy);
    return new DiagnosticInflection({
      input: noun,
      plural,
      alternatives: sink.alternatives,
      ambiguous: sink.ambiguous,
      stage: sink.stage,
      source: sink.id,
      countability: sink.countability,
      invariant: sink.invariant,
      note: sink.note,
      diagnostics: this.#lexicon.diagnostics,
    });
  }

  // ------------------------------------------------------------------ resolution

  #resolve(noun: string, sink: Sink | undefined, depth: number, policy: CasePolicy): string {
    if (noun.length === 0) {
      if (sink !== undefined) {
        record(
          sink,
          'FALLBACK',
          'fallback:empty',
          'UNKNOWN',
          false,
          false,
          [],
          'empty input returns empty output',
        );
      }
      return noun;
    }

    const override = this.#overrides.get(noun);
    if (override !== undefined) {
      if (sink !== undefined) {
        record(
          sink,
          'OVERRIDE',
          'override',
          'UNKNOWN',
          false,
          false,
          [],
          'caller override matched the exact input spelling',
        );
      }
      return override;
    }

    const lowerCase = lowerCaseOf(noun);
    const entry = this.#lexicon.lookup(lowerCase);
    if (entry !== undefined && (!entry.caseSensitive || entry.lemma === noun)) {
      return this.#fromEntry(noun, lowerCase, entry, sink, policy);
    }

    if (this.#initialisms.has(lowerCase)) {
      if (sink !== undefined) {
        record(
          sink,
          'LEXICON',
          'config:initialism',
          'UNKNOWN',
          false,
          false,
          [],
          'token declared an initialism by configuration',
        );
      }
      return renderInitialism(noun, lowerCase, initialismSuffix(lowerCase), policy);
    }

    // Unknown all-uppercase token: a bounded initialism policy, not an assumption that every
    // uppercase spelling is an acronym. Ordinary-word membership is checked first, and the
    // policy needs a membership table, exactly as the Java rules-only instance does.
    const ordinaryWord = this.#lexicon.isOrdinaryWord(lowerCase);
    if (
      this.#uppercasePolicy === 'INFER_INITIALISM' &&
      this.#lexicon.size > 0 &&
      ordinaryWord !== undefined &&
      !ordinaryWord &&
      isAllUpperLatinToken(noun) &&
      codePointLength(noun) >= 2 &&
      codePointLength(noun) <= 6
    ) {
      if (sink !== undefined) {
        record(
          sink,
          'INFERRED',
          'inferred:uppercase-initialism',
          'UNKNOWN',
          false,
          false,
          [],
          'unknown all-upper-case token of 2-6 Latin letters: appended lower-case s; ' +
            'lexical membership was checked first',
        );
      }
      return renderInitialism(noun, lowerCase, 's', policy);
    }

    if (depth < MAX_COMPOUND_DEPTH && (noun.includes('-') || noun.includes(' '))) {
      const compound = this.#resolveCompound(noun, lowerCase, depth, policy, sink);
      if (compound !== null) {
        return compound === noun ? noun : compound;
      }
    }

    if (isPlainLatinToken(noun)) {
      const rule = applyProductiveRule(noun, lowerCase);
      if (sink !== undefined) {
        record(sink, 'RULE', rule.id, 'UNKNOWN', false, false, [], rule.description);
      }
      return render(noun, lowerCase, rule.cut, rule.append, policy);
    }

    if (sink !== undefined) {
      record(
        sink,
        'FALLBACK',
        FALLBACK_ID,
        'UNKNOWN',
        false,
        false,
        [],
        'unsupported token shape (identifier, punctuation, possessive or non-Latin letter); ' +
          'returned unchanged',
      );
    }
    return noun;
  }

  #fromEntry(
    noun: string,
    lowerCase: string,
    entry: LexiconEntry,
    sink: Sink | undefined,
    policy: CasePolicy,
  ): string {
    const [cut, append] = deriveEdit(entry.lemma, entry.plural);

    if (entry.initialism) {
      if (sink !== undefined) {
        record(
          sink,
          'LEXICON',
          `lex:${entry.caseSensitive ? entry.lemma : entry.key}#initialism`,
          entry.countability,
          false,
          entry.ambiguous,
          [],
          "initialism metadata: append the entry's s/es suffix without whole-token re-casing",
        );
      }
      return renderInitialism(noun, lowerCase, append, policy);
    }

    if (entry.countability === 'MASS' || entry.countability === 'PLURAL_ONLY' || entry.invariant) {
      if (sink !== undefined) {
        record(
          sink,
          'LEXICON',
          `lex:${entry.caseSensitive ? entry.lemma : entry.key}`,
          entry.countability,
          entry.invariant,
          entry.ambiguous,
          this.#renderAlternatives(noun, lowerCase, entry, false, policy),
          entryNote(entry),
        );
      }
      return noun;
    }

    const classicalForm = this.#mode === 'CLASSICAL' ? entry.classical : null;
    const classicalUsed = classicalForm !== null;
    let pluralCut = cut;
    let pluralAppend = append;
    if (classicalForm !== null) {
      [pluralCut, pluralAppend] = deriveEdit(entry.lemma, classicalForm);
    }
    const plural = render(noun, lowerCase, pluralCut, pluralAppend, policy);

    if (sink !== undefined) {
      record(
        sink,
        'LEXICON',
        `lex:${entry.caseSensitive ? entry.lemma : entry.key}`,
        entry.countability,
        false,
        entry.ambiguous,
        this.#renderAlternatives(noun, lowerCase, entry, classicalUsed, policy),
        classicalUsed ? 'classical preference selected by mode' : entryNote(entry),
      );
    }
    return plural;
  }

  #renderAlternatives(
    noun: string,
    lowerCase: string,
    entry: LexiconEntry,
    classicalUsed: boolean,
    policy: CasePolicy,
  ): readonly string[] {
    const stored = entry.alternatives;
    const classical = entry.classical;
    if (stored.length === 0 && classical === null) {
      return [];
    }
    const alternatives: string[] = [];
    if (classicalUsed) {
      alternatives.push(renderFull(noun, lowerCase, entry.plural, policy));
    } else if (classical !== null) {
      alternatives.push(renderFull(noun, lowerCase, classical, policy));
    }
    for (const alternative of stored) {
      alternatives.push(renderFull(noun, lowerCase, alternative, policy));
    }
    return alternatives;
  }

  // ------------------------------------------------------------------ compounds

  #resolveCompound(
    noun: string,
    lowerCase: string,
    depth: number,
    policy: CasePolicy,
    sink: Sink | undefined,
  ): string | null {
    const exact = exactPlural(lowerCase);
    if (exact !== undefined) {
      if (sink !== undefined) {
        record(
          sink,
          'COMPOUND',
          `compound:exact:${lowerCase}`,
          'UNKNOWN',
          false,
          false,
          [],
          'exact compound entry',
        );
      }
      return renderFull(noun, lowerCase, exact, policy);
    }

    const freeSuffix = freeTailSuffix(lowerCase);
    if (freeSuffix > 0 && freeSuffix < noun.length) {
      const headPlural = this.#resolveHead(noun.slice(0, freeSuffix), depth, true);
      if (headPlural !== null) {
        if (sink !== undefined) {
          record(
            sink,
            'COMPOUND',
            'compound:hyphen:free-tail-suffix',
            'UNKNOWN',
            false,
            false,
            [],
            'documented trailing free tail: head before the suffix is inflected',
          );
        }
        return applyCasePolicy(headPlural.text + noun.slice(freeSuffix), policy);
      }
    }

    const hyphen = noun.indexOf('-');
    if (hyphen > 0 && hyphen < noun.length - 1) {
      const head = noun.slice(0, hyphen);
      const tail = lowerCase.slice(hyphen + 1);
      const recognised =
        isFreeTail(tail) || (isPostposed(tail) && isCuratedHead(lowerCaseOf(head)));
      if (recognised) {
        const headPlural = this.#resolveHead(head, depth);
        if (headPlural !== null) {
          if (sink !== undefined) {
            record(
              sink,
              'COMPOUND',
              `compound:hyphen:${tail}`,
              'UNKNOWN',
              false,
              false,
              [],
              `documented hyphenated compound pattern '${tail}'`,
            );
          }
          return applyCasePolicy(headPlural.text + noun.slice(hyphen), policy);
        }
      }
      if (this.#isParticleCompound(noun, hyphen, tail)) {
        if (sink !== undefined) {
          record(
            sink,
            'COMPOUND',
            'compound:hyphen:particle',
            'UNKNOWN',
            false,
            false,
            [],
            'verb-particle hyphenated noun: whole expression takes s',
          );
        }
        return render(noun, lowerCase, 0, 's', policy);
      }
      if (this.#isHeadFinalCompound(noun, hyphen, tail)) {
        const tailPlural = this.#pluralizeCompoundHead(noun.slice(hyphen + 1), depth, true);
        if (tailPlural !== null) {
          if (sink !== undefined) {
            record(
              sink,
              'COMPOUND',
              'compound:hyphen:head-final',
              'UNKNOWN',
              false,
              false,
              [],
              'head-final hyphenated compound: final noun head inflected',
            );
          }
          return applyCasePolicy(noun.slice(0, hyphen + 1) + tailPlural, policy);
        }
      }
      const lastHyphen = noun.lastIndexOf('-');
      if (lastHyphen > hyphen && this.#isMultiHeadFinalCompound(noun)) {
        const multiTail = noun.slice(lastHyphen + 1);
        const tailPlural = this.#pluralizeCompoundHead(multiTail, depth, true);
        if (tailPlural !== null) {
          if (sink !== undefined) {
            record(
              sink,
              'COMPOUND',
              'compound:hyphen:multi-head-final',
              'UNKNOWN',
              false,
              false,
              [],
              'multi-hyphen head-final noun without a function word: final head inflected',
            );
          }
          return applyCasePolicy(noun.slice(0, lastHyphen + 1) + tailPlural, policy);
        }
      }
      return null;
    }

    const space = noun.indexOf(' ');
    // Only single-space separators are a supported compound shape. Leading, trailing or
    // repeated whitespace is unsupported input and is returned unchanged.
    if (space < 0 || space === 0 || space === noun.length - 1 || noun.includes('  ')) {
      return null;
    }
    const tokens = noun.split(' ');
    if (tokens.length < 2 || tokens.some((token) => token.length === 0)) {
      return null;
    }
    const lastToken = tokens[tokens.length - 1];
    if (lastToken === undefined) {
      return null;
    }
    const lastLower = lowerCaseOf(lastToken);
    if (isPostposed(lastLower)) {
      const headLower = tokens
        .slice(0, -1)
        .map((token) => lowerCaseOf(token))
        .join(' ');
      if (isCuratedHead(headLower)) {
        const headPlural = this.#resolveHead(noun.slice(0, space), depth);
        if (headPlural !== null) {
          if (sink !== undefined) {
            record(
              sink,
              'COMPOUND',
              `compound:space:${lastLower}`,
              'UNKNOWN',
              false,
              false,
              [],
              `postposed modifier '${lastLower}' after a curated head`,
            );
          }
          return applyCasePolicy(headPlural.text + noun.slice(space), policy);
        }
      }
    }
    if (tokens.some((token) => isPreposition(lowerCaseOf(token)))) {
      return null;
    }
    if (!isPlainLatinToken(lastToken)) {
      return null;
    }
    const lastPlural = this.#pluralizeCompoundHead(lastToken, depth, false);
    if (lastPlural === null) {
      return null;
    }
    if (sink !== undefined) {
      record(
        sink,
        'COMPOUND',
        'compound:space:final',
        'UNKNOWN',
        false,
        false,
        [],
        'space-separated compound: final token inflected',
      );
    }
    const lastStart = noun.length - lastToken.length;
    return applyCasePolicy(noun.slice(0, lastStart) + lastPlural, policy);
  }

  #resolveHead(head: string, depth: number, allowCompoundShape = false): HeadResult | null {
    if (depth >= MAX_COMPOUND_DEPTH || head.length === 0) {
      return null;
    }
    if (!allowCompoundShape && !isPlainLatinToken(head)) {
      return null;
    }
    const headSink = createSink();
    const text = this.#resolve(head, headSink, depth + 1, 'PRESERVE');
    if (headSink.stage === 'FALLBACK') {
      return null;
    }
    return {
      text,
      stage: headSink.stage,
      id: headSink.id,
      invariant: headSink.invariant,
    };
  }

  #pluralizeCompoundHead(head: string, depth: number, allowRespell: boolean): string | null {
    const resolved = this.#resolveHead(head, depth);
    if (resolved === null) {
      return null;
    }
    if (allowRespell && this.#shouldRespell(resolved, head)) {
      return this.#spellHead(head, 'PRESERVE');
    }
    return resolved.text;
  }

  #shouldRespell(resolved: HeadResult, head: string): boolean {
    if (resolved.stage !== 'RULE') {
      return false;
    }
    const lowerCase = lowerCaseOf(head);
    // Without a membership table there is no compaction evidence; retain the documented
    // rules-only spelling heuristic. With a table, a hit means a regular word was omitted.
    return this.#lexicon.isOrdinaryWord(lowerCase) !== true;
  }

  #spellHead(head: string, policy: CasePolicy): string | null {
    if (!isPlainLatinToken(head)) {
      return null;
    }
    const lowerCase = lowerCaseOf(head);
    if (lowerCase.endsWith('child')) {
      return render(head, lowerCase, 5, 'children', policy);
    }
    for (const [singular, plural] of F_TO_VES) {
      if (lowerCase.endsWith(singular)) {
        return render(head, lowerCase, singular.length, plural, policy);
      }
    }
    const rule = applyProductiveRule(head, lowerCase);
    return render(head, lowerCase, rule.cut, rule.append, policy);
  }

  /**
   * Admission policy for the generic head-final hyphenated compound path. Exactly one separator,
   * both tokens plain Latin words, a multi-letter final token, no preposition, and a documented
   * non-noun head exclusion.
   */
  #isHeadFinalCompound(noun: string, hyphen: number, tailLower: string): boolean {
    if (hyphen !== noun.lastIndexOf('-')) {
      return false;
    }
    const head = noun.slice(0, hyphen);
    const tail = noun.slice(hyphen + 1);
    if (!isPlainLatinToken(head) || !isPlainLatinToken(tail)) {
      return false;
    }
    if (codePointLength(head) < 1 || codePointLength(tail) < 2) {
      return false;
    }
    return !(
      isNonNounHead(tailLower) ||
      (isPreposition(lowerCaseOf(head)) && !isCompoundPrefix(lowerCaseOf(head))) ||
      isPreposition(tailLower) ||
      isFreeTail(tailLower)
    );
  }

  /** Multi-hyphen modifier chains ending in a noun head, with no preposition/coordinator. */
  #isMultiHeadFinalCompound(noun: string): boolean {
    const tokens = noun.split('-');
    if (tokens.length < 3) {
      return false;
    }
    for (const token of tokens) {
      if (!isPlainLatinToken(token) || codePointLength(token) < 1) {
        return false;
      }
    }
    for (let index = 0; index < tokens.length - 1; index += 1) {
      const token = tokens[index];
      if (token === undefined) {
        return false;
      }
      const tokenLower = lowerCaseOf(token);
      if (isPreposition(tokenLower) || isCoordinator(tokenLower)) {
        return false;
      }
    }
    const last = tokens[tokens.length - 1];
    if (last === undefined) {
      return false;
    }
    const tailLower = lowerCaseOf(last);
    return !isPreposition(tailLower) && !isNonNounHead(tailLower) && !isCoordinator(tailLower);
  }

  /** A single-hyphen verb-particle noun such as `bake-off` or `blow-in`. */
  #isParticleCompound(noun: string, hyphen: number, tailLower: string): boolean {
    if (hyphen !== noun.lastIndexOf('-') || !isParticle(tailLower)) {
      return false;
    }
    const head = noun.slice(0, hyphen);
    const tail = noun.slice(hyphen + 1);
    return (
      isPlainLatinToken(head) &&
      isPlainLatinToken(tail) &&
      codePointLength(head) >= 1 &&
      codePointLength(tail) >= 2
    );
  }
}

/** Immutable configuration builder. Every setter returns the builder. */
export class Builder {
  #mode: Mode = 'ANGLICIZED';
  #casePolicy: CasePolicy = 'PRESERVE';
  #overrides = new Map<string, string>();
  #initialisms = new Set<string>();
  #uppercasePolicy: UppercasePolicy = 'INFER_INITIALISM';
  #lexicon: LexiconProvider | undefined;

  withMode(mode: Mode): this {
    this.#mode = mode;
    return this;
  }

  withCasePolicy(casePolicy: CasePolicy): this {
    this.#casePolicy = casePolicy;
    return this;
  }

  withUppercasePolicy(uppercasePolicy: UppercasePolicy): this {
    this.#uppercasePolicy = uppercasePolicy;
    return this;
  }

  /** Adds an exact, case-sensitive replacement for the whole input spelling. */
  withOverride(noun: string, plural: string): this {
    if (noun.length === 0) {
      throw new Error('override key must not be empty');
    }
    this.#overrides.set(noun, plural);
    return this;
  }

  withOverrides(replacements: Readonly<Record<string, string>>): this {
    for (const [noun, plural] of Object.entries(replacements)) {
      this.withOverride(noun, plural);
    }
    return this;
  }

  /** Declares a token an initialism: it appends `s`, or `es` after a final s. */
  withInitialism(token: string): this {
    if (token.length === 0) {
      throw new Error('initialism token must not be empty');
    }
    this.#initialisms.add(lowerCaseOf(token));
    return this;
  }

  withInitialisms(tokens: Iterable<string>): this {
    for (const token of tokens) {
      this.withInitialism(token);
    }
    return this;
  }

  /** Replaces the bundled lexicon with caller-supplied or custom data. */
  withLexicon(lexicon: LexiconProvider): this {
    this.#lexicon = lexicon;
    return this;
  }

  /** Builds a rules-only instance (no bundled lexicon). */
  withoutBundledLexicon(): this {
    this.#lexicon = RULES_ONLY_LEXICON;
    return this;
  }

  build(): EnglishInflector {
    return new EnglishInflector({
      mode: this.#mode,
      casePolicy: this.#casePolicy,
      overrides: new Map(this.#overrides),
      initialisms: new Set(this.#initialisms),
      uppercasePolicy: this.#uppercasePolicy,
      lexicon: this.#lexicon ?? defaultLexiconProvider(),
    });
  }
}
