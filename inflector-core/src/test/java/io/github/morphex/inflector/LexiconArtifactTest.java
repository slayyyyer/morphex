package io.github.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Integrity checks for the generated runtime artifact. No network, no external corpus. */
class LexiconArtifactTest {

    @Test
    void bundledArtifactLoadsAndCarriesProvenance() throws IOException {
        InputStream in = EnglishInflector.class.getResourceAsStream("/io/github/morphex/inflector/lexicon-v1.tsv");
        assertNotNull(in, "bundled artifact must be packaged");
        Set<String> sources = new HashSet<>();
        int records = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("lemma\t")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(7, columns.length, "row: " + line);
                assertFalse(columns[0].isEmpty(), "lemma");
                assertFalse(columns[1].isEmpty(), "plural");
                assertFalse(columns[6].isEmpty(), "provenance");
                sources.add(columns[6]);
                records++;
            }
        }
        assertTrue(records > 200, "expected the curated artifact at minimum, found " + records);
        assertTrue(sources.contains("curated:core-v1"));
    }

    @Test
    void loaderRejectsDuplicatesAndMalformedRows() {
        RuntimeException duplicate = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> Lexicon.load(new StringReader(
                        "lemma\tplural\tclassical\talternatives\tcountability\tflags\tsource\n"
                                + "cat\tcats\t-\t-\tCOUNTABLE\t-\tt\n"
                                + "Cat\tCats\t-\t-\tCOUNTABLE\t-\tt\n")));
        assertTrue(duplicate.getMessage().contains("duplicate"), duplicate.getMessage());

        RuntimeException malformed = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> Lexicon.load(new StringReader("cat\tcats\n")));
        assertTrue(malformed.getMessage().contains("columns"), malformed.getMessage());
    }

    @Test
    void loaderKeepsCaseSensitiveDistinctionInOneMap() throws IOException {
        Lexicon lexicon = Lexicon.load(new StringReader(
                "lemma\tplural\tclassical\talternatives\tcountability\tflags\tsource\n"
                        + "German\tGermans\t-\t-\tCOUNTABLE\tC\tt\n"
                        + "germ\tgerms\t-\t-\tCOUNTABLE\t-\tt\n"));
        assertEquals(2, lexicon.size());
        Lexicon.Entry german = lexicon.lookup("german");
        assertNotNull(german);
        assertTrue(german.caseSensitive);
        assertFalse(german.matchesCase("german"));
        assertTrue(german.matchesCase("German"));
    }
}
