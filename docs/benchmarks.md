# Benchmarks

JMH 1.37, two independent forks, average time, GC profiler. The current run is
`results/release-1.2.0/benchmarks/jmh-workload-v1.json`; the log is next to it. The fixed
input is `inflector-benchmarks/src/main/resources/benchmark-workload-v1.tsv` (version 1).

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

`avgt` is arithmetic mean latency. Alloc is normalized GC allocation per operation.

| benchmark | score ns/op | alloc B/op |
| --- | ---: | ---: |
| evo2LexicalHit | 118.495 | 121.7 |
| **inflectorLexicalHit** | **100.733** | 65.7 |
| evo2Compacted | 104.666 | 80.6 |
| inflectorCompacted | 456.362 | 395.4 |
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

Same-run ratios:

| path | ratio to evo2 | reading |
| --- | ---: | --- |
| repeated lookup | 0.73× | 1.36× faster |
| lexical hit | 0.85× | 1.18× faster |
| count path | 1.45× | slower |
| historical distinct corpus | 1.48× | slower |
| mixed case | 2.37× | slower |
| compacted regular | 4.36× | slower |
| compound | 5.22× | slower |
| unsupported | 4.89× | slower |
| uppercase inference | 7.92× | slower |
| unknown token | 11.27× | slower |
| diagnostics | 1.18× | slower (evo2 has no equivalent API) |

The fixed workload changes the conclusion from the old corpus-only run: the library is faster
only on repeated and exact lexical hits. Compacted, unknown, uppercase, compound and
unsupported paths repeatedly fall through compound tables and allocate diagnostic/rule
machinery even on the fast path. No general speedup is claimed.

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

* The resolver is not competitive on cold/unknown/compound paths; profile-guided reuse of
  compound parsing and avoiding diagnostic allocation on the plain fast path are the obvious
  next steps.
* Concurrency is 4.34× below evo2 in this run, despite immutability.
* The previous historical corpus-only JMH numbers are preserved in `results/benchmarks/` but
  are not comparable to the fixed-category workload and are not used for current claims.
