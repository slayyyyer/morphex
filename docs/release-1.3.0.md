# v1.3.0

Released 2026-11-07.

This is an evidence and performance release. It does not change the public API, rule IDs,
rule precedence, or pluralization policy.

## Highlights

- **Independent AGID stress test.** The AGID 2016.01.19 evaluation adds 34,642 lemmas,
  including 261 lemmas absent from the full permitted vocabulary. The lexicon and rules-only
  both select 249/261 genuinely unseen forms (95.40%); evo2 selects 248/261 (95.02%).
  AGID is automatically generated and has no countability labels. Its 93.84% lexicon overall
  score is dominated by 1,722 `MASS`/invariant identity returns against generated plurals and
  is not a natural-text or corpus-accuracy claim.
- **Faster compacted path.** ASCII shape fast paths and an ordered direct rule dispatch
  replace the seven-entry rule loop. The compacted benchmark is 79.445 ± 3.018 ns/op
  (51.430 B/op), down from 105.008 ns after the ASCII stage and 456.362 ns at the release
  baseline. The same-run evo2 reference is 59.826 ± 0.817 ns/op (80.572 B/op), so the path
  remains 1.33x slower. No general speedup is claimed.
- **Profile-backed changes.** `Rule.applies` no longer appears in the stack sample. Remaining
  top library frames are `WordShape.isPlainLatinToken`, `WordShape.casingOf`, `Lexicon.find`,
  and `WordShape.hasUpper`.
- **Corrected evidence labels.** The release baseline, compound stage, ASCII stage, and
  flattened stage are reported separately. The release evidence for P1/P2 and EWT remains
  frozen under `results/release-1.2.0/`; no new P1/P2 run was performed for 1.3.0.

## Compatibility

- Public Java API unchanged.
- Rule IDs and precedence unchanged: `woman`, `wife`, `man`, Greek `-sis`, sibilant,
  consonant-y, default.
- The removed `Rule` class was package-private and is not part of the consumer API.
- Compound, casing, initialism, countability, override, and classical-mode behaviour from
  1.2.0 is unchanged.
- Unicode shape handling is preserved by the ASCII fast paths and covered by the new
  `WordShapeTest` parity cases.

## Verification

- Maven reactor: `148` core tests and `3` evaluator tests passed; `BUILD SUCCESS`.
- Python research suite: `52` tests passed.
- `git diff --check`: clean.
- Compacted JMH: two forks, three warmups, five 500 ms measurements, GC profiler.
- Benchmark records:
  `results/release-1.2.0/benchmarks/optimization-compacted-flat-clean.json` and the
  accompanying log/profile files.

## Coordinates

```xml
<dependency>
  <groupId>io.github.slayyyyer.morphex</groupId>
  <artifactId>english-inflector-core</artifactId>
  <version>1.3.0</version>
</dependency>
```

## Known limits

- P1's 100% is frozen-lexicon coverage, not generalization.
- P2 remains a dictionary-lemma protocol, not application-call accuracy.
- The AGID unseen slice is 261 lemmas; the one-lemma lead over evo2 is not a broad
  state-of-the-art claim.
- The compacted path remains slower than evo2 and compound/unknown paths are unchanged.
- Full evaluation evidence remains under `results/release-1.2.0/` because 1.3.0 adds
  evidence and performance work, not a new evaluation run.
