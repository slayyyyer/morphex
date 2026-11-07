package io.github.morphex.inflector;

import java.util.Locale;

/**
 * Unicode-aware word shape helpers.
 *
 * <p>Lookup normalisation (lower-casing) is deliberately separated from output case restoration. Lookup
 * normalisation is used for case-insensitive map keys; restoration is applied to the rendered result and is
 * the only place that may change the vendor case of the caller's string.
 */
final class WordShape {

    enum Casing {
        LOWER,
        UPPER,
        CAPITALIZED,
        MIXED
    }

    private WordShape() {
    }

    /** Returns the same instance when the token has no upper-case code point. */
    static String lower(String token) {
        if (!hasUpper(token)) {
            return token;
        }
        return token.toLowerCase(Locale.ROOT);
    }

    static boolean hasUpper(String token) {
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            if (Character.isUpperCase(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    static Casing casingOf(String token) {
        boolean anyUpper = false;
        boolean anyLower = false;
        int firstLetter = -1;
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            if (Character.isLetter(cp)) {
                if (firstLetter < 0) {
                    firstLetter = i;
                }
                if (Character.isUpperCase(cp)) {
                    anyUpper = true;
                } else {
                    anyLower = true;
                }
            }
            i += Character.charCount(cp);
        }
        if (!anyUpper) {
            return Casing.LOWER;
        }
        if (!anyLower) {
            return Casing.UPPER;
        }
        if (firstLetter == 0 && Character.isUpperCase(token.codePointAt(0))) {
            return Casing.CAPITALIZED;
        }
        return Casing.MIXED;
    }

    /**
     * True when the token is a single word made only of Latin letters (plus combining marks). Hyphens,
     * spaces, digits, apostrophes and other symbols are not plain tokens: they belong to compound handling or
     * to the documented fallback.
     */
    static boolean isPlainLatinToken(String token) {
        if (token.isEmpty()) {
            return false;
        }
        boolean sawLetter = false;
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            if (Character.isLetter(cp)) {
                if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN) {
                    return false;
                }
                sawLetter = true;
            } else if (Character.getType(cp) != Character.NON_SPACING_MARK) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return sawLetter;
    }

    static boolean endsSibilant(String lower) {
        int n = lower.length();
        if (n == 0) {
            return false;
        }
        char last = lower.charAt(n - 1);
        if (last == 's' || last == 'x' || last == 'z') {
            return true;
        }
        if (n >= 2) {
            char prev = lower.charAt(n - 2);
            if ((prev == 'c' || prev == 's') && last == 'h') {
                return true;
            }
        }
        return false;
    }

    static boolean endsConsonantY(String lower) {
        int n = lower.length();
        if (n < 2 || lower.charAt(n - 1) != 'y') {
            return false;
        }
        char before = lower.charAt(n - 2);
        if (isAsciiVowel(before)) {
            // "soliloquy" -> "soliloquies": the u after q is a glide, not a vowel.
            return before == 'u' && n >= 3 && lower.charAt(n - 3) == 'q';
        }
        return true;
    }

    private static boolean isAsciiVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    static String capitalize(String token) {
        if (token.isEmpty()) {
            return token;
        }
        int first = token.codePointAt(0);
        int upper = Character.toUpperCase(first);
        if (upper == first) {
            return token;
        }
        return new StringBuilder(token.length())
                .appendCodePoint(upper)
                .append(token, Character.charCount(first), token.length())
                .toString();
    }
}
