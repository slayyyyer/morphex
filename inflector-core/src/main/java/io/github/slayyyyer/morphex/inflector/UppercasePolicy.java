package io.github.slayyyyer.morphex.inflector;

/**
 * How an <em>unknown</em> all-uppercase token is interpreted.
 *
 * <p>Explicit caller overrides, lexical entries (including the {@code N} initialism flag) and tokens declared
 * with {@link EnglishInflector.Builder#initialism(String)} always win. This policy only applies after those
 * sources have been exhausted, to a token of two to six upper-case Latin letters with no lexical entry.
 *
 * <p>{@link #INFER_INITIALISM} treats such a token as an initialism and appends a lower-case {@code s}
 * ({@code API -> APIs}, {@code UFO -> UFOs}, {@code SMS -> SMSs}). It requires a non-empty lexicon so that
 * ordinary-word membership can be checked first: if the lower-cased token is a lexical entry, the ordinary
 * upper-case rendering is used instead ({@code CAT -> CATS}, and {@code German -> GERMANS} when the
 * case-sensitive entry does not match). A rules-only instance has no membership table, so it keeps the
 * literal spelling path ({@code API -> APIS}, {@code CAT -> CATS}).
 *
 * <p>{@link #LITERAL} disables inference entirely and preserves the pre-1.1 spelling path for callers that
 * prefer it. It is an opt-out, not the default; P2 results are reported for the default configuration.
 */
public enum UppercasePolicy {
    /** Interpret an unknown 2-6 letter all-uppercase token as an initialism after lexical membership. */
    INFER_INITIALISM,
    /** Never infer initialism status; apply the ordinary productive spelling rules. */
    LITERAL
}
