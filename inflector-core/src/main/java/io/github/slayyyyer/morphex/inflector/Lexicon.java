package io.github.slayyyyer.morphex.inflector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>In memory the lexicon is columnar rather than an object graph: keys are concatenated into one
 * {@code char[]}, an open-addressed {@code int[]} table stores entry indexes, and per-entry metadata lives in
 * primitive columns. Full plural strings are retained only for the entries where diagnostics can expose them
 * (an alternative or a classical form); the rest are represented by the derived edit, which is enough for the
 * fast rendering path. This keeps the retained heap proportional to the artifact rather than to the number of
 * Java objects a naive map would create.
 */
final class Lexicon {

    static final String RESOURCE = "lexicon-v2.tsv";
    private static final int COLUMNS = 7;

    private static final int FLAG_INVARIANT = 1;
    private static final int FLAG_INITIALISM = 2;
    private static final int FLAG_CASE_SENSITIVE = 4;
    private static final int FLAG_AMBIGUOUS = 8;

    private static final byte NO_CLASSICAL = (byte) 0xFF;
    private static final Countability[] COUNTABILITIES = Countability.values();

    static final Lexicon EMPTY = new Lexicon(new Data());

    private final Data data;
    private final Membership membership;

    private Lexicon(Data data) {
        this(data, Membership.EMPTY);
    }

    private Lexicon(Data data, Membership membership) {
        this.data = data;
        this.membership = membership;
    }

    /** Returns the entry index for a lower-cased lemma, or {@code -1}. */
    int find(String lower) {
        int mask = data.table.length - 1;
        int hash = lower.hashCode();
        int slot = (hash ^ (hash >>> 16)) & mask;
        while (true) {
            int stored = data.table[slot];
            if (stored == 0) {
                return -1;
            }
            int index = stored - 1;
            if (keyEquals(data.keys, data.keyOffsets, index, lower)) {
                return index;
            }
            slot = (slot + 1) & mask;
        }
    }

    int size() {
        return data.size;
    }

    /** True when the lower-cased lemma is in the permitted corpus membership table. */
    boolean isOrdinaryWord(String lower) {
        return membership.contains(lower);
    }

    boolean hasMembership() {
        return !membership.isEmpty();
    }

    boolean matchesCase(int index, String token) {
        return !caseSensitive(index) || data.caseLemmas[data.caseLemmaRefs[index]].equals(token);
    }

    String id(int index) {
        return "lex:" + (caseSensitive(index)
                ? data.caseLemmas[data.caseLemmaRefs[index]]
                : keyString(index));
    }

    Countability countability(int index) {
        return COUNTABILITIES[data.countabilities[index] & 0xFF];
    }

    boolean invariant(int index) {
        return (data.flags[index] & FLAG_INVARIANT) != 0;
    }

    boolean initialism(int index) {
        return (data.flags[index] & FLAG_INITIALISM) != 0;
    }

    boolean caseSensitive(int index) {
        return (data.flags[index] & FLAG_CASE_SENSITIVE) != 0;
    }

    boolean ambiguous(int index) {
        return (data.flags[index] & FLAG_AMBIGUOUS) != 0;
    }

    int cut(int index) {
        return data.cuts[index] & 0xFF;
    }

    String append(int index) {
        return data.appendPool[data.appendIds[index] & 0xFFFF];
    }

    int classicalCut(int index) {
        return data.classicalCuts[index] == NO_CLASSICAL ? -1 : data.classicalCuts[index] & 0xFF;
    }

    String classicalAppend(int index) {
        return data.classicalAppendPool[data.classicalAppendIds[index] & 0xFFFF];
    }

    String classical(int index) {
        int ref = data.classicalRefs[index];
        return ref < 0 ? null : data.classicals[ref];
    }

    String plural(int index) {
        int ref = data.pluralRefs[index];
        if (ref >= 0) {
            return data.plurals[ref];
        }
        // Only reachable if a caller asks for a diagnostic form that was not retained; reconstructing it is
        // cheaper than retaining 157k full plural strings, and the result is still derived from the artifact.
        String key = keyString(index);
        int cut = cut(index);
        return cut == 0 ? key + append(index) : key.substring(0, key.length() - cut) + append(index);
    }

    boolean hasAlternatives(int index) {
        return data.alternativeRefs[index] >= 0;
    }

    List<String> alternatives(int index) {
        int ref = data.alternativeRefs[index];
        if (ref < 0) {
            return List.of();
        }
        return List.of(data.alternatives[ref].split("\u0000", -1));
    }

    private String keyString(int index) {
        int start = data.keyOffsets[index];
        int end = data.keyOffsets[index + 1];
        return new String(data.keys, start, end - start);
    }

    private static boolean keyEquals(char[] keys, int[] offsets, int index, String token) {
        int start = offsets[index];
        int length = offsets[index + 1] - start;
        if (length != token.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (keys[start + i] != token.charAt(i)) {
                return false;
            }
        }
        return true;
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
    private static final String MEMBERSHIP_RESOURCE = "lexicon-membership-v2.bin";

    private static Lexicon loadBundled() {
        String external = System.getProperty(LEXICON_PROPERTY);
        if (external != null && !external.isEmpty()) {
            try (Reader reader = new BufferedReader(java.nio.file.Files.newBufferedReader(
                    java.nio.file.Path.of(external), StandardCharsets.UTF_8))) {
                java.nio.file.Path sibling = java.nio.file.Path.of(external).resolveSibling(MEMBERSHIP_RESOURCE);
                Membership membership = java.nio.file.Files.exists(sibling)
                        ? loadMembership(java.nio.file.Files.newInputStream(sibling))
                        : Membership.EMPTY;
                return load(reader, membership);
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
            InputStream membershipStream = Lexicon.class.getResourceAsStream(MEMBERSHIP_RESOURCE);
            Membership membership = membershipStream == null
                    ? Membership.EMPTY
                    : loadMembership(membershipStream);
            return load(reader, membership);
        } catch (IOException e) {
            throw new IllegalStateException("could not read bundled lexicon", e);
        }
    }

    /** Loads an artifact from any reader. Package-private: consumers use the bundled resource. */
    static Lexicon load(Reader source) throws IOException {
        return load(source, Membership.EMPTY);
    }

    static Lexicon load(Reader source, Membership membership) throws IOException {
        Objects.requireNonNull(source, "source");
        BufferedReader reader = source instanceof BufferedReader br ? br : new BufferedReader(source);
        List<Raw> raws = new ArrayList<>(1 << 17);
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
            if (lemma.isEmpty() || plural.isEmpty()) {
                throw new IllegalArgumentException("lexicon line " + lineNo + ": empty lemma or plural");
            }
            boolean invariant = flags.indexOf('I') >= 0;
            boolean initialism = flags.indexOf('N') >= 0;
            boolean caseSensitive = flags.indexOf('C') >= 0;
            boolean ambiguous = flags.indexOf('A') >= 0;

            Raw raw = new Raw();
            raw.lemma = lemma;
            raw.key = WordShape.lower(lemma);
            raw.plural = plural;
            raw.classical = classical;
            raw.alternatives = alternatives.isEmpty() ? null : String.join("\u0000", alternatives);
            raw.countability = countability;
            raw.caseSensitive = caseSensitive;
            raw.flags = (invariant ? FLAG_INVARIANT : 0)
                    | (initialism ? FLAG_INITIALISM : 0)
                    | (caseSensitive ? FLAG_CASE_SENSITIVE : 0)
                    | (ambiguous ? FLAG_AMBIGUOUS : 0);
            int[] edit = deriveEdit(lemma, plural);
            raw.cut = edit[0];
            raw.append = plural.substring(lemma.length() - raw.cut);
            if (classical != null) {
                int[] classicalEdit = deriveEdit(lemma, classical);
                raw.classicalCut = classicalEdit[0];
                raw.classicalAppend = classical.substring(lemma.length() - raw.classicalCut);
            }
            raws.add(raw);
        }
        return new Lexicon(pack(raws), membership);
    }

    private static Membership loadMembership(InputStream source) throws IOException {
        try (InputStream in = source) {
            return Membership.load(in);
        }
    }

    private static Data pack(List<Raw> raws) {
        int size = raws.size();
        Data data = new Data();
        data.size = size;

        int keyChars = 0;
        for (Raw raw : raws) {
            keyChars += raw.key.length();
        }
        data.keys = new char[keyChars];
        data.keyOffsets = new int[size + 1];
        data.flags = new byte[size];
        data.countabilities = new byte[size];
        data.cuts = new byte[size];
        data.appendIds = new short[size];
        data.pluralRefs = new int[size];
        data.classicalRefs = new int[size];
        data.classicalCuts = new byte[size];
        data.classicalAppendIds = new short[size];
        data.alternativeRefs = new int[size];
        data.caseLemmaRefs = new int[size];
        Arrays.fill(data.pluralRefs, -1);
        Arrays.fill(data.classicalRefs, -1);
        Arrays.fill(data.classicalCuts, NO_CLASSICAL);
        Arrays.fill(data.alternativeRefs, -1);
        Arrays.fill(data.caseLemmaRefs, -1);

        Map<String, Integer> appendIndex = new HashMap<>();
        List<String> appendPool = new ArrayList<>();
        Map<String, Integer> classicalAppendIndex = new HashMap<>();
        List<String> classicalAppendPool = new ArrayList<>();
        List<String> plurals = new ArrayList<>();
        List<String> classicals = new ArrayList<>();
        List<String> alternatives = new ArrayList<>();
        List<String> caseLemmas = new ArrayList<>();

        int keyPosition = 0;
        int tableSize = 1;
        while (tableSize * 7 < size * 10) {
            tableSize <<= 1;
        }
        data.table = new int[tableSize];

        int mask = tableSize - 1;
        for (int index = 0; index < size; index++) {
            Raw raw = raws.get(index);
            data.keyOffsets[index] = keyPosition;
            raw.key.getChars(0, raw.key.length(), data.keys, keyPosition);
            keyPosition += raw.key.length();
            data.keyOffsets[index + 1] = keyPosition;

            data.flags[index] = (byte) raw.flags;
            data.countabilities[index] = (byte) raw.countability.ordinal();
            data.cuts[index] = (byte) raw.cut;
            data.appendIds[index] = (short) appendId(appendIndex, appendPool, raw.append);

            if (raw.alternatives != null || raw.classical != null) {
                data.pluralRefs[index] = plurals.size();
                plurals.add(raw.plural);
            }
            if (raw.classical != null) {
                data.classicalRefs[index] = classicals.size();
                classicals.add(raw.classical);
                data.classicalCuts[index] = (byte) raw.classicalCut;
                data.classicalAppendIds[index] =
                        (short) appendId(classicalAppendIndex, classicalAppendPool, raw.classicalAppend);
            }
            if (raw.alternatives != null) {
                data.alternativeRefs[index] = alternatives.size();
                alternatives.add(raw.alternatives);
            }
            if (raw.caseSensitive) {
                data.caseLemmaRefs[index] = caseLemmas.size();
                caseLemmas.add(raw.lemma);
            }

            int hash = raw.key.hashCode();
            int slot = (hash ^ (hash >>> 16)) & mask;
            while (data.table[slot] != 0) {
                int other = data.table[slot] - 1;
                if (keyEquals(data.keys, data.keyOffsets, other, raw.key)) {
                    throw new IllegalArgumentException(
                            "duplicate lemma key '" + raw.key + "' (artifact line " + (index + 2) + ")");
                }
                slot = (slot + 1) & mask;
            }
            data.table[slot] = index + 1;
        }

        data.appendPool = appendPool.toArray(new String[0]);
        data.classicalAppendPool = classicalAppendPool.toArray(new String[0]);
        data.plurals = plurals.toArray(new String[0]);
        data.classicals = classicals.toArray(new String[0]);
        data.alternatives = alternatives.toArray(new String[0]);
        data.caseLemmas = caseLemmas.toArray(new String[0]);
        // The temporary raw records and append indexes are collectible after this method returns.
        return data;
    }

    private static int appendId(Map<String, Integer> index, List<String> pool, String value) {
        Integer existing = index.get(value);
        if (existing != null) {
            return existing;
        }
        int id = pool.size();
        if (id > 0xFFFF) {
            throw new IllegalStateException("too many distinct append suffixes");
        }
        pool.add(value);
        index.put(value, id);
        return id;
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

    /** Mutable parse record, used only while loading; it is not retained by the lexicon. */
    private static final class Raw {
        String lemma;
        String key;
        String plural;
        String classical;
        String alternatives;
        String append;
        String classicalAppend;
        Countability countability;
        int flags;
        int cut;
        int classicalCut;
        boolean caseSensitive;
    }

    /** The immutable packed columns. */
    private static final class Data {
        int size;
        char[] keys = new char[0];
        int[] keyOffsets = new int[] {0};
        int[] table = new int[1];
        byte[] flags = new byte[0];
        byte[] countabilities = new byte[0];
        byte[] cuts = new byte[0];
        short[] appendIds = new short[0];
        String[] appendPool = new String[0];
        int[] pluralRefs = new int[0];
        String[] plurals = new String[0];
        int[] classicalRefs = new int[0];
        String[] classicals = new String[0];
        byte[] classicalCuts = new byte[0];
        short[] classicalAppendIds = new short[0];
        String[] classicalAppendPool = new String[0];
        int[] alternativeRefs = new int[0];
        String[] alternatives = new String[0];
        int[] caseLemmaRefs = new int[0];
        String[] caseLemmas = new String[0];
    }
}
