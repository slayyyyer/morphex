import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  parseP1Fixture,
  parseProbeFixture,
  type P1Fixture,
  type ProbeFixture,
} from '../../test-fixtures/schema.js';
import { Builder, EnglishInflector } from '../src/english-inflector.js';
import { createBundledFullLexicon } from '../src/lexicon-full.js';
import type { LexiconProvider } from '../src/types.js';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function readJson(relativePath: string): unknown {
  return JSON.parse(readFileSync(path.join(repositoryRoot, relativePath), 'utf8')) as unknown;
}

export const P1_FIXTURE: P1Fixture = parseP1Fixture(readJson('test-fixtures/p1.json'));
export const PROBE_FIXTURE: ProbeFixture = parseProbeFixture(
  readJson('test-fixtures/upstream-probe.json'),
);

/** The full artifact plus membership table: Java-identical diagnostics. */
export const fullLexicon: LexiconProvider = createBundledFullLexicon();

/** The default package: bundled delta lexicon. */
export const deltaEngine: EnglishInflector = EnglishInflector.standard();

/** A builder preconfigured with the full lexicon. */
export function fullBuilder(): Builder {
  return EnglishInflector.builder().withLexicon(fullLexicon);
}

/** The full-artifact engine used by conformance tests. */
export const fullEngine: EnglishInflector = fullBuilder().build();

export interface CatalogCase {
  readonly id: string;
  readonly group: string;
  readonly input: string;
  readonly casePolicy: 'PRESERVE' | 'LOWERCASE' | 'UPPERCASE';
  readonly mode: 'ANGLICIZED' | 'CLASSICAL';
  readonly uppercasePolicy: 'INFER_INITIALISM' | 'LITERAL';
  readonly initialisms: readonly string[];
  readonly overrides: Readonly<Record<string, string>>;
  readonly plural: string;
  readonly source: string;
  readonly sourceId: string;
  readonly countability: string;
  readonly invariant: boolean;
  readonly ambiguous: boolean;
  readonly alternatives: readonly string[];
  readonly note: string;
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function expectString(value: unknown, where: string): string {
  if (typeof value !== 'string') {
    throw new Error(`${where}: expected a string`);
  }
  return value;
}

function expectBoolean(value: unknown, where: string): boolean {
  if (typeof value !== 'boolean') {
    throw new Error(`${where}: expected a boolean`);
  }
  return value;
}

function expectStringArray(value: unknown, where: string): readonly string[] {
  if (!Array.isArray(value)) {
    throw new Error(`${where}: expected an array`);
  }
  return value.map((item, index) => expectString(item, `${where}[${index}]`));
}

/** Validates and types the Java-generated site catalog, which doubles as the analyze fixture. */
export function parseCatalog(value: unknown): readonly CatalogCase[] {
  if (!isRecord(value) || !Array.isArray(value['cases'])) {
    throw new Error('catalog: expected an object with cases');
  }
  return value['cases'].map((item, index): CatalogCase => {
    const where = `catalog case ${index}`;
    if (!isRecord(item)) {
      throw new Error(`${where}: expected an object`);
    }
    const overrides: Record<string, string> = {};
    const rawOverrides = item['overrides'];
    if (!isRecord(rawOverrides)) {
      throw new Error(`${where}.overrides: expected an object`);
    }
    for (const [key, entry] of Object.entries(rawOverrides)) {
      overrides[key] = expectString(entry, `${where}.overrides.${key}`);
    }
    return {
      id: expectString(item['id'], `${where}.id`),
      group: expectString(item['group'], `${where}.group`),
      input: expectString(item['input'], `${where}.input`),
      casePolicy: expectString(
        item['casePolicy'],
        `${where}.casePolicy`,
      ) as CatalogCase['casePolicy'],
      mode: expectString(item['mode'], `${where}.mode`) as CatalogCase['mode'],
      uppercasePolicy: expectString(
        item['uppercasePolicy'],
        `${where}.uppercasePolicy`,
      ) as CatalogCase['uppercasePolicy'],
      initialisms: expectStringArray(item['initialisms'], `${where}.initialisms`),
      overrides,
      plural: expectString(item['plural'], `${where}.plural`),
      source: expectString(item['source'], `${where}.source`),
      sourceId: expectString(item['sourceId'], `${where}.sourceId`),
      countability: expectString(item['countability'], `${where}.countability`),
      invariant: expectBoolean(item['invariant'], `${where}.invariant`),
      ambiguous: expectBoolean(item['ambiguous'], `${where}.ambiguous`),
      alternatives: expectStringArray(item['alternatives'], `${where}.alternatives`),
      note: expectString(item['note'], `${where}.note`),
    };
  });
}

export const CATALOG: readonly CatalogCase[] = parseCatalog(readJson('site/catalog.json'));
