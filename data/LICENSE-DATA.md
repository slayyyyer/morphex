# Data provenance and license

## What is derived from what

| File | Origin | License |
| --- | --- | --- |
| `data/raw/kaikki-english-nouns.jsonl.gz` (not committed, 298,543,101 bytes) | Kaikki.org English dictionary, noun part-of-speech extract | CC BY-SA 3.0 (Wiktionary) |
| `data/derived/nouns-v2.tsv.gz` | validated normalization of the above (`extract-v3`) | CC BY-SA 3.0 |
| `inflector-core/src/main/resources/.../lexicon-v2.tsv` and membership | subset of the above plus `data/curated/core-nouns.tsv` | CC BY-SA 3.0 for extracted rows; curated rows authored here |
| `data/curated/core-nouns.tsv` | hand-authored in this repository | MIT (same as code) |
| `data/regression/nouns-regression.tsv` | hand-authored in this repository | MIT |

The code is MIT. The generated data files are **not** relicensed by MIT; they remain
CC BY-SA 3.0. Redistributing the jar therefore distributes CC BY-SA 3.0 data alongside
MIT code. `NOTICE` records the attribution. `withoutBundledLexicon()` avoids loading the
data at runtime, but the resource remains in the JAR and the data terms still apply. To
remove the data entirely, build a jar that excludes the resources or regenerate them from a
source you can license.

## External validation corpora (not used for training)

`docs/evaluation.md` reports an external check on
[UD English EWT](https://github.com/UniversalDependencies/UD_English-EWT), licensed
**CC BY-SA 4.0**. The corpus is downloaded into `build/external/` and is not committed; only
aggregate reports and predictions under `results/release-1.2.0/external/` are kept. The
shipped artifact is not derived from this corpus and the corpus was not used for training or
tuning.

The larger external stress test uses
[AGID 2016.01.19](https://sourceforge.net/projects/wordlist/files/AGID/2016.01.19/), an
automatically generated inflection database. Its archive SHA-256 is
`15d2d792d309d2dc838bf75d8abcd3feb36708c219dc5158d4fff70b89e601d1`; the source
`infl.txt` SHA-256 is `df0c8f6edd4193d823abfad9e4ff6f42b4526211516649e1c73d4533aef749fa`.
`results/release-1.2.0/external/agid-gold.tsv` is a filtered derivative (lowercase lemmas,
clean preferred plurals, deduplicated). The AGID terms permit use, modification and
distribution provided the copyright and permission notices are retained; the source notice
is reproduced in `NOTICE` and `results/release-1.2.0/external/AGID-EVIDENCE.md`. AGID is not
derived from the shipped Kaikki/Wiktionary vocabulary and was not used to train or tune the
runtime artifact.

## Pinned snapshot

| Field | Value |
| --- | --- |
| URL | `https://kaikki.org/dictionary/English/pos-noun/kaikki.org-dictionary-English-by-pos-noun.jsonl.gz` |
| SHA-256 | `9a47500cabdb5d6c918a4b16abeefb55d32e2c1a2f29d260080c02329278ab43` |
| Bytes | 298,543,101 |
| Last-Modified | Sat, 31 Oct 2026 03:12:47 GMT |
| Extraction | `tools/research/extract.py`, version `extract-v3` |
| Generator | Kaikki.org, built with Wiktextract |

The dump does not carry a per-record revision field. Provenance is therefore the dump
identity above plus the Wiktionary sense ids recorded per lemma in the normalized dataset
(`record_id` column). The dump is not committed; `python tools/research/cli.py fetch`
downloads and verifies it explicitly (network).

## What the pipeline does

1. `extract` streams the gzip JSONL, keeps `pos == noun`, validates lemma and form surface
   shapes, separates restricted forms from accepted forms, classifies countability from the
   `en-noun` head expansion and sense tags, and inspects every head template for plural-only
   evidence. It quarantines unsupported records with a reason instead of guessing.
2. `splits` assigns whole lemma families to train/dev/test by the pinned `split-v1` hash.
3. `artifact` selects entries the productive rules cannot reproduce, plus entries that carry
   countability, invariance, initialism or casing metadata, and merges the curated table
   (curated rows win).

Wiktionary directives are data, never labels: `{{en-noun|++}}` for `quiz` yields the form
`quizzes`, and `p` for `scissors` yields a plural-only classification, not a literal `p`.

## Counts from the recorded v2 run

The normalized dataset has **340,734 lemmas**. The shipped artifact has **147,338 entries**
(7,553,688 bytes TSV) and a **340,741-lemma** full membership table (2,725,940 bytes). The P2
train-only artifact has 103,668 entries and 238,927 membership lemmas. Exact hashes and
versions are recorded in `results/release-1.2.0/EVIDENCE.md` and its manifest.

The v2 changes include 178 new plural-only lemmas, removal of `PADS` and `PL`, and
reclassification of `accent` and `work` as `MIXED`.

## Redistribution checklist

* Keep `NOTICE` and this file with any distribution of the jar or derived data.
* State the CC BY-SA 3.0 license and attribution to Wiktionary contributors, Kaikki.org and
  Wiktextract.
* If you modify the data, share the modified data under CC BY-SA 3.0 and mark it modified.
* Do not describe the data as MIT-licensed merely because it sits in the same artifact as
  MIT code.
