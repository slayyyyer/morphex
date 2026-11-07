package io.github.slayyyyer.morphex.inflector.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline prediction CLI for the dependency-free linear selector.
 *
 * <pre>
 * java ... LinearSelectorEval --model results/learning/linear-v1.bin \
 *     --records java-test-input.tsv --out java-test-predictions.tsv
 * </pre>
 *
 * <p>Input columns: {@code id}, {@code lemma}, {@code base}, {@code source}. Output uses the same
 * {@code id}, {@code system}, {@code prediction}, {@code decisionSource} shape as {@link EvalCli}.
 */
public final class LinearSelectorEval {

    private LinearSelectorEval() {
    }

    public static void main(String[] args) throws IOException {
        Path model = null;
        Path records = null;
        Path output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model" -> model = Path.of(args[++i]);
                case "--records" -> records = Path.of(args[++i]);
                case "--out" -> output = Path.of(args[++i]);
                case "--help" -> {
                    usage();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (model == null || records == null) {
            usage();
            System.exit(2);
        }
        LinearSelector selector = LinearSelector.load(model);
        Writer out = output == null
                ? new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))
                : Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        int count = 0;
        try (Reader reader = Files.newBufferedReader(records, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            out.write("id\tsystem\tprediction\tsource\n");
            String line;
            int lineNo = 0;
            while ((line = buffered.readLine()) != null) {
                lineNo++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length < 4) {
                    throw new IllegalArgumentException("line " + lineNo + " needs id<TAB>lemma<TAB>base<TAB>source");
                }
                if (lineNo == 1 && columns[0].equals("id") && columns[1].equals("lemma")) {
                    continue;
                }
                LinearSelector.Selection selection = selector.select(columns[1], columns[2], columns[3]);
                out.write(columns[0]);
                out.write("\tlinear\t");
                out.write(sanitize(selection.plural()));
                out.write('\t');
                out.write(selection.source());
                out.write('\n');
                count++;
            }
        }
        out.flush();
        if (output != null) {
            out.close();
        }
        System.err.println("records=" + count + " model_bytes=" + Files.size(model));
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void usage() {
        System.out.println("""
                Usage: LinearSelectorEval --model linear-v1.bin --records records.tsv [--out predictions.tsv]

                Input:  id<TAB>lemma<TAB>base<TAB>source
                Output: id<TAB>system<TAB>prediction<TAB>decisionSource
                """);
    }
}
