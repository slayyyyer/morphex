# Benchmarks

JMH 1.37, independent forks, throughput/avg-time with GC profiler. Raw data:
`results/benchmarks/jmh.json`. Sources:
`inflector-benchmarks/src/main/java/io/github/morphex/inflector/bench/`.

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
     -rf json -rff results/benchmarks/jmh.json
```

Every benchmark consumes the returned string through a `Blackhole`; inputs are prepared
outside the timed region. `inflectorRepeated` calls one already-warm word; `inflectorUnique`
cycles the distinct lemma corpus, so it includes compound, initialism, Unicode and fallback
paths rather than a pure map hit.

## Results (arithmetic mean, JDK 21)

| benchmark | mode | score | unit | alloc B/op |
| --- | --- | --- | --- | --- |
| evo2Repeated | avgt | 109.581 | ns/op | 113.3 |
| inflectorRepeated | avgt | 83.499 | ns/op | 35.3 |
| evo2Unique | avgt | 163.200 | ns/op | 136.6 |
| inflectorUnique | avgt | 395.168 | ns/op | 29.3 |
| evo2MixedCase | avgt | 184.122 | ns/op | 151.3 |
| inflectorMixedCase | avgt | 404.192 | ns/op | 50.8 |
| evo2Count (`plural(w, 2)`) | avgt | 141.640 | ns/op | 136.5 |
| inflectorCount (`pluralize(w, 2)`) | avgt | 379.623 | ns/op | 29.3 |
| evo2AnalyzeEquivalent | avgt | 246.058 | ns/op | 273.1 |
| inflectorAnalyze | avgt | 480.819 | ns/op | 188.5 |
| inflectorRulesOnly | avgt | 475.784 | ns/op | 77.0 |

Artifact: 157,344 entries, 8,053,686 bytes raw TSV (1,253,478 bytes gzip) in the jar; no
runtime dependencies. The final JMH run did not capture the concurrent benchmarks; the
previous run measured 10.2M ops/s on four threads, but that number belongs to the earlier
artifact and is not claimed here.

## Interpretation

* **Repeated lookup**: 83.5 ns vs 109.6 ns, i.e. 0.76× stock 2.0 (faster) with 69% fewer
  bytes allocated. This is the hot path for an application pluralizing the same word.
* **Distinct-word workload**: 395 ns vs 163 ns, i.e. **2.4× slower**. The corpus touches 157k
  scattered `Entry` objects and compound/initialism paths; stock 2.0's compiled trie is more
  cache-friendly. This is a real regression on that workload and is reported as such.
* **Diagnostics**: `analyze` is ~1.25× the cost of the fast path and builds alternatives;
  callers that do not need diagnostics should use `pluralize`.
* **Allocation**: 29-51 B/op versus 113-151 B/op for stock 2.0, but not zero: a changed string
  is a new string. Identity results (`sheep`, `information`) return the source.
* **Run-to-run variance**: evo2's repeated benchmark moved from 68.8 ns to 109.6 ns between
  recorded runs on the same machine, so absolute cross-run comparisons are noisy. The
  committed JSON is one run; both systems were measured under the same flags.

The measured claim is: faster on repeated lookup in this run (0.76×), 2.4× slower on
distinct-word lookup, and dramatically lower allocation, with substantially higher accuracy
(see `docs/evaluation.md`). No speedup is claimed on the distinct-word workload.

## Known gaps and next steps

* A packed trie or sorted `String[]` + binary search could close both the unique-lookup gap
  and the 55.5 MB retained heap; it is not implemented because it is a larger change than this
  iteration's budget covered. The current structure is a plain immutable `HashMap` of entry
  records.
* `evo2Concurrent` did not appear in the recorded JSON; only the inflector's concurrent
  benchmark was captured. No comparative concurrency claim is made.
## Startup and retained footprint

`StartupFootprint` (same machine, JDK 21):

| metric | value |
| --- | --- |
| first call (class load + artifact load + first pluralize) | 309 ms |
| first `pluralize` call | 1.64 ms |
| 50,000 steady calls | 34.3 ms total (0.69 µs/call) |
| lexicon entries | 157,344 |
| retained heap after load | **55.5 MB** |

The retained heap is object overhead: one `Entry` per lemma plus its strings and alternative
lists. It is the largest remaining efficiency gap; a packed representation would reduce both
footprint and distinct-word latency. No native or model memory is involved: the learning
experiment was rejected, so the product has no model and no native bridge.
