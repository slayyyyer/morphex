# Data provenance and license

## What is derived from what

| File | Origin | License |
| --- | --- | --- |
| `data/raw/kaikki-english-nouns.jsonl.gz` (not committed, 298,543,101 bytes) | Kaikki.org English dictionary, noun part-of-speech extract | CC BY-SA 3.0 (Wiktionary) |
| `data/derived/nouns-v1.tsv.gz` | validated normalization of the above | CC BY-SA 3.0 |
| `data/derived/lexicon-v1.tsv` and the packaged resource | subset of the above plus `data/curated/core-nouns.tsv` | CC BY-SA 3.0 for extracted rows; curated rows authored here |
| `data/curated/core-nouns.tsv` | hand-authored in this repository | MIT (same as code) |
| `data/regression/nouns-regression.tsv` | hand-authored in this repository | MIT |

The code is MIT. The generated data files are **not** relicensed by MIT; they remain
CC BY-SA 3.0. Redistributing the jar therefore distributes CC BY-SA 3.0 data alongside
MIT code. `NOTICE` records the attribution. If that is unacceptable, build without the
bundled artifact (see README) or regenerate the artifact from a source you can license.

## External validation corpus (not redistributed)

`docs/evaluation.md` reports an external check on
[UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), licensed
**CC BY-SA 4.0**. The corpus is downloaded into `build/external/` and is not committed; only aggregate
reports under `results/external/` and citations are kept (derived pair/prediction TSVs are
gitignored). The shipped artifact is not derived from this corpus and the corpus was not
used for training or tuning.

## Pinned snapshot

| Field | Value |
| --- | --- |
| URL | `https://kaikki.org/dictionary/English/pos-noun/kaikki.org-dictionary-English-by-pos-noun.jsonl.gz` |
| SHA-256 | `9a47500cabdb5d6c918a4b16abeefb55d32e2c1a2f29d260080c02329278ab43` |
| Bytes | 298,543,101 |
| Last-Modified | Sat, 31 Oct 2026 03:12:47 GMT |
| Extraction | `tools/research/extract.py`, version `extract-v1` |
| Generator | Kaikki.org, built with Wiktextract |

The dump does not carry a per-record revision field. Provenance is therefore the dump
identity above plus the Wiktionary sense ids recorded per lemma in the normalized dataset
(`record_id` column). The dump is not committed; `python tools/research/cli.py fetch`
downloads and verifies it explicitly (network).

## What the pipeline does

1. `extract` streams the gzip JSONL, keeps `pos == noun`, validates lemma and form surface
   shapes, separates restricted forms (archaic/obsolete/nonstandard/rare/...) from accepted
   forms, classifies countability from the `en-noun` head expansion and sense tags, and
   quarantines unsupported records with a reason instead of guessing.
2. `splits` assigns whole lemma families to train/dev/test by a pinned hash.
3. `artifact` selects entries the productive rules cannot reproduce, plus entries that carry
   countability, invariance, initialism or casing metadata, and merges the curated table
   (curated rows win).

Wiktionary directives are data, never labels: `{{en-noun|++}}` for `quiz` yields the form
`quizzes`, and `p` for `scissors` yields a plural-only classification, not a literal `p`.

## Counts from the recorded run

See `data/derived/manifest-v1.json` for exact numbers and checksums. The committed run:

* 834,878 raw noun records read
* 354,313 records kept, 340,558 unique lemmas
* quarantined: 256,764 `no-attested-plural`, 223,801 `lemma-surface` (multi-word, non-Latin)
* countability: 198,596 countable, 90,437 mass, 46,677 mixed, 4,848 plural-only
* runtime artifact: 150,267 entries, 7,708,247 bytes raw / 1,212,308 bytes gzip (compact
  selection; 190,298 lemmas whose plural is exactly the productive rule output are omitted and
  are still handled by rules)

## Redistribution checklist

* Keep `NOTICE` and this file with any distribution of the jar or the derived data.
* State the CC BY-SA 3.0 license and attribution to Wiktionary contributors, Kaikki.org and
  Wiktextract.
* If you modify the data (for example regenerate the artifact), the modified data must be
  shared under CC BY-SA 3.0; mark it as modified.
* Do not describe the data as MIT-licensed merely because it sits in the same artifact as
  MIT code.
