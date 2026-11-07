package io.github.morphex.inflector;

/**
 * Output casing policy.
 *
 * <p>Casing is applied after the lexical or rule decision, so it can never make a lexicon entry unreachable:
 * {@code CHILD} resolves the ordinary entry {@code child} and then re-cases the result to {@code CHILDREN}.
 */
public enum CasePolicy {
    /**
     * Infer the shape of the input and reproduce it: all lower stays lower, Capitalized stays capitalized,
     * ALL UPPER stays upper, and mixed case keeps its internal capitals ({@code iPhone -> iPhones}).
     */
    PRESERVE,
    /** Force the plural to lower case. Fallback results are still returned exactly as supplied. */
    LOWERCASE,
    /** Force the plural to upper case. Fallback results are still returned exactly as supplied. */
    UPPERCASE
}
