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
            "in-law", "in-arms", "in-aid", "in-kind", "in-chief", "of-war", "of-arms", "of-spring",
            "of-all-trades", "out-law", "at-arms", "at-law", "in-crime");

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

    /**
     * Final tokens that can be the end of a hyphenated expression but are not nouns, so the expression is not a
     * head-final noun compound. This is the guard that keeps {@code well-known} and the other documented
     * unsupported-input regressions unchanged while permitting {@code credit-card} and {@code coffee-shop}.
     */
    private final Set<String> nonNounHeads = Set.of(
            "known", "born", "bred", "built", "done", "gone", "held", "left", "lost", "made", "paid",
            "sold", "told", "worn", "written", "spoken", "taken", "given", "seen", "heard", "led", "won",
            "set", "put", "cut", "read", "run", "come", "become", "beaten", "broken", "chosen", "driven",
            "eaten", "fallen", "forgotten", "frozen", "hidden", "risen", "shaken", "shown",
            "stolen", "sworn", "torn", "woken", "wrong", "aware", "alike", "alone", "asleep", "awake");

    /** Verbed-particle nouns: the plural is the whole expression plus s, not the final token. */
    private final Set<String> particles = Set.of(
            "off", "in", "on", "up", "out", "over", "back", "down", "through", "around", "about", "away");

    /** Coordinators/articles that signal a fixed phrase, not a head-final noun compound. */
    private final Set<String> coordinators = Set.of("and", "or", "nor", "but", "the");

    /** Preposition-like prefixes reviewed as productive in head-final nouns ({@code on-ramp}). */
    private final Set<String> compoundPrefixes = Set.of("on");

    /**
     * Lower-case -man words that are not compounds of a noun + man, so the -man backoff must not touch them.
     * Proper names are additionally excluded by requiring an all-lower-case input for this rule.
     */
    private final Set<String> manExceptions = Set.of(
            "human", "german", "roman", "shaman", "talisman", "caiman", "ottoman", "brahman", "atman",
            "liman", "chroman", "hooman", "kundiman", "laraman", "oralman", "abhuman", "metahuman",
            "paleohuman", "parahuman", "dragoman", "firman", "harman", "tarman", "turkman", "touman",
            "tramman", "vataman", "velociman", "zygentoman");

    private CompoundTable() {
    }

    String exactPlural(String lower) {
        return exact.get(lower);
    }

    boolean isFreeTail(String tailLower) {
        return freeTails.contains(tailLower);
    }

    /** Returns the hyphen index of the longest recognized free-tail suffix, or -1. */
    int freeTailSuffix(String lower) {
        int best = -1;
        for (String tail : freeTails) {
            String suffix = "-" + tail;
            if (lower.endsWith(suffix)) {
                int index = lower.length() - suffix.length();
                if (index > 0 && index > best) {
                    best = index;
                }
            }
        }
        return best;
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

    boolean isNonNounHead(String tokenLower) {
        return nonNounHeads.contains(tokenLower);
    }

    boolean isParticle(String tokenLower) {
        return particles.contains(tokenLower);
    }

    boolean isManException(String tokenLower) {
        return manExceptions.contains(tokenLower) || tokenLower.endsWith("human");
    }

    boolean isCoordinator(String tokenLower) {
        return coordinators.contains(tokenLower);
    }

    boolean isCompoundPrefix(String tokenLower) {
        return compoundPrefixes.contains(tokenLower);
    }
}
