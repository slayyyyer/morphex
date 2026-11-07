package io.github.slayyyyer.morphex.inflector;

import java.util.List;
import java.util.Objects;

/**
 * Diagnostic result of {@link EnglishInflector#analyze(String)}.
 *
 * <p>Deliberately immutable and deliberately free of a numeric confidence: the deterministic engine has no
 * validated probability to report, and a raw model score would not be one.
 */
public final class Inflection {

    private final String input;
    private final String plural;
    private final List<String> alternatives;
    private final DecisionSource source;
    private final String id;
    private final Countability countability;
    private final boolean invariant;
    private final boolean ambiguous;
    private final String note;

    Inflection(String input,
               String plural,
               List<String> alternatives,
               DecisionSource source,
               String id,
               Countability countability,
               boolean invariant,
               boolean ambiguous,
               String note) {
        this.input = input;
        this.plural = plural;
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        this.source = Objects.requireNonNull(source, "source");
        this.id = id == null ? "" : id;
        this.countability = countability == null ? Countability.UNKNOWN : countability;
        this.invariant = invariant;
        this.ambiguous = ambiguous;
        this.note = note == null ? "" : note;
    }

    public String input() {
        return input;
    }

    /** The selected plural. */
    public String plural() {
        return plural;
    }

    /** Other accepted plural forms known for this lemma, in artifact order. */
    public List<String> alternatives() {
        return alternatives;
    }

    /** True when {@code form} is the selected plural or one of the accepted alternatives. */
    public boolean isAccepted(String form) {
        return plural.equals(form) || alternatives.contains(form);
    }

    public DecisionSource source() {
        return source;
    }

    /** Stable entry or rule identifier, for example {@code lex:child}, {@code rule:default-s}. */
    public String id() {
        return id;
    }

    public Countability countability() {
        return countability;
    }

    /** True when the lemma has the same singular and plural form ({@code sheep}). */
    public boolean invariant() {
        return invariant;
    }

    /**
     * True when the spelling alone cannot select one form, for example {@code die} (game piece versus stamping
     * tool). The selected plural is still returned; callers that care must disambiguate themselves.
     */
    public boolean ambiguous() {
        return ambiguous;
    }

    /** Human-readable explanation of the decision and of any policy that applied. Empty when there is none. */
    public String note() {
        return note;
    }

    @Override
    public String toString() {
        return "Inflection{input='" + input + "', plural='" + plural + "', alternatives=" + alternatives
                + ", source=" + source + ", id='" + id + "', countability=" + countability
                + ", invariant=" + invariant + ", ambiguous=" + ambiguous + '}';
    }
}
