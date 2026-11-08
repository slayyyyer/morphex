# Benchmarks and bundle sizes

These are **Node.js/V8 micro-benchmarks** for the TypeScript port. They are measured with a
different runtime and harness from the Java JMH results on the main site and are not comparable
with them. Nothing here is a cross-language speed claim.

## Method

- Harness: `morphex-ts/scripts/bench.ts` (`npm run bench`), no external benchmark dependency.
- Machine used for the recorded run: 12th Gen Intel Core i5-12500H, Windows 11 (10.0.26300).
- Runtime: Node.js 24.20.0 (the package targets Node 18+).
- Each workload warms up for one full pass, then takes seven timed samples and reports the
  median. The first timed sample is discarded while V8 finishes optimizing.
- Repeated workloads run 1,000,000 calls. Distinct-word workloads run ten passes over 4,000
  unique lemmas (40,000 calls per timed sample) taken from the committed P1 fixture.
- Five runs were recorded on the same machine to show the spread; ranges below are the
  minimum and maximum medians, not a confidence interval.
- Provider construction was measured separately with a tsx harness on the same machine: one
  warm-up, then twelve samples per run, with the median of each of two runs reported.

Reproduce with:

```bash
cd morphex-ts
npm install
npm run build:data   # writes the generated lexicons and dist/sizes.json
npm run bench        # writes bench-results.json and prints the table
```

## Latency (ns/op, lower is better)

| Workload | Delta engine | Full engine | Rules-only |
| --- | ---: | ---: | ---: |
| Repeated lexical word (`child`) | 111–119 | 269–280 | 130–139 |
| Repeated rule word (`cat`) | 112–118 | 174–188 | 104–116 |
| Repeated compound (`mother-in-law`) | 258–289 | 434–457 | 358–378 |
| Distinct fixture lemmas (4,000 words) | 354–432 | 1,006–1,057 | 327–355 |
| `analyze()` repeated (`die`) | 258–278 | 421–441 | 221–228 |

Reading the table:

- The **delta** engine answers `pluralize()` from the shipped artifact. At load time it parses
  the four artifact sections and inserts them directly into an open-addressed hash index over
  the 124,883 keys, so a rule-only word such as `cat` costs one hash and a short probe before
  the rules run. The original four-section implementation measured 673–678 ns; a merged-sort
  binary search measured 286–293 ns; the hash index measures 112–118 ns. That is about 5.7–6.0×
  faster than the original and 2.4–2.6× faster than the merged binary search, in the same
  harness.
- The **full** engine is opt-in (`morphex/full`) and adds alternatives, ambiguity and
  countability. It performs one binary search over its own 147,338-key array, so it is now slower
  than the delta engine on these paths (182–189 ns for `cat`), but it carries the metadata the
  diagnostics need.
- The **rules-only** engine (`morphex/rules`, or `withoutBundledLexicon()`) uses no data files and
  loses irregular forms by design.

The hash index costs 1 MiB of slot storage (262,144 × 4 bytes) and is built once, when the
provider is created (lazily, on first use). Creating the delta provider now measures 50.3–50.8
ms median over two runs; parsing the artifact accounts for 32.0–33.1 ms and building the index
roughly 17–19 ms. Before these changes, construction measured 77–81 ms and parsing 58–65 ms.
The reduction comes from inserting each section directly into the table instead of merging the
four lists first, dropping the cross-section duplicate `Set` and the intermediate body copy,
and sharing one frozen empty alternatives array. The index is still the smaller half of
construction; nothing is computed at module import.
- Distinct-word timings include cache misses over a 4,000-word working set and are naturally
  slower than repeated lookups; the spread between runs is largest there.

## Artifact and bundle sizes

Measured by `scripts/build-lexicon.ts` and `scripts/build-site.ts` and written to
`dist/sizes.json` / `dist/site/sizes.json`.

| Artifact | Raw | Gzip |
| --- | ---: | ---: |
| Source lexicon TSV (shared with Java, not shipped) | 7,553,688 B | — |
| Delta artifact for `pluralize()` (124,883 entries) | 1,962,900 B | 562,893 B |
| Full artifact for `analyze()` (147,338 entries) | 3,707,556 B | 785,131 B |
| Membership sidecar (340,726 hashes, base64) | 1,817,208 B | 1,333,152 B |
| Browser demo bundle (full lexicon, minified ESM) | 3,735,118 B | 794,141 B |

The delta artifact is 26.0% of the source TSV's size and 84.8% of the full artifact's entries:
85% of the lexicon is already reproduced by the three productive rules plus the identity lists,
so only the differing entries and the mass/plural-only/invariant key lists are stored.

The published `dist/lexicon-delta.txt` is the same 1,962,900-byte artifact the site links beside
the demo; browsers download it gzipped at 562,893 B.

## Notes

- The browser demo loads the **full lexicon without the membership sidecar**: 794,141 B gzip
  including the engine. Membership is only needed for unknown-uppercase initialism inference, so
  the demo behaves like a rules-only build for that one policy and says so on the page.
- The `morphex/rules` entry point replaces the generated delta module at build time; it contains
  zero lexicon data and therefore no CC BY-SA 3.0 material.
- Generated data and bundles live under `morphex-ts/dist/` and are gitignored; only the sizes in
  this file and on the site are committed.
