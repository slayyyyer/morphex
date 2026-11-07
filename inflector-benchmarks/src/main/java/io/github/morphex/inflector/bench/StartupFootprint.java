package io.github.morphex.inflector.bench;

import io.github.morphex.inflector.EnglishInflector;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Startup and footprint probe. Run in a fresh JVM so the first-call cost is not warmed by anything else:
 *
 * <pre>
 * java -Xms256m -Xmx256m -cp ... io.github.morphex.inflector.bench.StartupFootprint [artifactDir]
 * </pre>
 *
 * Reports: first-call latency, lexicon load latency and size, retained heap after a GC, and (optionally) the
 * jar sizes passed as arguments. Not a substitute for the JMH latency numbers.
 */
public final class StartupFootprint {

    private StartupFootprint() {
    }

    public static void main(String[] args) throws IOException {
        long t0 = System.nanoTime();
        EnglishInflector inflector = EnglishInflector.standard();
        long t1 = System.nanoTime();
        String first = inflector.pluralize("child");
        long t2 = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            inflector.pluralize("cities" + i);
        }
        long t3 = System.nanoTime();

        System.out.printf("first_call_ms=%.3f%n", (t1 - t0) / 1_000_000.0);
        System.out.printf("first_pluralize_us=%.3f%n", (t2 - t1) / 1_000.0);
        System.out.printf("steady_50k_calls_ms=%.3f%n", (t3 - t2) / 1_000_000.0);
        System.out.println("first_result=" + first);
        System.out.println("lexicon_entries=" + inflector.lexiconSize());

        long afterLoad = usedHeap();
        for (int i = 0; i < 3; i++) {
            System.gc();
            sleep(100);
        }
        long retained = usedHeap();
        System.out.println("heap_after_load_bytes=" + afterLoad);
        System.out.println("retained_heap_bytes=" + retained);
        System.out.println("max_heap_bytes=" + Runtime.getRuntime().maxMemory());

        List<Path> jars = new ArrayList<>();
        for (String arg : args) {
            Path path = Path.of(arg);
            if (Files.isRegularFile(path)) {
                jars.add(path);
            }
        }
        for (Path jar : jars) {
            System.out.println("artifact_bytes " + jar.getFileName() + "=" + Files.size(jar));
        }
        System.out.println("jvm=" + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        System.out.println("jvm_args=" + Arrays.toString(ManagementFactory.getRuntimeMXBean().getInputArguments().toArray()));
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
