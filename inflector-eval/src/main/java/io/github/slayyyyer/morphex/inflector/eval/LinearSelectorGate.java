package io.github.slayyyyer.morphex.inflector.eval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures the linear selector against the runtime gates: p99 latency, artifact size and cold
 * initialization. The numbers are wall-clock measurements in a fresh JVM, not JMH throughput numbers.
 *
 * <pre>
 * java ... LinearSelectorGate --model results/learning/linear-v1.bin \
 *     --records java-test-input.tsv --p99-us 10 --size-kib 32 --init-ms 10
 * </pre>
 */
public final class LinearSelectorGate {

    private static final int WARMUP_CALLS = 50_000;
    private static final int MEASURED_CALLS = 200_000;
    private static volatile long sink;

    private LinearSelectorGate() {
    }

    public static void main(String[] args) throws IOException {
        Path model = null;
        Path records = null;
        Path output = null;
        double p99GateMicros = 10.0;
        double sizeGateKib = 32.0;
        double initGateMillis = 10.0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model" -> model = Path.of(args[++i]);
                case "--records" -> records = Path.of(args[++i]);
                case "--out" -> output = Path.of(args[++i]);
                case "--p99-us" -> p99GateMicros = Double.parseDouble(args[++i]);
                case "--size-kib" -> sizeGateKib = Double.parseDouble(args[++i]);
                case "--init-ms" -> initGateMillis = Double.parseDouble(args[++i]);
                case "--help" -> {
                    usage();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (model == null || records == null) {
            usage();
            System.exit(2);
        }

        List<Record> loaded = readRecords(records);
        if (loaded.isEmpty()) {
            throw new IllegalArgumentException("no records in " + records);
        }

        long loadStart = System.nanoTime();
        LinearSelector selector = LinearSelector.load(model);
        long loadEnd = System.nanoTime();
        LinearSelector.Selection first = selector.select(loaded.get(0).lemma(), loaded.get(0).base(),
                loaded.get(0).source());
        long firstEnd = System.nanoTime();
        sink += first.plural().length();

        for (int i = 0; i < WARMUP_CALLS; i++) {
            Record record = loaded.get(i % loaded.size());
            sink += selector.select(record.lemma(), record.base(), record.source()).plural().length();
        }

        long[] samples = new long[MEASURED_CALLS];
        for (int i = 0; i < MEASURED_CALLS; i++) {
            Record record = loaded.get(i % loaded.size());
            long start = System.nanoTime();
            LinearSelector.Selection selected = selector.select(record.lemma(), record.base(), record.source());
            long end = System.nanoTime();
            samples[i] = end - start;
            sink += selected.plural().length();
        }
        Arrays.sort(samples);

        double p50 = percentile(samples, 0.50);
        double p99 = percentile(samples, 0.99);
        double p999 = percentile(samples, 0.999);
        double mean = Arrays.stream(samples).average().orElse(0.0) / 1_000.0;
        long modelBytes = Files.size(model);
        double loadMillis = (loadEnd - loadStart) / 1_000_000.0;
        double firstCallMillis = (firstEnd - loadEnd) / 1_000_000.0;
        double initMillis = (firstEnd - loadStart) / 1_000_000.0;

        boolean latencyPass = p99 <= p99GateMicros;
        boolean sizePass = modelBytes <= sizeGateKib * 1024.0;
        boolean initPass = initMillis <= initGateMillis;
        boolean overall = latencyPass && sizePass && initPass;

        List<String> lines = new ArrayList<>();
        lines.add("model=" + model);
        lines.add("records=" + loaded.size());
        lines.add("model_bytes=" + modelBytes);
        lines.add("model_kib=" + String.format("%.3f", modelBytes / 1024.0));
        lines.add("load_ms=" + String.format("%.3f", loadMillis));
        lines.add("first_call_ms=" + String.format("%.3f", firstCallMillis));
        lines.add("init_ms=" + String.format("%.3f", initMillis));
        lines.add("p50_us=" + String.format("%.3f", p50));
        lines.add("p99_us=" + String.format("%.3f", p99));
        lines.add("p999_us=" + String.format("%.3f", p999));
        lines.add("mean_us=" + String.format("%.3f", mean));
        lines.add("p99_gate_us=" + p99GateMicros);
        lines.add("size_gate_kib=" + sizeGateKib);
        lines.add("init_gate_ms=" + initGateMillis);
        lines.add("latency_pass=" + latencyPass);
        lines.add("size_pass=" + sizePass);
        lines.add("init_pass=" + initPass);
        lines.add("overall_pass=" + overall);
        String text = String.join("\n", lines) + "\n";
        if (output != null) {
            Files.writeString(output, text, StandardCharsets.UTF_8);
        }
        System.out.print(text);
    }

    private static List<Record> readRecords(Path path) throws IOException {
        List<Record> records = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            int lineNo = 0;
            while ((line = buffered.readLine()) != null) {
                lineNo++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length < 4) {
                    throw new IllegalArgumentException("line " + lineNo + " needs id<TAB>lemma<TAB>base<TAB>source");
                }
                if (lineNo == 1 && columns[0].equals("id") && columns[1].equals("lemma")) {
                    continue;
                }
                records.add(new Record(columns[1], columns[2], columns[3]));
            }
        }
        return records;
    }

    private static double percentile(long[] values, double fraction) {
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * fraction) - 1);
        return values[Math.max(0, index)] / 1_000.0;
    }

    private static void usage() {
        System.out.println("""
                Usage: LinearSelectorGate --model linear-v1.bin --records records.tsv [options]

                Options: --out gate.txt --p99-us 10 --size-kib 32 --init-ms 10
                """);
    }

    private record Record(String lemma, String base, String source) {
    }
}
