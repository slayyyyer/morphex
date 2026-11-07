package io.github.slayyyyer.morphex.inflector.eval;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Dependency-free Java mirror of the corrected linear candidate selector.
 *
 * <p>The scorer is research/evaluation code only. It reads the compact float32 artifact written by
 * {@code LinearRanker.save} and mirrors the candidate inventory, feature hashing, softmax selection and
 * residual eligibility gate used by the Python experiment. It does not depend on the deterministic
 * inflector at runtime; callers pass the base plural and its decision source.
 */
public final class LinearSelector {

    private static final byte[] MAGIC = "MORPHEX-LINEAR1\0".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int MIN_DIMS = 1;
    private static final int MAX_DIMS = 1_000_000;
    private static final ThreadLocal<CRC32> CRC = ThreadLocal.withInitial(CRC32::new);

    private static final List<Rule> RULES = List.of(
            new Rule("s", 0, "s", null),
            new Rule("es", 0, "es", null),
            new Rule("ies", 1, "ies", "y"),
            new Rule("ves", 1, "ves", "f"),
            new Rule("fe->ves", 2, "ves", "fe"),
            new Rule("oes", 1, "oes", "o"),
            new Rule("us->i", 2, "i", "us"),
            new Rule("um->a", 2, "a", "um"),
            new Rule("a->ae", 1, "ae", "a"),
            new Rule("ix->ices", 2, "ices", "ix"),
            new Rule("is->es", 2, "es", "is"),
            new Rule("man->men", 3, "men", "man"),
            new Rule("child->ren", 0, "ren", "child"),
            new Rule("mouse->ice", 4, "ice", "mouse"),
            new Rule("person->people", 5, "eople", "person"),
            new Rule("quiz->zzes", 1, "zzes", "z"),
            new Rule("lemma", 0, "", null)
    );

    private final double[] weights;
    private final double threshold;

    private LinearSelector(double[] weights, double threshold) {
        this.weights = weights;
        this.threshold = threshold;
    }

    /** Load and validate the artifact produced by the Python research pipeline. */
    public static LinearSelector load(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length < MAGIC.length + Integer.BYTES * 2 + Float.BYTES) {
            throw new IllegalArgumentException("linear selector artifact is too short: " + data.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new IllegalArgumentException("linear selector artifact has the wrong magic");
        }
        int version = buffer.getInt();
        int dimensions = buffer.getInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported linear selector artifact version: " + version);
        }
        if (dimensions < MIN_DIMS || dimensions > MAX_DIMS) {
            throw new IllegalArgumentException("invalid linear selector dimensions: " + dimensions);
        }
        float threshold = buffer.getFloat();
        if (!Float.isFinite(threshold)) {
            throw new IllegalArgumentException("linear selector threshold is not finite");
        }
        if (buffer.remaining() != dimensions * Float.BYTES) {
            throw new IllegalArgumentException("linear selector artifact length does not match dimensions");
        }
        double[] weights = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            float value = buffer.getFloat();
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("linear selector weight is not finite at index " + i);
            }
            weights[i] = value;
        }
        return new LinearSelector(weights, threshold);
    }

    public int dimensions() {
        return weights.length;
    }

    public double threshold() {
        return threshold;
    }

    /**
     * Apply the residual selector. A non-eligible record returns its base unchanged. An eligible record
     * returns the highest-scoring candidate only when it differs from the base and clears the frozen
     * probability threshold.
     */
    public Selection select(String lemma, String base, String source) {
        if (!eligible(base, source)) {
            return new Selection(base, 1.0, false, source);
        }
        Context context = context(lemma, base);
        List<Candidate> candidates = candidates(lemma, base, context.baseId(), context.low());
        double[] scores = new double[candidates.size()];
        int best = 0;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            scores[i] = score(candidates.get(i), context, source);
            if (scores[i] > maximum) {
                maximum = scores[i];
                best = i;
            }
        }
        double total = 0.0;
        for (double value : scores) {
            total += Math.exp(value - maximum);
        }
        Candidate chosen = candidates.get(best);
        double probability = 1.0 / total;
        boolean overridden = !chosen.form().equals(base) && probability >= threshold;
        return new Selection(overridden ? chosen.form() : base, probability, overridden,
                overridden ? "model:linear" : source);
    }

    private static boolean eligible(String base, String source) {
        return base != null && !base.isEmpty() && source != null
                && (source.endsWith(":rule") || source.endsWith(":fallback"));
    }

    private double score(Candidate candidate, Context context, String source) {
        CRC32 crc = CRC.get();
        double total = 0.0;
        total += weight(crc, "e:" + candidate.editId());
        total += weight(crc, "origin:" + candidate.origin());
        total += weight(crc, "base:" + context.baseId());
        total += weight(crc, "source:" + source);
        total += weight(crc, "edit:" + candidate.editId() + ":cut" + candidate.cut()
                + ":app" + candidate.append());
        for (int size = 1; size <= 3; size++) {
            if (context.lowLength() >= size) {
                total += weight(crc, "s" + size + ":" + tail(context.low(), size)
                        + "|e:" + candidate.editId());
            }
        }
        total += weight(crc, "len:" + context.lengthBucket() + "|e:" + candidate.editId());
        if (context.separator() != null) {
            total += weight(crc, "sep:" + context.separator() + ":" + context.parts().length
                    + "|e:" + candidate.editId());
            total += weight(crc, "head:" + tail(context.parts()[context.parts().length - 1], 4)
                    + "|e:" + candidate.editId());
        }
        if (context.uppercase()) {
            total += weight(crc, "upper:" + candidate.editId());
        }
        return total;
    }

    private static Context context(String lemma, String base) {
        String low = lemma.toLowerCase(Locale.ROOT);
        int lowLength = codePointCount(low);
        String separator = null;
        String[] parts = null;
        if (low.indexOf('-') >= 0) {
            separator = "-";
            parts = low.split("-", -1);
        } else if (low.indexOf(' ') >= 0) {
            separator = " ";
            parts = low.split(" ", -1);
        }
        return new Context(low, lowLength, baseEditId(lemma, base), hasUppercase(lemma), separator,
                parts, Math.min(lowLength / 3, 5));
    }

    private double weight(CRC32 crc, String key) {
        crc.reset();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return weights[(int) (crc.getValue() % weights.length)];
    }

    private static List<Candidate> candidates(String lemma, String base, String baseId, String lower) {
        List<Candidate> result = new ArrayList<>(20);
        result.add(new Candidate(baseId, base, 0, "", true, "base"));
        Set<String> seen = new HashSet<>();
        seen.add(baseId + "\u0000" + base);

        for (Rule rule : RULES) {
            if (rule.requires() != null && !lower.endsWith(rule.requires())) {
                continue;
            }
            push(lemma, result, seen, rule.editId(), rule.cut(), rule.append(), "rule");
        }
        if (isInitialism(lemma)) {
            push(lemma, result, seen, "initialism-s", 0, "s", "initialism");
            push(lemma, result, seen, "initialism-es", 0, "es", "initialism");
        }
        return result;
    }

    private static void push(String lemma, List<Candidate> result, Set<String> seen, String editId, int cut,
                             String append, String origin) {
        String form = applyEdit(lemma, cut, append);
        String key = editId + "\u0000" + form;
        if (seen.add(key)) {
            result.add(new Candidate(editId, form, cut, append, false, origin));
        }
    }

    private static String applyEdit(String lemma, int cut, String append) {
        int length = codePointCount(lemma);
        if (cut <= 0) {
            return lemma + append;
        }
        if (cut >= length) {
            return append;
        }
        int end = lemma.offsetByCodePoints(lemma.length(), -cut);
        return lemma.substring(0, end) + append;
    }

    private static String baseEditId(String lemma, String base) {
        if (base.equals(lemma)) {
            return "base:same";
        }
        String lowerLemma = lemma.toLowerCase(Locale.ROOT);
        String lowerBase = base.toLowerCase(Locale.ROOT);
        if (lowerBase.startsWith(lowerLemma)) {
            String suffix = lowerBase.substring(lowerLemma.length());
            switch (suffix) {
                case "s" -> {
                    return "base:s";
                }
                case "es" -> {
                    return "base:es";
                }
                case "ies" -> {
                    return "base:ies";
                }
                case "ves" -> {
                    return "base:ves";
                }
                case "oes" -> {
                    return "base:oes";
                }
                default -> {
                }
            }
        }
        int length = codePointCount(lowerLemma);
        if (length >= 2 && lowerBase.equals(dropLast(lowerLemma, 1) + "ies")) {
            return "base:ies";
        }
        if (length >= 2 && lowerBase.equals(dropLast(lowerLemma, 2) + "i")
                && lowerLemma.endsWith("us")) {
            return "base:us-i";
        }
        if (length >= 2 && lowerBase.equals(dropLast(lowerLemma, 2) + "a")
                && lowerLemma.endsWith("um")) {
            return "base:um-a";
        }
        if (length >= 2 && lowerBase.equals(dropLast(lowerLemma, 2) + "ices")
                && lowerLemma.endsWith("ix")) {
            return "base:ix-ices";
        }
        if (length >= 2 && lowerBase.equals(dropLast(lowerLemma, 2) + "es")
                && lowerLemma.endsWith("is")) {
            return "base:is-es";
        }
        if (length >= 3 && lowerBase.equals(dropLast(lowerLemma, 3) + "men")
                && lowerLemma.endsWith("man")) {
            return "base:man-men";
        }
        return "base:other";
    }

    private static boolean isInitialism(String lemma) {
        int length = codePointCount(lemma);
        if (length < 2 || length > 8) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        for (int index = 0; index < lemma.length(); ) {
            int codePoint = lemma.codePointAt(index);
            upper |= Character.isUpperCase(codePoint);
            lower |= Character.isLowerCase(codePoint);
            index += Character.charCount(codePoint);
        }
        return upper && !lower;
    }

    private static boolean hasUppercase(String value) {
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (Character.isUpperCase(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String tail(String value, int count) {
        int length = codePointCount(value);
        if (length <= count) {
            return value;
        }
        return value.substring(value.offsetByCodePoints(0, length - count));
    }

    private static String dropLast(String value, int count) {
        return value.substring(0, value.offsetByCodePoints(value.length(), -count));
    }

    private record Rule(String editId, int cut, String append, String requires) {
    }

    private record Context(String low, int lowLength, String baseId, boolean uppercase, String separator,
                           String[] parts, int lengthBucket) {
    }

    private record Candidate(String editId, String form, int cut, String append, boolean isBase,
                             String origin) {
    }

    /** Result of one residual-selector invocation. */
    public record Selection(String plural, double probability, boolean overridden, String source) {
    }
}
