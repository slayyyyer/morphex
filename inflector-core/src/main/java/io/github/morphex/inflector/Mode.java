package io.github.morphex.inflector;

/**
 * Preferred-form mode for nouns that have both an anglicized and a classical plural.
 *
 * <p>The mode changes <em>preferences only</em> for lexical entries that carry an attested classical form
 * (for example {@code index -> indexes} versus {@code index -> indices}). It never invents a classical form
 * from a suffix rule, so an unseen {@code blouse} can never become {@code blice}.
 */
public enum Mode {
    /** Ordinary modern English preference ({@code indexes}, {@code cactuses}). This is the default. */
    ANGLICIZED,
    /** Traditional classical preference where the lexicon attests one ({@code indices}, {@code cacti}). */
    CLASSICAL
}
