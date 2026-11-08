import { describe, expect, test } from 'vitest';

import { validateCompoundTable } from '../src/compound-table.js';
import { EnglishInflector } from '../src/english-inflector.js';
import { BUNDLED_DELTA_LEXICON } from '../src/generated/delta-data.js';
import {
  createDeltaLexicon,
  createFullLexicon,
  createLexicon,
  deriveEdit,
  fnv1aLow32,
  parseDeltaLexicon,
} from '../src/lexicon.js';
import type { Countability } from '../src/types.js';

const FULL_HEADER = 'morphex-lexicon-v1\tfull\t1';
const DELTA_HEADER = 'morphex-delta-v1\t1\t1\t1\t1';

describe('artifact parsing', () => {
  test('computes the documented end-of-token edit', () => {
    expect(deriveEdit('mouse', 'mice')).toEqual([4, 'ice']);
    expect(deriveEdit('child', 'children')).toEqual([0, 'ren']);
    expect(deriveEdit('cat', 'cats')).toEqual([0, 's']);
  });

  test('loads a minimal full artifact', () => {
    const packed = `${FULL_HEADER}\ncat\t0\ts\t-\t-\t-\tC\t0\n`;
    const provider = createFullLexicon(packed);
    expect(provider.size).toBe(1);
    expect(provider.diagnostics).toBe(true);
    expect(provider.lookup('cat')?.plural).toBe('cats');
    expect(provider.lookup('dog')).toBeUndefined();
  });

  test('loads a minimal delta artifact with identity key lists', () => {
    const packed = `${DELTA_HEADER}\ndie\t3\tdice\t-\t-\tdies\tC\t8\nadvice\nscissors\nsheep\n`;
    const provider = createDeltaLexicon(packed);
    expect(provider.size).toBe(4);
    expect(provider.diagnostics).toBe(false);
    const die = provider.lookup('die');
    expect(die?.plural).toBe('dice');
    expect(die?.alternatives).toEqual(['dies']);
    expect(die?.ambiguous).toBe(true);
    expect(provider.lookup('advice')?.countability).toBe('MASS');
    expect(provider.lookup('scissors')?.countability).toBe('PLURAL_ONLY');
    expect(provider.lookup('sheep')?.invariant).toBe(true);
    expect(provider.lookup('cat')).toBeUndefined();
  });

  test.each([
    ['empty artifact', ''],
    ['bad full header', 'nope\n'],
    ['bad delta header', 'nope\t0\t0\t0\t0\n'],
    ['count mismatch', `${DELTA_HEADER}\nadvice\nsheep\n`],
    ['unsorted records', `${FULL_HEADER}\ncat\t0\ts\t-\t-\t-\tC\t0\nant\t0\ts\t-\t-\t-\tC\t0\n`],
    ['duplicate keys', `${FULL_HEADER}\ncat\t0\ts\t-\t-\t-\tC\t0\ncat\t0\ts\t-\t-\t-\tC\t0\n`],
    ['unsorted identity keys', `${DELTA_HEADER}\nsheep\nadvice\n`],
    ['uppercase identity key', `${DELTA_HEADER}\nAdvice\n`],
    ['unknown countability', `${FULL_HEADER}\ncat\t0\ts\t-\t-\t-\tZ\t0\n`],
    ['unknown flags', `${FULL_HEADER}\ncat\t0\ts\t-\t-\t-\tC\t99\n`],
  ])('rejects malformed data: %s', (_label, packed) => {
    const load = packed.includes('morphex-delta-v1')
      ? () => createDeltaLexicon(packed)
      : () => createFullLexicon(packed);
    expect(load).toThrow();
  });

  test('rejects an entry that appears in two sections', () => {
    const packed = 'morphex-delta-v1\t1\t1\t0\t0\ndie\t3\tdice\t-\t-\tdies\tC\t8\ndie\n';
    expect(() => createDeltaLexicon(packed)).toThrow(/duplicate identity key/);
  });
});

describe('bundled delta hash index', () => {
  const sections = parseDeltaLexicon(BUNDLED_DELTA_LEXICON);
  const provider = createDeltaLexicon(BUNDLED_DELTA_LEXICON);

  test('reports every section in its size and misses unknown keys', () => {
    expect(provider.size).toBe(
      sections.edits.length +
        sections.massKeys.length +
        sections.pluralOnlyKeys.length +
        sections.invariantKeys.length,
    );
    expect(sections.edits.length).toBeGreaterThan(0);
    expect(sections.massKeys.length).toBeGreaterThan(0);
    expect(provider.lookup('zzzz-not-a-delta-key')).toBeUndefined();
    expect(provider.lookup('')).toBeUndefined();
  });

  test('resolves every edit entry to its own record', () => {
    const failures: string[] = [];
    for (const entry of sections.edits) {
      const found = provider.lookup(entry.key);
      if (
        found === undefined ||
        found.key !== entry.key ||
        found.plural !== entry.plural ||
        found.classical !== entry.classical ||
        found.countability !== entry.countability ||
        found.invariant !== entry.invariant ||
        found.initialism !== entry.initialism ||
        found.caseSensitive !== entry.caseSensitive ||
        found.ambiguous !== entry.ambiguous ||
        found.alternatives.length !== entry.alternatives.length
      ) {
        failures.push(entry.key);
      }
    }
    expect(failures).toEqual([]);
  });

  test('resolves every identity key with its identity semantics', () => {
    const failures: string[] = [];
    const check = (
      keys: readonly string[],
      countability: Countability,
      invariant: boolean,
    ): void => {
      for (const key of keys) {
        const found = provider.lookup(key);
        if (
          found === undefined ||
          found.key !== key ||
          found.lemma !== key ||
          found.plural !== key ||
          found.countability !== countability ||
          found.invariant !== invariant ||
          found.alternatives.length !== 0
        ) {
          failures.push(key);
        }
      }
    };
    check(sections.massKeys, 'MASS', false);
    check(sections.pluralOnlyKeys, 'PLURAL_ONLY', false);
    check(sections.invariantKeys, 'UNKNOWN', true);
    expect(failures).toEqual([]);
  });
});

describe('custom lexicons', () => {
  test('plugs into the same pipeline as the bundled data', () => {
    const lexicon = createLexicon([
      { lemma: 'zorp', plural: 'zorpi' },
      { lemma: 'frob', plural: 'frobs', alternatives: ['frobes'], ambiguous: true },
    ]);
    const inflector = EnglishInflector.builder().withLexicon(lexicon).build();
    expect(inflector.pluralize('zorp')).toBe('zorpi');
    expect(inflector.pluralize('ZORP')).toBe('ZORPI');
    expect(inflector.analyze('frob').alternatives).toEqual(['frobes']);
    expect(inflector.analyze('frob').ambiguous).toBe(true);
    expect(inflector.pluralize('cat')).toBe('cats');
  });

  test('rejects duplicate keys and empty forms', () => {
    expect(() =>
      createLexicon([
        { lemma: 'zorp', plural: 'zorpi' },
        { lemma: 'Zorp', plural: 'zorps' },
      ]),
    ).toThrow(/duplicate/);
    expect(() => createLexicon([{ lemma: '', plural: 'x' }])).toThrow();
    expect(() => createLexicon([{ lemma: 'zorp', plural: '' }])).toThrow();
  });
});

describe('membership hashing', () => {
  test('is deterministic and stable for known keys', () => {
    expect(fnv1aLow32('cat')).toBe(fnv1aLow32('cat'));
    expect(fnv1aLow32('cat')).not.toBe(fnv1aLow32('cats'));
    expect(Number.isInteger(fnv1aLow32('child'))).toBe(true);
    expect(fnv1aLow32('child') >>> 0).toBe(fnv1aLow32('child'));
  });
});

describe('compound table', () => {
  test('validates at load time', () => {
    expect(() => validateCompoundTable()).not.toThrow();
  });
});
