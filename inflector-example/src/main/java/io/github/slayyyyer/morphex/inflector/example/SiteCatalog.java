package io.github.slayyyyer.morphex.inflector.example;

import io.github.slayyyyer.morphex.inflector.CasePolicy;
import io.github.slayyyyer.morphex.inflector.EnglishInflector;
import io.github.slayyyyer.morphex.inflector.Inflection;
import io.github.slayyyyer.morphex.inflector.Mode;
import io.github.slayyyyer.morphex.inflector.UppercasePolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates {@code site/catalog.json} from the live engine. The website never reimplements
 * pluralization: it searches and displays these verified rows, and says so when the catalog is
 * unavailable. Run with:
 *
 * <pre>
 * ./mvnw -q -pl inflector-example -am compile exec:java \
 *   -Dexec.mainClass=io.github.slayyyyer.morphex.inflector.example.SiteCatalog \
 *   -Dexec.args="site/catalog.json"
 * </pre>
 */
public final class SiteCatalog {

    private static final String SCHEMA = "morphex-site-catalog-v1";

    private SiteCatalog() {
    }

    public static void main(String[] args) throws IOException {
        Path target = Path.of(args.length == 0 ? "site/catalog.json" : args[0]);
        String json = catalogJson();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, json, StandardCharsets.UTF_8);
        System.out.println("wrote " + target + " (" + cases().size() + " verified cases)");
    }

    static String catalogJson() {
        Map<Config, EnglishInflector> engines = new LinkedHashMap<>();
        List<Spec> specs = cases();
        StringBuilder out = new StringBuilder(32_768);
        out.append("{\n");
        out.append("  \"schema\": ").append(q(SCHEMA)).append(",\n");
        out.append("  \"generatedBy\": \"SiteCatalog\",\n");
        out.append("  \"engine\": \"EnglishInflector\",\n");
        out.append("  \"datasetVersion\": \"nouns-v2\",\n");
        out.append("  \"artifactVersion\": \"lexicon-v2\",\n");
        out.append("  \"arbitraryInput\": false,\n");
        out.append("  \"note\": \"Every row was produced by the Java engine at generation time. "
                + "The site searches this fixed catalog; it does not pluralize arbitrary browser input.\",\n");
        out.append("  \"count\": ").append(specs.size()).append(",\n");
        out.append("  \"cases\": [\n");
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            EnglishInflector engine = engines.computeIfAbsent(spec.config(), Config::build);
            Inflection result = engine.analyze(spec.input());
            appendCase(out, spec, result);
            out.append(i + 1 == specs.size() ? "\n" : ",\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendCase(StringBuilder out, Spec spec, Inflection result) {
        out.append("    {\n");
        field(out, "id", q(spec.id()), true, 6);
        field(out, "group", q(spec.group()), true, 6);
        field(out, "input", q(spec.input()), true, 6);
        field(out, "featured", Boolean.toString(spec.featured()), true, 6);
        field(out, "casePolicy", q(spec.config().policy().name()), true, 6);
        field(out, "mode", q(spec.config().mode().name()), true, 6);
        field(out, "uppercasePolicy", q(spec.config().uppercase().name()), true, 6);
        out.append("      \"initialisms\": [");
        int index = 0;
        for (String initialism : spec.config().initialisms()) {
            if (index++ > 0) {
                out.append(", ");
            }
            out.append(q(initialism));
        }
        out.append("],\n");
        out.append("      \"overrides\": {");
        index = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(spec.config().overrides()).entrySet()) {
            if (index++ > 0) {
                out.append(", ");
            }
            out.append(q(entry.getKey())).append(": ").append(q(entry.getValue()));
        }
        out.append("},\n");
        field(out, "plural", q(result.plural()), true, 6);
        field(out, "source", q(result.source().name()), true, 6);
        field(out, "sourceId", q(result.id()), true, 6);
        field(out, "countability", q(result.countability().name()), true, 6);
        field(out, "invariant", Boolean.toString(result.invariant()), true, 6);
        field(out, "ambiguous", Boolean.toString(result.ambiguous()), true, 6);
        out.append("      \"alternatives\": [");
        index = 0;
        for (String alternative : result.alternatives()) {
            if (index++ > 0) {
                out.append(", ");
            }
            out.append(q(alternative));
        }
        out.append("],\n");
        field(out, "note", q(result.note()), false, 6);
        out.append("    }");
    }

    private static void field(StringBuilder out, String name, String value, boolean comma, int indent) {
        out.append(" ".repeat(indent)).append(q(name)).append(": ").append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static List<Spec> cases() {
        List<Spec> specs = new ArrayList<>();
        add(specs, "regular-cat", "Regular", "cat", true);
        add(specs, "regular-city", "Regular", "city", false);
        add(specs, "regular-box", "Regular", "box", false);
        add(specs, "regular-analysis", "Regular", "analysis", false);
        add(specs, "regular-quiz", "Regular", "quiz", false);
        add(specs, "regular-knife", "Regular", "knife", false);
        add(specs, "regular-roof", "Regular", "roof", false);

        add(specs, "irregular-child", "Irregular", "child", true);
        add(specs, "irregular-mouse", "Irregular", "mouse", false);
        add(specs, "irregular-die", "Irregular", "die", true);
        add(specs, "irregular-person", "Irregular", "person", false);
        add(specs, "irregular-sheep", "Irregular", "sheep", false);
        add(specs, "irregular-ox", "Irregular", "ox", false);
        add(specs, "irregular-wife", "Irregular", "wife", false);

        add(specs, "compound-mother-in-law", "Compounds", "mother-in-law", true);
        add(specs, "compound-passer-by", "Compounds", "passer-by", false);
        add(specs, "compound-attorney-general", "Compounds", "attorney general", false);
        add(specs, "compound-credit-card", "Compounds", "credit-card", false);
        add(specs, "compound-ice-cream", "Compounds", "ice cream", false);
        add(specs, "compound-jack-in-the-box", "Compounds", "jack in the box", false);
        add(specs, "compound-well-known", "Compounds", "well-known", false);

        add(specs, "casing-child-upper", "Casing", "CHILD", true);
        add(specs, "casing-child-capitalized", "Casing", "Child", false);
        add(specs, "casing-iphone", "Casing", "iPhone", false);
        add(specs, "casing-blue-card-upper", "Casing", "blue-card", true,
                CasePolicy.UPPERCASE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());
        add(specs, "casing-blue-card-lower", "Casing", "BLUE-CARD", false,
                CasePolicy.LOWERCASE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());
        add(specs, "casing-custom-mother-preserve", "Casing", "CUSTOM-MOTHER-IN-LAW", false);
        add(specs, "casing-custom-mother-lower", "Casing", "CUSTOM-MOTHER-IN-LAW", false,
                CasePolicy.LOWERCASE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());

        add(specs, "countability-information", "Countability", "information", true);
        add(specs, "countability-scissors", "Countability", "scissors", false);
        add(specs, "countability-water", "Countability", "water", false);
        add(specs, "countability-fish", "Countability", "fish", false);
        add(specs, "countability-cattle", "Countability", "cattle", false);

        add(specs, "initialism-ufo", "Initialisms", "UFO", true);
        add(specs, "initialism-sms", "Initialisms", "SMS", false);
        add(specs, "initialism-api", "Initialisms", "API", false);
        add(specs, "initialism-word-zqke", "Initialisms", "word-ZQKE", false);
        add(specs, "initialism-literal-word-zqke", "Initialisms", "word-ZQKE", false,
                CasePolicy.PRESERVE, Mode.ANGLICIZED, UppercasePolicy.LITERAL,
                Map.of(), Set.of());
        add(specs, "initialism-g-pon", "Initialisms", "G-PON", false);
        add(specs, "initialism-declared-xyz", "Initialisms", "XYZ", false,
                CasePolicy.PRESERVE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of("XYZ"));

        add(specs, "mode-cactus-anglicized", "Modes", "cactus", false);
        add(specs, "mode-cactus-classical", "Modes", "cactus", false,
                CasePolicy.PRESERVE, Mode.CLASSICAL, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());
        add(specs, "mode-index-anglicized", "Modes", "index", false);
        add(specs, "mode-index-classical", "Modes", "index", false,
                CasePolicy.PRESERVE, Mode.CLASSICAL, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());
        add(specs, "mode-formula-anglicized", "Modes", "formula", false);
        add(specs, "mode-formula-classical", "Modes", "formula", false,
                CasePolicy.PRESERVE, Mode.CLASSICAL, UppercasePolicy.INFER_INITIALISM,
                Map.of(), Set.of());

        add(specs, "override-information", "Overrides", "information", false,
                CasePolicy.PRESERVE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of("information", "informations"), Set.of());
        add(specs, "override-zorp-compound", "Overrides", "eco-zorp", false,
                CasePolicy.PRESERVE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of("zorp", "zorpi"), Set.of());
        // Overrides are literal exact replacements; they are not re-cased by the configured policy.
        add(specs, "override-blue-card", "Overrides", "blue-card", false,
                CasePolicy.PRESERVE, Mode.ANGLICIZED, UppercasePolicy.INFER_INITIALISM,
                Map.of("blue-card", "blau-karte"), Set.of());

        add(specs, "unsupported-user-id", "Unsupported", "user_id", true);
        add(specs, "unsupported-version", "Unsupported", "3.14", false);
        add(specs, "unsupported-trailing-space", "Unsupported", "red cat ", false);
        add(specs, "unsupported-repeated-space", "Unsupported", "red  cat", false);
        add(specs, "unsupported-non-latin", "Unsupported", "日本", false);
        return List.copyOf(specs);
    }

    private static void add(List<Spec> specs, String id, String group, String input, boolean featured) {
        add(specs, id, group, input, featured, CasePolicy.PRESERVE, Mode.ANGLICIZED,
                UppercasePolicy.INFER_INITIALISM, Map.of(), Set.of());
    }

    private static void add(List<Spec> specs, String id, String group, String input, boolean featured,
            CasePolicy policy, Mode mode, UppercasePolicy uppercase,
            Map<String, String> overrides, Set<String> initialisms) {
        specs.add(new Spec(id, group, input, featured,
                new Config(policy, mode, uppercase, Map.copyOf(overrides), Set.copyOf(initialisms))));
    }

    private static String q(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private record Spec(String id, String group, String input, boolean featured, Config config) {
    }

    private record Config(CasePolicy policy, Mode mode, UppercasePolicy uppercase,
            Map<String, String> overrides, Set<String> initialisms) {
        EnglishInflector build() {
            EnglishInflector.Builder builder = EnglishInflector.builder()
                    .casePolicy(policy)
                    .mode(mode)
                    .uppercasePolicy(uppercase);
            overrides.forEach(builder::override);
            if (!initialisms.isEmpty()) {
                builder.initialisms(initialisms);
            }
            return builder.build();
        }
    }
}
