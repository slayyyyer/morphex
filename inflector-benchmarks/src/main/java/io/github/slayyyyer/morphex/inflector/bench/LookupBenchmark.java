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
 *
 * <p>Category methods consume {@code benchmark-workload-v1.tsv}. The older unique/mixed/repeated
 * methods are retained for comparison with the historical measurements.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class LookupBenchmark {

    private static final EnglishInflector INFLECTOR = EnglishInflector.standard();

    /** Loaded from the shipped artifact, so the historical corpus is the real vocabulary. */
    private String[] unique = new String[0];
    private String[] mixedCase = new String[0];
    private String[] mostlyRepeated = new String[0];
    private Workload workload;
    private int index;
    private int categoryIndex;
    private int allIndex;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        List<String> lemmas = new ArrayList<>(60_000);
        InputStream in = EnglishInflector.class.getResourceAsStream("/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv");
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
        workload = Workload.load();
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

    private String nextCategory(String category) {
        String[] values = workload.category(category);
        categoryIndex++;
        if (categoryIndex >= values.length) {
            categoryIndex = 0;
        }
        return values[categoryIndex];
    }

    private String nextAll() {
        String[] values = workload.all();
        allIndex++;
        if (allIndex >= values.length) {
            allIndex = 0;
        }
        return values[allIndex];
    }

    // ---------------------------------------------------------------- fixed workload: evo-inflector 2.0

    @Benchmark
    public void evo2LexicalHit(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("lexical")));
    }

    @Benchmark
    public void evo2Compacted(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("compacted")));
    }

    @Benchmark
    public void evo2Unknown(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("unknown")));
    }

    @Benchmark
    public void evo2Uppercase(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("uppercase")));
    }

    @Benchmark
    public void evo2Compound(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("compound")));
    }

    @Benchmark
    public void evo2Unsupported(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(nextCategory("unsupported")));
    }

    @Benchmark
    public void evo2AnalyzeWorkloadEquivalent(Blackhole blackhole) {
        String word = nextAll();
        blackhole.consume(org.atteo.evo.inflector.English.plural(word));
        blackhole.consume(org.atteo.evo.inflector.English.plural(word, 2));
    }

    // ---------------------------------------------------------------- fixed workload: this library

    @Benchmark
    public void inflectorLexicalHit(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("lexical")));
    }

    @Benchmark
    public void inflectorCompacted(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("compacted")));
    }

    @Benchmark
    public void inflectorUnknown(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("unknown")));
    }

    @Benchmark
    public void inflectorUppercase(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("uppercase")));
    }

    @Benchmark
    public void inflectorCompound(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("compound")));
    }

    @Benchmark
    public void inflectorUnsupported(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextCategory("unsupported")));
    }

    @Benchmark
    public void inflectorDiagnosticsWorkload(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.analyze(nextAll()));
    }

    // ---------------------------------------------------------------- historical corpus comparisons

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

    @Benchmark
    public void inflectorRepeated(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextRepeated()));
    }

    @Benchmark
    public void inflectorUnique(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextUnique()));
    }

    @Benchmark
    public void inflectorMixedCase(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextMixed()));
    }

    @Benchmark
    public void inflectorCount(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.pluralize(nextUnique(), 2));
    }

    @Benchmark
    public void inflectorRulesOnly(Blackhole blackhole) {
        blackhole.consume(RulesOnly.INFLECTOR.pluralize(nextUnique()));
    }

    @Benchmark
    public void inflectorAnalyze(Blackhole blackhole) {
        blackhole.consume(INFLECTOR.analyze(nextUnique()));
    }

    @Benchmark
    public void evo2AnalyzeEquivalent(Blackhole blackhole) {
        // evo-inflector has no diagnostic API; the closest equivalent is two plural calls.
        String word = nextUnique();
        blackhole.consume(org.atteo.evo.inflector.English.plural(word));
        blackhole.consume(org.atteo.evo.inflector.English.plural(word, 2));
    }

    /** Rules-only instance built once, mirroring a consumer that opts out of loading the bundled lexicon. */
    static final class RulesOnly {
        static final EnglishInflector INFLECTOR = EnglishInflector.builder().withoutBundledLexicon().build();
    }
}
