/**
 * Simple Node micro-benchmarks: repeated lookup versus distinct-word lookup, for the bundled
 * delta engine, the full-diagnostics engine and the rules-only engine.
 *
 * These numbers are Node/V8 measurements. They are not comparable to the Java JMH results and
 * are never presented as if they were. Writes `bench-results.json` and prints a table.
 */

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { cpus, platform, release } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

import { EnglishInflector } from '../src/english-inflector.js';
import { createBundledFullLexicon } from '../src/lexicon-full.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const packageRoot = path.resolve(here, '..');
const repoRoot = path.resolve(packageRoot, '..');

interface Timed {
  readonly nsPerOp: number;
  readonly opsPerSecond: number;
}

function median(values: readonly number[]): number {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.floor(sorted.length / 2)] ?? 0;
}

function time(label: string, iterations: number, action: (index: number) => string): Timed {
  for (let index = 0; index < iterations; index += 1) {
    action(index);
  }
  const samples: number[] = [];
  for (let sample = 0; sample < 7; sample += 1) {
    const start = process.hrtime.bigint();
    for (let index = 0; index < iterations; index += 1) {
      action(index);
    }
    const elapsed = Number(process.hrtime.bigint() - start);
    samples.push(elapsed / iterations);
  }
  // Drop the first timed sample: V8 may still be optimizing the loop body.
  samples.shift();
  const nsPerOp = median(samples);
  console.log(`${label.padEnd(46)} ${nsPerOp.toFixed(1).padStart(9)} ns/op`);
  return { nsPerOp, opsPerSecond: 1e9 / nsPerOp };
}

const delta = EnglishInflector.standard();
const full = EnglishInflector.builder().withLexicon(createBundledFullLexicon()).build();
const rulesOnly = EnglishInflector.builder().withoutBundledLexicon().build();

const fixtures = JSON.parse(
  readFileSync(path.join(repoRoot, 'test-fixtures', 'p1.json'), 'utf8'),
) as { readonly cases: readonly (readonly [string, string, ...unknown[]])[] };
const distinct = [...new Set(fixtures.cases.map((entry) => entry[1]))].slice(0, 4000);
if (distinct.length < 1000) {
  throw new Error('expected at least 1000 distinct fixture inputs');
}

const REPEATED = 1_000_000;
const DISTINCT = distinct.length;
// Ten passes per timed sample: 4,000 calls is too short to be stable on a cache-bound workload.
const DISTINCT_ITERATIONS = DISTINCT * 10;

console.log(
  `node ${process.version} · ${platform()} ${release()} · ${cpus()[0]?.model ?? 'unknown'}`,
);
console.log(`distinct fixture inputs: ${DISTINCT}\n`);

const results = {
  node: process.version,
  platform: `${platform()} ${release()}`,
  cpu: cpus()[0]?.model ?? 'unknown',
  repeatedIterations: REPEATED,
  distinctInputs: DISTINCT,
  distinctIterations: DISTINCT_ITERATIONS,
  workloads: {
    repeatedLexical: {
      delta: time('delta · repeated lexical (child)', REPEATED, () => delta.pluralize('child')),
      full: time('full · repeated lexical (child)', REPEATED, () => full.pluralize('child')),
      rulesOnly: time('rules-only · repeated (child)', REPEATED, () =>
        rulesOnly.pluralize('child'),
      ),
    },
    repeatedCompacted: {
      delta: time('delta · repeated compacted (cat)', REPEATED, () => delta.pluralize('cat')),
      full: time('full · repeated compacted (cat)', REPEATED, () => full.pluralize('cat')),
      rulesOnly: time('rules-only · repeated (cat)', REPEATED, () => rulesOnly.pluralize('cat')),
    },
    repeatedCompound: {
      delta: time('delta · repeated compound (mother-in-law)', REPEATED, () =>
        delta.pluralize('mother-in-law'),
      ),
      full: time('full · repeated compound (mother-in-law)', REPEATED, () =>
        full.pluralize('mother-in-law'),
      ),
      rulesOnly: time('rules-only · repeated (mother-in-law)', REPEATED, () =>
        rulesOnly.pluralize('mother-in-law'),
      ),
    },
    distinct: {
      delta: time('delta · distinct fixture words', DISTINCT_ITERATIONS, (index) =>
        delta.pluralize(distinct[index % DISTINCT] ?? ''),
      ),
      full: time('full · distinct fixture words', DISTINCT_ITERATIONS, (index) =>
        full.pluralize(distinct[index % DISTINCT] ?? ''),
      ),
      rulesOnly: time('rules-only · distinct fixture words', DISTINCT_ITERATIONS, (index) =>
        rulesOnly.pluralize(distinct[index % DISTINCT] ?? ''),
      ),
    },
    analyze: {
      full: time('full · analyze repeated (die)', REPEATED / 4, () => full.analyze('die').plural),
      delta: time(
        'delta · analyze repeated (die)',
        REPEATED / 4,
        () => delta.analyze('die').plural,
      ),
      rulesOnly: time(
        'rules-only · analyze repeated (die)',
        REPEATED / 4,
        () => rulesOnly.analyze('die').plural,
      ),
    },
  },
};

function bundleSizes(): Record<string, { bytes: number; gzipBytes: number }> {
  const distDir = path.join(packageRoot, 'dist');
  const files = readdirSync(distDir).filter(
    (name) => name.endsWith('.js') || name.endsWith('.cjs'),
  );
  const sizes: Record<string, { bytes: number; gzipBytes: number }> = {};
  for (const name of files.sort()) {
    const bytes = readFileSync(path.join(distDir, name));
    sizes[name] = { bytes: bytes.length, gzipBytes: gzipSync(bytes).length };
  }
  return sizes;
}

const dataSizes = JSON.parse(
  readFileSync(path.join(packageRoot, 'dist', 'sizes.json'), 'utf8'),
) as {
  readonly delta?: { readonly bytes: number; readonly gzipBytes: number };
  readonly full?: { readonly bytes: number; readonly gzipBytes: number };
};
const report = {
  ...results,
  bundles: bundleSizes(),
  delta: dataSizes.delta ?? null,
  full: dataSizes.full ?? null,
};
writeFileSync(path.join(packageRoot, 'bench-results.json'), `${JSON.stringify(report, null, 2)}\n`);
console.log('\nwrote bench-results.json');
