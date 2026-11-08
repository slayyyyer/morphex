import { defineConfig, type Options } from 'tsup';
import type { Plugin } from 'esbuild';

const banner =
  '/*! morphex — MIT code; bundled lexicon data is CC BY-SA 3.0 (Wiktionary contributors via Kaikki.org/Wiktextract). See NOTICE. */';

/**
 * The `morphex/rules` entry is the data-free build: the generated delta lexicon module is
 * replaced with an empty artifact, so `EnglishInflector.standard()` is a rules-only instance
 * and the bundle contains no lexicon data at all.
 */
const rulesOnlyDataPlugin: Plugin = {
  name: 'morphex-rules-only-data',
  setup(build) {
    build.onResolve({ filter: /generated\/delta-data\.js$/ }, () => ({
      path: 'empty-lexicon',
      namespace: 'morphex-rules-only',
    }));
    build.onLoad({ filter: /.*/, namespace: 'morphex-rules-only' }, () => ({
      contents: 'export const BUNDLED_DELTA_LEXICON = "morphex-lexicon-v1\\tdelta\\t0\\n";',
      loader: 'js',
    }));
  },
};

const shared: Options = {
  format: ['esm', 'cjs'],
  target: 'node18',
  dts: true,
  sourcemap: true,
  splitting: false,
  treeshake: true,
  banner: { js: banner },
};

export default defineConfig([
  {
    ...shared,
    entry: {
      index: 'src/index.ts',
      full: 'src/full.ts',
    },
    clean: true,
  },
  {
    ...shared,
    entry: {
      rules: 'src/index.ts',
    },
    clean: false,
    esbuildPlugins: [rulesOnlyDataPlugin],
  },
]);
