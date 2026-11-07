"""Optional third-party baseline: score PyPI `inflect` against the frozen gold labels.

This is the only research script with a non-stdlib dependency::

    pip install inflect

It writes a predictions TSV in the same shape as ``p1-test-preds.tsv``
(``id``, ``system``, ``prediction``, ``source``), scored afterwards with the
unchanged fixed evaluator::

    py -m tools.research.cli evaluate \\
      --gold results/eval/test-gold.tsv \\
      --predictions results/eval/p1-test-preds.tsv results/eval/pyinflect-test-preds.tsv \\
      --out results/eval/pyinflect-report.json --split test --lexicon-split all

``inflect.engine().plural_noun(lemma, 2)`` forces the plural form. A
non-string result (``inflect`` returns ``False`` when it believes no
inflection applies) falls back to the lemma unchanged, which is the closest
equivalent of this repository's documented fallback.
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

from . import dataset

SYSTEM = "pyinflect"


def main(argv: list[str] | None = None) -> int:
    try:
        import inflect
    except ImportError:
        print("error: this baseline needs PyPI `inflect` (`pip install inflect`)",
              file=sys.stderr)
        return 2

    args = (argv if argv is not None else sys.argv[1:])
    records_path = Path(args[0]) if len(args) > 0 else Path("results/eval/test-records.tsv")
    out_path = Path(args[1]) if len(args) > 1 else Path("results/eval/pyinflect-test-preds.tsv")

    records = dataset.read_tsv(records_path)
    engine = inflect.engine()
    try:
        version = getattr(inflect, "__version__", "unknown")
    except Exception:
        version = "unknown"

    rows: list[dict[str, str]] = []
    fallbacks = 0
    started = time.time()
    for index, record in enumerate(records, 1):
        lemma = record["lemma"]
        try:
            prediction = engine.plural_noun(lemma, 2)
        except Exception:
            prediction = False
        if not isinstance(prediction, str) or not prediction:
            prediction = lemma
            fallbacks += 1
        rows.append({"id": record["id"], "system": SYSTEM,
                     "prediction": prediction, "source": f"pyinflect:inflect-{version}"})
        if index % 10000 == 0:
            print(f"... {index}/{len(records)} ({time.time() - started:.0f}s)",
                  file=sys.stderr)

    dataset.write_tsv(out_path, ["id", "system", "prediction", "source"],
                        [[row["id"], row["system"], row["prediction"], row["source"]]
                         for row in rows])
    elapsed = time.time() - started
    print(f"{SYSTEM}: {len(rows)} predictions -> {out_path} "
          f"({elapsed:.1f}s, {fallbacks} lemma fallbacks)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
