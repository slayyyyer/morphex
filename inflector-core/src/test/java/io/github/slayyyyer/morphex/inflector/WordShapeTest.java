package io.github.slayyyyer.morphex.inflector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WordShapeTest {

    @Test
    void plainLatinAcceptsAsciiAndLatinLetters() {
        assertTrue(WordShape.isPlainLatinToken("camera"));
        assertTrue(WordShape.isPlainLatinToken("café"));
        assertTrue(WordShape.isPlainLatinToken("ÉTÉ"));
        assertTrue(WordShape.isPlainLatinToken("e\u0301"), "Latin letter plus combining mark");
        assertFalse(WordShape.isPlainLatinToken("日本"));
        assertFalse(WordShape.isPlainLatinToken("user_id"));
        assertFalse(WordShape.isPlainLatinToken("red cat"));
        assertFalse(WordShape.isPlainLatinToken("3"));
    }

    @Test
    void allUpperLatinAcceptsAsciiAndLatinUppercase() {
        assertTrue(WordShape.isAllUpperLatinToken("API"));
        assertTrue(WordShape.isAllUpperLatinToken("ÉTÉ"));
        assertFalse(WordShape.isAllUpperLatinToken("Api"));
        assertFalse(WordShape.isAllUpperLatinToken("A1"));
        assertFalse(WordShape.isAllUpperLatinToken("A"));
        assertFalse(WordShape.isAllUpperLatinToken("日本"));
    }

    @Test
    void casingClassificationIsUnchanged() {
        assertEquals(WordShape.Casing.LOWER, WordShape.casingOf("camera"));
        assertEquals(WordShape.Casing.UPPER, WordShape.casingOf("CAMERA"));
        assertEquals(WordShape.Casing.CAPITALIZED, WordShape.casingOf("Camera"));
        assertEquals(WordShape.Casing.MIXED, WordShape.casingOf("iPhone"));
        assertEquals(WordShape.Casing.UPPER, WordShape.casingOf("ÉTÉ"));
        assertEquals(WordShape.Casing.CAPITALIZED, WordShape.casingOf("Été"));
    }

    @Test
    void hasUpperDetectsAsciiAndUnicodeUppercase() {
        assertTrue(WordShape.hasUpper("Camera"));
        assertTrue(WordShape.hasUpper("Été"));
        assertFalse(WordShape.hasUpper("camera"));
        assertFalse(WordShape.hasUpper("café"));
    }
}
