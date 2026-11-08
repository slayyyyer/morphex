import { describe, expectTypeOf, test } from 'vitest';

import { Builder, EnglishInflector } from '../src/english-inflector.js';
import type {
  CasePolicy,
  Countability,
  Inflection,
  InflectionSource,
  LexiconEntry,
  LexiconProvider,
  Mode,
  UppercasePolicy,
} from '../src/types.js';

describe('public type surface', () => {
  test('Inflection is readonly and fully typed', () => {
    expectTypeOf<Inflection['input']>().toEqualTypeOf<string>();
    expectTypeOf<Inflection['plural']>().toEqualTypeOf<string>();
    expectTypeOf<Inflection['alternatives']>().toEqualTypeOf<readonly string[]>();
    expectTypeOf<Inflection['ambiguous']>().toEqualTypeOf<boolean>();
    expectTypeOf<Inflection['stage']>().toEqualTypeOf<InflectionSource>();
    expectTypeOf<Inflection['source']>().toEqualTypeOf<string>();
    expectTypeOf<Inflection['countability']>().toEqualTypeOf<Countability>();
    expectTypeOf<Inflection['invariant']>().toEqualTypeOf<boolean>();
    expectTypeOf<Inflection['note']>().toEqualTypeOf<string>();
    expectTypeOf<Inflection['diagnostics']>().toEqualTypeOf<boolean>();
    expectTypeOf<Inflection['isAccepted']>().toEqualTypeOf<(form: string) => boolean>();
  });

  test('InflectionSource is the documented literal union', () => {
    expectTypeOf<InflectionSource>().toEqualTypeOf<
      'OVERRIDE' | 'LEXICON' | 'INFERRED' | 'COMPOUND' | 'RULE' | 'FALLBACK'
    >();
    expectTypeOf<Countability>().toEqualTypeOf<
      'COUNTABLE' | 'MASS' | 'MIXED' | 'PLURAL_ONLY' | 'UNKNOWN'
    >();
    expectTypeOf<Mode>().toEqualTypeOf<'ANGLICIZED' | 'CLASSICAL'>();
    expectTypeOf<CasePolicy>().toEqualTypeOf<'PRESERVE' | 'LOWERCASE' | 'UPPERCASE'>();
    expectTypeOf<UppercasePolicy>().toEqualTypeOf<'INFER_INITIALISM' | 'LITERAL'>();
  });

  test('engine and builder signatures', () => {
    expectTypeOf<ReturnType<typeof EnglishInflector.standard>>().toEqualTypeOf<EnglishInflector>();
    expectTypeOf<ReturnType<typeof EnglishInflector.builder>>().toEqualTypeOf<Builder>();
    expectTypeOf<EnglishInflector['pluralize']>().toBeCallableWith('cat');
    expectTypeOf<EnglishInflector['pluralize']>().toBeCallableWith('cat', 2);
    expectTypeOf<ReturnType<EnglishInflector['analyze']>>().toEqualTypeOf<Inflection>();
    expectTypeOf<EnglishInflector['lexiconSize']>().toEqualTypeOf<number>();
    expectTypeOf(new Builder().withOverride('a', 'b')).toEqualTypeOf<Builder>();
    expectTypeOf(new Builder().withoutBundledLexicon()).toEqualTypeOf<Builder>();
    expectTypeOf(new Builder().build()).toEqualTypeOf<EnglishInflector>();
  });

  test('lexicon provider is an interface, not an any-shaped bag', () => {
    expectTypeOf<LexiconProvider['kind']>().toEqualTypeOf<
      'rules-only' | 'delta' | 'full' | 'custom'
    >();
    expectTypeOf<LexiconProvider['lookup']>().toEqualTypeOf<
      (lower: string) => LexiconEntry | undefined
    >();
    expectTypeOf<LexiconProvider['isOrdinaryWord']>().toEqualTypeOf<
      (lower: string) => boolean | undefined
    >();
    expectTypeOf<LexiconEntry['alternatives']>().toEqualTypeOf<readonly string[]>();
    expectTypeOf<LexiconEntry['classical']>().toEqualTypeOf<string | null>();
  });
});
