/**
 * Default bundled lexicon. The generated module contains the delta artifact: only the entries
 * whose true answer differs from the three productive rules, plus entries that carry metadata
 * which changes a decision. `pluralize()` needs nothing else.
 *
 * The `morphex/rules` build replaces this module with an empty artifact, so that entry ships no
 * lexicon data at all.
 */

import { BUNDLED_DELTA_LEXICON } from './generated/delta-data.js';
import { createDeltaLexicon } from './lexicon.js';
import type { LexiconProvider } from './types.js';

let bundled: LexiconProvider | undefined;

export function defaultLexiconProvider(): LexiconProvider {
  bundled ??= createDeltaLexicon(BUNDLED_DELTA_LEXICON);
  return bundled;
}
