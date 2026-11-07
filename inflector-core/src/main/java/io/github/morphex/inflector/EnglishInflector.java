package io.github.morphex.inflector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable English noun pluralizer.
 *
 * <p>Resolution order is fixed: caller override, exact lexical entry, supported compound, productive spelling
 * rule, then the documented fallback that returns the input unchanged. The fast {@code String}-returning path
 * allocates nothing for identity results and never builds diagnostic collections.
 *
 * <p>Instances are immutable and thread-safe. There is no mutable global configuration: the bundled lexicon is
 * loaded once into packed arrays and shared.
 *
 * <pre>{@code
 * EnglishInflector inflector = EnglishInflector.standard();
 * inflector.pluralize("child");        // "children"
 * inflector.pluralize("UFO");          // "UFOs"
 * inflector.pluralize("cat", 1);       // "cat" (source instance preserved)
 * inflector.analyze("die").alternatives(); // ["dies"]
 * }</pre>
 */
public final class EnglishInflector {

    private static final Rule SIBILANT = new Rule(
            "rule:sibilant-es", "suffix s/x/z/ch/sh appends es", 0, "es", WordShape::endsSibilant);
    private static final Rule CONSONANT_Y = new Rule(
            "rule:consonant-y-ies", "consonant before y becomes ies", 1, "ies", WordShape::endsConsonantY);
    private static final Rule DEFAULT = new Rule(
            "rule:default-s", "default appends s", 0, "s", lower -> true);

    private static final List<Rule> RULES = List.of(SIBILANT, CONSONANT_Y, DEFAULT);

    /** Head pluralization inside a compound may use simple words only, never another compound. */
    private static final int MAX_COMPOUND_DEPTH = 1;

    private static final String FALLBACK_ID = "fallback:unsupported";

    private final Mode mode;
    private final CasePolicy casePolicy;
    private final Map<String, String> overrides;
    private final Set<String> initialisms;
    private final Lexicon lexicon;
    private final CompoundTable compounds = CompoundTable.INSTANCE;

    private EnglishInflector(Builder builder) {
        this.mode = builder.mode;
        this.casePolicy = builder.casePolicy;
        this.overrides = builder.overrides.isEmpty() ? Map.of() : Map.copyOf(builder.overrides);
        this.initialisms = builder.initialisms.isEmpty() ? Set.of() : Set.copyOf(builder.initialisms);
        this.lexicon = builder.bundledLexicon ? Lexicon.bundled() : Lexicon.EMPTY;
    }

    /** Shared default instance: anglicized mode, {@link CasePolicy#PRESERVE}, bundled lexicon. */
    public static EnglishInflector standard() {
        return StandardHolder.INSTANCE;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final class StandardHolder {
        static final EnglishInflector INSTANCE = builder().build();
    }

    public Mode mode() {
        return mode;
    }

    public CasePolicy casePolicy() {
        return casePolicy;
    }

    /** Number of lexical entries visible to this instance (0 when built without the bundled lexicon). */
    public int lexiconSize() {
        return lexicon.size();
    }

    /**
     * Returns the plural of a singular noun.
     *
     * @throws NullPointerException if {@code noun} is null
     */
    public String pluralize(String noun) {
        Objects.requireNonNull(noun, "noun must not be null");
        return resolve(noun, null, 0);
    }

    /**
     * Returns {@code noun} unchanged when {@code count == 1} (the source instance is preserved) and the plural
     * otherwise. This is deliberately a plain count test, not an absolute-value or locale rule.
     *
     * @throws NullPointerException if {@code noun} is null
     */
    public String pluralize(String noun, int count) {
        Objects.requireNonNull(noun, "noun must not be null");
        return count == 1 ? noun : resolve(noun, null, 0);
    }

    /**
     * Returns a full diagnostic result. The chosen form is identical to {@link #pluralize(String)}; the extra
     * fields explain the decision and are only built here.
     *
     * @throws NullPointerException if {@code noun} is null
     */
    public Inflection analyze(String noun) {
        Objects.requireNonNull(noun, "noun must not be null");
        Sink sink = new Sink();
        String plural = resolve(noun, sink, 0);
        return new Inflection(noun, plural, sink.alternatives, sink.source, sink.id, sink.countability,
                sink.invariant, sink.ambiguous, sink.note);
    }

    // ------------------------------------------------------------------ resolution

    private String resolve(String noun, Sink sink, int depth) {
        if (noun.isEmpty()) {
            if (sink != null) {
                sink.set(DecisionSource.FALLBACK, "fallback:empty", Countability.UNKNOWN,
                        false, false, null, "empty input returns empty output");
            }
            return noun;
        }

        String override = overrides.get(noun);
        if (override != null) {
            if (sink != null) {
                sink.set(DecisionSource.OVERRIDE, "override", Countability.UNKNOWN,
                        false, false, null, "caller override matched the exact input spelling");
            }
            return override;
        }

        String lower = WordShape.lower(noun);
        int entry = lexicon.find(lower);
        if (entry >= 0 && lexicon.matchesCase(entry, noun)) {
            return fromEntry(noun, lower, entry, sink);
        }

        if (!initialisms.isEmpty() && initialisms.contains(lower)) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, "config:initialism", Countability.UNKNOWN,
                        false, false, null, "token declared an initialism by configuration");
            }
            return renderInitialism(noun, lower, initialismSuffix(lower));
        }

        if (depth < MAX_COMPOUND_DEPTH) {
            String compound = resolveCompound(noun, lower, depth, sink);
            if (compound != null) {
                return compound;
            }
        }

        if (WordShape.isPlainLatinToken(noun)) {
            for (Rule rule : RULES) {
                if (rule.applies(lower)) {
                    if (sink != null) {
                        sink.set(DecisionSource.RULE, rule.id(), Countability.UNKNOWN,
                                false, false, null, rule.description());
                    }
                    return render(noun, lower, rule.cut(), rule.append());
                }
            }
        }

        if (sink != null) {
            sink.set(DecisionSource.FALLBACK, FALLBACK_ID, Countability.UNKNOWN,
                    false, false, null,
                    "unsupported token shape (identifier, punctuation, possessive or non-Latin letter); "
                            + "returned unchanged");
        }
        return noun;
    }

    private String fromEntry(String noun, String lower, int entry, Sink sink) {
        if (lexicon.initialism(entry)) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, lexicon.id(entry) + "#initialism", lexicon.countability(entry),
                        false, lexicon.ambiguous(entry), null,
                        "initialism metadata: append the entry's s/es suffix without whole-token re-casing");
            }
            return renderInitialism(noun, lower, lexicon.append(entry));
        }

        Countability countability = lexicon.countability(entry);
        boolean invariant = lexicon.invariant(entry);
        if (countability == Countability.MASS
                || countability == Countability.PLURAL_ONLY
                || invariant) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, lexicon.id(entry), countability,
                        invariant, lexicon.ambiguous(entry), renderAlternatives(noun, lower, entry, false),
                        entryNote(entry));
            }
            return noun;
        }

        boolean classical = mode == Mode.CLASSICAL && lexicon.classicalCut(entry) >= 0;
        int cut = classical ? lexicon.classicalCut(entry) : lexicon.cut(entry);
        String append = classical ? lexicon.classicalAppend(entry) : lexicon.append(entry);
        String plural = render(noun, lower, cut, append);

        if (sink != null) {
            sink.set(DecisionSource.LEXICON, lexicon.id(entry), countability, false, lexicon.ambiguous(entry),
                    renderAlternatives(noun, lower, entry, classical),
                    classical ? "classical preference selected by mode" : entryNote(entry));
        }
        return plural;
    }

    private List<String> renderAlternatives(String noun, String lower, int entry, boolean classicalUsed) {
        List<String> stored = lexicon.alternatives(entry);
        String classical = lexicon.classical(entry);
        if (stored.isEmpty() && classical == null && !classicalUsed) {
            return null;
        }
        List<String> alternatives = new ArrayList<>(stored.size() + 1);
        if (classicalUsed) {
            alternatives.add(renderFull(noun, lower, lexicon.plural(entry)));
        } else if (classical != null) {
            alternatives.add(renderFull(noun, lower, classical));
        }
        for (String alternative : stored) {
            alternatives.add(renderFull(noun, lower, alternative));
        }
        return alternatives;
    }

    private String entryNote(int entry) {
        return switch (lexicon.countability(entry)) {
            case MASS -> "mass noun: returned unchanged by policy";
            case PLURAL_ONLY -> "plural-only lemma: returned unchanged by policy";
            case MIXED -> "mixed mass/countable noun: pluralized in its countable sense";
            default -> lexicon.invariant(entry) ? "invariant plural: singular and plural are identical" : "";
        };
    }

    // ------------------------------------------------------------------ compounds

    private String resolveCompound(String noun, String lower, int depth, Sink sink) {
        String exact = compounds.exactPlural(lower);
        if (exact != null) {
            if (sink != null) {
                sink.set(DecisionSource.COMPOUND, "compound:exact:" + lower, Countability.UNKNOWN,
                        false, false, null, "exact compound entry");
            }
            return renderFull(noun, lower, exact);
        }

        int hyphen = noun.indexOf('-');
        if (hyphen > 0 && hyphen < noun.length() - 1) {
            String head = noun.substring(0, hyphen);
            String tail = lower.substring(hyphen + 1);
            boolean recognised = compounds.isFreeTail(tail)
                    || (compounds.isPostposed(tail) && compounds.isCuratedHead(WordShape.lower(head)));
            if (recognised) {
                String headPlural = pluralizeHead(head, depth);
                if (headPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:hyphen:" + tail, Countability.UNKNOWN,
                                false, false, null, "documented hyphenated compound pattern '" + tail + "'");
                    }
                    return headPlural + noun.substring(hyphen);
                }
            }
            return null;
        }

        int space = lower.indexOf(' ');
        if (space < 0) {
            return null;
        }
        String[] tokens = noun.split(" ");
        if (tokens.length < 2) {
            return null;
        }
        for (String token : tokens) {
            if (token.isEmpty()) {
                return null;
            }
        }
        String lastLower = WordShape.lower(tokens[tokens.length - 1]);
        if (compounds.isPostposed(lastLower)) {
            StringBuilder headBuilder = new StringBuilder();
            for (int i = 0; i < tokens.length - 1; i++) {
                if (i > 0) {
                    headBuilder.append(' ');
                }
                headBuilder.append(WordShape.lower(tokens[i]));
            }
            String headLower = headBuilder.toString();
            if (compounds.isCuratedHead(headLower)) {
                String headPlural = pluralizeHead(noun.substring(0, space), depth);
                if (headPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:space:" + lastLower, Countability.UNKNOWN,
                                false, false, null,
                                "postposed modifier '" + lastLower + "' after a curated head");
                    }
                    return headPlural + noun.substring(space);
                }
            }
        }
        for (String token : tokens) {
            if (compounds.isPreposition(WordShape.lower(token))) {
                return null;
            }
        }
        String last = tokens[tokens.length - 1];
        if (!WordShape.isPlainLatinToken(last)) {
            return null;
        }
        String lastPlural = pluralizeHead(last, depth);
        if (lastPlural == null) {
            return null;
        }
        if (sink != null) {
            sink.set(DecisionSource.COMPOUND, "compound:space:final", Countability.UNKNOWN,
                    false, false, null, "space-separated compound: final token inflected");
        }
        return noun.substring(0, noun.length() - last.length()) + lastPlural;
    }

    /** Pluralizes a compound head using only productive rules and lexical entries. */
    private String pluralizeHead(String head, int depth) {
        if (depth >= MAX_COMPOUND_DEPTH || !WordShape.isPlainLatinToken(head)) {
            return null;
        }
        return resolve(head, null, depth + 1);
    }

    // ------------------------------------------------------------------ rendering

    private String render(String source, String lower, int cut, String append) {
        switch (casePolicy) {
            case LOWERCASE:
                return edit(lower, cut, append);
            case UPPERCASE:
                return edit(source, cut, append).toUpperCase(Locale.ROOT);
            default:
                break;
        }
        WordShape.Casing casing = WordShape.casingOf(source);
        if (casing == WordShape.Casing.LOWER) {
            return edit(lower, cut, append);
        }
        if (source.length() != lower.length()) {
            // Case mapping changed the length (rare, e.g. Turkish dotted I): render lower and re-case.
            String base = edit(lower, cut, append);
            return switch (casing) {
                case UPPER -> base.toUpperCase(Locale.ROOT);
                case CAPITALIZED -> WordShape.capitalize(base);
                default -> base;
            };
        }
        String edited = edit(source, cut, append);
        return casing == WordShape.Casing.UPPER ? edited.toUpperCase(Locale.ROOT) : edited;
    }

    private static String edit(String token, int cut, String append) {
        int end = token.length() - cut;
        if (end < 0) {
            end = 0;
        }
        if (append.isEmpty()) {
            return end == token.length() ? token : token.substring(0, end);
        }
        if (end == 0) {
            return append;
        }
        return token.substring(0, end) + append;
    }

    /** Renders a complete lowercase form (lexical alternative, compound) with the caller's casing shape. */
    private String renderFull(String source, String lower, String fullLower) {
        switch (casePolicy) {
            case LOWERCASE:
                return fullLower;
            case UPPERCASE:
                return fullLower.toUpperCase(Locale.ROOT);
            default:
                break;
        }
        return switch (WordShape.casingOf(source)) {
            case LOWER -> fullLower;
            case UPPER -> fullLower.toUpperCase(Locale.ROOT);
            case CAPITALIZED -> WordShape.capitalize(fullLower);
            case MIXED -> fullLower.startsWith(lower)
                    ? source + fullLower.substring(lower.length())
                    : fullLower;
        };
    }

    /**
     * Suffix for a caller-declared initialism: {@code es} after a final S (SMSes, SOSes), otherwise {@code s}
     * (ABCs, CDs, UFOs). Lexical initialisms use their own attested suffix instead of this default.
     */
    private static String initialismSuffix(String lower) {
        return lower.charAt(lower.length() - 1) == 's' ? "es" : "s";
    }

    private String renderInitialism(String source, String lower, String suffix) {
        return switch (casePolicy) {
            case LOWERCASE -> lower + suffix;
            case UPPERCASE -> source.toUpperCase(Locale.ROOT) + suffix.toUpperCase(Locale.ROOT);
            default -> source + suffix;
        };
    }

    // ------------------------------------------------------------------ diagnostics sink

    private static final class Sink {
        DecisionSource source = DecisionSource.FALLBACK;
        String id = FALLBACK_ID;
        Countability countability = Countability.UNKNOWN;
        boolean invariant;
        boolean ambiguous;
        List<String> alternatives;
        String note = "";

        void set(DecisionSource source, String id, Countability countability, boolean invariant,
                 boolean ambiguous, List<String> alternatives, String note) {
            this.source = source;
            this.id = id;
            this.countability = countability;
            this.invariant = invariant;
            this.ambiguous = ambiguous;
            this.alternatives = alternatives == null || alternatives.isEmpty() ? null : alternatives;
            this.note = note == null ? "" : note;
        }
    }

    // ------------------------------------------------------------------ builder

    /** Immutable configuration builder. Every setter returns the builder; instances share nothing mutable. */
    public static final class Builder {
        private Mode mode = Mode.ANGLICIZED;
        private CasePolicy casePolicy = CasePolicy.PRESERVE;
        private final Map<String, String> overrides = new LinkedHashMap<>();
        private final Set<String> initialisms = new LinkedHashSet<>();
        private boolean bundledLexicon = true;

        private Builder() {
        }

        public Builder mode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode must not be null");
            return this;
        }

        public Builder casePolicy(CasePolicy casePolicy) {
            this.casePolicy = Objects.requireNonNull(casePolicy, "casePolicy must not be null");
            return this;
        }

        /** Adds an exact, case-sensitive replacement for the whole input spelling. */
        public Builder override(String noun, String plural) {
            Objects.requireNonNull(noun, "noun must not be null");
            Objects.requireNonNull(plural, "plural must not be null");
            if (noun.isEmpty()) {
                throw new IllegalArgumentException("override key must not be empty");
            }
            overrides.put(noun, plural);
            return this;
        }

        public Builder overrides(Map<String, String> replacements) {
            Objects.requireNonNull(replacements, "replacements must not be null");
            replacements.forEach(this::override);
            return this;
        }

        /** Declares a token an initialism: it appends {@code s}, or {@code es} after S/X/Z/C. */
        public Builder initialism(String token) {
            Objects.requireNonNull(token, "token must not be null");
            if (token.isEmpty()) {
                throw new IllegalArgumentException("initialism token must not be empty");
            }
            initialisms.add(WordShape.lower(token));
            return this;
        }

        public Builder initialisms(Collection<String> tokens) {
            Objects.requireNonNull(tokens, "tokens must not be null");
            tokens.forEach(this::initialism);
            return this;
        }

        /** Builds a rules-only instance (no bundled lexicon). Useful for baselines and tests. */
        public Builder withoutBundledLexicon() {
            this.bundledLexicon = false;
            return this;
        }

        public EnglishInflector build() {
            return new EnglishInflector(this);
        }
    }
}
