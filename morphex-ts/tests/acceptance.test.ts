import { describe, expect, test } from 'vitest';

import { EnglishInflector } from '../src/english-inflector.js';
import { deltaEngine, fullBuilder, fullEngine } from './fixtures.js';

const rulesOnly = EnglishInflector.builder().withoutBundledLexicon().build();

describe('required acceptance cases', () => {
  test.each([
    ['German', 'Germans'],
    ['blouse', 'blouses'],
    ['scarp', 'scarps'],
    ['UFO', 'UFOs'],
    ['mother-in-law', 'mothers-in-law'],
    ['CHILD', 'CHILDREN'],
    ['die', 'dice'],
    ['cat', 'cats'],
  ])('pluralize(%s) === %s', (input, expected) => {
    expect(fullEngine.pluralize(input)).toBe(expected);
    expect(deltaEngine.pluralize(input)).toBe(expected);
  });

  test('count 1 preserves the source and any other count pluralizes', () => {
    expect(fullEngine.pluralize('cat', 1)).toBe('cat');
    expect(fullEngine.pluralize('cat', 3)).toBe('cats');
    expect(fullEngine.pluralize('child', 1)).toBe('child');
    expect(fullEngine.pluralize('child', 0)).toBe('children');
    expect(fullEngine.pluralize('child', -3)).toBe('children');
  });

  test('die is ambiguous with dies as an accepted alternative', () => {
    const result = fullEngine.analyze('die');
    expect(result.plural).toBe('dice');
    expect(result.stage).toBe('LEXICON');
    expect(result.source).toBe('lex:die');
    expect(result.alternatives).toContain('dies');
    expect(result.ambiguous).toBe(true);
    expect(result.isAccepted('dies')).toBe(true);
  });

  test('a padded input is returned unchanged', () => {
    const padded = ' cat';
    expect(fullEngine.pluralize(padded)).toBe(padded);
    expect(fullEngine.pluralize('')).toBe('');
  });
});

describe('casing policy', () => {
  test.each([
    ['CHILD', 'CHILDREN'],
    ['Child', 'Children'],
    ['child', 'children'],
    ['CITY', 'CITIES'],
    ['MOUSE', 'MICE'],
    ['iPhone', 'iPhones'],
    ['UFO', 'UFOs'],
    ['SMS', 'SMSes'],
  ])('preserves %s -> %s', (input, expected) => {
    expect(fullEngine.pluralize(input)).toBe(expected);
  });

  test('LOWERCASE and UPPERCASE are explicit', () => {
    const lower = fullBuilder().withCasePolicy('LOWERCASE').build();
    expect(lower.pluralize('CAT')).toBe('cats');
    expect(lower.pluralize('UFO')).toBe('ufos');
    const upper = EnglishInflector.builder().withCasePolicy('UPPERCASE').build();
    expect(upper.pluralize('cat')).toBe('CATS');
    expect(upper.pluralize('Child')).toBe('CHILDREN');
  });

  test('overrides are literal and are not re-cased', () => {
    const custom = EnglishInflector.builder()
      .withOverride('blue-card', 'blau-karte')
      .withCasePolicy('UPPERCASE')
      .build();
    expect(custom.pluralize('blue-card')).toBe('blau-karte');
  });
});

describe('compounds', () => {
  test.each([
    ['mother-in-law', 'mothers-in-law'],
    ['child-in-law', 'children-in-law'],
    ['great-grandfather-in-law', 'great-grandfathers-in-law'],
    ['passer-by', 'passers-by'],
    ['stand-in', 'stand-ins'],
    ['drive-by', 'drive-bys'],
    ['attorney general', 'attorneys general'],
    ['court-martial', 'courts-martial'],
    ['heir apparent', 'heirs apparent'],
    ['governor-elect', 'governors-elect'],
    ['secretary-general', 'secretaries-general'],
    ['credit-card', 'credit-cards'],
    ['CREDIT-CARD', 'CREDIT-CARDS'],
    ['ice-shelf', 'ice-shelves'],
    ['bake-off', 'bake-offs'],
    ['on-ramp', 'on-ramps'],
    ['G-PON', 'G-PONs'],
    ['great-great-grandfather', 'great-great-grandfathers'],
    ['ice cream', 'ice creams'],
    ['major general', 'major generals'],
  ])('%s -> %s', (input, expected) => {
    expect(fullEngine.pluralize(input)).toBe(expected);
    expect(deltaEngine.pluralize(input)).toBe(expected);
  });

  test('unsupported compound shapes are left alone', () => {
    expect(fullEngine.pluralize('well-known')).toBe('well-known');
    expect(fullEngine.pluralize('jack in the box')).toBe('jack in the box');
    expect(fullEngine.analyze('well-known').stage).toBe('FALLBACK');
  });
});

describe('overrides and fallback', () => {
  test('caller overrides win and are exact', () => {
    const custom = EnglishInflector.builder()
      .withOverride('German', 'Germanics')
      .withOverride('Goose', 'Goosen')
      .withOverrides({ die: 'dice-plural' })
      .build();
    expect(custom.pluralize('German')).toBe('Germanics');
    expect(custom.pluralize('die')).toBe('dice-plural');
    expect(custom.pluralize('Goose')).toBe('Goosen');
    expect(custom.pluralize('goose')).toBe('geese');
    expect(custom.analyze('German').stage).toBe('OVERRIDE');
    expect(custom.analyze('German').source).toBe('override');
    expect(() => EnglishInflector.builder().withOverride('', 'x')).toThrow();
  });

  test.each(['user_id', '3.14', "mother's", 'red  cat', 'red cat ', '日本'])(
    'unsupported %s is returned unchanged and flagged',
    (input) => {
      expect(fullEngine.pluralize(input)).toBe(input);
      expect(fullEngine.analyze(input).stage).toBe('FALLBACK');
    },
  );
});

describe('rules-only build', () => {
  test('has no lexicon and still applies the productive rules', () => {
    expect(rulesOnly.lexiconSize).toBe(0);
    expect(rulesOnly.pluralize('blouse')).toBe('blouses');
    expect(rulesOnly.pluralize('scarp')).toBe('scarps');
    expect(rulesOnly.pluralize('city')).toBe('cities');
    expect(rulesOnly.pluralize('analysis')).toBe('analyses');
    expect(rulesOnly.pluralize('woman')).toBe('women');
    expect(rulesOnly.pluralize('quiz')).toBe('quizes');
  });

  test('loses irregulars and uppercase inference by design', () => {
    expect(rulesOnly.pluralize('child')).toBe('childs');
    expect(rulesOnly.pluralize('UFO')).toBe('UFOS');
    expect(rulesOnly.pluralize('CAT')).toBe('CATS');
    expect(rulesOnly.pluralize('API')).toBe('APIS');
    expect(rulesOnly.analyze('child').stage).toBe('RULE');
    expect(rulesOnly.analyze('child').diagnostics).toBe(false);
  });

  test('count and fallback still work without data', () => {
    expect(rulesOnly.pluralize('cat', 1)).toBe('cat');
    expect(rulesOnly.pluralize('user_id')).toBe('user_id');
    expect(rulesOnly.pluralize('')).toBe('');
  });
});

describe('diagnostics', () => {
  test('exposes stable stages and identifiers', () => {
    expect(fullEngine.analyze('child').stage).toBe('LEXICON');
    expect(fullEngine.analyze('child').source).toBe('lex:child');
    expect(fullEngine.analyze('zorb').stage).toBe('RULE');
    expect(fullEngine.analyze('zorb').source).toBe('rule:default-s');
    expect(fullEngine.analyze('zork-in-law').stage).toBe('COMPOUND');
    expect(fullEngine.analyze('ice cream').stage).toBe('COMPOUND');
    expect(fullEngine.analyze('').source).toBe('fallback:empty');
  });

  test('reports countability and invariants', () => {
    const information = fullEngine.analyze('information');
    expect(information.plural).toBe('information');
    expect(information.countability).toBe('MASS');
    const sheep = fullEngine.analyze('sheep');
    expect(sheep.plural).toBe('sheep');
    expect(sheep.invariant).toBe(true);
    expect(sheep.countability).toBe('COUNTABLE');
    const water = fullEngine.analyze('water');
    expect(water.plural).toBe('waters');
    expect(water.countability).toBe('MIXED');
  });

  test('classical mode prefers attested classical forms without inventing them', () => {
    const classical = fullBuilder().withMode('CLASSICAL').build();
    expect(classical.pluralize('cactus')).toBe('cacti');
    expect(classical.pluralize('formula')).toBe('formulae');
    expect(classical.pluralize('index')).toBe('indices');
    expect(classical.pluralize('CACTUS')).toBe('CACTI');
    expect(classical.pluralize('blouse')).toBe('blouses');
    expect(classical.analyze('cactus').isAccepted('cactuses')).toBe(true);
  });

  test('configured initialisms use the documented suffix', () => {
    const custom = EnglishInflector.builder().withInitialism('ABC').build();
    expect(custom.pluralize('ABC')).toBe('ABCs');
    expect(fullEngine.pluralize('SMS')).toBe('SMSes');
    expect(fullEngine.pluralize('UFO')).toBe('UFOs');
    expect(fullEngine.pluralize('CD')).toBe('CDs');
  });

  test('unknown uppercase tokens infer an initialism only with membership evidence', () => {
    expect(fullEngine.pluralize('CAT')).toBe('CATS');
    expect(fullEngine.pluralize('ZQX')).toBe('ZQXs');
    expect(fullEngine.analyze('ZQX').stage).toBe('INFERRED');
    expect(fullEngine.pluralize('API')).toBe('APIs');
  });
});
