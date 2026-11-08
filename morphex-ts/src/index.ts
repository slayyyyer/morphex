/**
 * morphex: deterministic English noun pluralization.
 *
 * ```ts
 * import { EnglishInflector } from 'morphex';
 * EnglishInflector.standard().pluralize('mother-in-law'); // 'mothers-in-law'
 * ```
 */

export { EnglishInflector, Builder } from './english-inflector.js';
export {
  createDeltaLexicon,
  createFullLexicon,
  createLexicon,
  createMembershipTable,
  decodeBase64,
  deriveEdit,
  fnv1aLow32,
  parseFullLexicon,
  RULES_ONLY_LEXICON,
} from './lexicon.js';
export { defaultLexiconProvider } from './defaults.js';
export { COMPOUND_EXACT, validateCompoundTable } from './compound-table.js';
export { COUNTABILITIES, INFLECTION_SOURCES } from './types.js';
export type {
  CasePolicy,
  Countability,
  Inflection,
  InflectionSource,
  LexiconEntry,
  LexiconEntryInput,
  LexiconProvider,
  MembershipTable,
  Mode,
  UppercasePolicy,
} from './types.js';
