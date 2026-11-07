package io.github.morphex.inflector.eval;

import java.util.List;

/**
 * Reproduces the reported evo-inflector behaviour probes against the published artifact on the local JDK.
 * Output is recorded in {@code docs/upstream-probe.md}; nothing here is a network call.
 *
 * <p>Run: {@code ./mvnw -q -pl inflector-eval exec:java -Dexec.mainClass=...UpstreamProbe}
 */
public final class UpstreamProbe {

    private UpstreamProbe() {
    }

    public static void main(String[] args) {
        String version = versionOf("org.atteo.evo.inflector.English");
        System.out.println("evo-inflector artifact location: " + version);
        System.out.println("java.version=" + System.getProperty("java.version")
                + " vendor=" + System.getProperty("java.vendor"));

        List<String> inputs = List.of(
                "German", "blouse", "scarp", "UFO", "mother-in-law", "passer-by",
                "child", "CHILD", "city", "CITY", "mouse", "MOUSE", "SMS", "scissors",
                "information", "analysis", "quiz", "die", "person", "sheep", "cactus",
                "stand-in", "drive-by", "attorney general", "ice cream", "café");

        System.out.println("input\tevo_pluralize\tevo_pluralize(count=2)\tevo_pluralize(count=1)");
        for (String input : inputs) {
            System.out.println(escape(input) + "\t" + call(() -> org.atteo.evo.inflector.English.plural(input))
                    + "\t" + call(() -> org.atteo.evo.inflector.English.plural(input, 2))
                    + "\t" + call(() -> org.atteo.evo.inflector.English.plural(input, 1)));
        }
    }

    private static String call(java.util.function.Supplier<String> supplier) {
        try {
            return escape(supplier.get());
        } catch (RuntimeException e) {
            return "EXCEPTION:" + e.getClass().getSimpleName();
        }
    }

    private static String escape(String value) {
        return value == null ? "<null>" : value.replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String versionOf(String className) {
        try {
            Class<?> type = Class.forName(className);
            var location = type.getProtectionDomain().getCodeSource().getLocation();
            return location.toString();
        } catch (ClassNotFoundException e) {
            return "MISSING";
        }
    }
}
