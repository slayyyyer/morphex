"""Fixed scoring for every system.

Labels are read once from the frozen gold file. A prediction is correct only when it is a member of the
accepted form set; no prediction ever changes the label or the denominator. Mass, plural-only and invariant
records are scored in their own buckets and never merged into the singular-to-plural headline.
"""

from __future__ import annotations

import json
import random
import subprocess
from collections import defaultdict
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path

from . import dataset

POLICY_BUCKETS = {"MASS", "PLURAL_ONLY"}
PRIMARY_COUNTABILITY = {"COUNTABLE", "MIXED"}


def is_primary(record: dict) -> bool:
    """The one primary selector used by every headline, comparison, and learning gate."""
    return record["countability"] in PRIMARY_COUNTABILITY and not record["invariant"]


def load_gold(path) -> dict[str, dict]:
    rows = dataset.read_tsv(path)
    gold: dict[str, dict] = {}
    for row in rows:
        accepted = tuple(form for form in row["accepted"].split(",") if form)
        if row["id"] in gold:
            raise ValueError(f"duplicate gold id {row['id']!r} in {path}")
        gold[row["id"]] = {
            "id": row["id"],
            "lemma": row["lemma"],
            "family": row["family"],
            "bucket": row["bucket"],
            "countability": row["countability"],
            "invariant": row["invariant"] == "1",
            "accepted": accepted,
            "accepted_set": frozenset(accepted),
        }
    return gold


def load_predictions(paths: list) -> tuple[dict[str, dict[str, str]], dict[str, dict[str, str]]]:
    """Returns (predictions, decision sources) keyed by system then record id."""
    predictions: dict[str, dict[str, str]] = defaultdict(dict)
    sources: dict[str, dict[str, str]] = defaultdict(dict)
    for path in paths:
        for row in dataset.read_tsv(path):
            system = row["system"]
            record_id = row["id"]
            if record_id in predictions[system]:
                raise ValueError(f"duplicate prediction {system}/{record_id!r} in {path}")
            predictions[system][record_id] = row["prediction"]
            sources[system][record_id] = row["source"]
    return dict(predictions), dict(sources)


def _slice_stats(gold: dict[str, dict], preds: dict[str, str], selector) -> dict:
    total = correct = 0
    for record_id, record in gold.items():
        if not selector(record):
            continue
        total += 1
        prediction = preds.get(record_id)
        if prediction is not None and prediction in record["accepted_set"]:
            correct += 1
    return {"total": total, "correct": correct, "errors": total - correct,
            "accuracy": (correct / total) if total else None}


def score_system(gold: dict[str, dict], preds: dict[str, str],
                 sources: dict[str, str] | None = None) -> dict:
    def by(field: str) -> dict:
        result: dict[str, dict] = {}
        values = sorted({record[field] for record in gold.values()})
        for value in values:
            result[value] = _slice_stats(gold, preds, lambda record, value=value: record[field] == value)
        return result

    primary = _slice_stats(gold, preds, is_primary)
    primary_by_bucket: dict[str, dict] = {}
    for bucket in sorted({record["bucket"] for record in gold.values() if is_primary(record)}):
        primary_by_bucket[bucket] = _slice_stats(
            gold, preds, lambda record, bucket=bucket: is_primary(record) and record["bucket"] == bucket)
    primary_by_source: dict[str, dict] = {}
    if sources:
        for source in sorted({sources.get(record_id, "") for record_id in gold if is_primary(gold[record_id])}):
            primary_by_source[source] = _slice_stats(
                gold, preds,
                lambda record, source=source: is_primary(record) and sources.get(record["id"], "") == source)
    plain_non_invariant = _slice_stats(
        gold, preds,
        lambda record: record["bucket"] == "simple"
        and record["countability"] in PRIMARY_COUNTABILITY
        and not record["invariant"],
    )
    lengths = {"short": [0, 0], "medium": [0, 0], "long": [0, 0]}
    for record_id, record in gold.items():
        if not is_primary(record):
            continue
        size = len(record["lemma"])
        key = "short" if size <= 5 else "medium" if size <= 9 else "long"
        lengths[key][1] += 1
        prediction = preds.get(record_id)
        if prediction is not None and prediction in record["accepted_set"]:
            lengths[key][0] += 1
    return {
        "overall": _slice_stats(gold, preds, lambda record: True),
        "primary": primary,
        "primary_by_bucket": primary_by_bucket,
        "primary_by_source": primary_by_source,
        "primary_plain_non_invariant": plain_non_invariant,
        "by_bucket": by("bucket"),
        "by_countability": by("countability"),
        "by_length": {
            key: {"total": total, "correct": correct, "errors": total - correct,
                  "accuracy": (correct / total) if total else None}
            for key, (correct, total) in lengths.items()
        },
        "predicted": len(preds),
    }


def compare(gold: dict[str, dict], baseline: dict[str, str], candidate: dict[str, str],
            iterations: int = 2000, seed: int = 20261107) -> dict:
    corrections = 0
    regressions = 0
    family_delta: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    for record_id, record in gold.items():
        if not is_primary(record):
            continue
        base_correct = baseline.get(record_id) in record["accepted_set"]
        cand_correct = candidate.get(record_id) in record["accepted_set"]
        delta = int(cand_correct) - int(base_correct)
        corrections += int(cand_correct and not base_correct)
        regressions += int(base_correct and not cand_correct)
        entry = family_delta[record["family"]]
        entry[0] += delta
        entry[1] += 1

    families = list(family_delta.items())
    rng = random.Random(seed)
    deltas: list[float] = []
    total = sum(count for _, (_, count) in families)
    observed = sum(delta for _, (delta, _) in families) / total if total else 0.0
    for _ in range(iterations):
        sampled_delta = 0
        sampled_n = 0
        for _ in range(len(families)):
            _, (delta, count) = families[rng.randrange(len(families))]
            sampled_delta += delta
            sampled_n += count
        if sampled_n:
            deltas.append(sampled_delta / sampled_n)
    deltas.sort()
    low = deltas[int(0.025 * (len(deltas) - 1))]
    high = deltas[int(0.975 * (len(deltas) - 1))]
    baseline_errors = sum(1 for record_id, record in gold.items()
                          if is_primary(record) and baseline.get(record_id) not in record["accepted_set"])
    candidate_errors = sum(1 for record_id, record in gold.items()
                           if is_primary(record) and candidate.get(record_id) not in record["accepted_set"])
    net = corrections - regressions
    return {
        "corrections": corrections,
        "regressions": regressions,
        "net_corrections": net,
        "baseline_errors": baseline_errors,
        "candidate_errors": candidate_errors,
        "delta_accuracy_pp": observed * 100,
        "ci95_pp": [low * 100, high * 100],
        "relative_error_reduction": (baseline_errors - candidate_errors) / baseline_errors
        if baseline_errors else 0.0,
        "iterations": iterations,
        "families": len(families),
    }


def render_markdown(report: dict) -> str:
    lines = [f"# Evaluation report ({report['split']} split, lexicon split {report['lexicon_split']})", ""]
    lines.append("Primary slice: countable and mixed-use, non-invariant records, accepted-form accuracy.")
    lines.append("")
    lines.append("| system | accepted accuracy | errors | primary accuracy | primary errors |")
    lines.append("| --- | --- | --- | --- | --- |")
    for name, stats in report["systems"].items():
        overall = stats["overall"]
        primary = stats["primary"]
        acc = f"{overall['accuracy'] * 100:.3f}%" if overall["accuracy"] is not None else "n/a"
        pacc = f"{primary['accuracy'] * 100:.3f}%" if primary["accuracy"] is not None else "n/a"
        lines.append(f"| {name} | {acc} | {overall['errors']} | {pacc} | {primary['errors']} |")
    lines.append("")
    lines.append("## Paired comparisons (primary slice, family-clustered bootstrap)")
    lines.append("")
    lines.append("| baseline | candidate | delta pp | 95% CI | corrections | regressions | "
                 "baseline errors | candidate errors | relative error reduction |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for comparison in report["comparisons"]:
        ci = comparison["ci95_pp"]
        lines.append(
            f"| {comparison['baseline']} | {comparison['candidate']} | "
            f"{comparison['delta_accuracy_pp']:+.4f} | [{ci[0]:+.4f}, {ci[1]:+.4f}] | "
            f"{comparison['corrections']} | {comparison['regressions']} | "
            f"{comparison['baseline_errors']} | {comparison['candidate_errors']} | "
            f"{comparison['relative_error_reduction'] * 100:+.3f}% |")
    lines.append("")
    lines.append("## Slice detail")
    lines.append("")
    for name, stats in report["systems"].items():
        lines.append(f"### {name}")
        lines.append("")
        lines.append("| slice | total | correct | accuracy |")
        lines.append("| --- | --- | --- | --- |")
        for group in ("by_bucket", "by_countability", "by_length"):
            for key, value in stats[group].items():
                acc = f"{value['accuracy'] * 100:.2f}%" if value["accuracy"] is not None else "n/a"
                lines.append(f"| {group}:{key} | {value['total']} | {value['correct']} | {acc} |")
        lines.append("")
    return "\n".join(lines)


def _git_revision() -> str | None:
    try:
        result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=dataset.ROOT,
                                capture_output=True, text=True, check=True)
        return result.stdout.strip() or None
    except (OSError, subprocess.CalledProcessError):
        return None


def run(gold_path, prediction_paths: list, baseline: str, out_path, lexicon_split: str,
        split_name: str, iterations: int = 2000) -> dict:
    gold = load_gold(gold_path)
    predictions, sources = load_predictions(prediction_paths)
    gold_ids = set(gold)
    for name, preds in predictions.items():
        missing = sorted(gold_ids - set(preds))
        extra = sorted(set(preds) - gold_ids)
        if missing or extra:
            raise ValueError(
                f"prediction ids do not match gold for {name}: missing={len(missing)} "
                f"(first {missing[:3]}), extra={len(extra)} (first {extra[:3]})")
    systems = {name: score_system(gold, preds, sources[name])
               for name, preds in sorted(predictions.items())}
    comparisons = []
    if baseline in predictions:
        for name, preds in sorted(predictions.items()):
            if name == baseline:
                continue
            comparison = compare(gold, predictions[baseline], preds, iterations=iterations)
            comparison.update({"baseline": baseline, "candidate": name})
            comparisons.append(comparison)
    report = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "protocol_version": "eval-v2",
        "split": split_name,
        "lexicon_split": lexicon_split,
        "records": len(gold),
        "denominators": {
            "overall": len(gold),
            "primary": sum(1 for record in gold.values() if is_primary(record)),
        },
        "gold_sha256": sha256(Path(gold_path).read_bytes()).hexdigest(),
        "prediction_sha256": {Path(path).name: sha256(Path(path).read_bytes()).hexdigest()
                              for path in prediction_paths},
        "config": {"baseline": baseline, "iterations": iterations, "seed": 20261107,
                   "primary_selector": "countability in {COUNTABLE,MIXED} and not invariant"},
        "revision": _git_revision(),
        "systems": systems,
        "comparisons": comparisons,
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    out_path.with_suffix(".md").write_text(render_markdown(report) + "\n", encoding="utf-8")
    return report
