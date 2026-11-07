package io.github.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Runs the pinned regression suite from {@code data/regression/nouns-regression.tsv}. Expectations are
 * source-backed and author-reviewed; the suite is redistributable and needs no network or external corpus.
 */
class RegressionSuiteTest {

    private record Row(String lemma, String expected, String mode, String category, String source) {
    }

    private static List<Row> suite() throws IOException {
        InputStream in = RegressionSuiteTest.class.getResourceAsStream("/nouns-regression.tsv");
        assertNotNull(in, "regression suite resource must be present");
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = false;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!header) {
                    header = true;
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(5, columns.length, "malformed suite row: " + line);
                rows.add(new Row(columns[0], columns[1], columns[2], columns[3], columns[4]));
            }
        }
        return rows;
    }

    @Test
    void suiteIsPinnedAndCoversTheRequiredCategories() throws IOException {
        List<Row> rows = suite();
        assertTrue(rows.size() >= 90, "suite unexpectedly small: " + rows.size());
        Set<String> categories = new HashSet<>();
        for (Row row : rows) {
            categories.add(row.category());
            assertTrue(!row.source().isEmpty(), "row without provenance: " + row.lemma());
        }
        for (String required : List.of("upstream-fix", "casing", "compound", "countability",
                "unicode", "fallback", "alternatives", "initialism", "lexicon", "rule", "mode")) {
            assertTrue(categories.contains(required), "suite is missing category " + required);
        }
    }

    @Test
    void everyReviewedExpectationHolds() throws IOException {
        EnglishInflector anglicized = EnglishInflector.standard();
        EnglishInflector classical = EnglishInflector.builder().mode(Mode.CLASSICAL).build();
        for (Row row : suite()) {
            EnglishInflector inflector = switch (row.mode()) {
                case "anglicized" -> anglicized;
                case "classical" -> classical;
                default -> throw new AssertionError("unknown mode in suite: " + row.mode());
            };
            assertEquals(row.expected(), inflector.pluralize(row.lemma()),
                    () -> "suite row failed: " + row.lemma() + " (" + row.category() + ", " + row.source() + ")");
        }
    }
}
