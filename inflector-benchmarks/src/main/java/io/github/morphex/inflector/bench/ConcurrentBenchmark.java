package io.github.morphex.inflector.bench;

import io.github.morphex.inflector.EnglishInflector;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Concurrent use of one shared immutable instance: throughput with four competing threads. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(4)
@State(Scope.Benchmark)
public class ConcurrentBenchmark {

    private String[] words = new String[0];
    private EnglishInflector inflector;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        List<String> lemmas = new ArrayList<>(20_000);
        InputStream in = EnglishInflector.class.getResourceAsStream("/io/github/morphex/inflector/lexicon-v1.tsv");
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
        words = lemmas.toArray(new String[0]);
        inflector = EnglishInflector.standard();
    }

    private String next() {
        return words[(int) (Thread.currentThread().getId() * 31 + System.nanoTime()) % words.length];
    }

    @Benchmark
    public void inflectorConcurrent(Blackhole blackhole) {
        blackhole.consume(inflector.pluralize(next()));
    }

    @Benchmark
    public void evo2Concurrent(Blackhole blackhole) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(next()));
    }
}
