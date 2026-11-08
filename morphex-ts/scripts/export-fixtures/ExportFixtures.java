import io.github.slayyyyer.morphex.inflector.EnglishInflector;
import io.github.slayyyyer.morphex.inflector.Inflection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports the shared conformance fixtures from the Java engine.
 *
 * <p>Run from the repository root with the inflector-core classes on the classpath:
 *
 * <pre>
 * java -cp inflector-core/target/classes \
 *   morphex-ts/scripts/export-fixtures/ExportFixtures.java \
 *   results/release-1.2.0/eval/p1-test-gold.tsv test-fixtures
 * </pre>
 *
 * <p>Output is deterministic: no timestamps, no locale-dependent formatting, LF newlines, so the
 * CI drift check can compare the committed files byte for byte.
 */
public final class ExportFixtures {

    private static final String SCHEMA = "morphex-fixtures-v1";

    private ExportFixtures() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: ExportFixtures <p1-gold.tsv> <output-dir>");
        }
        Path gold = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);
        EnglishInflector inflector = EnglishInflector.standard();
        writeP1(inflector, gold, outDir.resolve("p1.json"));
        writeProbe(inflector, outDir.resolve("upstream-probe.json"));
    }

    private static void writeP1(EnglishInflector inflector, Path gold, Path target) throws IOException {
        StringBuilder out = new StringBuilder(8 << 20);
        out.append("{\n");
        out.append("  \"schema\": ").append(q(SCHEMA)).append(",\n");
        out.append("  \"kind\": \"p1\",\n");
        out.append("  \"source\": ").append(q("results/release-1.2.0/eval/p1-test-gold.tsv")).append(",\n");
        out.append("  \"engine\": ").append(q("EnglishInflector 1.4.0")).append(",\n");

        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(gold, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("id\t")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 8) {
                throw new IllegalStateException("malformed gold row: " + line);
            }
            String id = fields[0];
            String lemma = fields[1];
            String countability = fields[4];
            List<String> accepted = new ArrayList<>();
            for (String form : fields[6].split(",")) {
                if (!form.isEmpty()) {
                    accepted.add(form);
                }
            }
            boolean primary = ("COUNTABLE".equals(countability) || "MIXED".equals(countability))
                    && "0".equals(fields[5]);

            Inflection result = inflector.analyze(lemma);
            StringBuilder row = new StringBuilder(160);
            row.append("    [")
                    .append(q(id)).append(", ")
                    .append(q(lemma)).append(", ")
                    .append(q(result.plural())).append(", ");
            appendArray(row, accepted);
            row.append(", ").append(primary ? '1' : '0')
                    .append(", ").append(q(result.countability().name()))
                    .append(", ").append(result.invariant() ? '1' : '0')
                    .append(", ").append(q(result.source().name()))
                    .append(", ").append(q(result.id()))
                    .append(", ").append(result.ambiguous() ? '1' : '0')
                    .append("]");
            rows.add(row.toString());
        }

        out.append("  \"count\": ").append(rows.size()).append(",\n");
        out.append("  \"cases\": [\n");
        out.append(String.join(",\n", rows)).append('\n');
        out.append("  ]\n");
        out.append("}\n");
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + target + " (" + rows.size() + " cases)");
    }

    private static void writeProbe(EnglishInflector inflector, Path target) throws IOException {
        List<String> inputs = List.of(
                "German", "blouse", "scarp", "UFO", "mother-in-law", "passer-by", "child", "CHILD",
                "city", "CITY", "mouse", "MOUSE", "SMS", "scissors", "information", "analysis",
                "quiz", "die", "person", "sheep", "cactus", "stand-in", "drive-by",
                "attorney general", "ice cream", "café");
        StringBuilder out = new StringBuilder(4096);
        out.append("{\n");
        out.append("  \"schema\": ").append(q(SCHEMA)).append(",\n");
        out.append("  \"kind\": \"upstream-probe\",\n");
        out.append("  \"source\": ").append(q("docs/upstream-probe.md")).append(",\n");
        out.append("  \"engine\": ").append(q("EnglishInflector 1.4.0")).append(",\n");
        out.append("  \"count\": ").append(inputs.size()).append(",\n");
        out.append("  \"cases\": [\n");
        List<String> rows = new ArrayList<>();
        for (String input : inputs) {
            Inflection result = inflector.analyze(input);
            rows.add("    [" + q(input) + ", " + q(result.plural()) + ", "
                    + q(inflector.pluralize(input, 1)) + ", "
                    + q(result.source().name()) + ", " + q(result.id()) + "]");
        }
        out.append(String.join(",\n", rows)).append('\n');
        out.append("  ]\n");
        out.append("}\n");
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + target + " (" + inputs.size() + " cases)");
    }

    private static void appendArray(StringBuilder out, List<String> values) {
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            out.append(q(values.get(index)));
        }
        out.append(']');
    }

    private static String q(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}
