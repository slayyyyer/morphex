# 1.3.0 P1/P2 revalidation

This directory re-runs the frozen P1/P2 protocol against the **published Maven Central
1.3.0 runtime**. It is a revalidation, not a new evaluation protocol.

## Artifact

| field | value |
| --- | --- |
| coordinate | `io.github.slayyyyer.morphex:english-inflector-core:1.3.0` |
| source | https://repo1.maven.org/maven2/io/github/slayyyyer/morphex/english-inflector-core/1.3.0/english-inflector-core-1.3.0.jar |
| SHA-256 | `ec46fce57348e42b723f693dc5272e718cb9a6431ed08d8e680d8b7c6d3fd5da` |
| P2 train-only artifact | `build/p2/lexicon-v2.tsv` `b0aba4a7616222228a6b5f61866502d6c27c755bfb328dd706a4bef12a4675dd` |
| P2 membership sidecar | `build/p2/lexicon-membership-v2.bin` `70d7bf7a63c38c81b6d551232ca38d4afe5e1ab72b9b7835f2952c46decb80f6` |
| run revision | `28b979ccd3ca846260eb24641c4a170c917d8ef2` |
| frozen revision | `05fefd36553cae68490acd317e472373e21bd8c7` |

The Central JAR was placed first on the evaluator classpath. The P1 run used its bundled
artifact; P2 used the unchanged train-only artifact through the evaluator's external-lexicon
hook. The input records and gold files were copied byte-for-byte from
`results/release-1.2.0/eval/`; the comparison confirms their hashes.

## Results

| protocol | system | primary accuracy | primary errors | overall errors |
| --- | --- | ---: | ---: | ---: |
| P1 test | evo2 | 96.474% | 1278/36241 | 15889 |
| P1 test | rules | 95.163% | 1753/36241 | 16416 |
| P1 test | lexicon | **100.000%** | **0/36241** | **0/50978** |
| P2 test | evo2 | 96.474% | 1278/36241 | 15889 |
| P2 test | rules | 95.163% | 1753/36241 | 16416 |
| P2 test | lexicon | **97.365%** | **955/36241** | **15571/50978** |
| P2 dev | evo2 | 96.400% | 1313/36473 | 15557 |
| P2 dev | rules | 95.257% | 1730/36473 | 16018 |
| P2 dev | lexicon | **97.382%** | **955/36473** | **15202/50829** |

Paired P2 test comparison against evo2: **+0.8913 pp**
`[+0.7355, +1.0531]`, 538 corrections and
215 regressions. P1 remains 0 errors because this is
frozen-lexicon coverage, not a generalization claim.

## Comparison with frozen 1.2.0 evidence

- P1 prediction file: **byte-identical** (`0283afa3183d272ced51509fe88bc00b28a9371aafa008ac56c1dd8ec6785c07`).
- P2 dev prediction file: **byte-identical** (`1ab5af69ecc0a069179eea8e6bc9052bb76b0739954d4199c83b1abe3db55b93`).
- P2 test prediction file: **byte-identical** (`6c2c4ce29ab48619cb93d927affaf7f47d483bd10ac9c456f99797977de8beaf`).
- All `systems` and `comparisons` payloads: **identical** in all three reports.
- Full report payloads, excluding `generated` and `revision`: **identical** in all three reports.
- The 2,000-round family-clustered bootstrap seed is unchanged, so the confidence intervals
  are identical.
- Only the report generation timestamp and source revision differ. The rerun revision is
  `28b979ccd3ca846260eb24641c4a170c917d8ef2`; the frozen report revision was `05fefd36553cae68490acd317e472373e21bd8c7`.

## Interpretation

The 1.3.0 runtime preserves the 1.2.0 P1/P2 behavior exactly on the frozen protocol. The
release's changes are therefore evidence and performance work, not a pluralization-behavior
change. This rerun does not cover EWT, AGID, benchmarks, or application-call accuracy; those
remain in their separate recorded evidence.

## Files

- `comparison.json` - machine-readable comparison and artifact hashes.
- `EVIDENCE.tsv` - hashes for the rerun reports, predictions, and comparison.
- `eval/*-report.json|md` - rerun scores.
- `eval/*-preds.tsv` - rerun predictions.
- `eval/*predict.log`, `eval/*score.log` - command output.
