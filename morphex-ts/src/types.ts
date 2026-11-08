/**
 * Public type surface for morphex.
 *
 * The shapes mirror the Java library's `Inflection`, `DecisionSource`, `Countability`,
 * `CasePolicy`, `Mode` and `UppercasePolicy`, adapted to TypeScript idioms.
 */

/** Stable decision stages, in precedence order. */
export const INFLECTION_SOURCES = [
  'OVERRIDE',
  'LEXICON',
  'INFERRED',
  'COMPOUND',
  'RULE',
  'FALLBACK',
] as const;

/** Where the reported plural came from. Ordered by precedence. */
export type InflectionSource = (typeof INFLECTION_SOURCES)[number];

/** Countability metadata for a noun lemma. */
export const COUNTABILITIES = ['COUNTABLE', 'MASS', 'MIXED', 'PLURAL_ONLY', 'UNKNOWN'] as const;

export type Countability = (typeof COUNTABILITIES)[number];

/** Preferred-form mode for lemmas with both an anglicized and a classical plural. */
export type Mode = 'ANGLICIZED' | 'CLASSICAL';

/** Output casing policy. */
export type CasePolicy = 'PRESERVE' | 'LOWERCASE' | 'UPPERCASE';

/** How an unknown all-uppercase token is interpreted. */
export type UppercasePolicy = 'INFER_INITIALISM' | 'LITERAL';

/**
 * Diagnostic result of `analyze()`.
 *
 * `stage` is the decision stage (the Java `DecisionSource`); `source` is the stable identifier
 * for the entry or rule that won (the Java `Inflection.id()`), e.g. `lex:child` or
 * `rule:default-s`. Every field is always present: a build without full lexical diagnostics
 * reports `diagnostics: false` and leaves `countability` at `UNKNOWN` rather than `undefined`.
 */
export interface Inflection {
  /** The caller's exact input spelling. */
  readonly input: string;
  /** The selected plural. */
  readonly plural: string;
  /** Other accepted plural forms known for this lemma, in artifact order. */
  readonly alternatives: readonly string[];
  /**
   * True when the spelling alone cannot select one form, for example `die` (game piece versus
   * stamping tool). The selected plural is still returned.
   */
  readonly ambiguous: boolean;
  /** Decision stage that produced the plural. */
  readonly stage: InflectionSource;
  /** Stable identifier of the winning entry or rule. */
  readonly source: string;
  readonly countability: Countability;
  /** True when singular and plural are identical (`sheep`). */
  readonly invariant: boolean;
  /** Human-readable explanation of the decision. Empty when there is none. */
  readonly note: string;
  /**
   * True when the backing lexicon supplies complete diagnostics (a full artifact or a
   * caller-supplied lexicon). False for the bundled delta lexicon and rules-only builds, where
   * metadata for lemmas outside the shipped delta may be absent.
   */
  readonly diagnostics: boolean;
  /** True when `form` is the selected plural or one of the accepted alternatives. */
  isAccepted(form: string): boolean;
}

/** A single lexical entry as seen by the engine. */
export interface LexiconEntry {
  /** The artifact's exact lemma spelling (case-sensitive entries rely on this). */
  readonly lemma: string;
  /** The lower-cased lookup key. */
  readonly key: string;
  /** The lower-cased preferred plural. */
  readonly plural: string;
  /** An attested classical plural, or `null` when the entry has none. */
  readonly classical: string | null;
  /** Other accepted plural forms, in artifact order. */
  readonly alternatives: readonly string[];
  readonly countability: Countability;
  readonly invariant: boolean;
  readonly initialism: boolean;
  readonly caseSensitive: boolean;
  readonly ambiguous: boolean;
}

/** Caller-supplied lexicon entry; missing fields take the documented defaults. */
export interface LexiconEntryInput {
  readonly lemma: string;
  readonly plural: string;
  readonly classical?: string | null;
  readonly alternatives?: readonly string[];
  readonly countability?: Countability;
  readonly invariant?: boolean;
  readonly initialism?: boolean;
  readonly caseSensitive?: boolean;
  readonly ambiguous?: boolean;
}

/**
 * A pluggable lexicon. The bundled delta artifact, the opt-in full artifact, rules-only mode and
 * caller-supplied data all implement this interface.
 */
export interface LexiconProvider {
  readonly kind: 'rules-only' | 'delta' | 'full' | 'custom';
  /** Number of entries visible to the engine. */
  readonly size: number;
  /** True when entries carry complete diagnostics (alternatives, countability, notes). */
  readonly diagnostics: boolean;
  /** Returns the entry for a lower-cased key, or `undefined`. */
  lookup(lower: string): LexiconEntry | undefined;
  /**
   * Membership evidence for ordinary-word checks. `undefined` means the provider has no
   * membership table, which disables uppercase-initialism inference exactly as the Java
   * rules-only instance does.
   */
  isOrdinaryWord(lower: string): boolean | undefined;
}

/** A membership table for lower-cased lemmas, independent of the exception artifact. */
export interface MembershipTable {
  readonly size: number;
  contains(lower: string): boolean;
}
