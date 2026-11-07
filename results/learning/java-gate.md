# Java linear-selector runtime gates

The corrected linear selector was exported as a 4,096-float32 artifact and mirrored by the
dependency-free Java scorer in `inflector-eval`. The scorer has no runtime dependencies and is
not on the consumer classpath.

## Exactness

The Java scorer was run over the full 36,239-record P2 test population and compared with the
Python selector loaded from the same artifact:

| check | result |
| --- | --- |
| records | 36,239 |
| prediction mismatches | **0** |
| test errors | **801** (97.790%) |
| corrections / regressions | **310 / 110** |
| overrides | **537** |

This reproduces the corrected selector's frozen result exactly.

## Gate measurements

Five fresh JVMs (`-Xms256m -Xmx256m`), 50,000 warm-up calls and 200,000 measured calls each.
Latency is per selector invocation; initialization is model load plus the first selection.

| metric | median | observed range | gate | verdict |
| --- | ---: | ---: | ---: | --- |
| p99 latency | **14.1 µs** | 13.2–17.7 µs | ≤ 10 µs | **FAIL** |
| model artifact | **16,412 B** (16.027 KiB) | fixed | ≤ 32 KiB | **PASS** |
| cold initialization | **14.709 ms** | 11.844–19.326 ms | ≤ 10 ms | **FAIL** |

Supporting medians: p50 2.8 µs, p999 66.1 µs, mean 3.976 µs; model load 12.062 ms and first
selection 1.382 ms. The overall runtime gate is **FAIL**.

The repository does not record numeric values for these gates. The runner uses explicit,
CLI-overridable defaults (10 µs / 32 KiB / 10 ms); the report above is the strict-default
evaluation, not a claim that another deployment's gate values would produce the same verdict.

## Reproduction

```bash
./mvnw -q -o -pl inflector-eval -am test
py -3 -m tools.research.cli learn --linear-only --iterations 1000
java -cp inflector-eval/target/classes \
  io.github.slayyyyer.morphex.inflector.eval.LinearSelectorEval \
  --model results/learning/linear-v1.bin \
  --records results/learning/java-test-input.tsv \
  --out results/learning/java-test-predictions.tsv
java -Xms256m -Xmx256m -cp inflector-eval/target/classes \
  io.github.slayyyyer.morphex.inflector.eval.LinearSelectorGate \
  --model results/learning/linear-v1.bin \
  --records results/learning/java-test-input.tsv \
  --out results/learning/java-gate.txt
```

The full machine-readable result is `results/learning/java-gate.json`.
