/**
 * Lexicon providers: the packed delta artifact, the packed full artifact, rules-only mode and
 * caller-supplied data all implement the same `LexiconProvider` interface.
 *
 * Two packed formats are emitted, both line-oriented so they can be reviewed in a diff.
 *
 * Full artifact (`morphex-lexicon-v1`):
 *
 *     morphex-lexicon-v1<TAB>full<TAB><count>
 *     <lemma><TAB><cut><TAB><append><TAB><classicalCut|-><TAB><classicalAppend|-><TAB><alternatives|-><TAB><countability><TAB><flags>
 *
 * Delta artifact (`morphex-delta-v1`): a small table of entries whose edit differs from the
 * productive rules, followed by sorted key lists for the mass, plural-only and invariant nouns
 * whose pluralize() answer is "return the input unchanged". Those identity nouns are 90k of the
 * 147k artifact entries, and a key list stores them without a redundant record per noun.
 *
 *     morphex-delta-v1<TAB><edits><TAB><mass><TAB><pluralOnly><TAB><invariant>
 *     <edit record, full format minus the leading kind>
 *     <lower-case key>            (mass)
 *     <lower-case key>            (plural-only)
 *     <lower-case key>            (invariant)
 *
 * Both loaders reject a malformed artifact, an unsorted file, a duplicate key or a bad flag
 * instead of guessing.
 */

import type {
  Countability,
  LexiconEntry,
  LexiconEntryInput,
  LexiconProvider,
  MembershipTable,
} from './types.js';
import { COUNTABILITIES } from './types.js';
import { lower } from './word-shape.js';

const FULL_HEADER = 'morphex-lexicon-v1';
const DELTA_HEADER = 'morphex-delta-v1';
const RECORD_FIELDS = 8;

const COUNTABILITY_CODES = {
  COUNTABLE: 'C',
  MASS: 'M',
  MIXED: 'X',
  PLURAL_ONLY: 'P',
  UNKNOWN: 'U',
} as const satisfies Record<Countability, string>;

const COUNTABILITY_BY_CODE: ReadonlyMap<string, Countability> = new Map(
  COUNTABILITIES.map((countability) => [COUNTABILITY_CODES[countability], countability]),
);

const FLAG_INVARIANT = 1;
const FLAG_INITIALISM = 2;
const FLAG_CASE_SENSITIVE = 4;
const FLAG_AMBIGUOUS = 8;
const ALL_FLAGS = FLAG_INVARIANT | FLAG_INITIALISM | FLAG_CASE_SENSITIVE | FLAG_AMBIGUOUS;

/** Shared immutable empty alternatives list: most entries have none. */
const NO_ALTERNATIVES: readonly string[] = Object.freeze([]);

/** Computes the end-of-token edit that turns `lemma` into `plural` (longest common prefix). */
export function deriveEdit(lemma: string, plural: string): readonly [cut: number, append: string] {
  const max = Math.min(lemma.length, plural.length);
  let prefix = 0;
  while (prefix < max && lemma.charAt(prefix) === plural.charAt(prefix)) {
    prefix += 1;
  }
  return [lemma.length - prefix, plural.slice(prefix)];
}

function reconstructPlural(lemma: string, cut: number, append: string): string {
  const end = Math.max(0, lemma.length - cut);
  return lemma.slice(0, end) + append;
}

function binarySearch(keys: readonly string[], key: string): number {
  let low = 0;
  let high = keys.length - 1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    const candidate = keys[middle];
    if (candidate === undefined) {
      return -1;
    }
    if (candidate === key) {
      return middle;
    }
    if (candidate < key) {
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  return -1;
}

class TableLexicon implements LexiconProvider {
  readonly kind: 'full' | 'custom';
  readonly size: number;
  readonly diagnostics = true;
  readonly #keys: readonly string[];
  readonly #entries: readonly LexiconEntry[];
  readonly #membership: MembershipTable | undefined;

  constructor(
    kind: 'full' | 'custom',
    keys: readonly string[],
    entries: readonly LexiconEntry[],
    membership: MembershipTable | undefined,
  ) {
    this.kind = kind;
    this.size = entries.length;
    this.#keys = keys;
    this.#entries = entries;
    this.#membership = membership;
  }

  lookup(key: string): LexiconEntry | undefined {
    const index = binarySearch(this.#keys, key);
    return index < 0 ? undefined : this.#entries[index];
  }

  isOrdinaryWord(key: string): boolean | undefined {
    return this.#membership?.contains(key);
  }
}
const DELTA_KIND_MASS = -1;
const DELTA_KIND_PLURAL_ONLY = -2;
const DELTA_KIND_INVARIANT = -3;

/**
 * Fast internal string hash for the delta lookup table. This never leaves the engine: it only
 * needs to be deterministic and well distributed over the artifact's lower-case keys.
 */
function hashDeltaKey(key: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < key.length; index += 1) {
    hash = Math.imul(hash ^ key.charCodeAt(index), 0x01000193);
  }
  return hash >>> 0;
}

interface DeltaIndex {
  readonly keys: readonly string[];
  readonly kinds: Int32Array;
  /** Open-addressed slots holding `position + 1`; zero means empty. */
  readonly slots: Uint32Array;
  readonly mask: number;
}

/**
 * One key array over every delta section, with a parallel kind code and an open-addressed hash
 * index.
 *
 * Keys are stored in insertion order (edits, then mass, plural-only and invariant), not sorted:
 * the hash table is the only lookup path. A non-negative code is an index into the edit records;
 * negative codes select an identity section. Capacity is twice the key count, so the table is
 * about half full (1 MiB for the bundled artifact) and probes stay short. Duplicate keys across
 * sections are rejected here, during insertion.
 */
function buildDeltaIndex(
  edits: readonly LexiconEntry[],
  massKeys: readonly string[],
  pluralOnlyKeys: readonly string[],
  invariantKeys: readonly string[],
): DeltaIndex {
  const total = edits.length + massKeys.length + pluralOnlyKeys.length + invariantKeys.length;
  const keys = new Array<string>(total);
  const kinds = new Int32Array(total);
  let capacity = 1;
  while (capacity < total * 2) {
    capacity *= 2;
  }
  const slots = new Uint32Array(capacity);
  const mask = capacity - 1;
  let position = 0;

  const insert = (key: string, kind: number): void => {
    let slot = hashDeltaKey(key) & mask;
    for (;;) {
      const stored = slots[slot];
      if (stored === undefined || stored === 0) {
        break;
      }
      if (keys[stored - 1] === key) {
        throw new Error(`duplicate identity key '${key}'`);
      }
      slot = (slot + 1) & mask;
    }
    keys[position] = key;
    kinds[position] = kind;
    slots[slot] = position + 1;
    position += 1;
  };

  for (let index = 0; index < edits.length; index += 1) {
    const entry = edits[index];
    if (entry === undefined) {
      throw new Error('delta artifact is missing an edit record');
    }
    insert(entry.key, index);
  }
  for (const key of massKeys) {
    insert(key, DELTA_KIND_MASS);
  }
  for (const key of pluralOnlyKeys) {
    insert(key, DELTA_KIND_PLURAL_ONLY);
  }
  for (const key of invariantKeys) {
    insert(key, DELTA_KIND_INVARIANT);
  }
  if (position !== total) {
    throw new Error('delta artifact index is shorter than its sections');
  }
  return { keys, kinds, slots, mask };
}

class DeltaLexicon implements LexiconProvider {
  readonly kind = 'delta' as const;
  readonly size: number;
  readonly diagnostics = false;
  readonly #keys: readonly string[];
  readonly #kinds: Int32Array;
  readonly #slots: Uint32Array;
  readonly #mask: number;
  readonly #edits: readonly LexiconEntry[];
  readonly #membership: MembershipTable | undefined;

  constructor(
    edits: readonly LexiconEntry[],
    massKeys: readonly string[],
    pluralOnlyKeys: readonly string[],
    invariantKeys: readonly string[],
    membership: MembershipTable | undefined,
  ) {
    this.#edits = edits;
    const index = buildDeltaIndex(edits, massKeys, pluralOnlyKeys, invariantKeys);
    this.#keys = index.keys;
    this.#kinds = index.kinds;
    this.#slots = index.slots;
    this.#mask = index.mask;
    this.size = edits.length + massKeys.length + pluralOnlyKeys.length + invariantKeys.length;
    this.#membership = membership;
  }

  lookup(key: string): LexiconEntry | undefined {
    let slot = hashDeltaKey(key) & this.#mask;
    for (;;) {
      const stored = this.#slots[slot];
      if (stored === undefined || stored === 0) {
        return undefined;
      }
      const position = stored - 1;
      if (this.#keys[position] === key) {
        const kind = this.#kinds[position];
        if (kind === undefined) {
          return undefined;
        }
        if (kind >= 0) {
          return this.#edits[kind];
        }
        if (kind === DELTA_KIND_MASS) {
          return identityEntry(key, 'MASS', false);
        }
        if (kind === DELTA_KIND_PLURAL_ONLY) {
          return identityEntry(key, 'PLURAL_ONLY', false);
        }
        return identityEntry(key, 'UNKNOWN', true);
      }
      slot = (slot + 1) & this.#mask;
    }
  }

  isOrdinaryWord(key: string): boolean | undefined {
    return this.#membership?.contains(key);
  }
}

function identityEntry(key: string, countability: Countability, invariant: boolean): LexiconEntry {
  return Object.freeze({
    lemma: key,
    key,
    plural: key,
    classical: null,
    alternatives: NO_ALTERNATIVES,
    countability,
    invariant,
    initialism: false,
    caseSensitive: false,
    ambiguous: false,
  });
}

/** Rules-only provider: no entries, no membership evidence, no data. */
export const RULES_ONLY_LEXICON: LexiconProvider = Object.freeze({
  kind: 'rules-only' as const,
  size: 0,
  diagnostics: false,
  lookup: () => undefined,
  isOrdinaryWord: () => undefined,
});

function parseRecord(record: string, lineNumber: number): LexiconEntry {
  const fields = record.split('\t');
  if (fields.length !== RECORD_FIELDS) {
    throw new Error(
      `lexicon line ${lineNumber}: expected ${RECORD_FIELDS} fields, found ${fields.length}`,
    );
  }
  const [lemmaField, cutField, appendField, classicalCutField, classicalAppendField] = fields;
  const alternativesField = fields[5];
  const countabilityField = fields[6];
  const flagsField = fields[7];
  if (
    lemmaField === undefined ||
    cutField === undefined ||
    appendField === undefined ||
    classicalCutField === undefined ||
    classicalAppendField === undefined ||
    alternativesField === undefined ||
    countabilityField === undefined ||
    flagsField === undefined
  ) {
    throw new Error(`lexicon line ${lineNumber}: malformed record`);
  }

  if (lemmaField.length === 0) {
    throw new Error(`lexicon line ${lineNumber}: empty lemma`);
  }
  if (!/^\d+$/.test(cutField)) {
    throw new Error(`lexicon line ${lineNumber}: bad cut '${cutField}'`);
  }
  const cut = Number.parseInt(cutField, 10);
  if (!Number.isSafeInteger(cut) || cut > lemmaField.length) {
    throw new Error(`lexicon line ${lineNumber}: cut ${cut} exceeds lemma length`);
  }
  if (!/^\d+$/.test(flagsField)) {
    throw new Error(`lexicon line ${lineNumber}: bad flags '${flagsField}'`);
  }
  const flags = Number.parseInt(flagsField, 10);
  if (flags < 0 || (flags & ~ALL_FLAGS) !== 0) {
    throw new Error(`lexicon line ${lineNumber}: unknown flags ${flags}`);
  }
  const countability = COUNTABILITY_BY_CODE.get(countabilityField);
  if (countability === undefined) {
    throw new Error(`lexicon line ${lineNumber}: bad countability '${countabilityField}'`);
  }

  let classical: string | null = null;
  if (classicalCutField !== '-' || classicalAppendField !== '-') {
    if (!/^\d+$/.test(classicalCutField)) {
      throw new Error(`lexicon line ${lineNumber}: bad classical cut '${classicalCutField}'`);
    }
    const classicalCut = Number.parseInt(classicalCutField, 10);
    if (classicalCut > lemmaField.length) {
      throw new Error(`lexicon line ${lineNumber}: classical cut exceeds lemma length`);
    }
    classical = reconstructPlural(lemmaField, classicalCut, classicalAppendField);
    if (classical.length === 0) {
      throw new Error(`lexicon line ${lineNumber}: empty classical plural`);
    }
  }

  let alternatives: readonly string[] = NO_ALTERNATIVES;
  if (alternativesField !== '-') {
    const parsed = alternativesField.split(',');
    if (parsed.some((alternative) => alternative.length === 0)) {
      throw new Error(`lexicon line ${lineNumber}: empty alternative`);
    }
    alternatives = Object.freeze(parsed);
  }

  const plural = reconstructPlural(lemmaField, cut, appendField);
  if (plural.length === 0) {
    throw new Error(`lexicon line ${lineNumber}: empty plural`);
  }

  return Object.freeze({
    lemma: lemmaField,
    key: lower(lemmaField),
    plural,
    classical,
    alternatives,
    countability,
    invariant: (flags & FLAG_INVARIANT) !== 0,
    initialism: (flags & FLAG_INITIALISM) !== 0,
    caseSensitive: (flags & FLAG_CASE_SENSITIVE) !== 0,
    ambiguous: (flags & FLAG_AMBIGUOUS) !== 0,
  });
}

function parseEditRecords(
  lines: readonly string[],
  start: number,
  count: number,
  startLine: number,
): readonly LexiconEntry[] {
  const entries = new Array<LexiconEntry>(count);
  let previousKey: string | null = null;
  for (let offset = 0; offset < count; offset += 1) {
    const record = lines[start + offset];
    if (record === undefined || record.length === 0) {
      throw new Error(`lexicon line ${startLine + offset}: missing record`);
    }
    const entry = parseRecord(record, startLine + offset);
    if (previousKey !== null && entry.key <= previousKey) {
      throw new Error(
        `lexicon line ${startLine + offset}: keys are not strictly ascending ('${entry.key}')`,
      );
    }
    previousKey = entry.key;
    entries[offset] = entry;
  }
  return entries;
}

function parseKeyList(
  lines: readonly string[],
  start: number,
  count: number,
  startLine: number,
): readonly string[] {
  const keys = new Array<string>(count);
  let previousKey: string | null = null;
  for (let offset = 0; offset < count; offset += 1) {
    const key = lines[start + offset];
    if (key === undefined || key.length === 0) {
      throw new Error(`lexicon line ${startLine + offset}: empty identity key`);
    }
    if (key !== lower(key)) {
      throw new Error(`lexicon line ${startLine + offset}: identity key is not lower case`);
    }
    if (previousKey !== null && key <= previousKey) {
      throw new Error(`lexicon line ${startLine + offset}: identity keys are not ascending`);
    }
    previousKey = key;
    keys[offset] = key;
  }
  return keys;
}

/** Parses the full packed artifact. Throws on malformed, unsorted or duplicate data. */
export function parseFullLexicon(packed: string): readonly LexiconEntry[] {
  const lines = packed.split('\n');
  const headerLine = lines[0];
  if (headerLine === undefined) {
    throw new Error('lexicon artifact is empty');
  }
  const header = headerLine.split('\t');
  if (header.length !== 3 || header[0] !== FULL_HEADER || header[1] !== 'full') {
    throw new Error('lexicon artifact has an unknown header');
  }
  const reportedCount = Number.parseInt(header[2] ?? '', 10);
  if (!Number.isSafeInteger(reportedCount) || reportedCount < 0) {
    throw new Error('lexicon artifact has a bad entry count');
  }
  const records: string[] = [];
  for (let index = 1; index < lines.length; index += 1) {
    const record = lines[index];
    if (record === undefined || record.length === 0) {
      continue;
    }
    records.push(record);
  }
  const entries = parseEditRecords(records, 0, records.length, 2);
  if (entries.length !== reportedCount) {
    throw new Error(
      `lexicon artifact reports ${reportedCount} entries but contains ${entries.length}`,
    );
  }
  return entries;
}

/** Builds the opt-in full provider from a packed full artifact. */
export function createFullLexicon(packed: string, membership?: MembershipTable): LexiconProvider {
  const entries = parseFullLexicon(packed);
  return new TableLexicon(
    'full',
    entries.map((entry) => entry.key),
    entries,
    membership,
  );
}

/** The four sections of a parsed delta artifact. */
export interface DeltaSections {
  readonly edits: readonly LexiconEntry[];
  readonly massKeys: readonly string[];
  readonly pluralOnlyKeys: readonly string[];
  readonly invariantKeys: readonly string[];
}

/**
 * Parses the packed delta artifact into its sections.
 *
 * @internal Exported for the bundled-artifact index test; not part of the package's public
 * entry points.
 */
export function parseDeltaLexicon(packed: string): DeltaSections {
  const lines = packed.split('\n');
  const headerLine = lines[0];
  if (headerLine === undefined) {
    throw new Error('delta artifact is empty');
  }
  const header = headerLine.split('\t');
  if (header.length !== 5 || header[0] !== DELTA_HEADER) {
    throw new Error('delta artifact has an unknown header');
  }
  const counts = header.slice(1).map((field) => Number.parseInt(field, 10));
  if (counts.length !== 4 || counts.some((count) => !Number.isSafeInteger(count) || count < 0)) {
    throw new Error('delta artifact has a bad section count');
  }
  const [editCount = -1, massCount = -1, pluralOnlyCount = -1, invariantCount = -1] = counts;

  const expected = editCount + massCount + pluralOnlyCount + invariantCount;
  const records = lines.length - 1;
  const trailingEmptyLine = records > 0 && lines[lines.length - 1] === '';
  if (records - (trailingEmptyLine ? 1 : 0) !== expected) {
    throw new Error(
      `delta artifact reports ${expected} records but contains ${
        records - (trailingEmptyLine ? 1 : 0)
      }`,
    );
  }

  const edits = parseEditRecords(lines, 1, editCount, 2);
  let offset = 1 + editCount;
  const massKeys = parseKeyList(lines, offset, massCount, 1 + offset);
  offset += massCount;
  const pluralOnlyKeys = parseKeyList(lines, offset, pluralOnlyCount, 1 + offset);
  offset += pluralOnlyCount;
  const invariantKeys = parseKeyList(lines, offset, invariantCount, 1 + offset);

  return { edits, massKeys, pluralOnlyKeys, invariantKeys };
}

/** Builds the bundled delta provider. `pluralize()` needs nothing else. */
export function createDeltaLexicon(packed: string, membership?: MembershipTable): LexiconProvider {
  const { edits, massKeys, pluralOnlyKeys, invariantKeys } = parseDeltaLexicon(packed);
  return new DeltaLexicon(edits, massKeys, pluralOnlyKeys, invariantKeys, membership);
}

function normalizeInput(input: LexiconEntryInput, index: number): LexiconEntry {
  if (input.lemma.length === 0) {
    throw new Error(`custom lexicon entry ${index}: lemma must be a non-empty string`);
  }
  if (input.plural.length === 0) {
    throw new Error(`custom lexicon entry ${index}: plural must be a non-empty string`);
  }
  const alternatives = input.alternatives ?? [];
  if (alternatives.some((alternative) => alternative.length === 0)) {
    throw new Error(`custom lexicon entry ${index}: alternatives must be non-empty strings`);
  }
  return Object.freeze({
    lemma: input.lemma,
    key: lower(input.lemma),
    plural: input.plural,
    classical: input.classical ?? null,
    alternatives: Object.freeze([...alternatives]),
    countability: input.countability ?? 'COUNTABLE',
    invariant: input.invariant ?? false,
    initialism: input.initialism ?? false,
    caseSensitive: input.caseSensitive ?? false,
    ambiguous: input.ambiguous ?? false,
  });
}

/**
 * Builds a provider from caller-supplied entries. Duplicate lookup keys are rejected, matching
 * the bundled-artifact loader.
 */
export function createLexicon(
  entries: Iterable<LexiconEntryInput>,
  membership?: MembershipTable,
): LexiconProvider {
  const normalized: LexiconEntry[] = [];
  let index = 0;
  for (const input of entries) {
    normalized.push(normalizeInput(input, index));
    index += 1;
  }
  normalized.sort((left, right) => (left.key < right.key ? -1 : left.key > right.key ? 1 : 0));
  for (let position = 1; position < normalized.length; position += 1) {
    const previous = normalized[position - 1];
    const current = normalized[position];
    if (previous !== undefined && current !== undefined && previous.key === current.key) {
      throw new Error(`custom lexicon: duplicate lemma key '${current.key}'`);
    }
  }
  return new TableLexicon(
    'custom',
    normalized.map((entry) => entry.key),
    normalized,
    membership,
  );
}

/** FNV-1a 64-bit, low 32 bits only: identical to the Java membership hash truncated. */
export function fnv1aLow32(value: string): number {
  // Low 32 bits of the 64-bit FNV-1a offset basis (0xcbf29ce484222325) and prime
  // (0x100000001b3), so the truncated hash is identical to `Membership.hash` in Java.
  let hash = 0x84222325;
  const bytes = new TextEncoder().encode(value);
  for (const byte of bytes) {
    hash = Math.imul(hash ^ byte, 0x1b3) >>> 0;
  }
  return hash >>> 0;
}

/** Decodes standard base64 without depending on `atob` or Node-specific APIs. */
export function decodeBase64(value: string): Uint8Array {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const clean = value.endsWith('==')
    ? value.slice(0, -2)
    : value.endsWith('=')
      ? value.slice(0, -1)
      : value;
  const output = new Uint8Array(Math.floor((clean.length * 3) / 4));
  let bits = 0;
  let bitCount = 0;
  let written = 0;
  for (const character of clean) {
    const digit = alphabet.indexOf(character);
    if (digit < 0) {
      throw new Error('invalid base64 lexicon data');
    }
    bits = (bits << 6) | digit;
    bitCount += 6;
    if (bitCount >= 8) {
      bitCount -= 8;
      output[written] = (bits >> bitCount) & 0xff;
      written += 1;
      bits &= (1 << bitCount) - 1;
    }
  }
  if (written !== output.length) {
    throw new Error('truncated base64 lexicon data');
  }
  return output;
}

/** Wraps a base64-encoded, sorted, little-endian `uint32` hash table as a membership table. */
export function createMembershipTable(base64: string): MembershipTable {
  const bytes = decodeBase64(base64);
  if (bytes.length % 4 !== 0) {
    throw new Error('membership table length is not a multiple of four');
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const hashes = new Uint32Array(bytes.length / 4);
  for (let index = 0; index < hashes.length; index += 1) {
    hashes[index] = view.getUint32(index * 4, true);
  }
  return {
    size: hashes.length,
    contains(value: string): boolean {
      const target = fnv1aLow32(value);
      let low = 0;
      let high = hashes.length - 1;
      while (low <= high) {
        const middle = (low + high) >>> 1;
        const candidate = hashes[middle];
        if (candidate === undefined) {
          return false;
        }
        if (candidate === target) {
          return true;
        }
        if (candidate < target) {
          low = middle + 1;
        } else {
          high = middle - 1;
        }
      }
      return false;
    },
  };
}
