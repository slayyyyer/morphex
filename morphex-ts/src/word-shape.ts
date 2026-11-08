/**
 * Unicode-aware word-shape helpers, ported from the Java `WordShape`.
 *
 * Lookup normalisation is deliberately separate from output case restoration: `lower()` is used
 * for case-insensitive keys, and `casingOf()`/`capitalize()` are only used when rendering a
 * result in the caller's shape.
 */

export type Casing = 'LOWER' | 'UPPER' | 'CAPITALIZED' | 'MIXED';

const LETTER = /^\p{L}$/u;
const LATIN_SCRIPT = /^\p{Script=Latin}$/u;
const NON_SPACING_MARK = /^\p{Mn}$/u;

function isAsciiLetterCodePoint(codePoint: number): boolean {
  return (codePoint >= 0x41 && codePoint <= 0x5a) || (codePoint >= 0x61 && codePoint <= 0x7a);
}

/** True when the single code point is an upper-case letter. */
export function isUpperCaseCodePoint(character: string): boolean {
  const upper = character.toUpperCase();
  return upper === character && character.toLowerCase() !== character;
}

/** Returns the same string when the token has no upper-case code point. */
export function lower(token: string): string {
  return hasUpper(token) ? token.toLowerCase() : token;
}

export function hasUpper(token: string): boolean {
  for (const character of token) {
    if (character.length === 1) {
      const codePoint = character.charCodeAt(0);
      if (codePoint >= 0x41 && codePoint <= 0x5a) {
        return true;
      }
      if (codePoint >= 0x80 && isUpperCaseCodePoint(character)) {
        return true;
      }
    } else if (isUpperCaseCodePoint(character)) {
      return true;
    }
  }
  return false;
}

export function casingOf(token: string): Casing {
  let anyUpper = false;
  let anyLower = false;
  let firstLetterIndex = -1;
  let firstCodePoint = '';

  let index = 0;
  for (const character of token) {
    const codePoint = character.codePointAt(0) ?? 0;
    if (codePoint < 0x80) {
      if (codePoint >= 0x41 && codePoint <= 0x5a) {
        if (firstLetterIndex < 0) {
          firstLetterIndex = index;
          firstCodePoint = character;
        }
        anyUpper = true;
      } else if (codePoint >= 0x61 && codePoint <= 0x7a) {
        if (firstLetterIndex < 0) {
          firstLetterIndex = index;
          firstCodePoint = character;
        }
        anyLower = true;
      }
    } else if (LETTER.test(character)) {
      if (firstLetterIndex < 0) {
        firstLetterIndex = index;
        firstCodePoint = character;
      }
      if (isUpperCaseCodePoint(character)) {
        anyUpper = true;
      } else {
        anyLower = true;
      }
    }
    index += character.length;
  }

  if (!anyUpper) {
    return 'LOWER';
  }
  if (!anyLower) {
    return 'UPPER';
  }
  if (firstLetterIndex === 0 && isUpperCaseCodePoint(firstCodePoint)) {
    return 'CAPITALIZED';
  }
  return 'MIXED';
}

/**
 * True when the token is a single word made only of Latin letters (plus combining marks).
 * Hyphens, spaces, digits, apostrophes and other symbols are not plain tokens.
 */
export function isPlainLatinToken(token: string): boolean {
  if (token.length === 0) {
    return false;
  }
  let sawLetter = false;
  for (const character of token) {
    if (character.length === 1) {
      const codePoint = character.charCodeAt(0);
      if (codePoint < 0x80) {
        if (!isAsciiLetterCodePoint(codePoint)) {
          return false;
        }
        sawLetter = true;
        continue;
      }
    }
    if (LETTER.test(character) && LATIN_SCRIPT.test(character)) {
      sawLetter = true;
    } else if (!NON_SPACING_MARK.test(character)) {
      return false;
    }
  }
  return sawLetter;
}

/**
 * True when every letter is an upper-case Latin letter, there are at least two letters, and
 * there is no lower-case letter, digit or symbol.
 */
export function isAllUpperLatinToken(token: string): boolean {
  let letters = 0;
  for (const character of token) {
    const codePoint = character.codePointAt(0) ?? 0;
    if (codePoint < 0x80) {
      if (codePoint < 0x41 || codePoint > 0x5a) {
        return false;
      }
    } else if (!LETTER.test(character) || !LATIN_SCRIPT.test(character)) {
      return false;
    } else if (!isUpperCaseCodePoint(character)) {
      return false;
    }
    letters += 1;
  }
  return letters >= 2;
}

export function codePointLength(token: string): number {
  let length = 0;
  for (let index = 0; index < token.length; index += 1) {
    if ((token.codePointAt(index) ?? 0) > 0xffff) {
      index += 1;
    }
    length += 1;
  }
  return length;
}

export function endsSibilant(lowerCase: string): boolean {
  const length = lowerCase.length;
  if (length === 0) {
    return false;
  }
  const last = lowerCase.charAt(length - 1);
  if (last === 's' || last === 'x' || last === 'z') {
    return true;
  }
  if (length >= 2) {
    const previous = lowerCase.charAt(length - 2);
    if ((previous === 'c' || previous === 's') && last === 'h') {
      return true;
    }
  }
  return false;
}

export function endsConsonantY(lowerCase: string): boolean {
  const length = lowerCase.length;
  if (length < 2 || lowerCase.charAt(length - 1) !== 'y') {
    return false;
  }
  const before = lowerCase.charAt(length - 2);
  if (isAsciiVowel(before)) {
    // "soliloquy" -> "soliloquies": the u after q is a glide, not a vowel.
    return before === 'u' && length >= 3 && lowerCase.charAt(length - 3) === 'q';
  }
  return true;
}

function isAsciiVowel(character: string): boolean {
  return (
    character === 'a' ||
    character === 'e' ||
    character === 'i' ||
    character === 'o' ||
    character === 'u'
  );
}

export function capitalize(token: string): string {
  if (token.length === 0) {
    return token;
  }
  const first = token.codePointAt(0);
  if (first === undefined) {
    return token;
  }
  const character = String.fromCodePoint(first);
  const upper = character.toUpperCase();
  if (upper === character) {
    return token;
  }
  return upper + token.slice(character.length);
}
