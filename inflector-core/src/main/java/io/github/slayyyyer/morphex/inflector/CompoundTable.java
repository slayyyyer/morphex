package io.github.slayyyyer.morphex.inflector;

import java.util.Map;
import java.util.Set;

/**
 * Bounded, documented compound support.
 *
 * <p>Deliberately small: a suffix match is not proof of a morpheme boundary, so only structures that were
 * individually reviewed are recognised. Exact entries cover the rest, including counterexamples that a
 * generic "inflect the first word" or "inflect the last word" rule would get wrong.
 */
final class CompoundTable {

    static final CompoundTable INSTANCE = new CompoundTable();

    /** Whole compound (lower case) to whole plural (lower case). */
    private final Map<String, String> exact = Map.ofEntries(
            Map.entry("passer-by", "passers-by"),
            Map.entry("hanger-on", "hangers-on"),
            Map.entry("looker-on", "lookers-on"),
            Map.entry("runner-up", "runners-up"),
            Map.entry("stand-in", "stand-ins"),
            Map.entry("drive-by", "drive-bys"),
            Map.entry("tie-in", "tie-ins"),
            Map.entry("close-up", "close-ups"),
            Map.entry("grown-up", "grown-ups"),
            Map.entry("go-between", "go-betweens"),
            Map.entry("forget-me-not", "forget-me-nots"),
            Map.entry("jack-in-the-box", "jack-in-the-boxes"),
            Map.entry("man-of-war", "men-of-war"),
            Map.entry("major general", "major generals"));

    /** Hyphenated tails that cannot themselves be a noun, so any head is safe. */
    private final Set<String> freeTails = Set.of(
            "in-law", "in-chief", "of-war", "at-arms", "at-law", "of-arms");

    /** Postposed modifiers: only with a curated head, because "major general" is a counterexample. */
    private final Set<String> postposed = Set.of(
            "general", "elect", "apparent", "designate", "martial", "laureate", "presumptive", "public", "errant");

    /** Heads reviewed as taking a postposed modifier. */
    private final Set<String> heads = Set.of(
            "attorney", "court", "heir", "notary", "poet", "governor", "ambassador", "president", "mayor",
            "minister", "secretary", "treasurer", "surgeon", "solicitor", "lord", "postmaster", "editor",
            "commander");

    /** Tokens that signal a fixed expression rather than a head-final noun compound. */
    private final Set<String> prepositions = Set.of(
            "in", "of", "at", "on", "by", "to", "with", "for", "from", "upon", "under", "over", "into");

    private CompoundTable() {
    }

    String exactPlural(String lower) {
        return exact.get(lower);
    }

    boolean isFreeTail(String tailLower) {
        return freeTails.contains(tailLower);
    }

    boolean isPostposed(String tailLower) {
        return postposed.contains(tailLower);
    }

    boolean isCuratedHead(String headLower) {
        return heads.contains(headLower);
    }

    boolean isPreposition(String tokenLower) {
        return prepositions.contains(tokenLower);
    }
}
