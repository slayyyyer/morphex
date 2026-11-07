package io.github.slayyyyer.morphex.inflector;

/** Where the reported plural came from. Ordered by precedence. */
public enum DecisionSource {
    /** Caller-supplied override; returned verbatim. */
    OVERRIDE,
    /** Exact lexical entry, including countability-only entries. */
    LEXICON,
    /** Supported compound transformation (exact compound entry or a documented compound pattern). */
    COMPOUND,
    /** Productive spelling rule. */
    RULE,
    /** Documented unresolved-input fallback: the input is returned unchanged. */
    FALLBACK,
    /** Reserved for an optional learned selector; unused by the deterministic engine. */
    MODEL
}
