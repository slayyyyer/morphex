/**
 * The complete-package entry: the main API plus the opt-in full lexicon.
 *
 * ```ts
 * import { EnglishInflector, createBundledFullLexicon } from 'morphex/full';
 * const inflector = EnglishInflector.builder()
 *   .withLexicon(createBundledFullLexicon())
 *   .build();
 * inflector.analyze('die').ambiguous; // true
 * ```
 */

export * from './index.js';
export { createBundledFullLexicon } from './lexicon-full.js';
