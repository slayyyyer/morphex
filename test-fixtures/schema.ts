/**
 * Shared fixture schema for the Java/TypeScript conformance tests.
 *
 * The JSON files in this directory are produced from the Java engine by
 * `morphex-ts/scripts/export-fixtures/ExportFixtures.java` and committed. CI regenerates them and
 * fails on a diff, so the two ports cannot drift silently.
 *
 * Cases are tuples rather than objects: the P1 fixture has 50,978 rows, and a tuple keeps the
 * committed file reviewable while still being strictly typed here.
 */

import type { Countability, InflectionSource } from '../morphex-ts/src/types.js';

export const FIXTURE_SCHEMA = 'morphex-fixtures-v1' as const;

export type PrimaryFlag = 0 | 1;

/** [id, lemma, plural, accepted, primary, countability, invariant, stage, sourceId, ambiguous] */
export type P1Case = readonly [
  id: string,
  lemma: string,
  plural: string,
  accepted: readonly string[],
  primary: PrimaryFlag,
  countability: Countability,
  invariant: PrimaryFlag,
  stage: InflectionSource,
  sourceId: string,
  ambiguous: PrimaryFlag,
];

export interface P1Fixture {
  readonly schema: typeof FIXTURE_SCHEMA;
  readonly kind: 'p1';
  readonly source: string;
  readonly engine: string;
  readonly count: number;
  readonly cases: readonly P1Case[];
}

/** [input, plural, pluralWithCountOne, stage, sourceId] */
export type ProbeCase = readonly [
  input: string,
  plural: string,
  pluralCountOne: string,
  stage: InflectionSource,
  sourceId: string,
];

export interface ProbeFixture {
  readonly schema: typeof FIXTURE_SCHEMA;
  readonly kind: 'upstream-probe';
  readonly source: string;
  readonly engine: string;
  readonly count: number;
  readonly cases: readonly ProbeCase[];
}

const COUNTABILITY_VALUES: readonly string[] = [
  'COUNTABLE',
  'MASS',
  'MIXED',
  'PLURAL_ONLY',
  'UNKNOWN',
];
const STAGE_VALUES: readonly string[] = [
  'OVERRIDE',
  'LEXICON',
  'INFERRED',
  'COMPOUND',
  'RULE',
  'FALLBACK',
];

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function expectString(value: unknown, where: string): string {
  if (typeof value !== 'string') {
    throw new Error(`${where}: expected a string`);
  }
  return value;
}

function expectCount(value: unknown, where: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${where}: expected a non-negative integer`);
  }
  return value;
}

function expectFlag(value: unknown, where: string): PrimaryFlag {
  if (value !== 0 && value !== 1) {
    throw new Error(`${where}: expected 0 or 1`);
  }
  return value;
}

function expectStringArray(value: unknown, where: string): readonly string[] {
  if (!Array.isArray(value)) {
    throw new Error(`${where}: expected an array`);
  }
  return value.map((item, index) => expectString(item, `${where}[${index}]`));
}

function expectCountability(value: unknown, where: string): Countability {
  const text = expectString(value, where);
  if (!COUNTABILITY_VALUES.includes(text)) {
    throw new Error(`${where}: unknown countability '${text}'`);
  }
  return text as Countability;
}

function expectStage(value: unknown, where: string): InflectionSource {
  const text = expectString(value, where);
  if (!STAGE_VALUES.includes(text)) {
    throw new Error(`${where}: unknown stage '${text}'`);
  }
  return text as InflectionSource;
}

function expectSchema(
  value: unknown,
  kind: 'p1' | 'upstream-probe',
): Readonly<Record<string, unknown>> {
  if (!isRecord(value)) {
    throw new Error('fixture: expected an object');
  }
  if (value['schema'] !== FIXTURE_SCHEMA) {
    throw new Error(`fixture: unexpected schema '${String(value['schema'])}'`);
  }
  if (value['kind'] !== kind) {
    throw new Error(`fixture: expected kind '${kind}'`);
  }
  return value;
}

export function parseP1Fixture(value: unknown): P1Fixture {
  const root = expectSchema(value, 'p1');
  const casesValue = root['cases'];
  if (!Array.isArray(casesValue)) {
    throw new Error('fixture: cases must be an array');
  }
  const cases = casesValue.map((item, index): P1Case => {
    const where = `p1 case ${index}`;
    if (!Array.isArray(item) || item.length !== 10) {
      throw new Error(`${where}: expected a 10-tuple`);
    }
    return [
      expectString(item[0], `${where}.id`),
      expectString(item[1], `${where}.lemma`),
      expectString(item[2], `${where}.plural`),
      expectStringArray(item[3], `${where}.accepted`),
      expectFlag(item[4], `${where}.primary`),
      expectCountability(item[5], `${where}.countability`),
      expectFlag(item[6], `${where}.invariant`),
      expectStage(item[7], `${where}.stage`),
      expectString(item[8], `${where}.sourceId`),
      expectFlag(item[9], `${where}.ambiguous`),
    ];
  });
  const count = expectCount(root['count'], 'fixture.count');
  if (count !== cases.length) {
    throw new Error(`fixture: count ${count} does not match ${cases.length} cases`);
  }
  return {
    schema: FIXTURE_SCHEMA,
    kind: 'p1',
    source: expectString(root['source'], 'fixture.source'),
    engine: expectString(root['engine'], 'fixture.engine'),
    count,
    cases,
  };
}

export function parseProbeFixture(value: unknown): ProbeFixture {
  const root = expectSchema(value, 'upstream-probe');
  const casesValue = root['cases'];
  if (!Array.isArray(casesValue)) {
    throw new Error('fixture: cases must be an array');
  }
  const cases = casesValue.map((item, index): ProbeCase => {
    const where = `probe case ${index}`;
    if (!Array.isArray(item) || item.length !== 5) {
      throw new Error(`${where}: expected a 5-tuple`);
    }
    return [
      expectString(item[0], `${where}.input`),
      expectString(item[1], `${where}.plural`),
      expectString(item[2], `${where}.pluralCountOne`),
      expectStage(item[3], `${where}.stage`),
      expectString(item[4], `${where}.sourceId`),
    ];
  });
  const count = expectCount(root['count'], 'fixture.count');
  if (count !== cases.length) {
    throw new Error(`fixture: count ${count} does not match ${cases.length} cases`);
  }
  return {
    schema: FIXTURE_SCHEMA,
    kind: 'upstream-probe',
    source: expectString(root['source'], 'fixture.source'),
    engine: expectString(root['engine'], 'fixture.engine'),
    count,
    cases,
  };
}
