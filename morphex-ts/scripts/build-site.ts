/**
 * Builds the browser demo published beside the GitHub Pages site.
 *
 * Outputs (gitignored), copied to `_pages/demo/` by the Pages workflow:
 *   dist/site/morphex-demo.js     minified ESM bundle of the real engine + full lexicon
 *   dist/site/lexicon-delta.txt   the packed delta artifact, downloadable beside the page
 *   dist/site/sizes.json          measured bundle and data sizes
 */

import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

import { build, type Plugin } from 'esbuild';

const here = path.dirname(fileURLToPath(import.meta.url));
const packageRoot = path.resolve(here, '..');
const distDir = path.join(packageRoot, 'dist');
const siteDir = path.join(distDir, 'site');

interface Size {
  readonly bytes: number;
  readonly gzipBytes: number;
}

/**
 * The demo never calls `EnglishInflector.standard()`, but `defaults.ts` imports the bundled delta
 * artifact, which would add 1.2 MB gzip of unused data to the page bundle. Replace it with an
 * empty, valid artifact so only the full lexicon is shipped.
 */
const emptyDeltaPlugin: Plugin = {
  name: 'morphex-demo-empty-delta',
  setup(build) {
    build.onResolve({ filter: /generated\/delta-data\.js$/ }, () => ({
      path: 'empty-delta',
      namespace: 'morphex-demo',
    }));
    build.onLoad({ filter: /.*/, namespace: 'morphex-demo' }, () => ({
      contents: 'export const BUNDLED_DELTA_LEXICON = "morphex-delta-v1\\t0\\t0\\t0\\t0\\n";',
      loader: 'js',
    }));
  },
};

function measure(file: string): Size {
  const bytes = readFileSync(file);
  return { bytes: bytes.length, gzipBytes: gzipSync(bytes).length };
}

async function main(): Promise<void> {
  mkdirSync(siteDir, { recursive: true });
  const outfile = path.join(siteDir, 'morphex-demo.js');
  await build({
    entryPoints: [path.join(packageRoot, 'src', 'site', 'demo.ts')],
    outfile,
    bundle: true,
    format: 'esm',
    platform: 'browser',
    target: 'es2020',
    minify: true,
    legalComments: 'inline',
    banner: {
      js: '/*! morphex — MIT code; bundled lexicon data is CC BY-SA 3.0 (Wiktionary contributors via Kaikki.org/Wiktextract). See NOTICE. */',
    },
    plugins: [emptyDeltaPlugin],
  });

  const deltaSource = path.join(distDir, 'lexicon-delta.txt');
  copyFileSync(deltaSource, path.join(siteDir, 'lexicon-delta.txt'));

  const sizesFile = path.join(distDir, 'sizes.json');
  const sizes = JSON.parse(readFileSync(sizesFile, 'utf8')) as Record<string, unknown>;
  const report = {
    ...sizes,
    demoBundle: measure(outfile),
    publishedDelta: measure(path.join(siteDir, 'lexicon-delta.txt')),
  };
  writeFileSync(path.join(siteDir, 'sizes.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(
    `demo    ${report.demoBundle.bytes} B raw, ${report.demoBundle.gzipBytes} B gzip ` +
      `(${statSync(outfile).size} bytes on disk)`,
  );
}

await main();
