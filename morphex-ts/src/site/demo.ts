/**
 * Browser demo entry point: the real inflector, not a re-implementation.
 *
 * The bundled full lexicon is used so `analyze()` can report alternatives, ambiguity,
 * countability and the decision stage exactly as the Java engine does.
 */

import { EnglishInflector } from '../english-inflector.js';
import { BUNDLED_FULL_LEXICON } from '../generated/full-data.js';
import { createFullLexicon } from '../lexicon.js';

export interface DemoAnalysis {
  readonly input: string;
  readonly plural: string;
  readonly stage: string;
  readonly source: string;
  readonly alternatives: readonly string[];
  readonly ambiguous: boolean;
  readonly countability: string;
  readonly invariant: boolean;
  readonly diagnostics: boolean;
  readonly note: string;
}

// The browser demo deliberately loads the full lexicon without the 1.3 MB gzip membership
// sidecar: analyze() diagnostics stay complete, and only unknown-uppercase initialism inference
// falls back to the rules-only behaviour. The demo page states this explicitly.
const fullLexicon = createFullLexicon(BUNDLED_FULL_LEXICON);
const bundled = EnglishInflector.builder().withLexicon(fullLexicon).build();

export const demoInfo = Object.freeze({
  bundledEntries: fullLexicon.size,
});

export function analyzeWord(input: string): DemoAnalysis | null {
  const word = input.trim();
  if (word.length === 0) {
    return null;
  }
  const result = bundled.analyze(word);
  return {
    input: word,
    plural: result.plural,
    stage: result.stage,
    source: result.source,
    alternatives: result.alternatives,
    ambiguous: result.ambiguous,
    countability: result.countability,
    invariant: result.invariant,
    diagnostics: result.diagnostics,
    note: result.note,
  };
}

export function pluralizeWord(input: string, count?: number): string {
  return count === undefined ? bundled.pluralize(input) : bundled.pluralize(input, count);
}
