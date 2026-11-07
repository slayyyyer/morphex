package io.github.slayyyyer.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Focused compound regressions for provenance-aware head resolution, coherent case policies, separator
 * preservation and bounded termination. These are the behaviours the audited 1.1.0 engine got wrong.
 */
class CompoundPolicyTest {

    private final EnglishInflector standard = EnglishInflector.standard();

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource({
            "blue-card, UPPERCASE, BLUE-CARDS",
            "BLUE-CARD, LOWERCASE, blue-cards",
            "CUSTOM-MOTHER-IN-LAW, LOWERCASE, custom-mothers-in-law",
            "CUSTOM-MOTHER-IN-LAW, PRESERVE, CUSTOM-MOTHERS-IN-LAW",
            "Zork-In-Law, PRESERVE, Zorks-In-Law",
            "child-in-law, UPPERCASE, CHILDREN-IN-LAW",
            "attorney general, UPPERCASE, ATTORNEYS GENERAL",
            "attorney general, LOWERCASE, attorneys general"
    })
    void casePolicyAppliesOnceToTheReconstructedCompound(String input, String policy, String expected) {
        EnglishInflector inflector = EnglishInflector.builder()
                .casePolicy(CasePolicy.valueOf(policy))
                .build();
        assertEquals(expected, inflector.pluralize(input));
    }

    @Test
    void lexicalInvarianceAndLegitimateLexicalResultsAreNotRespelled() {
        assertSame("steel-chassis", standard.pluralize("steel-chassis"));
        assertSame("foo-sheep", standard.pluralize("foo-sheep"));
        assertEquals("foo-information", standard.pluralize("foo-information"),
                "an MASS head keeps its lexical identity rather than being respelled");
        assertEquals("credit-cards", standard.pluralize("credit-card"),
                "a regular lexical head still inflects");
    }

    @Test
    void overriddenHeadIsSelectedButNeverRespelled() {
        EnglishInflector preserve = EnglishInflector.builder().override("zorp", "zorpi").build();
        assertEquals("eco-zorpi", preserve.pluralize("eco-zorp"));
        assertEquals(DecisionSource.COMPOUND, preserve.analyze("eco-zorp").source());

        EnglishInflector upper = EnglishInflector.builder()
                .casePolicy(CasePolicy.UPPERCASE)
                .override("zorp", "zorpi")
                .build();
        assertEquals("ECO-ZORPI", upper.pluralize("eco-zorp"),
                "configured compound casing applies after head selection; the selected form is not respelled");

        EnglishInflector lower = EnglishInflector.builder()
                .casePolicy(CasePolicy.LOWERCASE)
                .override("ZORP", "Zorpi")
                .build();
        assertEquals("eco-zorpi", lower.pluralize("eco-ZORP"),
                "head overrides are exact and final compound casing is explicit");

        EnglishInflector wholeInput = EnglishInflector.builder()
                .casePolicy(CasePolicy.UPPERCASE)
                .override("blue-card", "blau-karte")
                .build();
        assertEquals("blau-karte", wholeInput.pluralize("blue-card"),
                "a whole-input override is authoritative and returned verbatim");
    }

    @Test
    void initialismSuffixRenderingRespectsPolicyAndHeadMetadata() {
        assertEquals("word-ZQKEs", standard.pluralize("word-ZQKE"),
                "default INFER_INITIALISM appends a lower-case suffix to an unknown initialism head");
        assertEquals("word-ZQKES",
                EnglishInflector.builder().uppercasePolicy(UppercasePolicy.LITERAL).build()
                        .pluralize("word-ZQKE"),
                "LITERAL keeps the ordinary upper-case rendering");
        assertEquals("WORD-ZQKES",
                EnglishInflector.builder()
                        .uppercasePolicy(UppercasePolicy.LITERAL)
                        .casePolicy(CasePolicy.UPPERCASE)
                        .build()
                        .pluralize("word-ZQKE"));
        assertEquals("WORD-ZQKES",
                EnglishInflector.builder().casePolicy(CasePolicy.UPPERCASE).build()
                        .pluralize("word-ZQKE"));

        assertEquals("foo-SMSes", standard.pluralize("foo-SMS"), "lexical initialism metadata supplies the suffix");
        assertEquals("foo-smses",
                EnglishInflector.builder().casePolicy(CasePolicy.LOWERCASE).build().pluralize("foo-SMS"));
        assertEquals("FOO-SMSES",
                EnglishInflector.builder().casePolicy(CasePolicy.UPPERCASE).build().pluralize("foo-SMS"));

        assertEquals("G-PONs", standard.pluralize("G-PON"));
        assertEquals("LV-ROMs", standard.pluralize("LV-ROM"));
    }

    @Test
    void compoundShapesPreserveTheirOriginalStructure() {
        assertEquals("mothers-in-law", standard.pluralize("mother-in-law"));
        assertEquals("great-grandfathers-in-law", standard.pluralize("great-grandfather-in-law"));
        assertEquals("ice creams", standard.pluralize("ice cream"));
        assertEquals("attorneys general", standard.pluralize("attorney general"));
        assertEquals("well-known", standard.pluralize("well-known"));
        assertEquals("jack in the box", standard.pluralize("jack in the box"));
    }

    @Test
    void paddedOrRepeatedWhitespaceIsUnsupportedRatherThanTrimmed() {
        for (String input : new String[] {"red cat ", " red cat", "red  cat", "red cat  ", "red\tcat"}) {
            assertSame(input, standard.pluralize(input), "unsupported padded input must be returned unchanged");
            assertEquals(DecisionSource.FALLBACK, standard.analyze(input).source(), input);
        }
        assertEquals("red cats", standard.pluralize("red cat"));
    }

    @Test
    void repeatedFreeTailTerminatesWithoutOverflow() {
        String input = "cat" + "-in-law".repeat(5_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertSame(input, standard.pluralize(input));
            assertEquals(DecisionSource.FALLBACK, standard.analyze(input).source());
        });
        assertTrue(input.length() > 30_000);
    }
}
