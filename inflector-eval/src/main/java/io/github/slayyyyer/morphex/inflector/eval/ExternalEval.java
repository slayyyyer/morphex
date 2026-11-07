package io.github.slayyyyer.morphex.inflector.eval;

import io.github.slayyyyer.morphex.inflector.EnglishInflector;
import io.github.slayyyyer.morphex.inflector.Inflection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Scores the shipped and rules-only engines against an external, independently sourced gold TSV.
 *
 * <p>Input: {@code lemma<TAB>form1[,form2...]}. Output:
 * {@code lemma<TAB>system<TAB>selected<TAB>accepted<TAB>source}. {@code accepted} is true when the
 * selected form or any reported alternative is one of the gold forms. No network access.
 */
public final class ExternalEval {

    private ExternalEval() {
    }

    public static void main(String[] args) throws IOException {
        Path gold = null;
        Path out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--gold" -> gold = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (gold == null || out == null) {
            System.err.println("Usage: ExternalEval --gold gold.tsv --out predictions.tsv");
            System.exit(2);
        }

        EnglishInflector lexicon = EnglishInflector.standard();
        EnglishInflector rules = EnglishInflector.builder().withoutBundledLexicon().build();

        try (BufferedReader reader = Files.newBufferedReader(gold, StandardCharsets.UTF_8);
                Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write("lemma\tsystem\tselected\taccepted\tsource\n");
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length < 2) {
                    throw new IllegalArgumentException("gold line " + lineNo + " needs lemma<TAB>forms");
                }
                String lemma = columns[0];
                String[] forms = columns[1].split(",", -1);
                writeSystem(writer, lemma, "lexicon", lexicon, forms);
                writeSystem(writer, lemma, "rules", rules, forms);
                String evo = org.atteo.evo.inflector.English.plural(lemma);
                writer.write(lemma + "\tevo2\t" + sanitize(evo) + "\t" + contains(forms, evo) + "\tevo2\n");
            }
        }
    }

    private static void writeSystem(Writer writer, String lemma, String name, EnglishInflector inflector,
            String[] forms) throws IOException {
        Inflection inflection = inflector.analyze(lemma);
        boolean accepted = false;
        for (String form : forms) {
            if (inflection.isAccepted(form)) {
                accepted = true;
                break;
            }
        }
        writer.write(lemma);
        writer.write('\t');
        writer.write(name);
        writer.write('\t');
        writer.write(sanitize(inflection.plural()));
        writer.write('\t');
        writer.write(Boolean.toString(accepted));
        writer.write('\t');
        writer.write(inflection.source().name().toLowerCase(java.util.Locale.ROOT));
        writer.write('\n');
    }

    private static boolean contains(String[] forms, String value) {
        for (String form : forms) {
            if (form.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
