package io.github.slayyyyer.morphex.inflector;

/** Countability metadata for a noun lemma. */
public enum Countability {
    /** Ordinary countable noun ({@code cat -> cats}). */
    COUNTABLE,
    /** Mass-only noun; returned unchanged ({@code information}). */
    MASS,
    /** Noun with both mass and countable senses; pluralized normally ({@code water -> waters}). */
    MIXED,
    /** Plural-only lemma; returned unchanged ({@code scissors}). */
    PLURAL_ONLY,
    /** No countability metadata was available for the token. */
    UNKNOWN
}
