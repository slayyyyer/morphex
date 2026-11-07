# Benchmarks

JMH 1.37, independent forks, average time with GC profiler. Raw data for the packed
implementation: `results/benchmarks/jmh-packed.json`. The previous
object-graph run is `results/benchmarks/jmh.json`; the exploratory 0.3-load run is preserved
as `jmh-packed-noisy.json` because its control benchmark showed the machine was loaded and
its numbers are not used. Sources:
`inflector-benchmarks/src/main/java/io/github/slayyyyer/morphex/inflector/bench/`.

## Setup

| field | value |
| --- | --- |
| CPU | 12th Gen Intel Core i5-12500H (12 cores / 16 threads) |
| RAM | 16 GB |
| OS | Windows 11 Home |
| JDK | Eclipse Temurin 21.0.13+9-LTS |
| Maven | 3.9.9 (checked-in wrapper) |
| JMH | 1.37, `-f 2`, `-wi 3`, `-i 5`, `-w 1s`, `-r 1s`, `-prof gc` |
| Compare | `org.atteo:evo-inflector:2.0` from Maven Central |

Command:

```bash
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 -w 1s -r 1s -prof gc \
     -rf json -rff results/benchmarks/jmh-packed.json
```

Every benchmark consumes the returned string through a `Blackhole`; inputs are prepared
outside the timed region. `inflectorRepeated` calls one already-warm word; `inflectorUnique`
cycles the distinct lemma corpus, so it includes compound, initialism, Unicode and fallback
paths rather than a pure map hit.

## Results (arithmetic mean, JDK 21)

| benchmark | mode | score | unit | alloc B/op |
| --- | --- | --- | --- | --- |
| evo2Repeated | avgt | 98.211 | ns/op | 113.3 |
| inflectorRepeated | avgt | 81.696 | ns/op | 35.3 |
| evo2Unique | avgt | 150.706 | ns/op | 136.6 |
| inflectorUnique | avgt | 201.128 | ns/op | 29.3 |
| evo2MixedCase | avgt | 169.060 | ns/op | 151.3 |
| inflectorMixedCase | avgt | 267.856 | ns/op | 50.8 |
| evo2Count (`plural(w, 2)`) | avgt | 170.932 | ns/op | 136.5 |
| inflectorCount (`pluralize(w, 2)`) | avgt | 216.921 | ns/op | 29.3 |
| evo2AnalyzeEquivalent | avgt | 269.945 | ns/op | 273.1 |
| inflectorAnalyze | avgt | 321.475 | ns/op | 234.9 |
| inflectorRulesOnly | avgt | 391.191 | ns/op | 77.0 |

Artifact: 150,267 entries, 7,708,247 bytes raw TSV (1,212,308 bytes gzip) in the jar; no
runtime dependencies. The final JMH run did not capture the concurrent benchmarks; the
earlier run measured 10.2M ops/s on four threads, but that number belongs to the previous
artifact and is not claimed here.

## Interpretation

* **Repeated lookup**: 81.7 ns vs 98.2 ns in the same run, i.e. 0.83× stock 2.0 (faster), and
  essentially unchanged from the previous implementation (83.5 ns). This is the hot path for
  an application pluralizing the same word.
* **Distinct-word workload**: 201 ns vs 151 ns, i.e. **1.34× slower**. Before the packed
  representation this gap was 2.4× (395 ns vs 163 ns). The corpus touches the full vocabulary
  plus compound, initialism and Unicode paths; stock 2.0's compiled trie is still faster.
* **Mixed case / count / diagnostics**: 268 ns (1.58× evo2), 217 ns (1.27×), and 321 ns
  (1.19× the closest evo2 equivalent). All three improved materially against the previous
  implementation (404, 380 and 481 ns respectively).
* **Allocation**: 29-51 B/op versus 113-151 B/op for stock 2.0, but not zero: a changed string
  is a new string. Identity results (`sheep`, `information`) return the source.
* **Run-to-run variance**: the rules-only control moved from 476 ns to 391 ns between runs on
  the same machine, so cross-run absolute numbers are noisy. Same-run ratios are the fair
  comparison; the previous implementation's row is shown only as historical context.
* **Packed layout**: keys are concatenated in one `char[]` with an `int[]` offset column, an
  open-addressed `int[]` table stores entry indexes, and metadata lives in byte/short columns.
  Full plural strings are retained only for the ~14k entries whose diagnostics can expose an
  alternative or classical form. There is no per-entry object.

The measured claim is: faster than stock 2.0 on repeated lookup (0.83×), 1.34× slower on
distinct-word lookup, lower allocation, and a ~57% smaller retained heap, with substantially
higher accuracy (see `docs/evaluation.md`). No distinct-word speedup is claimed. Per-op
latencies were measured with the larger pre-fix artifact; the engine code is unchanged.

## Startup and retained footprint

`StartupFootprint` (same machine, JDK 21, three runs):

| metric | value |
| --- | --- |
| first call (class load + artifact load + first pluralize) | 392-505 ms (median 426 ms) |
| first `pluralize` call | 2.2-3.2 ms |
| 50,000 cold calls with unseen tokens | 60.7-61.8 ms |
| lexicon entries | 150,267 |
| retained heap after load | **24.1 MB** (25,316,272 bytes; remeasured with the current artifact) |

The previous object-graph lexicon retained **55.5 MB**; the packed layout cuts that by ~57%
(about 31 MB). Most of the remaining footprint is the key/plural character pools, the sparse
diagnostic strings, the index table and the JVM baseline. Load is slower than the old map
(426 ms vs 309 ms) because packing copies every column and builds the index; that is a
one-time startup cost, not a per-lookup cost. The cold 50k loop is slower than the old map's
34 ms because it is dominated by unknown-token misses and JIT warmup; JMH's warm hit paths are
the representative latency numbers. No native or model memory is involved: the learning
experiment was rejected, so the product has no model and no native bridge.

## Known gaps and next steps

* The distinct-word path is still 1.34× slower than stock 2.0. A packed UTF-8 key pool or a
  trie/automaton over the same columns could close the rest without giving back the footprint
  win.
* 24.1 MB retained is much better than 55.5 MB but still larger than the 7.71 MB TSV; the
  difference is Java array overhead, the index table and the sparse diagnostic strings.
* `evo2Concurrent` did not appear in the recorded JSON; no comparative concurrency claim is
  made.
