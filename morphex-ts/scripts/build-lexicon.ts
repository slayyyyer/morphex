/**
 * Builds the TypeScript lexicons from the repository's authoritative artifact.
 *
 * Inputs (shared with the Java build, not copied):
 *   inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv
 *   inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-membership-v2.bin
 *
 * Outputs (gitignored):
 *   src/generated/delta-data.ts        delta artifact for pluralize()
 *   src/generated/full-data.ts         full artifact for analyze() diagnostics
 *   src/generated/membership-data.ts   ordinary-word membership hashes
 *   dist/lexicon-delta.txt             packed artifact (downloadable)
 *   dist/lexicon-full.txt              packed artifact
 *   dist/sizes.json                    measured raw and gzipped byte counts
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

import { validateCompoundTable } from '../src/compound-table.js';
import { deriveEdit } from '../src/lexicon.js';
import { rulesPlural } from '../src/rules.js';
import type { Countability } from '../src/types.js';
import { COUNTABILITIES } from '../src/types.js';
import { isPlainLatinToken, lower } from '../src/word-shape.js';

const FULL_HEADER = 'morphex-lexicon-v1';
const DELTA_HEADER = 'morphex-delta-v1';
const SOURCE =
  'inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv';
const MEMBERSHIP = 'lexicon-membership-v2.bin';

const here = path.dirname(fileURLToPath(import.meta.url));
const packageRoot = path.resolve(here, '..');
const repoRoot = path.resolve(packageRoot, '..');
const quiet = process.argv.includes('--quiet');
const explicitInput = process.argv.slice(2).find((argument) => !argument.startsWith('--'));

const lexiconPath = explicitInput ? path.resolve(explicitInput) : path.join(repoRoot, SOURCE);
const membershipPath = path.join(path.dirname(lexiconPath), MEMBERSHIP);
const generatedDir = path.join(packageRoot, 'src', 'generated');
const distDir = path.join(packageRoot, 'dist');

const COUNTABILITY_CODES = {
  COUNTABLE: 'C',
  MASS: 'M',
  MIXED: 'X',
  PLURAL_ONLY: 'P',
  UNKNOWN: 'U',
} as const satisfies Record<Countability, string>;

const FLAG_INVARIANT = 1;
const FLAG_INITIALISM = 2;
const FLAG_CASE_SENSITIVE = 4;
const FLAG_AMBIGUOUS = 8;
const ALL_FLAGS = FLAG_INVARIANT | FLAG_INITIALISM | FLAG_CASE_SENSITIVE | FLAG_AMBIGUOUS;

interface ArtifactEntry {
  readonly lemma: string;
  readonly key: string;
  readonly plural: string;
  readonly classical: string | null;
  readonly alternatives: readonly string[];
  readonly countability: Countability;
  readonly invariant: boolean;
  readonly initialism: boolean;
  readonly caseSensitive: boolean;
  readonly ambiguous: boolean;
  readonly flags: number;
}

function fail(lineNumber: number, message: string): never {
  throw new Error(`lexicon line ${lineNumber}: ${message}`);
}

function parseCountability(value: string, lineNumber: number): Countability {
  const found = COUNTABILITIES.find((candidate) => candidate === value);
  if (found === undefined) {
    fail(lineNumber, `unknown countability '${value}'`);
  }
  return found;
}

function parseFlags(value: string, lineNumber: number): number {
  let flags = 0;
  for (const character of value) {
    switch (character) {
      case 'I':
        flags |= FLAG_INVARIANT;
        break;
      case 'N':
        flags |= FLAG_INITIALISM;
        break;
      case 'C':
        flags |= FLAG_CASE_SENSITIVE;
        break;
      case 'A':
        flags |= FLAG_AMBIGUOUS;
        break;
      case '-':
      case ',':
        break;
      default:
        fail(lineNumber, `unknown lexicon flag '${character}'`);
    }
  }
  if ((flags & ~ALL_FLAGS) !== 0) {
    fail(lineNumber, 'unknown lexicon flags');
  }
  return flags;
}

function assertForm(value: string, lineNumber: number, field: string): void {
  if (value.length === 0) {
    fail(lineNumber, `empty ${field}`);
  }
  if (value.includes('\t')) {
    fail(lineNumber, `${field} contains a tab`);
  }
  for (const character of value) {
    const codePoint = character.codePointAt(0) ?? 0;
    if (codePoint < 0x20 || codePoint === 0x7f) {
      fail(lineNumber, `${field} contains a control character`);
    }
  }
}

function parseArtifact(text: string): readonly ArtifactEntry[] {
  const entries: ArtifactEntry[] = [];
  const seen = new Set<string>();
  const lines = text.split('\n');
  let sawHeader = false;
  for (let index = 0; index < lines.length; index += 1) {
    const raw = lines[index];
    if (raw === undefined) {
      continue;
    }
    const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
    if (line.length === 0 || line.startsWith('#')) {
      continue;
    }
    if (!sawHeader) {
      sawHeader = true;
      if (line.startsWith('lemma\t')) {
        continue;
      }
    }
    const fields = line.split('\t');
    if (fields.length !== 7) {
      fail(index + 1, `expected 7 columns, found ${fields.length}`);
    }
    const [lemma, plural, classicalField, alternativesField, countabilityField, flagsField] =
      fields;
    if (
      lemma === undefined ||
      plural === undefined ||
      classicalField === undefined ||
      alternativesField === undefined ||
      countabilityField === undefined ||
      flagsField === undefined
    ) {
      fail(index + 1, 'malformed row');
    }
    assertForm(lemma, index + 1, 'lemma');
    assertForm(plural, index + 1, 'plural');

    const key = lower(lemma);
    if (seen.has(key)) {
      fail(index + 1, `duplicate lemma key '${key}'`);
    }
    seen.add(key);

    const classical = classicalField === '-' ? null : classicalField;
    if (classical !== null) {
      assertForm(classical, index + 1, 'classical plural');
    }
    let alternatives: readonly string[] = [];
    if (alternativesField !== '-') {
      const parsed = alternativesField.split(',');
      for (const alternative of parsed) {
        assertForm(alternative, index + 1, 'alternative');
        if (alternative.includes(',')) {
          fail(index + 1, 'alternative contains a comma');
        }
      }
      alternatives = parsed;
    }
    const countability = parseCountability(countabilityField, index + 1);
    const flags = parseFlags(flagsField, index + 1);

    entries.push({
      lemma,
      key,
      plural,
      classical,
      alternatives,
      countability,
      invariant: (flags & FLAG_INVARIANT) !== 0,
      initialism: (flags & FLAG_INITIALISM) !== 0,
      caseSensitive: (flags & FLAG_CASE_SENSITIVE) !== 0,
      ambiguous: (flags & FLAG_AMBIGUOUS) !== 0,
      flags,
    });
  }
  if (entries.length === 0) {
    throw new Error('lexicon artifact contains no entries');
  }
  return entries;
}

function packEntry(entry: ArtifactEntry): string {
  const [cut, append] = deriveEdit(entry.lemma, entry.plural);
  let classicalCut = '-';
  let classicalAppend = '-';
  if (entry.classical !== null) {
    const [classicalCutValue, classicalAppendValue] = deriveEdit(entry.lemma, entry.classical);
    classicalCut = String(classicalCutValue);
    classicalAppend = classicalAppendValue;
  }
  const alternatives = entry.alternatives.length === 0 ? '-' : entry.alternatives.join(',');
  return [
    entry.lemma,
    String(cut),
    append,
    classicalCut,
    classicalAppend,
    alternatives,
    COUNTABILITY_CODES[entry.countability],
    String(entry.flags),
  ].join('\t');
}

function packFull(entries: readonly ArtifactEntry[]): string {
  const sorted = [...entries].sort((left, right) =>
    left.key < right.key ? -1 : left.key > right.key ? 1 : 0,
  );
  const records = sorted.map((entry) => packEntry(entry));
  return `${FULL_HEADER}\tfull\t${records.length}\n${records.join('\n')}\n`;
}

type DeltaPlacement = 'edit' | 'mass' | 'plural-only' | 'invariant' | 'drop';

/**
 * Places an artifact entry in the smallest delta structure that preserves its pluralize() answer.
 *
 * Mass, plural-only and invariant nouns return the input unchanged, so they need only a sorted
 * key, not a full record. An entry is a real edit when the true answer differs from the rules, or
 * when a classical form or initialism suffix changes a decision. Alternatives and ambiguity alone
 * do not change pluralize(), so those entries are only carried by the full artifact.
 */
function placeInDelta(entry: ArtifactEntry): DeltaPlacement {
  const identity =
    entry.invariant || entry.countability === 'MASS' || entry.countability === 'PLURAL_ONLY';
  if (
    identity &&
    !entry.caseSensitive &&
    entry.classical === null &&
    entry.alternatives.length === 0 &&
    !entry.ambiguous &&
    !entry.initialism
  ) {
    if (entry.countability === 'MASS') {
      return 'mass';
    }
    if (entry.countability === 'PLURAL_ONLY') {
      return 'plural-only';
    }
    return 'invariant';
  }
  // The productive rules are only reachable for plain Latin tokens. A hyphenated or spaced
  // lemma ("blow-by-blow", "ape-person") would fall to the fallback instead, so its lexical
  // entry must stay in the delta even when the rules could reproduce the same letters.
  const rulesReachable = isPlainLatinToken(entry.lemma);
  const mismatch =
    rulesPlural(entry.lemma) !== entry.plural || rulesPlural(entry.key) !== lower(entry.plural);
  if (!rulesReachable || mismatch || entry.classical !== null || entry.initialism || identity) {
    return 'edit';
  }
  return 'drop';
}

function packDelta(entries: readonly ArtifactEntry[]): {
  readonly packed: string;
  readonly editCount: number;
  readonly massCount: number;
  readonly pluralOnlyCount: number;
  readonly invariantCount: number;
} {
  const edits: ArtifactEntry[] = [];
  const mass: string[] = [];
  const pluralOnly: string[] = [];
  const invariant: string[] = [];
  for (const entry of entries) {
    switch (placeInDelta(entry)) {
      case 'edit':
        edits.push(entry);
        break;
      case 'mass':
        mass.push(entry.key);
        break;
      case 'plural-only':
        pluralOnly.push(entry.key);
        break;
      case 'invariant':
        invariant.push(entry.key);
        break;
      case 'drop':
        break;
    }
  }
  const sortKeys = (values: string[]): string[] =>
    values.sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  const sortedEdits = [...edits].sort((left, right) =>
    left.key < right.key ? -1 : left.key > right.key ? 1 : 0,
  );
  const header = [
    DELTA_HEADER,
    String(sortedEdits.length),
    String(mass.length),
    String(pluralOnly.length),
    String(invariant.length),
  ].join('\t');
  const body = [
    ...sortedEdits.map((entry) => packEntry(entry)),
    ...sortKeys(mass),
    ...sortKeys(pluralOnly),
    ...sortKeys(invariant),
  ];
  return {
    packed: `${header}\n${body.join('\n')}\n`,
    editCount: sortedEdits.length,
    massCount: mass.length,
    pluralOnlyCount: pluralOnly.length,
    invariantCount: invariant.length,
  };
}

/** Reads the Java membership artifact and keeps the low 32 bits of each 64-bit hash. */
function readMembershipHashes(file: string): Uint32Array {
  const data = readFileSync(file);
  const magic = data.subarray(0, 8).toString('ascii');
  if (magic !== 'MORPHEX1') {
    throw new Error(`membership artifact has an unknown magic header: ${magic}`);
  }
  const count = data.readInt32BE(8);
  if (count < 0 || count > 4_000_000) {
    throw new Error(`membership artifact has an implausible count ${count}`);
  }
  if (data.length !== 12 + count * 8) {
    throw new Error('membership artifact length does not match its count');
  }
  const truncated = new Uint32Array(count);
  let previous = -1n;
  for (let index = 0; index < count; index += 1) {
    const value = data.readBigInt64BE(12 + index * 8);
    if (index > 0 && value <= previous) {
      throw new Error(`membership hashes are not strictly ascending at ${index}`);
    }
    previous = value;
    truncated[index] = Number(value & 0xffffffffn);
  }
  // The Java artifact is sorted by the full 64-bit hash. Truncating the low 32 bits destroys
  // that order, so the loader's binary search needs a re-sorted, de-duplicated table.
  truncated.sort();
  let unique = 0;
  for (let index = 0; index < truncated.length; index += 1) {
    const value = truncated[index];
    if (value === undefined) {
      continue;
    }
    if (unique === 0 || truncated[unique - 1] !== value) {
      truncated[unique] = value;
      unique += 1;
    }
  }
  return truncated.slice(0, unique);
}

function toBase64Uint32(values: Uint32Array): string {
  const bytes = new Uint8Array(values.length * 4);
  const view = new DataView(bytes.buffer);
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index];
    if (value === undefined) {
      throw new Error('membership hash index out of range');
    }
    view.setUint32(index * 4, value, true);
  }
  return Buffer.from(bytes).toString('base64');
}

function dataModule(exportName: string, value: string): string {
  return [
    '/* eslint-disable */',
    '// Generated by morphex-ts/scripts/build-lexicon.ts. Do not edit by hand.',
    '// Lexicon data is CC BY-SA 3.0 (Wiktionary contributors via Kaikki.org/Wiktextract). See NOTICE.',
    `export const ${exportName} = ${JSON.stringify(value)};`,
    '',
  ].join('\n');
}

function sizeOf(packed: string): { bytes: number; gzipBytes: number } {
  return {
    bytes: Buffer.byteLength(packed, 'utf8'),
    gzipBytes: gzipSync(Buffer.from(packed, 'utf8')).length,
  };
}

function main(): void {
  validateCompoundTable();

  const artifactText = readFileSync(lexiconPath, 'utf8');
  const entries = parseArtifact(artifactText);

  const fullPacked = packFull(entries);
  const delta = packDelta(entries);
  const deltaPacked = delta.packed;
  const membership = readMembershipHashes(membershipPath);
  const membershipBase64 = toBase64Uint32(membership);

  mkdirSync(generatedDir, { recursive: true });
  mkdirSync(distDir, { recursive: true });

  writeFileSync(
    path.join(generatedDir, 'delta-data.ts'),
    dataModule('BUNDLED_DELTA_LEXICON', deltaPacked),
  );
  writeFileSync(
    path.join(generatedDir, 'full-data.ts'),
    dataModule('BUNDLED_FULL_LEXICON', fullPacked),
  );
  writeFileSync(
    path.join(generatedDir, 'membership-data.ts'),
    dataModule('BUNDLED_MEMBERSHIP', membershipBase64),
  );
  writeFileSync(path.join(distDir, 'lexicon-delta.txt'), deltaPacked);
  writeFileSync(path.join(distDir, 'lexicon-full.txt'), fullPacked);

  const sizes = {
    source: {
      path: SOURCE,
      entries: entries.length,
      bytes: Buffer.byteLength(artifactText, 'utf8'),
    },
    delta: {
      entries: delta.editCount + delta.massCount + delta.pluralOnlyCount + delta.invariantCount,
      editEntries: delta.editCount,
      massKeys: delta.massCount,
      pluralOnlyKeys: delta.pluralOnlyCount,
      invariantKeys: delta.invariantCount,
      ...sizeOf(deltaPacked),
    },
    full: {
      entries: entries.length,
      ...sizeOf(fullPacked),
    },
    membership: {
      lemmas: membership.length,
      bytes: Buffer.byteLength(membershipBase64, 'utf8'),
      gzipBytes: gzipSync(Buffer.from(membershipBase64, 'utf8')).length,
    },
  };
  writeFileSync(path.join(distDir, 'sizes.json'), `${JSON.stringify(sizes, null, 2)}\n`);

  if (!quiet) {
    const deltaEntries = sizes.delta.entries;
    const deltaShare = ((deltaEntries / entries.length) * 100).toFixed(1);
    console.log(`source      ${entries.length.toLocaleString('en-US')} entries (${SOURCE})`);
    console.log(
      `delta       ${deltaEntries.toLocaleString('en-US')} entries (${deltaShare}% of artifact): ` +
        `${delta.editCount.toLocaleString('en-US')} edits, ${delta.massCount.toLocaleString('en-US')} mass keys, ` +
        `${delta.pluralOnlyCount.toLocaleString('en-US')} plural-only keys, ` +
        `${delta.invariantCount.toLocaleString('en-US')} invariant keys; ` +
        `${sizes.delta.bytes.toLocaleString('en-US')} B raw, ${sizes.delta.gzipBytes.toLocaleString('en-US')} B gzip`,
    );
    console.log(
      `full        ${sizes.full.bytes.toLocaleString('en-US')} B raw, ${sizes.full.gzipBytes.toLocaleString('en-US')} B gzip`,
    );
    console.log(
      `membership  ${membership.length.toLocaleString('en-US')} lemmas, ` +
        `${sizes.membership.bytes.toLocaleString('en-US')} B base64, ${sizes.membership.gzipBytes.toLocaleString('en-US')} B gzip`,
    );
  }
}

main();
