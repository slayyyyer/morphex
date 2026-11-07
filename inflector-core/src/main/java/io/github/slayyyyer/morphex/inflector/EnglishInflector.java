package io.github.slayyyyer.morphex.inflector;

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

    /**
     * Compound transformations are nested, never unbounded: a free tail may contain another documented
     * compound ({@code great-grandfather-in-law}), so the parser carries an explicit recursion budget.
     */
    private static final int MAX_COMPOUND_DEPTH = 4;

    private static final String FALLBACK_ID = "fallback:unsupported";

    private final Mode mode;
    private final CasePolicy casePolicy;
    private final Map<String, String> overrides;
    private final Set<String> initialisms;
    private final UppercasePolicy uppercasePolicy;
    private final Lexicon lexicon;
    private final CompoundTable compounds = CompoundTable.INSTANCE;

    private EnglishInflector(Builder builder) {
        this.mode = builder.mode;
        this.casePolicy = builder.casePolicy;
        this.overrides = builder.overrides.isEmpty() ? Map.of() : Map.copyOf(builder.overrides);
        this.initialisms = builder.initialisms.isEmpty() ? Set.of() : Set.copyOf(builder.initialisms);
        this.uppercasePolicy = builder.uppercasePolicy;
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

    public UppercasePolicy uppercasePolicy() {
        return uppercasePolicy;
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
        return resolve(noun, null, 0, casePolicy);
    }

    /**
     * Returns {@code noun} unchanged when {@code count == 1} (the source instance is preserved) and the plural
     * otherwise. This is deliberately a plain count test, not an absolute-value or locale rule.
     *
     * @throws NullPointerException if {@code noun} is null
     */
    public String pluralize(String noun, int count) {
        Objects.requireNonNull(noun, "noun must not be null");
        return count == 1 ? noun : resolve(noun, null, 0, casePolicy);
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
        String plural = resolve(noun, sink, 0, casePolicy);
        return new Inflection(noun, plural, sink.alternatives, sink.source, sink.id, sink.countability,
                sink.invariant, sink.ambiguous, sink.note);
    }

    // ------------------------------------------------------------------ resolution

    private String resolve(String noun, Sink sink, int depth, CasePolicy policy) {
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
            return fromEntry(noun, lower, entry, sink, policy);
        }

        if (!initialisms.isEmpty() && initialisms.contains(lower)) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, "config:initialism", Countability.UNKNOWN,
                        false, false, null, "token declared an initialism by configuration");
            }
            return renderInitialism(noun, lower, initialismSuffix(lower), policy);
        }

        // Unknown all-uppercase token: a bounded initialism policy, not an assumption that every
        // uppercase spelling is an acronym. Ordinary-word membership in the runtime lexicon is checked
        // first, and the policy is disabled for a rules-only instance because it has no membership table.
        if (uppercasePolicy == UppercasePolicy.INFER_INITIALISM
                && lexicon.size() > 0
                && lexicon.hasMembership()
                && WordShape.isAllUpperLatinToken(noun)
                && WordShape.codePointLength(noun) >= 2
                && WordShape.codePointLength(noun) <= 6
                && !lexicon.isOrdinaryWord(lower)) {
            if (sink != null) {
                sink.set(DecisionSource.INFERRED, "inferred:uppercase-initialism", Countability.UNKNOWN,
                        false, false, null,
                        "unknown all-upper-case token of 2-6 Latin letters: appended lower-case s; "
                                + "lexical membership was checked first");
            }
            return renderInitialism(noun, lower, "s", policy);
        }

        // Compound spelling always contains a hyphen or a supported single space. Avoid the compound
        // table and suffix scans for the overwhelmingly common single-token input.
        if (depth < MAX_COMPOUND_DEPTH && (noun.indexOf('-') >= 0 || noun.indexOf(' ') >= 0)) {
            String compound = resolveCompound(noun, lower, depth, policy, sink);
            if (compound != null) {
                // An unchanged reconstruction is the caller's own input: preserve the instance and avoid an
                // allocation, matching the documented identity behaviour for invariant words.
                return compound.equals(noun) ? noun : compound;
            }
        }

        if (WordShape.isPlainLatinToken(noun)) {
            return applyRule(noun, lower, sink, policy);
        }

        if (sink != null) {
            sink.set(DecisionSource.FALLBACK, FALLBACK_ID, Countability.UNKNOWN,
                    false, false, null,
                    "unsupported token shape (identifier, punctuation, possessive or non-Latin letter); "
                            + "returned unchanged");
        }
        return noun;
    }

    /**
     * Ordered productive rules and documented backoffs. This is deliberately a direct branch chain:
     * the previous {@code List<Rule>} loop made seven interface-dispatched predicate calls for every
     * dictionary miss, including the dominant regular-word path.
     */
    private String applyRule(String noun, String lower, Sink sink, CasePolicy policy) {
        if (lower.endsWith("woman")) {
            return ruleResult(noun, lower, 5, "women",
                    "backoff:woman->women", "documented -woman -> -women backoff", sink, policy);
        }
        if (lower.endsWith("wife")) {
            return ruleResult(noun, lower, 4, "wives",
                    "backoff:wife->wives", "documented -wife -> -wives backoff", sink, policy);
        }
        if (lower.length() >= 6
                && lower.endsWith("man")
                && !lower.endsWith("woman")
                && noun.equals(lower)
                && !compounds.isManException(lower)) {
            return ruleResult(noun, lower, 3, "men",
                    "backoff:man->men", "documented noun + man -> men backoff", sink, policy);
        }
        if (lower.length() >= 5 && lower.endsWith("sis")) {
            return ruleResult(noun, lower, 2, "es",
                    "backoff:sis->ses", "documented Greek -sis -> -ses backoff", sink, policy);
        }
        if (WordShape.endsSibilant(lower)) {
            return ruleResult(noun, lower, 0, "es",
                    "rule:sibilant-es", "suffix s/x/z/ch/sh appends es", sink, policy);
        }
        if (WordShape.endsConsonantY(lower)) {
            return ruleResult(noun, lower, 1, "ies",
                    "rule:consonant-y-ies", "consonant before y becomes ies", sink, policy);
        }
        return ruleResult(noun, lower, 0, "s",
                "rule:default-s", "default appends s", sink, policy);
    }

    private String ruleResult(String noun, String lower, int cut, String append, String id, String description,
            Sink sink, CasePolicy policy) {
        if (sink != null) {
            sink.set(DecisionSource.RULE, id, Countability.UNKNOWN, false, false, null, description);
        }
        return render(noun, lower, cut, append, policy);
    }

    private String fromEntry(String noun, String lower, int entry, Sink sink, CasePolicy policy) {
        if (lexicon.initialism(entry)) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, lexicon.id(entry) + "#initialism", lexicon.countability(entry),
                        false, lexicon.ambiguous(entry), null,
                        "initialism metadata: append the entry's s/es suffix without whole-token re-casing");
            }
            return renderInitialism(noun, lower, lexicon.append(entry), policy);
        }

        Countability countability = lexicon.countability(entry);
        boolean invariant = lexicon.invariant(entry);
        if (countability == Countability.MASS
                || countability == Countability.PLURAL_ONLY
                || invariant) {
            if (sink != null) {
                sink.set(DecisionSource.LEXICON, lexicon.id(entry), countability,
                        invariant, lexicon.ambiguous(entry), renderAlternatives(noun, lower, entry, false, policy),
                        entryNote(entry));
            }
            return noun;
        }

        boolean classical = mode == Mode.CLASSICAL && lexicon.classicalCut(entry) >= 0;
        int cut = classical ? lexicon.classicalCut(entry) : lexicon.cut(entry);
        String append = classical ? lexicon.classicalAppend(entry) : lexicon.append(entry);
        String plural = render(noun, lower, cut, append, policy);

        if (sink != null) {
            sink.set(DecisionSource.LEXICON, lexicon.id(entry), countability, false, lexicon.ambiguous(entry),
                    renderAlternatives(noun, lower, entry, classical, policy),
                    classical ? "classical preference selected by mode" : entryNote(entry));
        }
        return plural;
    }

    private List<String> renderAlternatives(String noun, String lower, int entry, boolean classicalUsed,
            CasePolicy policy) {
        List<String> stored = lexicon.alternatives(entry);
        String classical = lexicon.classical(entry);
        if (stored.isEmpty() && classical == null && !classicalUsed) {
            return null;
        }
        List<String> alternatives = new ArrayList<>(stored.size() + 1);
        if (classicalUsed) {
            alternatives.add(renderFull(noun, lower, lexicon.plural(entry), policy));
        } else if (classical != null) {
            alternatives.add(renderFull(noun, lower, classical, policy));
        }
        for (String alternative : stored) {
            alternatives.add(renderFull(noun, lower, alternative, policy));
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

    private String resolveCompound(String noun, String lower, int depth, CasePolicy policy, Sink sink) {
        String exact = compounds.exactPlural(lower);
        if (exact != null) {
            if (sink != null) {
                sink.set(DecisionSource.COMPOUND, "compound:exact:" + lower, Countability.UNKNOWN,
                        false, false, null, "exact compound entry");
            }
            return renderFull(noun, lower, exact, policy);
        }

        int freeSuffix = compounds.freeTailSuffix(lower);
        if (freeSuffix > 0 && freeSuffix < noun.length()) {
            HeadResult headPlural = resolveHead(noun.substring(0, freeSuffix), depth, true);
            if (headPlural != null) {
                if (sink != null) {
                    sink.set(DecisionSource.COMPOUND, "compound:hyphen:free-tail-suffix", Countability.UNKNOWN,
                            false, false, null,
                            "documented trailing free tail: head before the suffix is inflected");
                }
                return applyCasePolicy(headPlural.text() + noun.substring(freeSuffix), policy);
            }
        }

        int hyphen = noun.indexOf('-');
        if (hyphen > 0 && hyphen < noun.length() - 1) {
            String head = noun.substring(0, hyphen);
            String tail = lower.substring(hyphen + 1);
            boolean recognised = compounds.isFreeTail(tail)
                    || (compounds.isPostposed(tail) && compounds.isCuratedHead(WordShape.lower(head)));
            if (recognised) {
                HeadResult headPlural = resolveHead(head, depth);
                if (headPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:hyphen:" + tail, Countability.UNKNOWN,
                                false, false, null, "documented hyphenated compound pattern '" + tail + "'");
                    }
                    return applyCasePolicy(headPlural.text() + noun.substring(hyphen), policy);
                }
            }
            if (isParticleCompound(noun, hyphen, tail)) {
                if (sink != null) {
                    sink.set(DecisionSource.COMPOUND, "compound:hyphen:particle", Countability.UNKNOWN,
                            false, false, null,
                            "verb-particle hyphenated noun: whole expression takes s");
                }
                return render(noun, lower, 0, "s", policy);
            }
            if (isHeadFinalCompound(noun, hyphen, tail)) {
                String tailPlural = pluralizeCompoundHead(noun.substring(hyphen + 1), depth, true);
                if (tailPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:hyphen:head-final", Countability.UNKNOWN,
                                false, false, null,
                                "head-final hyphenated compound: final noun head inflected");
                    }
                    return applyCasePolicy(noun.substring(0, hyphen + 1) + tailPlural, policy);
                }
            }
            int lastHyphen = noun.lastIndexOf('-');
            if (lastHyphen > hyphen && isMultiHeadFinalCompound(noun)) {
                String multiTail = noun.substring(lastHyphen + 1);
                String tailPlural = pluralizeCompoundHead(multiTail, depth, true);
                if (tailPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:hyphen:multi-head-final", Countability.UNKNOWN,
                                false, false, null,
                                "multi-hyphen head-final noun without a function word: final head inflected");
                    }
                    return applyCasePolicy(noun.substring(0, lastHyphen + 1) + tailPlural, policy);
                }
            }
            return null;
        }

        int space = noun.indexOf(' ');
        // Only single-space separators are a supported compound shape. Leading, trailing or repeated
        // whitespace is unsupported input and is returned unchanged; it is never trimmed or rewritten.
        if (space < 0 || space == 0 || space == noun.length() - 1 || noun.indexOf("  ") >= 0) {
            return null;
        }
        String[] tokens = noun.split(" ", -1);
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
                HeadResult headPlural = resolveHead(noun.substring(0, space), depth);
                if (headPlural != null) {
                    if (sink != null) {
                        sink.set(DecisionSource.COMPOUND, "compound:space:" + lastLower, Countability.UNKNOWN,
                                false, false, null,
                                "postposed modifier '" + lastLower + "' after a curated head");
                    }
                    return applyCasePolicy(headPlural.text() + noun.substring(space), policy);
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
        String lastPlural = pluralizeCompoundHead(last, depth, false);
        if (lastPlural == null) {
            return null;
        }
        if (sink != null) {
            sink.set(DecisionSource.COMPOUND, "compound:space:final", Countability.UNKNOWN,
                    false, false, null, "space-separated compound: final token inflected");
        }
        int lastStart = noun.length() - last.length();
        return applyCasePolicy(noun.substring(0, lastStart) + lastPlural, policy);
    }

    /**
     * Resolves one compound constituent and keeps the provenance needed to decide whether a final head may be
     * respelled. The head is rendered in the caller's shape only; the enclosing compound applies its case
     * policy once to the complete reconstructed expression.
     */
    private HeadResult resolveHead(String head, int depth) {
        return resolveHead(head, depth, false);
    }

    private HeadResult resolveHead(String head, int depth, boolean allowCompoundShape) {
        if (depth >= MAX_COMPOUND_DEPTH || head.isEmpty()) {
            return null;
        }
        if (!allowCompoundShape && !WordShape.isPlainLatinToken(head)) {
            return null;
        }
        Sink headSink = new Sink();
        String text = resolve(head, headSink, depth + 1, CasePolicy.PRESERVE);
        if (headSink.source == DecisionSource.FALLBACK) {
            return null;
        }
        return new HeadResult(text, headSink.source, headSink.id, headSink.invariant);
    }

    /**
     * A final head may be respelled only when it is genuinely unresolved: no override, no lexical entry, no
     * nested compound, no initialism metadata and no evidence of ordinary source membership. A regular word
     * omitted by artifact compaction is still a lexical word and keeps its productive result.
     */
    private String pluralizeCompoundHead(String head, int depth, boolean allowRespell) {
        HeadResult resolved = resolveHead(head, depth);
        if (resolved == null) {
            return null;
        }
        if (allowRespell && shouldRespell(resolved, head)) {
            return spellHead(head, CasePolicy.PRESERVE);
        }
        return resolved.text();
    }

    private boolean shouldRespell(HeadResult resolved, String head) {
        if (resolved.source() != DecisionSource.RULE) {
            return false;
        }
        String lower = WordShape.lower(head);
        // Without a membership table there is no compaction evidence; retain the documented rules-only
        // spelling heuristic. With a table, a hit means a regular word was omitted by compaction.
        return !lexicon.hasMembership() || !lexicon.isOrdinaryWord(lower);
    }

    /** Applies the productive spelling rules without consulting the lexicon. */
    private String spellHead(String head, CasePolicy policy) {
        if (!WordShape.isPlainLatinToken(head)) {
            return null;
        }
        String lower = WordShape.lower(head);
        if (lower.endsWith("child")) {
            return render(head, lower, 5, "children", policy);
        }
        String[][] fToVes = {
                {"life", "lives"}, {"shelf", "shelves"}, {"half", "halves"}, {"staff", "staves"},
                {"wolf", "wolves"}, {"leaf", "leaves"}, {"loaf", "loaves"}, {"calf", "calves"},
                {"elf", "elves"}, {"self", "selves"}, {"thief", "thieves"}, {"sheaf", "sheaves"},
                {"hoof", "hooves"}
        };
        for (String[] pair : fToVes) {
            if (lower.endsWith(pair[0])) {
                return render(head, lower, pair[0].length(), pair[1], policy);
            }
        }
        return applyRule(head, lower, null, policy);
    }

    /**
     * Admission policy for the generic head-final hyphenated compound path. Exactly one separator, both
     * tokens plain Latin words, a multi-letter final token, no preposition, and a documented non-noun head
     * exclusion. The final token's lexical membership is <em>not</em> required: P2 removes held-out families
     * from the artifact, and requiring membership would collapse the path back to the old behaviour.
     */
    private boolean isHeadFinalCompound(String noun, int hyphen, String tailLower) {
        if (hyphen != noun.lastIndexOf('-')) {
            return false;
        }
        String head = noun.substring(0, hyphen);
        String tail = noun.substring(hyphen + 1);
        if (!WordShape.isPlainLatinToken(head) || !WordShape.isPlainLatinToken(tail)) {
            return false;
        }
        if (WordShape.codePointLength(head) < 1 || WordShape.codePointLength(tail) < 2) {
            return false;
        }
        if (compounds.isNonNounHead(tailLower)
                || (compounds.isPreposition(WordShape.lower(head))
                        && !compounds.isCompoundPrefix(WordShape.lower(head)))
                || compounds.isPreposition(tailLower)
                || compounds.isFreeTail(tailLower)) {
            return false;
        }
        return true;
    }

    /** Multi-hyphen modifier chains ending in a noun head, with no preposition/coordinator in the prefix. */
    private boolean isMultiHeadFinalCompound(String noun) {
        String[] tokens = noun.split("-");
        if (tokens.length < 3) {
            return false;
        }
        for (String token : tokens) {
            if (!WordShape.isPlainLatinToken(token) || WordShape.codePointLength(token) < 1) {
                return false;
            }
        }
        for (int i = 0; i < tokens.length - 1; i++) {
            String tokenLower = WordShape.lower(tokens[i]);
            if (compounds.isPreposition(tokenLower) || compounds.isCoordinator(tokenLower)) {
                return false;
            }
        }
        String tailLower = WordShape.lower(tokens[tokens.length - 1]);
        return !compounds.isPreposition(tailLower) && !compounds.isNonNounHead(tailLower)
                && !compounds.isCoordinator(tailLower);
    }

    /** A single-hyphen verb-particle noun such as {@code bake-off} or {@code blow-in}. */
    private boolean isParticleCompound(String noun, int hyphen, String tailLower) {
        if (hyphen != noun.lastIndexOf('-') || !compounds.isParticle(tailLower)) {
            return false;
        }
        String head = noun.substring(0, hyphen);
        String tail = noun.substring(hyphen + 1);
        return WordShape.isPlainLatinToken(head) && WordShape.isPlainLatinToken(tail)
                && WordShape.codePointLength(head) >= 1 && WordShape.codePointLength(tail) >= 2;
    }

    // ------------------------------------------------------------------ rendering

    private String render(String source, String lower, int cut, String append, CasePolicy policy) {
        switch (policy) {
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
    private String renderFull(String source, String lower, String fullLower, CasePolicy policy) {
        switch (policy) {
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

    private String renderInitialism(String source, String lower, String suffix, CasePolicy policy) {
        return switch (policy) {
            case LOWERCASE -> lower + suffix;
            case UPPERCASE -> source.toUpperCase(Locale.ROOT) + suffix.toUpperCase(Locale.ROOT);
            default -> source + suffix;
        };
    }

    /** Applies one configured case policy to a fully reconstructed compound. */
    private static String applyCasePolicy(String value, CasePolicy policy) {
        return switch (policy) {
            case LOWERCASE -> value.toLowerCase(Locale.ROOT);
            case UPPERCASE -> value.toUpperCase(Locale.ROOT);
            default -> value;
        };
    }

    /** A resolved compound constituent plus the provenance that controls final-head respelling. */
    private record HeadResult(String text, DecisionSource source, String id, boolean invariant) {
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
        private UppercasePolicy uppercasePolicy = UppercasePolicy.INFER_INITIALISM;
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

        /** Selects how unknown all-uppercase tokens are interpreted; defaults to {@link UppercasePolicy#INFER_INITIALISM}. */
        public Builder uppercasePolicy(UppercasePolicy uppercasePolicy) {
            this.uppercasePolicy = Objects.requireNonNull(uppercasePolicy, "uppercasePolicy must not be null");
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
