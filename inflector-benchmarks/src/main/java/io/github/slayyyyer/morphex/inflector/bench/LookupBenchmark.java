package io.github.slayyyyer.morphex.inflector.bench;

import io.github.slayyyyer.morphex.inflector.EnglishInflector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Identical workloads for stock evo-inflector 2.0 and this library. Data preparation happens in
 * {@code @Setup}, outside the timed region; every result is consumed by the blackhole.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class LookupBenchmark {

    /** Loaded from the shipped artifact, so the corpus is the real vocabulary, not five tokens. */
    private String[] unique = new String[0];
    private String[] mixedCase = new String[0];
    private String[] mostlyRepeated = new String[0];
    private int index;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        List<String> lemmas = new ArrayList<>(60_000);
        InputStream in = EnglishInflector.class.getResourceAsStream("/io/github/slayyyyer/morphex/inflector/lexicon-v1.tsv");
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean header = false;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (!header) {
                        header = true;
                        continue;
                    }
                    lemmas.add(line.substring(0, line.indexOf('\t')));
                }
            }
        }
        if (lemmas.isEmpty()) {
            throw new IllegalStateException("benchmark corpus requires the bundled lexicon artifact");
        }
        unique = lemmas.toArray(new String[0]);
        mixedCase = new String[unique.length];
        for (int i = 0; i < unique.length; i++) {
            mixedCase[i] = i % 3 == 0 ? unique[i].toUpperCase(java.util.Locale.ROOT) : unique[i];
        }
        mostlyRepeated = new String[Math.min(32, unique.length)];
        for (int i = 0; i < mostlyRepeated.length; i++) {
            mostlyRepeated[i] = unique[i * 97 % unique.length];
        }
    }

    private String nextUnique() {
        index++;
        if (index >= unique.length) {
            index = 0;
        }
        return unique[index];
    }

    private String nextMixed() {
        index++;
        if (index >= mixedCase.length) {
            index = 0;
        }
        return mixedCase[index];
    }

    private String nextRepeated() {
        index++;
        if (index >= mostlyRepeated.length) {
            index = 0;
        }
        return mostlyRepeated[index];
    }

    // ---------------------------------------------------------------- evo-inflector 2.0 (stock)

    @Benchmark
    public void evo2Repeated(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextRepeated()));
    }

    @Benchmark
    public void evo2Unique(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextUnique()));
    }

    @Benchmark
    public void evo2MixedCase(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextMixed()));
    }

    @Benchmark
    public void evo2Count(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextUnique(), 2));
    }

    // ---------------------------------------------------------------- this library

    @Benchmark
    public void inflectorRepeated(Blackhole blackhole) {
        blackhole.consume(EnglishInflector.standard().pluralize(nextRepeated()));
    }

    @Benchmark
    public void inflectorUnique(Blackhole blackhole) {
        blackhole.consume(EnglishInflector.standard().pluralize(nextUnique()));
    }

    @Benchmark
    public void inflectorMixedCase(Blackhole blackhole) {
        blackhole.consume(EnglishInflector.standard().pluralize(nextMixed()));
    }

    @Benchmark
    public void inflectorCount(Blackhole blackhole) {
        blackhole.consume(EnglishInflector.standard().pluralize(nextUnique(), 2));
    }

    @Benchmark
    public void inflectorRulesOnly(Blackhole blackhole) {
        blackhole.consume(RulesOnly.INFLECTOR.pluralize(nextUnique()));
    }

    @Benchmark
    public void inflectorAnalyze(Blackhole blackhole) {
        blackhole.consume(EnglishInflector.standard().analyze(nextUnique()));
    }

    @Benchmark
    public void evo2AnalyzeEquivalent(Blackhole blackhole) {
        // evo-inflector has no diagnostic API; the closest equivalent is two plural calls (word and count).
        String word = nextUnique();
        blackhole.consume(org.atteo.evo.inflector.English.plural(word));
        blackhole.consume(org.atteo.evo.inflector.English.plural(word, 2));
    }

    /** Rules-only instance built once, mirroring a consumer that opts out of the bundled lexicon. */
    static final class RulesOnly {
        static final EnglishInflector INFLECTOR = EnglishInflector.builder().withoutBundledLexicon().build();
    }
}
