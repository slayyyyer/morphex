package io.github.slayyyyer.morphex.inflector.eval;

import io.github.slayyyyer.morphex.inflector.EnglishInflector;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Offline prediction CLI. Reads a TSV of {@code id<TAB>lemma} records and writes
 * {@code id<TAB>system<TAB>prediction<TAB>decisionSource} rows for the requested systems.
 *
 * <p>Systems: {@code evo2} (stock published evo-inflector 2.0), {@code rules} (this library without the
 * bundled lexicon), {@code lexicon} (this library as shipped). No network access.
 */
public final class EvalCli {

    private EvalCli() {
    }

    public static void main(String[] args) throws IOException {
        Path input = null;
        Path output = null;
        String systems = "evo2,rules,lexicon";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> input = Path.of(args[++i]);
                case "--out" -> output = Path.of(args[++i]);
                case "--systems" -> systems = args[++i];
                case "--help" -> {
                    usage();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (input == null) {
            usage();
            System.exit(2);
        }

        Map<String, Predictor> selected = new LinkedHashMap<>();
        EnglishInflector rules = EnglishInflector.builder().withoutBundledLexicon().build();
        EnglishInflector lexicon = EnglishInflector.standard();
        for (String name : systems.split(",")) {
            switch (name.trim()) {
                case "evo2" -> selected.put("evo2", new EvoSystem());
                case "rules" -> selected.put("rules", new DeterministicSystem("rules", rules));
                case "lexicon" -> selected.put("lexicon", new DeterministicSystem("lexicon", lexicon));
                case "" -> {
                }
                default -> throw new IllegalArgumentException("unknown system: " + name);
            }
        }

        Writer out = output == null
                ? new BufferedWriter(new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8))
                : Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            BufferedReader buffered = new BufferedReader(reader);
            out.write("id\tsystem\tprediction\tsource\n");
            String line;
            int lineNo = 0;
            boolean headerSeen = false;
            Set<String> ids = new HashSet<>();
            int records = 0;
            while ((line = buffered.readLine()) != null) {
                lineNo++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length < 2) {
                    throw new IllegalArgumentException("input line " + lineNo + " needs id<TAB>lemma");
                }
                if (!headerSeen) {
                    headerSeen = true;
                    if (columns[0].equals("id") && columns[1].equals("lemma")) {
                        continue;
                    }
                }
                String id = columns[0];
                String lemma = columns[1];
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("duplicate record id '" + id + "' at line " + lineNo);
                }
                records++;
                for (Map.Entry<String, Predictor> entry : selected.entrySet()) {
                    String prediction;
                    String source;
                    try {
                        Prediction result = entry.getValue().predict(lemma);
                        prediction = result.plural();
                        source = result.source();
                    } catch (RuntimeException e) {
                        prediction = "";
                        source = "exception:" + e.getClass().getSimpleName();
                    }
                    out.write(id);
                    out.write('\t');
                    out.write(entry.getKey());
                    out.write('\t');
                    out.write(sanitize(prediction));
                    out.write('\t');
                    out.write(source);
                    out.write('\n');
                }
            }
            System.err.println("records=" + records + " systems=" + selected.keySet());
        }
        out.flush();
        if (output != null) {
            out.close();
        }
    }

    private static String sanitize(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void usage() {
        System.out.println("""
                Usage: EvalCli --input records.tsv [--out predictions.tsv] [--systems evo2,rules,lexicon]

                Input:  id<TAB>lemma  (header lines starting with # are ignored)
                Output: id<TAB>system<TAB>prediction<TAB>decisionSource
                """);
    }

    private record Prediction(String plural, String source) {
    }

    private interface Predictor {
        Prediction predict(String lemma);
    }

    /** Stock published evo-inflector 2.0, anglicized defaults. */
    private static final class EvoSystem implements Predictor {
        

        @Override
        public Prediction predict(String lemma) {
            return new Prediction(org.atteo.evo.inflector.English.plural(lemma), "evo2");
        }
    }

    private static final class DeterministicSystem implements Predictor {
        private final String name;
        private final EnglishInflector inflector;

        DeterministicSystem(String name, EnglishInflector inflector) {
            this.name = name;
            this.inflector = inflector;
        }

        @Override
        public Prediction predict(String lemma) {
            var analysis = inflector.analyze(lemma);
            return new Prediction(analysis.plural(), name + ":" + analysis.source().name().toLowerCase());
        }
    }
}
