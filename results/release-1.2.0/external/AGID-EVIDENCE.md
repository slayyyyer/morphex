# AGID 2016.01.19 external stress test

This is a separate follow-up to the frozen 1.2.0 evidence. It does **not** replace the
hand-adjudicated UD English EWT result and is **not** a corpus-accuracy claim.

## Source

| field | value |
| --- | --- |
| source | Automatically Generated Inflection Database (AGID) 2016.01.19 |
| project | https://wordlist.aspell.net/ |
| archive | https://sourceforge.net/projects/wordlist/files/AGID/2016.01.19/agid-2016.01.19.tar.gz |
| archive SHA-256 | `15d2d792d309d2dc838bf75d8abcd3feb36708c219dc5158d4fff70b89e601d1` |
| extracted input | `infl.txt` |
| `infl.txt` SHA-256 | `df0c8f6edd4193d823abfad9e4ff6f42b4526211516649e1c73d4533aef749fa` |
| source kind | automatically generated inflection lexicon |
| used for training/tuning | no |

AGID is independent of the shipped Kaikki/Wiktionary vocabulary. It was selected after the
permissive UD English alternatives checked yielded only dozens of unseen lemmas each; GUM,
LinES and ParTUT are CC BY-NC-SA 4.0 and were excluded.

## Filter

`infl.txt` contains 48,500 `N:` entries. The preparation:

1. keeps `N:` noun entries only;
2. rejects capitalized source lemmas, removing proper names and initialisms;
3. takes one clean preferred plural with no `<`, `~`, `!`, `?`, or `{}` marker and no
   non-zero variant level;
4. requires a single-token alphabetic lemma and form matching `[a-z]{3,}`;
5. rejects form == lemma;
6. lower-cases and de-duplicates by lemma.

Result: **34,642 lemmas / 34,642 forms**.

| skipped reason | count |
| --- | ---: |
| capitalized or initialism | 10,141 |
| uncertain marker | 5,934 |
| no accepted form | 3,717 |
| unsupported shape | 54 |
| identity | 428 |
| non-preferred variant | 51 |

## Commands

Run from the repository root with the pinned JDK/Maven toolchain:

```bash
curl -L --fail -o build/external/agid/agid-2016.01.19.tar.gz \
  https://downloads.sourceforge.net/project/wordlist/AGID/2016.01.19/agid-2016.01.19.tar.gz
tar -xzf build/external/agid/agid-2016.01.19.tar.gz -C build/external/agid
sha256sum build/external/agid/agid-2016.01.19.tar.gz

py -3 tools/research/external_eval.py prepare --source agid \
  --agid build/external/agid/agid-2016.01.19/infl.txt \
  --out-dir results/release-1.2.0/external

./mvnw -q -o -pl inflector-eval -am install -DskipTests
./mvnw -q -o -pl inflector-eval exec:java \
  -Dexec.mainClass=io.github.slayyyyer.morphex.inflector.eval.ExternalEval \
  -Dexec.args="--gold results/release-1.2.0/external/agid-gold.tsv --out results/release-1.2.0/external/agid-predictions.tsv"

py -3 tools/research/external_eval.py score --source agid \
  --gold results/release-1.2.0/external/agid-gold.tsv \
  --predictions results/release-1.2.0/external/agid-predictions.tsv \
  --lexicon inflector-core/src/main/resources/io/github/slayyyyer/morphex/inflector/lexicon-v2.tsv \
  --out-dir results/release-1.2.0/external --family-mode lexical
```

The score uses 2,000 bootstrap rounds. For unclustered Bernoulli values the implementation
draws the resample count from `Binomial(n, observed_rate)`, which is distributionally
identical to resampling the binary list with replacement.

## Output hashes

| file | SHA-256 |
| --- | --- |
| `agid-manifest.json` | `b4fbb7508b8135893f8d181ab96e7bd4b631afc56bcc1cea176fd906bd795d10` |
| `agid-gold.tsv` | `38579d384aadde0094c77ce0be8079733ffc8721f2b0749b3cee99f51df813eb` |
| `agid-predictions.tsv` | `2c70d8bdbbd376ac1f84765ca6e5da79b6df4a4f021fb0b8c2f479b3192fa07c` |
| `agid-report.json` | `0f2b23917e895b73e5fe443990f14660a663f1cd73072b27810ef6068132fb0b` |
| `agid-report.md` | `bc747fcb446a86a9ed8e614d8754646c2ed6cf0bcfec66eaa4fbe853078c153b` |

## Denominators and results

Full-vocabulary membership: **34,381 seen / 261 unseen**. Compact-artifact membership:
**5,463 hit / 29,179 miss**. The unseen denominator is 7.25x the EWT unseen slice.

| system | selected overall | full-vocab seen | full-vocab unseen (261) | artifact hit (5,463) | artifact miss (29,179) |
| --- | ---: | ---: | ---: | ---: | ---: |
| stock evo-inflector 2.0 | 97.48% | 97.50% | 95.02% | 87.50% | 99.35% |
| shipped lexicon | 93.84% | 93.83% | **95.40%** | 61.54% | 99.89% |
| rules only | **98.20%** | **98.22%** | **95.40%** | **89.16%** | **99.89%** |

Unseen accuracy is 249/261 for the lexicon and rules, and 248/261 for evo2. There is no
stable-improvement claim: the lexicon is tied with rules-only on the unseen slice.

Of 2,133 lexicon errors, 1,722 are identity returns (1,685 `MASS/I`). AGID has no
countability labels, so the overall row is a lexical-policy stress test rather than
natural-text accuracy. The 12 unseen errors are `calliopsis`, `corditis`, `corneum`, `dalo`,
`globigerina`, `marum`, `pizzaz`, `siris`, `stelis`, `stypsis`, `tabis`, and `yourself`;
`yourself` is an AGID pronoun misclassified as a noun.

## License notice

AGID 2016.01.19 is Copyright 2000-2016 by Kevin Atkinson `<kevina@gnu.org>`.

> Permission to use, copy, modify, distribute and sell this database, the associated
> scripts, the output created from the scripts and its documentation for any purpose is
> hereby granted without fee, provided that the above copyright notice appears in all
> copies and that both that copyright notice and this permission notice appear in
> supporting documentation. Kevin Atkinson makes no representations about the suitability
> of this array for any purpose. It is provided "as is" without express or implied
> warranty.

The AGID part-of-speech database is partly derived from Princeton WordNet 1.6. Its notice is:

> This software and database is being provided to you, the LICENSEE, by Princeton
> University under the following license. By obtaining, using and/or copying this software
> and database, you agree that you have read, understood, and will comply with these terms
> and conditions: Permission to use, copy, modify and distribute this software and database
> and its documentation for any purpose and without fee or royalty is hereby granted,
> provided that you agree to comply with the following copyright notice and statements,
> including the disclaimer, and that the same appear on ALL copies of the software,
> database and documentation, including modifications that you make for internal use or for
> distribution. THIS SOFTWARE AND DATABASE IS PROVIDED "AS IS" AND PRINCETON UNIVERSITY
> MAKES NO REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT NOT
> LIMITATION, PRINCETON UNIVERSITY MAKES NO REPRESENTATIONS OR WARRANTIES OF
> MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE LICENSED
> SOFTWARE, DATABASE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
> COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. The name of Princeton University or Princeton may
> not be used in advertising or publicity pertaining to distribution of the software and/or
> database. Title to copyright in this software, database and any associated documentation
> shall at all times remain with Princeton University and LICENSEE agrees to preserve same.

The full AGID README is at https://wordlist.aspell.net/agid-readme/.
