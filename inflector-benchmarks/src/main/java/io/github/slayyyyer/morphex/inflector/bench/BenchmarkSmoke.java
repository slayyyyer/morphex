package io.github.slayyyyer.morphex.inflector.bench;

import io.github.slayyyyer.morphex.inflector.EnglishInflector;

/**
 * Coarse CI smoke gate for the fixed benchmark workload.
 *
 * <p>This is deliberately not JMH and its timings are not release-benchmark results. It runs
 * deterministic batches with a warmup, takes the best of three coarse measurements, and fails only
 * when a category exceeds a very generous ceiling. The ceiling is intended to catch an accidental
 * order-of-magnitude regression, not to measure stable nanosecond-level differences.
 */
public final class BenchmarkSmoke {

    /** A run above this is treated as a catastrophic regression, not ordinary CI noise. */
    static final long CEILING_NANOS_PER_OP = 50_000L;

    private static final int WARMUP_ITERATIONS = 20_000;
    private static final int MEASURE_ITERATIONS = 30_000;
    private static final int ROUNDS = 3;

    /** Keeps warmup work observable to the JIT without adding a benchmark dependency. */
    private static volatile long warmupSink;

    private BenchmarkSmoke() {
    }

    public static void main(String[] args) {
        Workload workload = Workload.load();
        EnglishInflector inflector = EnglishInflector.standard();

        for (String category : Workload.CATEGORIES) {
            warmupSink = run(inflector, workload.category(category), WARMUP_ITERATIONS);
        }

        System.out.printf(
                "benchmark-smoke: fixed workload v1; best of %d coarse runs; ceiling %d ns/op; "
                        + "not a JMH or release-benchmark result%n",
                ROUNDS, CEILING_NANOS_PER_OP);
        System.out.printf("%-12s %14s %14s %20s %8s%n",
                "category", "best ns/op", "worst ns/op", "checksum", "status");

        boolean failed = false;
        for (String category : Workload.CATEGORIES) {
            String[] inputs = workload.category(category);
            long best = Long.MAX_VALUE;
            long worst = 0;
            long checksum = -1;
            boolean stableChecksum = true;

            for (int round = 0; round < ROUNDS; round++) {
                long start = System.nanoTime();
                long nextChecksum = run(inflector, inputs, MEASURE_ITERATIONS);
                long elapsed = System.nanoTime() - start;
                long nsPerOp = Math.max(1L, elapsed / MEASURE_ITERATIONS);
                best = Math.min(best, nsPerOp);
                worst = Math.max(worst, nsPerOp);
                if (checksum < 0) {
                    checksum = nextChecksum;
                } else if (checksum != nextChecksum) {
                    stableChecksum = false;
                }
            }

            boolean passed = stableChecksum && best <= CEILING_NANOS_PER_OP;
            failed |= !passed;
            System.out.printf("%-12s %14d %14d %20d %8s%n",
                    category, best, worst, checksum, passed ? "PASS" : "FAIL");
            if (!stableChecksum) {
                System.out.printf("  %s output checksum changed between rounds%n", category);
            }
        }

        if (failed) {
            System.out.println("benchmark-smoke: FAILED - investigate a possible catastrophic regression");
            System.exit(1);
        }
        System.out.println("benchmark-smoke: PASS");
    }

    private static long run(EnglishInflector inflector, String[] inputs, int iterations) {
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            String result = inflector.pluralize(inputs[i % inputs.length]);
            checksum += result.length();
        }
        return checksum;
    }
}
