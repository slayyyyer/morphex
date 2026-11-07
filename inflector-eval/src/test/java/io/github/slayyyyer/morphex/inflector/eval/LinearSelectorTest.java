package io.github.slayyyyer.morphex.inflector.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinearSelectorTest {

    @TempDir
    Path temp;

    @Test
    void ineligibleRecordKeepsTheBaseAndSource() throws IOException {
        LinearSelector selector = LinearSelector.load(artifact(0.9f));

        LinearSelector.Selection selection = selector.select("cat", "cats", "lexicon:inferred");

        assertFalse(selection.overridden());
        assertEquals("cats", selection.plural());
        assertEquals("lexicon:inferred", selection.source());
        assertEquals(1.0, selection.probability());
    }

    @Test
    void zeroWeightArtifactDoesNotOverrideTheBase() throws IOException {
        LinearSelector selector = LinearSelector.load(artifact(0.9f));

        LinearSelector.Selection selection = selector.select("cat", "cats", "lexicon:rule");

        assertFalse(selection.overridden());
        assertEquals("cats", selection.plural());
        assertEquals("lexicon:rule", selection.source());
        assertTrue(selection.probability() > 0.0);
        assertTrue(selection.probability() < 0.9);
    }

    @Test
    void wrongMagicIsRejected() throws IOException {
        Path path = temp.resolve("bad.bin");
        Files.write(path, new byte[32]);

        assertThrows(IllegalArgumentException.class, () -> LinearSelector.load(path));
    }

    private Path artifact(float threshold) throws IOException {
        int dimensions = 4096;
        ByteBuffer buffer = ByteBuffer.allocate(16 + 12 + dimensions * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("MORPHEX-LINEAR1\0".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(1);
        buffer.putInt(dimensions);
        buffer.putFloat(threshold);
        Path path = temp.resolve("linear.bin");
        Files.write(path, buffer.array());
        return path;
    }
}
