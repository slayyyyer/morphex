/**
 * The three productive rules plus the four documented backoffs, ported from the Java
 * `EnglishInflector.applyRule`. This is a direct ordered branch chain: the first match wins.
 */

import { isManException } from './compound-table.js';
import { render } from './render.js';
import { endsConsonantY, endsSibilant, lower as lowerCaseOf } from './word-shape.js';

export type RuleId =
  | 'backoff:woman->women'
  | 'backoff:wife->wives'
  | 'backoff:man->men'
  | 'backoff:sis->ses'
  | 'rule:sibilant-es'
  | 'rule:consonant-y-ies'
  | 'rule:default-s';

export interface RuleApplication {
  readonly id: RuleId;
  readonly cut: number;
  readonly append: string;
  readonly description: string;
}

/** Applies the ordered rule chain to a token whose lower-cased form is already known. */
export function applyProductiveRule(noun: string, lowerCase: string): RuleApplication {
  if (lowerCase.endsWith('woman')) {
    return {
      id: 'backoff:woman->women',
      cut: 5,
      append: 'women',
      description: 'documented -woman -> -women backoff',
    };
  }
  if (lowerCase.endsWith('wife')) {
    return {
      id: 'backoff:wife->wives',
      cut: 4,
      append: 'wives',
      description: 'documented -wife -> -wives backoff',
    };
  }
  if (
    lowerCase.length >= 6 &&
    lowerCase.endsWith('man') &&
    !lowerCase.endsWith('woman') &&
    noun === lowerCase &&
    !isManException(lowerCase)
  ) {
    return {
      id: 'backoff:man->men',
      cut: 3,
      append: 'men',
      description: 'documented noun + man -> men backoff',
    };
  }
  if (lowerCase.length >= 5 && lowerCase.endsWith('sis')) {
    return {
      id: 'backoff:sis->ses',
      cut: 2,
      append: 'es',
      description: 'documented Greek -sis -> -ses backoff',
    };
  }
  if (endsSibilant(lowerCase)) {
    return {
      id: 'rule:sibilant-es',
      cut: 0,
      append: 'es',
      description: 'suffix s/x/z/ch/sh appends es',
    };
  }
  if (endsConsonantY(lowerCase)) {
    return {
      id: 'rule:consonant-y-ies',
      cut: 1,
      append: 'ies',
      description: 'consonant before y becomes ies',
    };
  }
  return {
    id: 'rule:default-s',
    cut: 0,
    append: 's',
    description: 'default appends s',
  };
}

/** The plural a rules-only engine produces for a token, in the caller's casing shape. */
export function rulesPlural(noun: string): string {
  const lowerCase = lowerCaseOf(noun);
  const rule = applyProductiveRule(noun, lowerCase);
  return render(noun, lowerCase, rule.cut, rule.append, 'PRESERVE');
}
