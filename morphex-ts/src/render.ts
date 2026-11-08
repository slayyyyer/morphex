/**
 * Rendering helpers, ported from the Java `EnglishInflector.render*` family.
 *
 * A rule or lexical entry produces a `(cut, append)` end-of-token edit. Rendering applies that
 * edit and then restores the caller's casing shape exactly once.
 */

import type { CasePolicy } from './types.js';
import { capitalize, casingOf } from './word-shape.js';

/** Replaces the last `cut` characters with `append`, clamping an over-long cut. */
export function edit(token: string, cut: number, append: string): string {
  const end = Math.max(0, token.length - cut);
  if (append.length === 0) {
    return end === token.length ? token : token.slice(0, end);
  }
  if (end === 0) {
    return append;
  }
  return token.slice(0, end) + append;
}

export function render(
  source: string,
  lowerCase: string,
  cut: number,
  append: string,
  policy: CasePolicy,
): string {
  if (policy === 'LOWERCASE') {
    return edit(lowerCase, cut, append);
  }
  if (policy === 'UPPERCASE') {
    return edit(source, cut, append).toUpperCase();
  }
  const casing = casingOf(source);
  if (casing === 'LOWER') {
    return edit(lowerCase, cut, append);
  }
  if (source.length !== lowerCase.length) {
    // Case mapping changed the length (rare, e.g. Turkish dotted I): render lower and re-case.
    const base = edit(lowerCase, cut, append);
    if (casing === 'UPPER') {
      return base.toUpperCase();
    }
    if (casing === 'CAPITALIZED') {
      return capitalize(base);
    }
    return base;
  }
  const edited = edit(source, cut, append);
  return casing === 'UPPER' ? edited.toUpperCase() : edited;
}

/** Renders a complete lower-case form (lexical alternative, compound) in the caller's shape. */
export function renderFull(
  source: string,
  lowerCase: string,
  fullLower: string,
  policy: CasePolicy,
): string {
  if (policy === 'LOWERCASE') {
    return fullLower;
  }
  if (policy === 'UPPERCASE') {
    return fullLower.toUpperCase();
  }
  switch (casingOf(source)) {
    case 'LOWER':
      return fullLower;
    case 'UPPER':
      return fullLower.toUpperCase();
    case 'CAPITALIZED':
      return capitalize(fullLower);
    case 'MIXED':
      return fullLower.startsWith(lowerCase)
        ? source + fullLower.slice(lowerCase.length)
        : fullLower;
    default:
      return fullLower;
  }
}

/** Appends an initialism suffix without whole-token re-casing. */
export function renderInitialism(
  source: string,
  lowerCase: string,
  suffix: string,
  policy: CasePolicy,
): string {
  if (policy === 'LOWERCASE') {
    return lowerCase + suffix;
  }
  if (policy === 'UPPERCASE') {
    return source.toUpperCase() + suffix.toUpperCase();
  }
  return source + suffix;
}

/** Applies one configured case policy to a fully reconstructed compound. */
export function applyCasePolicy(value: string, policy: CasePolicy): string {
  if (policy === 'LOWERCASE') {
    return value.toLowerCase();
  }
  if (policy === 'UPPERCASE') {
    return value.toUpperCase();
  }
  return value;
}
