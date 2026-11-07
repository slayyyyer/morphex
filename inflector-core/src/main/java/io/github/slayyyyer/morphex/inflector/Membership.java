package io.github.slayyyyer.morphex.inflector;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Exact, immutable membership table for lower-cased lemmas, independent of the compact exception artifact.
 *
 * <p>The compact artifact deliberately omits regular words that the productive rules reproduce. Those words
 * are still ordinary English nouns, so an unknown-uppercase policy cannot use "absent from the exception
 * artifact" as evidence that {@code CARD} or {@code CAT} is an abbreviation. This resource is generated from
 * the same permitted corpus as the artifact, contains every lemma in scope, and is stored as sorted 64-bit
 * FNV-1a hashes (8 bytes per lemma) so lookups are a binary search without retaining 150k strings.
 *
 * <p>Format: {@code MORPHEX1} (8 bytes), a big-endian {@code int} count, then {@code count} big-endian
 * {@code long} values in ascending order. A 64-bit collision can only make the policy more conservative
 * (ordinary-word rendering instead of inferred initialism), never less.
 */
final class Membership {

    private static final byte[] MAGIC = "MORPHEX1".getBytes(StandardCharsets.US_ASCII);
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    static final Membership EMPTY = new Membership(new long[0]);

    private final long[] hashes;

    private Membership(long[] hashes) {
        this.hashes = hashes;
    }

    static Membership of(long[] sortedHashes) {
        if (sortedHashes.length == 0) {
            return EMPTY;
        }
        long[] copy = sortedHashes.clone();
        Arrays.sort(copy);
        return new Membership(copy);
    }

    boolean isEmpty() {
        return hashes.length == 0;
    }

    boolean contains(String lower) {
        return Arrays.binarySearch(hashes, hash(lower)) >= 0;
    }

    static long hash(String value) {
        long hash = FNV_OFFSET;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFFL);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    static Membership load(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("membership resource has an unknown magic header");
        }
        int count = in.readInt();
        if (count < 0 || count > 4_000_000) {
            throw new IllegalArgumentException("membership resource has an implausible count " + count);
        }
        long[] hashes = new long[count];
        for (int i = 0; i < count; i++) {
            hashes[i] = in.readLong();
            if (i > 0 && hashes[i] <= hashes[i - 1]) {
                throw new IllegalArgumentException("membership hashes are not strictly ascending at " + i);
            }
        }
        return new Membership(hashes);
    }
}
