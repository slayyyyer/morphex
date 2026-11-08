import { describe, expect, test } from 'vitest';

import {
  CATALOG,
  P1_FIXTURE,
  PROBE_FIXTURE,
  deltaEngine,
  fullBuilder,
  fullEngine,
} from './fixtures.js';

function summarize(failures: readonly string[]): string {
  return failures.length === 0 ? 'none' : failures.slice(0, 20).join('\n');
}

describe('P1 frozen-lexicon conformance', () => {
  test('fixture is the committed 50,978-row Java export', () => {
    expect(P1_FIXTURE.count).toBe(50_978);
    expect(P1_FIXTURE.cases.length).toBe(50_978);
    expect(P1_FIXTURE.engine).toContain('EnglishInflector');
  });

  test('delta-backed pluralize matches Java on every P1 row', () => {
    const failures: string[] = [];
    let mismatches = 0;
    for (const [id, lemma, plural] of P1_FIXTURE.cases) {
      const actual = deltaEngine.pluralize(lemma);
      if (actual !== plural) {
        mismatches += 1;
        if (failures.length < 20) {
          failures.push(`${id} ${lemma}: expected ${plural}, got ${actual}`);
        }
      }
    }
    expect(mismatches, summarize(failures)).toBe(0);
  });

  test('full-backed analyze matches every Java diagnostic field', () => {
    const failures: string[] = [];
    let mismatches = 0;
    for (const [
      id,
      lemma,
      plural,
      ,
      ,
      countability,
      invariant,
      stage,
      sourceId,
      ambiguous,
    ] of P1_FIXTURE.cases) {
      const result = fullEngine.analyze(lemma);
      const actual = [
        result.plural,
        result.countability,
        result.invariant ? 1 : 0,
        result.stage,
        result.source,
        result.ambiguous ? 1 : 0,
      ] as const;
      const expected = [plural, countability, invariant, stage, sourceId, ambiguous] as const;
      const different = actual.some((value, index) => value !== expected[index]);
      if (different) {
        mismatches += 1;
        if (failures.length < 20) {
          failures.push(
            `${id} ${lemma}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`,
          );
        }
      }
    }
    expect(mismatches, summarize(failures)).toBe(0);
  });

  test('delta and full engines agree on every P1 plural', () => {
    const failures: string[] = [];
    for (const [id, lemma] of P1_FIXTURE.cases) {
      const delta = deltaEngine.pluralize(lemma);
      const full = fullEngine.pluralize(lemma);
      if (delta !== full && failures.length < 20) {
        failures.push(`${id} ${lemma}: delta ${delta}, full ${full}`);
      }
    }
    expect(failures, summarize(failures)).toEqual([]);
  });
});

describe('upstream probe table', () => {
  test('matches the Java engine for plural, count-one and stage', () => {
    const failures: string[] = [];
    for (const [input, plural, pluralCountOne, stage, sourceId] of PROBE_FIXTURE.cases) {
      const result = fullEngine.analyze(input);
      if (
        result.plural !== plural ||
        fullEngine.pluralize(input, 1) !== pluralCountOne ||
        result.stage !== stage ||
        result.source !== sourceId
      ) {
        failures.push(
          `${input}: ${result.plural}/${result.stage}/${result.source}, expected ${plural}/${stage}/${sourceId}`,
        );
      }
    }
    expect(failures, summarize(failures)).toEqual([]);
    expect(PROBE_FIXTURE.cases.find(([input]) => input === 'German')?.[1]).toBe('Germans');
    expect(PROBE_FIXTURE.cases.find(([input]) => input === 'blouse')?.[1]).toBe('blouses');
  });
});

describe('site catalog diagnostics', () => {
  test('replays every Java catalog case through the full engine', () => {
    expect(CATALOG.length).toBe(54);
    const failures: string[] = [];
    for (const entry of CATALOG) {
      const builder = fullBuilder()
        .withCasePolicy(entry.casePolicy)
        .withMode(entry.mode)
        .withUppercasePolicy(entry.uppercasePolicy)
        .withOverrides(entry.overrides);
      if (entry.initialisms.length > 0) {
        builder.withInitialisms(entry.initialisms);
      }
      const result = builder.build().analyze(entry.input);
      const alternativesMatch =
        result.alternatives.length === entry.alternatives.length &&
        result.alternatives.every((form, index) => form === entry.alternatives[index]);
      const different =
        result.plural !== entry.plural ||
        result.stage !== entry.source ||
        result.source !== entry.sourceId ||
        result.countability !== entry.countability ||
        result.invariant !== entry.invariant ||
        result.ambiguous !== entry.ambiguous ||
        result.note !== entry.note ||
        !alternativesMatch;
      if (different) {
        failures.push(
          `${entry.id} ${JSON.stringify(entry.input)}: got ${JSON.stringify({
            plural: result.plural,
            stage: result.stage,
            source: result.source,
            countability: result.countability,
            invariant: result.invariant,
            ambiguous: result.ambiguous,
            alternatives: result.alternatives,
            note: result.note,
          })}`,
        );
      }
    }
    expect(failures, summarize(failures)).toEqual([]);
  });
});
