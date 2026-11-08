/**
 * Opt-in full lexicon: every artifact entry with alternatives, classical forms, countability and
 * ambiguity metadata, plus the ordinary-word membership table used by uppercase-initialism
 * inference. Load this when `analyze()` must match the Java engine for every lemma.
 */

import { BUNDLED_FULL_LEXICON } from './generated/full-data.js';
import { BUNDLED_MEMBERSHIP } from './generated/membership-data.js';
import { createFullLexicon, createMembershipTable } from './lexicon.js';
import type { LexiconProvider } from './types.js';

let bundled: LexiconProvider | undefined;

export function createBundledFullLexicon(): LexiconProvider {
  bundled ??= createFullLexicon(BUNDLED_FULL_LEXICON, createMembershipTable(BUNDLED_MEMBERSHIP));
  return bundled;
}
