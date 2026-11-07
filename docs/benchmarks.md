# Benchmarks

JMH 1.37, two independent forks, average time, GC profiler. The release baseline is
`results/release-1.2.0/benchmarks/jmh-workload-v1.json`; the log is next to it. Separate
post-optimization targeted reruns are
`results/release-1.2.0/benchmarks/optimization-unknown-compound.json`,
`results/release-1.2.0/benchmarks/optimization-compacted.json`,
`results/release-1.2.0/benchmarks/optimization-compacted-ascii.json`, and
`results/release-1.2.0/benchmarks/optimization-compacted-flat-clean.json`, with their logs
alongside.
The fixed input is `inflector-benchmarks/src/main/resources/benchmark-workload-v1.tsv`
(version 1).

## Setup

| field | value |
| --- | --- |
| CPU | 12th Gen Intel Core i5-12500H (12 cores / 16 threads) |
| RAM | 16 GB |
| OS | Windows 11 Home |
| JDK | Eclipse Temurin 21.0.13+9-LTS |
| Maven | 3.9.9 (checked-in wrapper) |
| JMH | 1.37, `-f 2 -wi 3 -i 5 -w 500ms -r 500ms -prof gc` |
| Compare | `org.atteo:evo-inflector:2.0`, same JVM and inputs |

```bash
./mvnw -q -pl inflector-benchmarks -am package
java -jar inflector-benchmarks/target/benchmarks.jar -f 2 -wi 3 -i 5 \
  -w 500ms -r 500ms -prof gc -rf json \
  -rff results/release-1.2.0/benchmarks/jmh-workload-v1.json
```

The workload is fixed and versioned, not sampled at run time. Categories are lexical hits,
compacted regular words (in the full vocabulary but absent from the exact artifact),
unknown lowercase tokens, uppercase inference, compounds, and unsupported inputs. Every
returned value is consumed through a `Blackhole`; setup is outside the timed region.

## Results

`avgt` is arithmetic mean latency. Alloc is normalized GC allocation per operation. This is the
release baseline; the unknown and compound rows are superseded by the targeted rerun below.

| benchmark | score ns/op | alloc B/op |
| --- | ---: | ---: |
| evo2LexicalHit | 118.495 | 121.7 |
| **inflectorLexicalHit** | **100.733** | 65.7 |
| evo2Compacted | 104.666 | 80.6 |
| inflectorCompacted (release baseline) | 456.362 | 395.4 |
| evo2Unknown | 80.420 | 73.6 |
| inflectorUnknown | 906.080 | 392.8 |
| evo2Uppercase | 89.823 | 120.0 |
| inflectorUppercase | 711.409 | 163.6 |
| evo2Compound | 95.773 | 103.2 |
| inflectorCompound | 499.737 | 404.0 |
| evo2Unsupported | 78.645 | 113.1 |
| inflectorUnsupported | 384.329 | 377.1 |
| evo2Count | 171.252 | 134.8 |
| inflectorCount | 247.600 | 29.9 |
| evo2MixedCase | 183.287 | 149.3 |
| inflectorMixedCase | 434.602 | 87.8 |
| evo2Unique | 180.011 | 134.8 |
| inflectorUnique | 267.127 | 29.9 |
| evo2Repeated | 110.879 | 104.8 |
| **inflectorRepeated** | **81.372** | 17.3 |
| evo2AnalyzeEquivalent | 327.661 | 269.6 |
| **inflectorAnalyze** | **387.412** | 236.7 |
| evo2AnalyzeWorkloadEquivalent | 222.395 | 248.4 |
| inflectorDiagnosticsWorkload | 574.171 | 423.5 |
| inflectorRulesOnly | 1,032.422 | 910.2 |

Release-baseline same-run ratios (unknown, compound and compacted are superseded by the targeted reruns below):

| path | ratio to evo2 | reading |
| --- | ---: | --- |
| repeated lookup | 0.73× | 1.36× faster |
| lexical hit | 0.85× | 1.18× faster |
| count path | 1.45× | slower |
| historical distinct corpus | 1.48× | slower |
| mixed case | 2.37× | slower |
| compacted regular (pre-optimization) | 4.36× | slower |
| compound | 5.22× | slower |
| unsupported | 4.89× | slower |
| uppercase inference | 7.92× | slower |
| unknown token | 11.27× | slower |
| diagnostics | 1.18× | slower (evo2 has no equivalent API) |

The fixed workload changes the conclusion from the old corpus-only run: the library is faster
only on repeated and exact lexical hits. Compacted, uppercase and unsupported paths remain
slower; the optimized unknown, compacted and compound paths are still 7.57x, 1.33x and 4.62x
slower than their same-run evo2 references. No general speedup is claimed.

### Post-release optimization: unknown and compound

Profiling used JMH `-prof stack`, `-prof gc`, and JFR `jdk.ObjectAllocationSample`. On the
unknown path `CompoundTable.freeTailSuffix` built a fresh `"-" + tail` string for every one
of the known free tails on every call. That was the dominant allocation: 392.8 B/op before,
48.8 B/op after. Plain single-token inputs also entered the compound parser even though no
compound entry can match without a hyphen or space. The retained changes are:

* test the hyphen and tail suffix without constructing a string in `freeTailSuffix`;
* only call `resolveCompound` when the input contains `-` or a supported space.

An additional head/split fast path was measured and reverted: it did not reduce the compound
allocation count or produce a reliable latency change.

| path | baseline ns/op | after ns/op | change | baseline B/op | after B/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| unknown token | 906.080 | 608.613 | 1.49x faster (-32.8%) | 392.8 | 48.8 |
| compound | 499.737 | 442.657 | 1.13x faster (-11.4%) | 404.0 | 197.6 |

Both runs use the same JVM and JMH settings, but they are separate runs and only the two
methods above were selected in the second run; the ratios are point estimates, not a
same-session A/B with a confidence interval. The after run's 99.9% JMH errors were ±36.0 ns
for unknown and ±51.6 ns for compound. Against the release run's evo2 references (80.420 and
95.773 ns), the optimized paths remain 7.57x and 4.62x slower.

### Post-release optimization: compacted

The 456.4 ns / 395.4 B compacted row is the pre-optimization release baseline. The same
unconditional single-token compound scan also ran here: before `4b79062`, every compacted
word entered `resolveCompound`, and `freeTailSuffix` allocated a fresh `"-" + tail` string
for each known free tail before falling through to the productive rule. That accounts for
most of the old 395 B/op.

Rerunning only the compacted method after the compound fast path:

| path | release baseline ns/op | after compound fix ns/op | change | baseline B/op | after B/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| compacted regular | 456.362 | 187.287 | 2.44x faster (-59.0%) | 395.4 | 51.4 |

Stack profiling then showed that roughly 24% of RUNNABLE samples went through
`Character.UnicodeScript.of` inside `WordShape.isPlainLatinToken` and
`isAllUpperLatinToken`. The ASCII fast path bypasses Unicode script lookup for ASCII
letters, which are all Latin, and for ASCII non-letters, which are never combining marks.
It also classifies ASCII casing without `Character.isLetter`/`isUpperCase` calls.

| path | before ASCII ns/op | after ASCII ns/op | change | B/op |
| --- | ---: | ---: | ---: | ---: |
| compacted regular | 187.287 | 105.008 | 1.78x faster (-43.9%) | 51.4 |

The seven-rule `List<Rule>` loop was then replaced with an ordered direct branch chain in
`applyRule`, shared by the fast path and compound-head respelling. This removes seven
interface-dispatched predicate calls for every dictionary miss.

| path | after ASCII ns/op | after flat dispatch ns/op | change | B/op |
| --- | ---: | ---: | ---: | ---: |
| compacted regular | 105.008 | 79.445 | 1.32x faster (-24.3%) | 51.4 |

The clean same-run evo2 reference is 59.826 ns/op and 80.572 B/op; the current compacted path
is **1.33x slower**, down from 1.78x after the ASCII path, 3.09x after the compound fix, and
4.36x in the release baseline. Allocation is unchanged and lower than evo2. The after figures
are separate targeted runs, not same-session A/Bs and not the full release workload.

The dispatch rewrite also removed `Rule.applies` from the stack sample. The remaining top
library frames were `isPlainLatinToken` (7.1% cumulative in one fork), `casingOf` (5.0%),
`Lexicon.find` (4.1%), and `hasUpper` (3.6%); `Workload.category` is harness overhead present
in both systems. Profile: `optimization-compacted-flat.log`; clean measurement:
`optimization-compacted-flat-clean.json`.

### CI smoke gate

The CI workflow runs `BenchmarkSmoke` after the normal Maven verification. It is a coarse gate,
not a benchmark: a 20,000-call warmup per category, then three 30,000-call measurements, with
the best reported. A category fails only when it exceeds **50,000 ns/op** or when its output
checksum changes between rounds. That ceiling is intentionally far above the release run
(roughly 55-500x the reported path times) so CI scheduling noise does not create a false
failure; it catches order-of-magnitude breakage, not small regressions. The command is:

```bash
java -cp inflector-benchmarks/target/benchmarks.jar \
  io.github.slayyyyer.morphex.inflector.bench.BenchmarkSmoke
```

`BenchmarkSmoke` timings must not be cited as performance measurements or compared with the
JMH results above.

### Concurrency

Four threads sharing one immutable instance, throughput over the mixed workload:

| system | ops/ms | approx ops/s |
| --- | ---: | ---: |
| evo-inflector 2.0 | 20,912.800 | 20.9 M/s |
| this library | 4,823.792 | 4.8 M/s |

This is a **4.34× lower throughput** in the same run. The instance is thread-safe, but the
current resolver does more allocation and table probing per call. The earlier “10.2M ops/s”
number was from the previous artifact and is not claimed here.

## Startup and retained footprint

`StartupFootprint`, fresh JVM with `-Xms256m -Xmx256m`, three runs:

| metric | measured |
| --- | --- |
| startup (class load + artifact load + first call) | 718-726 ms |
| first `pluralize` after load | 2.5-3.6 ms |
| retained heap after load (after GC) | 19,076,408-19,076,968 bytes (~18.2 MiB) |
| cold 50,000 calls with unseen tokens | 427.7-448.8 ms |
| lexicon entries | 147,338 |
| shaded benchmark JAR | 11,898,219 bytes |

The core artifact JAR is 8,840,811 bytes. There are no runtime dependencies, no model and no
native memory. Retained heap is lower than the historical packed measurement (~24 MB), but
startup on this run was slower than the old 426 ms median. Both figures are kept as measured
rather than mixing runs.

## Known gaps

* The resolver is still not competitive on cold/unknown/compacted/compound paths. The
  optimized unknown path is 7.57x slower, compacted is 1.33x slower, and compound is 4.62x
  slower than evo2; unknown lookup still pays for a cold lexical table probe, compacted still
  pays for shape scans and casing, and compound heads still allocate diagnostic state.
  The experimental head/split fast path was not retained because it did not measurably help.
* Concurrency is 4.34× below evo2 in this run, despite immutability.
* The previous historical corpus-only JMH numbers are preserved in `results/benchmarks/` but
  are not comparable to the fixed-category workload and are not used for current claims.
