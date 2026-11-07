package io.github.slayyyyer.morphex.inflector.bench;

import io.github.slayyyyer.morphex.inflector.EnglishInflector;
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

/** Concurrent use of one shared immutable instance over the fixed mixed workload: four threads. */
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
    public void setUp() {
        words = Workload.load().all();
        inflector = EnglishInflector.standard();
    }

    /** Per-thread cursor: a shared atomic cursor would measure contention rather than the inflector. */
    @State(Scope.Thread)
    public static class Cursor {
        private int index;

        String next(String[] words) {
            index++;
            if (index >= words.length) {
                index = 0;
            }
            return words[index];
        }
    }

    @Benchmark
    public void inflectorConcurrent(Blackhole blackhole, Cursor cursor) {
        blackhole.consume(inflector.pluralize(cursor.next(words)));
    }

    @Benchmark
    public void evo2Concurrent(Blackhole blackhole, Cursor cursor) {
        blackhole.consume(org.atteo.evo.inflector.English.plural(cursor.next(words)));
    }
}
