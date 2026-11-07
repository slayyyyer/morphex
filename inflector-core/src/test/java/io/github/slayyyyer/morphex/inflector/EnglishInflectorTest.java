package io.github.slayyyyer.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Mandatory offline regression suite. Expectations are source-backed (standard dictionary forms) and were
 * author-reviewed in this repository; they are not claimed to be externally human-reviewed.
 */
class EnglishInflectorTest {

    private final EnglishInflector inflector = EnglishInflector.standard();

    private void plural(String lemma, String expected) {
        assertEquals(expected, inflector.pluralize(lemma), "pluralize(" + lemma + ")");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // Reported upstream 2.0/default failures, now ordinary results.
            "German,Germans",
            "blouse,blouses",
            "scarp,scarps",
            "UFO,UFOs",
            "mother-in-law,mothers-in-law",
            "passer-by,passers-by",
            // Ordinary-word casing regressions (the ordinary-word policy, not acronym treatment).
            "CHILD,CHILDREN",
            "CITY,CITIES",
            "MOUSE,MICE",
            "Child,Children",
            "City,Cities",
            "child,children",
            "city,cities",
            "mouse,mice",
            // Productive spelling rules.
            "cat,cats",
            "dog,dogs",
            "bus,buses",
            "gas,gases",
            "lens,lenses",
            "box,boxes",
            "church,churches",
            "dish,dishes",
            "quiz,quizzes",
            "boy,boys",
            "day,days",
            "key,keys",
            "soliloquy,soliloquies",
            "café,cafés",
            "naïve,naïves",
            // Lexical exceptions must not leak into unrelated words sharing a suffix.
            "carp,carps",
            "louse,lice",
            "knife,knives",
            "roof,roofs",
            "chief,chiefs",
            "chef,chefs",
            "potato,potatoes",
            "piano,pianos",
            "photo,photos",
            "analysis,analyses",
            "crisis,crises",
            "axis,axes",
            "matrix,matrices",
            "index,indexes",
            "focus,focuses",
            "status,statuses",
            "virus,viruses",
            "octopus,octopuses",
            // Compounds, including counterexamples to suffix-based guessing.
            "stand-in,stand-ins",
            "drive-by,drive-bys",
            "close-up,close-ups",
            "hanger-on,hangers-on",
            "runner-up,runners-up",
            "go-between,go-betweens",
            "jack-in-the-box,jack-in-the-boxes",
            "man-of-war,men-of-war",
            "attorney general,attorneys general",
            "court-martial,courts-martial",
            "heir apparent,heirs apparent",
            "governor-elect,governors-elect",
            "solicitor general,solicitors general",
            "secretary-general,secretaries-general",
            "child-in-law,children-in-law",
            "zork-in-law,zorks-in-law",
            "major general,major generals",
            "ice cream,ice creams",
            "high school,high schools"
    })
    void producesExpectedPlurals(String lemma, String expected) {
        plural(lemma, expected);
    }

    @Test
    void countOverloadPreservesSingularInstance() {
        String lemma = new String("child");
        assertSame(lemma, inflector.pluralize(lemma, 1));
        assertEquals("children", inflector.pluralize(lemma, 2));
        assertEquals("children", inflector.pluralize(lemma, 0));
        assertEquals("children", inflector.pluralize(lemma, -3));
    }

    @Test
    void nullIsRejectedWithDocumentedException() {
        NullPointerException direct = assertThrows(NullPointerException.class, () -> inflector.pluralize(null));
        assertEquals("noun must not be null", direct.getMessage());
        assertThrows(NullPointerException.class, () -> inflector.pluralize(null, 1));
        assertThrows(NullPointerException.class, () -> inflector.analyze(null));
    }

    @Test
    void emptyAndUnsupportedInputsAreReturnedUnchanged() {
        assertEquals("", inflector.pluralize(""));
        String padded = " cat";
        assertSame(padded, inflector.pluralize(padded), "no trimming or rewriting of caller input");
        assertSame("user_id", inflector.pluralize("user_id"));
        assertSame("3.14", inflector.pluralize("3.14"));
        assertSame("mother's", inflector.pluralize("mother's"));
        assertSame("jack in the box", inflector.pluralize("jack in the box"));
        assertSame("well-known", inflector.pluralize("well-known"));
        assertEquals(DecisionSource.FALLBACK, inflector.analyze("user_id").source());
        assertEquals("fallback:empty", inflector.analyze("").id());
    }

    @Test
    void invariantNounsReturnTheSameInstance() {
        String sheep = new String("sheep");
        assertSame(sheep, inflector.pluralize(sheep));
        Inflection inf = inflector.analyze("sheep");
        assertTrue(inf.invariant());
        assertEquals(Countability.COUNTABLE, inf.countability());
    }

    @Test
    void massAndPluralOnlyAreSeparatePolicies() {
        Inflection information = inflector.analyze("information");
        assertEquals("information", information.plural());
        assertEquals(Countability.MASS, information.countability());
        assertEquals(DecisionSource.LEXICON, information.source());

        Inflection scissors = inflector.analyze("scissors");
        assertEquals("scissors", scissors.plural());
        assertEquals(Countability.PLURAL_ONLY, scissors.countability());

        Inflection water = inflector.analyze("water");
        assertEquals("waters", water.plural());
        assertEquals(Countability.MIXED, water.countability());

        // Mass and plural-only forms are not singular-to-plural accuracy claims.
        assertEquals("information", inflector.pluralize("information", 4));
    }

    @Test
    void retainsLegitimateAlternatives() {
        Inflection person = inflector.analyze("person");
        assertEquals("people", person.plural());
        assertTrue(person.alternatives().contains("persons"));
        assertTrue(person.isAccepted("persons"));
        assertFalse(person.ambiguous());

        Inflection die = inflector.analyze("die");
        assertEquals("dice", die.plural());
        assertTrue(die.alternatives().contains("dies"));
        assertTrue(die.ambiguous(), "spelling alone cannot choose dice vs dies");

        Inflection fish = inflector.analyze("fish");
        assertEquals("fish", fish.plural());
        assertTrue(fish.isAccepted("fishes"));
    }

    @Test
    void classicalModeChangesPreferencesWithoutInventingForms() {
        EnglishInflector classical = EnglishInflector.builder().mode(Mode.CLASSICAL).build();
        assertEquals("cacti", classical.pluralize("cactus"));
        assertEquals("formulae", classical.pluralize("formula"));
        assertEquals("indices", classical.pluralize("index"));
        assertEquals("media", classical.pluralize("medium"));
        assertEquals("CACTI", classical.pluralize("CACTUS"));
        assertTrue(classical.analyze("cactus").isAccepted("cactuses"));

        assertEquals("cactuses", inflector.pluralize("cactus"));
        assertTrue(inflector.analyze("cactus").isAccepted("cacti"));

        // An unseen word never receives a neo-classical suffix.
        assertEquals("blouses", classical.pluralize("blouse"));
        assertEquals("zorbuses", classical.pluralize("zorbus"));
    }

    @Test
    void overridesWinAndAreExact() {
        EnglishInflector custom = EnglishInflector.builder()
                .override("German", "Germanics")
                .override("Goose", "Goosen")
                .overrides(Map.of("die", "dice-plural"))
                .build();
        assertEquals("Germanics", custom.pluralize("German"));
        assertEquals("dice-plural", custom.pluralize("die"));
        assertEquals("Goosen", custom.pluralize("Goose"));
        assertEquals("geese", custom.pluralize("goose"), "override keys are exact and do not leak across casing");
        assertEquals("germans", custom.pluralize("german"), "case-sensitive entries are not reached from another casing");
        Inflection inf = custom.analyze("German");
        assertEquals(DecisionSource.OVERRIDE, inf.source());
        assertEquals("override", inf.id());
        assertThrows(IllegalArgumentException.class, () -> EnglishInflector.builder().override("", "x"));
    }

    @Test
    void casePolicyIsExplicitAndReported() {
        EnglishInflector upper = EnglishInflector.builder().casePolicy(CasePolicy.UPPERCASE).build();
        assertEquals("CATS", upper.pluralize("cat"));
        assertEquals("CHILDREN", upper.pluralize("Child"));
        assertEquals("iPhones", inflector.pluralize("iPhone"), "mixed case keeps internal capitals");

        EnglishInflector lower = EnglishInflector.builder().casePolicy(CasePolicy.LOWERCASE).build();
        assertEquals("cats", lower.pluralize("CAT"));
        assertEquals("ufos", lower.pluralize("UFO"));
    }

    @Test
    void configuredInitialismsUseDocumentedSuffixPolicy() {
        EnglishInflector custom = EnglishInflector.builder().initialism("ABC").build();
        assertEquals("ABCs", custom.pluralize("ABC"));
        assertEquals("SMSes", inflector.pluralize("SMS"));
        assertEquals("UFOs", inflector.pluralize("UFO"));
        assertEquals("CDs", inflector.pluralize("CD"));
        assertEquals(DecisionSource.LEXICON, inflector.analyze("SMS").source());
    }

    @Test
    void rulesOnlyInstanceStillHandlesProductiveSpelling() {
        EnglishInflector rules = EnglishInflector.builder().withoutBundledLexicon().build();
        assertEquals(0, rules.lexiconSize());
        assertEquals("scarps", rules.pluralize("scarp"));
        assertEquals("blouses", rules.pluralize("blouse"));
        assertEquals("cities", rules.pluralize("city"));
        assertEquals("quizes", rules.pluralize("quiz"), "rules-only loses lexical quirk by design");
    }

    @Test
    void diagnosticsExposeStableIdentifiers() {
        Inflection child = inflector.analyze("child");
        assertEquals(DecisionSource.LEXICON, child.source());
        assertEquals("lex:child", child.id());

        Inflection zorb = inflector.analyze("zorb");
        assertEquals(DecisionSource.RULE, zorb.source());
        assertEquals("rule:default-s", zorb.id());
        assertEquals("zorbs", zorb.plural());

        Inflection compound = inflector.analyze("zork-in-law");
        assertEquals(DecisionSource.COMPOUND, compound.source());
        assertTrue(compound.id().startsWith("compound:"), compound.id());

        assertEquals(DecisionSource.COMPOUND, inflector.analyze("ice cream").source());
    }

    @Test
    void diagnosticsDoNotLeakIntoFastPath() {
        // The fast path must not build collections; this test is behavioural, not an allocation assertion.
        assertEquals("children", inflector.pluralize("child"));
        assertTrue(inflector.analyze("child").alternatives().isEmpty());
    }

    @Test
    void immutableConfigurationIsThreadSafe() {
        EnglishInflector shared = EnglishInflector.builder()
                .mode(Mode.CLASSICAL)
                .casePolicy(CasePolicy.PRESERVE)
                .override("zorb", "zorbs")
                .build();
        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            jobs.add(() -> {
                StringBuilder builder = new StringBuilder();
                for (int j = 0; j < 200; j++) {
                    builder.append(shared.pluralize("city")).append(shared.pluralize("CHILD"))
                            .append(shared.pluralize("cactus")).append(shared.pluralize("sheep"));
                }
                return builder.toString();
            });
        }
        String expected = "citiesCHILDRENcactisheep".repeat(200);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Future<String> future : pool.invokeAll(jobs)) {
                assertEquals(expected, future.get());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void builderRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () -> EnglishInflector.builder().mode(null));
        assertThrows(NullPointerException.class, () -> EnglishInflector.builder().casePolicy(null));
        assertThrows(NullPointerException.class, () -> EnglishInflector.builder().overrides(null));
        assertThrows(NullPointerException.class, () -> EnglishInflector.builder().initialism(null));
    }

    @Test
    void standardInstanceIsSharedAndImmutable() {
        assertSame(EnglishInflector.standard(), EnglishInflector.standard());
        assertNotSame(inflector, EnglishInflector.builder().build());
        assertEquals(Mode.ANGLICIZED, inflector.mode());
        assertEquals(CasePolicy.PRESERVE, inflector.casePolicy());
    }
}
