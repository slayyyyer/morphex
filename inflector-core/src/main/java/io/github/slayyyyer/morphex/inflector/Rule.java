package io.github.slayyyyer.morphex.inflector;

import java.util.function.Predicate;

/**
 * A productive spelling rule: a suffix condition plus a fixed edit at the end of the token.
 *
 * <p>Rules are separate from the lexicon by construction. A rule may only describe a general spelling
 * pattern; word-specific knowledge belongs in a {@link Lexicon.Entry}. That is what prevents the upstream
 * failure mode where a lexical exception such as {@code carp} or {@code louse} changes {@code scarp} or
 * {@code blouse}.
 */
final class Rule {

    private final String id;
    private final String description;
    private final int cut;
    private final String append;
    private final Predicate<String> matcher;
    private final boolean lowercaseOnly;

    Rule(String id, String description, int cut, String append, Predicate<String> matcher) {
        this(id, description, cut, append, matcher, false);
    }

    Rule(String id, String description, int cut, String append, Predicate<String> matcher,
            boolean lowercaseOnly) {
        this.id = id;
        this.description = description;
        this.cut = cut;
        this.append = append;
        this.matcher = matcher;
        this.lowercaseOnly = lowercaseOnly;
    }

    String id() {
        return id;
    }

    String description() {
        return description;
    }

    int cut() {
        return cut;
    }

    String append() {
        return append;
    }

    /** Returns true when this rule applies to the lower-cased token. */
    boolean applies(String lower) {
        return matcher.test(lower);
    }

    /** True when the rule must not change a capitalized proper-name spelling such as {@code Cartman}. */
    boolean lowercaseOnly() {
        return lowercaseOnly;
    }
}
