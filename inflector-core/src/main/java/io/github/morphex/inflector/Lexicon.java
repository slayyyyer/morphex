package io.github.morphex.inflector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable lexical resource: exact lemma entries, loaded once from the versioned generated artifact.
 *
 * <p>The artifact is plain TSV so that it can be diffed, validated and regenerated offline. Lookup keys are
 * lower-cased ({@link WordShape#lower(String)}); entries that must not be reachable from a different casing
 * carry the {@code C} flag and are additionally compared against the caller's exact spelling.
 *
 * <p>Each entry stores a derived <em>edit</em> (how many trailing code units to remove and what to append)
 * rather than a finished plural. Applying the edit to the caller's token preserves internal capitals and
 * makes whole-token re-casing a policy decision instead of an accident.
 */
final class Lexicon {

    static final String RESOURCE = "lexicon-v1.tsv";
    private static final int COLUMNS = 7;

    static final Lexicon EMPTY = new Lexicon(Map.of());

    static final class Entry {
        final String lemma;
        final String plural;
        final String classical;
        final List<String> alternatives;
        final Countability countability;
        final boolean invariant;
        final boolean initialism;
        final boolean caseSensitive;
        final boolean ambiguous;
        final String source;
        final int cut;
        final String append;
        final int classicalCut;
        final String classicalAppend;

        Entry(String lemma,
              String plural,
              String classical,
              List<String> alternatives,
              Countability countability,
              boolean invariant,
              boolean initialism,
              boolean caseSensitive,
              boolean ambiguous,
              String source) {
            this.lemma = lemma;
            this.plural = plural;
            this.classical = classical;
            this.alternatives = List.copyOf(alternatives);
            this.countability = countability;
            this.invariant = invariant;
            this.initialism = initialism;
            this.caseSensitive = caseSensitive;
            this.ambiguous = ambiguous;
            this.source = source;
            int[] edit = deriveEdit(lemma, plural);
            this.cut = edit[0];
            this.append = plural.substring(lemma.length() - cut);
            if (classical == null) {
                this.classicalCut = -1;
                this.classicalAppend = "";
            } else {
                int[] classicalEdit = deriveEdit(lemma, classical);
                this.classicalCut = classicalEdit[0];
                this.classicalAppend = classical.substring(lemma.length() - classicalCut);
            }
        }

        String id() {
            return "lex:" + lemma;
        }

        boolean matchesCase(String token) {
            return !caseSensitive || lemma.equals(token);
        }
    }

    /**
     * Computes how many trailing characters of {@code lemma} to replace so that the result ends with
     * {@code plural}. Example: {@code mouse/mice} -> cut 4, append "ice"; {@code child/children} -> cut 0.
     */
    static int[] deriveEdit(String lemma, String plural) {
        int max = Math.min(lemma.length(), plural.length());
        int prefix = 0;
        while (prefix < max && lemma.charAt(prefix) == plural.charAt(prefix)) {
            prefix++;
        }
        return new int[] {lemma.length() - prefix, prefix};
    }

    private final Map<String, Entry> byLemma;

    private Lexicon(Map<String, Entry> byLemma) {
        this.byLemma = byLemma;
    }

    Entry lookup(String lower) {
        return byLemma.get(lower);
    }

    int size() {
        return byLemma.size();
    }

    Set<String> lemmas() {
        return Collections.unmodifiableSet(byLemma.keySet());
    }

    private static final class Bundled {
        static final Lexicon INSTANCE = loadBundled();
    }

    static Lexicon bundled() {
        return Bundled.INSTANCE;
    }

    /**
     * Offline evaluation hook: points the bundled lookup at an alternative artifact (for example a
     * leakage-safe train-only lexicon). Not part of the supported application API; see README.
     */
    private static final String LEXICON_PROPERTY = "english.inflector.lexicon";

    private static Lexicon loadBundled() {
        String external = System.getProperty(LEXICON_PROPERTY);
        if (external != null && !external.isEmpty()) {
            try (Reader reader = new BufferedReader(java.nio.file.Files.newBufferedReader(
                    java.nio.file.Path.of(external), StandardCharsets.UTF_8))) {
                return load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("could not read lexicon from " + external, e);
            }
        }
        InputStream in = Lexicon.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException(
                    "bundled lexicon resource '" + RESOURCE + "' is missing from the jar; "
                            + "build without it via EnglishInflector.builder().withoutBundledLexicon()");
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("could not read bundled lexicon", e);
        }
    }

    /** Loads an artifact from any reader. Package-private: consumers use the bundled resource. */
    static Lexicon load(Reader source) throws IOException {
        Objects.requireNonNull(source, "source");
        BufferedReader reader = source instanceof BufferedReader br ? br : new BufferedReader(source);
        Map<String, Entry> map = new HashMap<>(1 << 16);
        String line;
        int lineNo = 0;
        boolean headerSeen = false;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
                if (line.startsWith("lemma\t")) {
                    continue;
                }
            }
            String[] f = line.split("\t", -1);
            if (f.length != COLUMNS) {
                throw new IllegalArgumentException(
                        "lexicon line " + lineNo + ": expected " + COLUMNS + " columns, found " + f.length);
            }
            String lemma = f[0];
            String plural = f[1];
            String classical = "-".equals(f[2]) ? null : f[2];
            List<String> alternatives = parseAlternatives(f[3]);
            Countability countability;
            try {
                countability = Countability.valueOf(f[4]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("lexicon line " + lineNo + ": bad countability " + f[4], e);
            }
            String flags = f[5];
            String sourceId = f[6];
            if (lemma.isEmpty() || plural.isEmpty()) {
                throw new IllegalArgumentException("lexicon line " + lineNo + ": empty lemma or plural");
            }
            boolean invariant = flags.indexOf('I') >= 0;
            boolean initialism = flags.indexOf('N') >= 0;
            boolean caseSensitive = flags.indexOf('C') >= 0;
            boolean ambiguous = flags.indexOf('A') >= 0;
            Entry entry = new Entry(lemma, plural, classical, alternatives, countability,
                    invariant, initialism, caseSensitive, ambiguous, sourceId);
            String key = WordShape.lower(lemma);
            Entry previous = map.putIfAbsent(key, entry);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "lexicon line " + lineNo + ": duplicate lemma key '" + key + "'");
            }
        }
        return new Lexicon(Map.copyOf(map));
    }

    private static List<String> parseAlternatives(String field) {
        if (field == null || field.isEmpty() || "-".equals(field)) {
            return List.of();
        }
        String[] parts = field.split(",", -1);
        Set<String> unique = new LinkedHashSet<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                unique.add(part);
            }
        }
        return new ArrayList<>(unique);
    }
}
