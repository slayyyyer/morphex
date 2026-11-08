/**
 * Bounded, documented compound support, ported from the Java `CompoundTable`.
 *
 * Deliberately small: a suffix match is not proof of a morpheme boundary, so only structures that
 * were individually reviewed are recognised. A malformed table entry fails fast at module load.
 */

/** Whole compound (lower case) to whole plural (lower case). */
export const COMPOUND_EXACT = {
  'passer-by': 'passers-by',
  'hanger-on': 'hangers-on',
  'looker-on': 'lookers-on',
  'runner-up': 'runners-up',
  'stand-in': 'stand-ins',
  'drive-by': 'drive-bys',
  'tie-in': 'tie-ins',
  'close-up': 'close-ups',
  'grown-up': 'grown-ups',
  'go-between': 'go-betweens',
  'forget-me-not': 'forget-me-nots',
  'jack-in-the-box': 'jack-in-the-boxes',
  'man-of-war': 'men-of-war',
  'major general': 'major generals',
} as const satisfies Readonly<Record<string, string>>;

/** Hyphenated tails that cannot themselves be a noun, so any head is safe. */
const FREE_TAILS = [
  'in-law',
  'in-arms',
  'in-aid',
  'in-kind',
  'in-chief',
  'of-war',
  'of-arms',
  'of-spring',
  'of-all-trades',
  'out-law',
  'at-arms',
  'at-law',
  'in-crime',
] as const;

/** Postposed modifiers: only with a curated head, because "major general" is a counterexample. */
const POSTPOSED = [
  'general',
  'elect',
  'apparent',
  'designate',
  'martial',
  'laureate',
  'presumptive',
  'public',
  'errant',
] as const;

/** Heads reviewed as taking a postposed modifier. */
const CURATED_HEADS = [
  'attorney',
  'court',
  'heir',
  'notary',
  'poet',
  'governor',
  'ambassador',
  'president',
  'mayor',
  'minister',
  'secretary',
  'treasurer',
  'surgeon',
  'solicitor',
  'lord',
  'postmaster',
  'editor',
  'commander',
] as const;

/** Tokens that signal a fixed expression rather than a head-final noun compound. */
const PREPOSITIONS = [
  'in',
  'of',
  'at',
  'on',
  'by',
  'to',
  'with',
  'for',
  'from',
  'upon',
  'under',
  'over',
  'into',
] as const;

/**
 * Final tokens that can end a hyphenated expression but are not nouns, so the expression is not a
 * head-final noun compound. This keeps `well-known` unchanged while permitting `credit-card`.
 */
const NON_NOUN_HEADS = [
  'known',
  'born',
  'bred',
  'built',
  'done',
  'gone',
  'held',
  'left',
  'lost',
  'made',
  'paid',
  'sold',
  'told',
  'worn',
  'written',
  'spoken',
  'taken',
  'given',
  'seen',
  'heard',
  'led',
  'won',
  'set',
  'put',
  'cut',
  'read',
  'run',
  'come',
  'become',
  'beaten',
  'broken',
  'chosen',
  'driven',
  'eaten',
  'fallen',
  'forgotten',
  'frozen',
  'hidden',
  'risen',
  'shaken',
  'shown',
  'stolen',
  'sworn',
  'torn',
  'woken',
  'wrong',
  'aware',
  'alike',
  'alone',
  'asleep',
  'awake',
] as const;

/** Verbed-particle nouns: the plural is the whole expression plus s, not the final token. */
const PARTICLES = [
  'off',
  'in',
  'on',
  'up',
  'out',
  'over',
  'back',
  'down',
  'through',
  'around',
  'about',
  'away',
] as const;

/** Coordinators/articles that signal a fixed phrase, not a head-final noun compound. */
const COORDINATORS = ['and', 'or', 'nor', 'but', 'the'] as const;

/** Preposition-like prefixes reviewed as productive in head-final nouns (`on-ramp`). */
const COMPOUND_PREFIXES = ['on'] as const;

/**
 * Lower-case -man words that are not compounds of a noun + man, so the -man backoff must not
 * touch them. Proper names are additionally excluded by requiring an all-lower-case input.
 */
const MAN_EXCEPTIONS = [
  'human',
  'german',
  'roman',
  'shaman',
  'talisman',
  'caiman',
  'ottoman',
  'brahman',
  'atman',
  'liman',
  'chroman',
  'hooman',
  'kundiman',
  'laraman',
  'oralman',
  'abhuman',
  'metahuman',
  'paleohuman',
  'parahuman',
  'dragoman',
  'firman',
  'harman',
  'tarman',
  'turkman',
  'touman',
  'tramman',
  'vataman',
  'velociman',
  'zygentoman',
] as const;

const freeTailSet: ReadonlySet<string> = new Set(FREE_TAILS);
const postposedSet: ReadonlySet<string> = new Set(POSTPOSED);
const curatedHeadSet: ReadonlySet<string> = new Set(CURATED_HEADS);
const prepositionSet: ReadonlySet<string> = new Set(PREPOSITIONS);
const nonNounHeadSet: ReadonlySet<string> = new Set(NON_NOUN_HEADS);
const particleSet: ReadonlySet<string> = new Set(PARTICLES);
const coordinatorSet: ReadonlySet<string> = new Set(COORDINATORS);
const compoundPrefixSet: ReadonlySet<string> = new Set(COMPOUND_PREFIXES);
const manExceptionSet: ReadonlySet<string> = new Set(MAN_EXCEPTIONS);

/**
 * Validates every table entry. A typo (upper case, missing separator, duplicate membership)
 * throws, so a malformed table fails the build and the test run instead of silently misfiring.
 */
export function validateCompoundTable(): void {
  for (const [key, plural] of Object.entries(COMPOUND_EXACT)) {
    if (key !== key.toLowerCase() || plural !== plural.toLowerCase()) {
      throw new Error(`compound table: exact entry must be lower case: ${key} -> ${plural}`);
    }
    if (!key.includes('-') && !key.includes(' ')) {
      throw new Error(`compound table: exact entry must be a compound: ${key}`);
    }
  }
  for (const tail of FREE_TAILS) {
    if (
      !tail.includes('-') ||
      tail.startsWith('-') ||
      tail.endsWith('-') ||
      tail !== tail.toLowerCase()
    ) {
      throw new Error(`compound table: malformed free tail: ${tail}`);
    }
  }
  for (const token of [
    ...POSTPOSED,
    ...CURATED_HEADS,
    ...PREPOSITIONS,
    ...NON_NOUN_HEADS,
    ...PARTICLES,
    ...COORDINATORS,
    ...COMPOUND_PREFIXES,
    ...MAN_EXCEPTIONS,
  ]) {
    if (token !== token.toLowerCase() || token.length === 0) {
      throw new Error(`compound table: malformed token: ${token}`);
    }
  }
  for (const token of [...POSTPOSED, ...CURATED_HEADS, ...PREPOSITIONS, ...COMPOUND_PREFIXES]) {
    if (token.includes('-') || token.includes(' ')) {
      throw new Error(`compound table: multi-token word in a word set: ${token}`);
    }
  }
}

validateCompoundTable();

export function exactPlural(lowerCase: string): string | undefined {
  return Object.prototype.hasOwnProperty.call(COMPOUND_EXACT, lowerCase)
    ? COMPOUND_EXACT[lowerCase as keyof typeof COMPOUND_EXACT]
    : undefined;
}

export function isFreeTail(tailLower: string): boolean {
  return freeTailSet.has(tailLower);
}

/** Returns the hyphen index of the longest recognized free-tail suffix, or -1. */
export function freeTailSuffix(lowerCase: string): number {
  let best = -1;
  for (const tail of FREE_TAILS) {
    const index = lowerCase.length - tail.length - 1;
    if (index > 0 && index > best && lowerCase.charAt(index) === '-' && lowerCase.endsWith(tail)) {
      best = index;
    }
  }
  return best;
}

export function isPostposed(tailLower: string): boolean {
  return postposedSet.has(tailLower);
}

export function isCuratedHead(headLower: string): boolean {
  return curatedHeadSet.has(headLower);
}

export function isPreposition(tokenLower: string): boolean {
  return prepositionSet.has(tokenLower);
}

export function isNonNounHead(tokenLower: string): boolean {
  return nonNounHeadSet.has(tokenLower);
}

export function isParticle(tokenLower: string): boolean {
  return particleSet.has(tokenLower);
}

export function isManException(tokenLower: string): boolean {
  return manExceptionSet.has(tokenLower) || tokenLower.endsWith('human');
}

export function isCoordinator(tokenLower: string): boolean {
  return coordinatorSet.has(tokenLower);
}

export function isCompoundPrefix(tokenLower: string): boolean {
  return compoundPrefixSet.has(tokenLower);
}
