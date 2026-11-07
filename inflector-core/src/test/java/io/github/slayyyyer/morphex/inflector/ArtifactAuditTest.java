package io.github.slayyyyer.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Offline audit for artifact compaction and the policies that interact with it. The full frozen P1 benchmark
 * remains the authoritative audit; this fixture keeps the specific compaction traps executable in a normal
 * checkout, where the 298 MB source dump and derived corpus are absent.
 */
class ArtifactAuditTest {

    private record Row(String lemma, String expected, String category, String reason) {
    }

    private static List<Row> fixture() throws IOException {
        InputStream in = ArtifactAuditTest.class.getResourceAsStream("/artifact-audit.tsv");
        assertNotNull(in, "artifact audit fixture must be packaged");
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
                assertEquals(4, columns.length, "malformed audit row: " + line);
                rows.add(new Row(columns[0], columns[1], columns[2], columns[3]));
            }
        }
        return rows;
    }

    @Test
    void compactedAndPolicySensitiveEntriesStillResolve() throws IOException {
        EnglishInflector inflector = EnglishInflector.standard();
        assertTrue(fixture().size() >= 20);
        for (Row row : fixture()) {
            assertEquals(row.expected(), inflector.pluralize(row.lemma()),
                    () -> "audit row failed: " + row.lemma() + " (" + row.category() + ", " + row.reason() + ")");
        }
    }

    @Test
    void auditFixtureIncludesTheTwoSisCompactionTraps() throws IOException {
        List<String> lemmas = fixture().stream().map(Row::lemma).toList();
        assertTrue(lemmas.contains("heterotrichosis"));
        assertTrue(lemmas.contains("phalaenopsis"));
        assertTrue(lemmas.contains("cassis"));
    }
}
