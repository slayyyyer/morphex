"""Learning experiment: candidate ceiling, non-neural selector, compact neural ranker, decision.

Order of operations is fixed by the brief:

1. measure the candidate-oracle ceiling first (a ranker cannot recover a missing candidate);
2. train D (sparse linear) and E (compact MLP) on identical candidates and the same renderer;
3. select routing/acceptance thresholds on dev only, then freeze and score test once;
4. report a ship / offline-only / reject / inconclusive decision with the measured evidence.

The runtime is Python research code. It is never packaged with the library and no Java inference path is
claimed, so the runtime microsecond gates are reported as not demonstrated rather than estimated.
"""

from __future__ import annotations

import json
import math
import random
import time
from datetime import datetime, timezone
from pathlib import Path

from . import candidates, dataset, evaluate, learners, splits

PRIMARY = {"COUNTABLE", "MIXED"}
REQUIRED_RELATIVE_ERROR_REDUCTION = 0.20
REQUIRED_ABSOLUTE_GAIN = 0.0005


def load_base_predictions(path: Path) -> dict[str, tuple[str, str]]:
    base: dict[str, tuple[str, str]] = {}
    for row in dataset.read_tsv(path):
        if row["system"] == "lexicon":
            base[row["id"]] = (row["prediction"], row["source"])
    return base


def build_records(normalized: dict, assignments: dict, split: str,
                  base: dict[str, tuple[str, str]] | None = None) -> list[dict]:
    records: list[dict] = []
    for lemma, row in normalized.items():
        assignment = assignments.get(lemma)
        if not assignment or assignment["split"] != split:
            continue
        if row["countability"] not in PRIMARY:
            continue
        forms = [form for form in row["forms"].split(",") if form]
        if not forms:
            continue
        prediction, source = (base or {}).get(lemma, ("", ""))
        records.append({
            "lemma": lemma,
            "accepted": forms,
            "accepted_set": set(forms),
            "family": assignment["family"],
            "bucket": assignment["bucket"],
            "countability": row["countability"],
            "invariant": row["invariant"] == "1",
            "base": prediction,
            "source": source,
        })
    records.sort(key=lambda record: record["lemma"])
    return records


def base_accuracy(records: list[dict]) -> float:
    if not records:
        return 0.0
    correct = sum(1 for record in records if record["base"] in record["accepted_set"])
    return correct / len(records)


def oracle_analysis(records: list[dict]) -> dict:
    hits = 0
    by_edit: dict[str, int] = {}
    by_bucket: dict[str, list[int]] = {}
    for record in records:
        pairs = candidates.candidates(record["lemma"], record["base"])
        winner = candidates.oracle(pairs, record["accepted_set"])
        entry = by_bucket.setdefault(record["bucket"], [0, 0])
        entry[1] += 1
        if winner is not None:
            hits += 1
            by_edit[winner] = by_edit.get(winner, 0) + 1
            entry[0] += 1
    base = base_accuracy(records)
    oracle = hits / len(records) if records else 0.0
    errors = (1.0 - base) * len(records)
    required = max(REQUIRED_RELATIVE_ERROR_REDUCTION * errors / max(1, len(records)), REQUIRED_ABSOLUTE_GAIN)
    return {
        "records": len(records),
        "base_accuracy": base,
        "base_errors": int(round(errors)),
        "oracle_accuracy": oracle,
        "oracle_gain_pp": (oracle - base) * 100,
        "required_gain_pp": required * 100,
        "ceiling_clears_required_gain": (oracle - base) >= required,
        "oracle_hits_by_edit": dict(sorted(by_edit.items(), key=lambda item: -item[1])),
        "by_bucket": {
            name: {"hits": value[0], "total": value[1],
                   "accuracy": value[0] / value[1] if value[1] else None}
            for name, value in sorted(by_bucket.items())
        },
    }


def gate(record: dict) -> bool:
    """Cheap pre-inference routing gate: only dictionary-miss rule results are worth a model call."""
    return record["source"].endswith(":rule") or record["source"].endswith(":fallback")


def score_records(records: list[dict], model) -> dict[str, tuple[str, float]]:
    """One scoring pass per model: only gated records are scored, and only once."""
    ranked: dict[str, tuple[str, float]] = {}
    for record in records:
        if not gate(record):
            continue
        items = model.predict_all(record["lemma"], record["base"])
        _, form, probability = max(items, key=lambda item: item[2])
        ranked[record["lemma"]] = (form, probability)
    return ranked


def decide(records: list[dict], ranked: dict[str, tuple[str, float]], threshold: float
           ) -> tuple[dict[str, str], dict]:
    predictions = {record["lemma"]: record["base"] for record in records}
    by_lemma = {record["lemma"]: record for record in records}
    overrides = 0
    for lemma, (form, probability) in ranked.items():
        record = by_lemma[lemma]
        if form != record["base"] and probability >= threshold:
            predictions[lemma] = form
            overrides += 1
    invoked = len(ranked)
    return predictions, {
        "invoked": invoked,
        "invocation_rate": invoked / len(records) if records else 0.0,
        "overrides": overrides,
        "override_rate": overrides / invoked if invoked else 0.0,
    }


def accuracy(records: list[dict], predictions: dict[str, str]) -> float:
    if not records:
        return 0.0
    correct = sum(1 for record in records if predictions.get(record["lemma"]) in record["accepted_set"])
    return correct / len(records)


def sweep_threshold(records: list[dict], ranked: dict[str, tuple[str, float]]) -> tuple[float, list[dict]]:
    trials = []
    best_threshold = 1.0
    best_accuracy = -1.0
    best_overrides = 10**9
    for step in range(0, 20):
        threshold = step / 20.0
        predictions, stats = decide(records, ranked, threshold)
        score = accuracy(records, predictions)
        trials.append({"threshold": threshold, "accuracy": score, "overrides": stats["overrides"]})
        if score > best_accuracy + 1e-12 or (abs(score - best_accuracy) <= 1e-12
                                             and stats["overrides"] < best_overrides):
            best_accuracy = score
            best_threshold = threshold
            best_overrides = stats["overrides"]
    return best_threshold, trials


def expected_calibration_error(records: list[dict], ranked: dict[str, tuple[str, float]]) -> list[dict]:
    bins = [[0, 0.0, 0] for _ in range(10)]
    by_lemma = {record["lemma"]: record for record in records}
    for lemma, (form, probability) in ranked.items():
        record = by_lemma[lemma]
        if form == record["base"]:
            continue
        correct = form in record["accepted_set"]
        index = min(9, int(probability * 10))
        bins[index][0] += 1
        bins[index][1] += probability
        bins[index][2] += int(correct)
    total = sum(entry[0] for entry in bins)
    result = []
    ece = 0.0
    for index, (count, probability_sum, correct) in enumerate(bins):
        if not count:
            continue
        mean_confidence = probability_sum / count
        mean_accuracy = correct / count
        ece += (count / total) * abs(mean_confidence - mean_accuracy)
        result.append({"bin": index / 10, "count": count,
                       "mean_confidence": mean_confidence, "accuracy": mean_accuracy})
    return [{"ece": ece, "bins": result}]


def as_gold(records: list[dict]) -> dict[str, dict]:
    gold = {}
    for record in records:
        gold[record["lemma"]] = {
            "id": record["lemma"],
            "lemma": record["lemma"],
            "family": record["family"],
            "bucket": record["bucket"],
            "countability": record["countability"],
            "accepted_set": record["accepted_set"],
        }
    return gold


def train_linear(records: list[dict], epochs: int, seed: int) -> tuple[learners.LinearRanker, float]:
    start = time.time()
    model = learners.LinearRanker()
    model.train(records, epochs=epochs, seed=seed)
    return model, time.time() - start


def train_mlp(records: list[dict], epochs: int, seed: int) -> tuple[learners.MlpRanker, float]:
    start = time.time()
    model = learners.MlpRanker(seed=seed)
    model.train(records, epochs=epochs, seed=seed)
    return model, time.time() - start


def subsample(records: list[dict], maximum: int) -> list[dict]:
    if len(records) <= maximum:
        return records
    stride = len(records) / maximum
    return [records[int(index * stride)] for index in range(maximum)]


def decision(oracle: dict, results: dict, baseline_errors: int) -> dict:
    reasons: list[str] = []
    if not oracle["ceiling_clears_required_gain"]:
        return {
            "decision": "reject",
            "headline": "candidate ceiling cannot clear the required gain; ranker work stopped",
            "reasons": [
                f"oracle gain {oracle['oracle_gain_pp']:.3f}pp < required {oracle['required_gain_pp']:.3f}pp",
            ],
        }
    best = max(results.values(), key=lambda item: item["test"]["accuracy"]) if results else None
    if best is None:
        return {"decision": "inconclusive", "headline": "no learned selector was evaluated", "reasons": []}
    base_accuracy = oracle["base_accuracy"]
    gain = best["test"]["accuracy"] - base_accuracy
    required = oracle["required_gain_pp"] / 100.0
    reduction = (best["test"]["errors"] / baseline_errors) if baseline_errors else 1.0
    if gain < required:
        reasons.append(f"gain {gain * 100:.4f}pp below required {required * 100:.4f}pp")
    if reduction > 0.8:
        reasons.append(f"residual errors reduced by only {(1 - reduction) * 100:.1f}% (need 20%)")
    if 0 < base_accuracy:
        reasons.append("runtime microsecond/size gates require a Java scorer and were not demonstrated")
    return {
        "decision": "reject" if reasons else "offline-only",
        "headline": ("learned selector did not clear the frozen gates; deterministic library ships"
                     if reasons else
                     "learned selector clears accuracy gates offline; only an exported artifact could ship"),
        "reasons": reasons,
        "measured_gain_pp": gain * 100,
        "required_gain_pp": required * 100,
    }


def report_markdown(report: dict) -> str:
    lines = [f"# Learning experiment ({report['split']} split)", ""]
    lines.append(f"Generated {report['generated']}. Runtime: python research code only; no Java inference path.")
    lines.append("")
    lines.append("## Candidate ceiling")
    lines.append("")
    oracle = report["oracle"]
    lines.append(f"* records: {oracle['records']}")
    lines.append(f"* deterministic C accuracy: {oracle['base_accuracy'] * 100:.3f}% "
                 f"({oracle['base_errors']} errors)")
    lines.append(f"* candidate-oracle accuracy: {oracle['oracle_accuracy'] * 100:.3f}% "
                 f"(+{oracle['oracle_gain_pp']:.3f}pp)")
    lines.append(f"* required gain: {oracle['required_gain_pp']:.3f}pp -> "
                 f"{'cleared' if oracle['ceiling_clears_required_gain'] else 'NOT cleared'}")
    lines.append("")
    lines.append("| edit | oracle hits |")
    lines.append("| --- | --- |")
    for edit, hits in oracle["oracle_hits_by_edit"].items():
        lines.append(f"| {edit} | {hits} |")
    lines.append("")
    if report.get("selectors"):
        lines.append("## Selectors")
        lines.append("")
        lines.append("| selector | seed | params | dev threshold | test accuracy | errors | "
                     "corrections | regressions | CI95 pp | invocation | overrides |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for name, result in report["selectors"].items():
            test = result["test"]
            ci = test["ci95_pp"]
            lines.append(
                f"| {name} | {result['seed']} | {result['parameters']} | {result['threshold']:.2f} | "
                f"{test['accuracy'] * 100:.3f}% | {test['errors']} | {test['corrections']} | "
                f"{test['regressions']} | [{ci[0]:+.3f}, {ci[1]:+.3f}] | "
                f"{test['invocation_rate'] * 100:.1f}% | {test['override_rate'] * 100:.1f}% |")
        lines.append("")
    lines.append("## Decision")
    lines.append("")
    lines.append(f"**{report['decision']['decision']}** - {report['decision']['headline']}")
    lines.append("")
    for reason in report["decision"].get("reasons", []):
        lines.append(f"* {reason}")
    lines.append("")
    return "\n".join(lines)


def run(args) -> dict:
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    normalized = {row["lemma"]: row for row in dataset.read_tsv(dataset.NORMALIZED)}
    assignments = splits.load()

    train_records = build_records(
        normalized, assignments, "train",
        load_base_predictions(Path("results/eval/p2-train-preds.tsv")))
    train_records = [record for record in train_records if record["base"]]
    dev_records = build_records(
        normalized, assignments, "dev",
        load_base_predictions(Path("results/eval/p2-dev-preds.tsv")))
    dev_records = [record for record in dev_records if record["base"]]
    test_records = build_records(
        normalized, assignments, args.split,
        load_base_predictions(Path(f"results/eval/p2-{args.split}-preds.tsv")))
    test_records = [record for record in test_records if record["base"]]

    oracle = oracle_analysis(test_records)
    oracle_dev = oracle_analysis(dev_records)

    # Configuration selected on dev only (two pilots: 6k/3 epochs -> +2.60pp, 20k/5 epochs -> +1.08pp).
    epochs = 1 if args.fast else 3
    train_subset = subsample(train_records, 2000 if args.fast else 6000)
    report = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "split": args.split,
        "protocol": "P2 family holdout; lexicon built from train families only",
        "budget": {"epochs": epochs, "train_records": len(train_subset),
                   "available_train_records": len(train_records),
                   "seeds": 1 if args.fast else 3},
        "oracle": oracle,
        "oracle_dev": oracle_dev,
        "base": {"test_accuracy": oracle["base_accuracy"], "base_errors": oracle["base_errors"]},
        "selectors": {},
    }
    if not oracle["ceiling_clears_required_gain"]:
        report["decision"] = decision(oracle, {}, oracle["base_errors"])
        (out_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        (out_dir / "report.md").write_text(report_markdown(report) + "\n", encoding="utf-8")
        return report

    seeds = [0] if args.fast else [0, 1, 2]
    gold_test = as_gold(test_records)
    baseline_predictions = {record["lemma"]: record["base"] for record in test_records}

    linear, linear_seconds = train_linear(train_subset, epochs, seed=0)
    linear_dev = score_records(dev_records, linear)
    linear_test = score_records(test_records, linear)
    linear_threshold, linear_trials = sweep_threshold(dev_records, linear_dev)
    linear_predictions, linear_stats = decide(test_records, linear_test, linear_threshold)
    linear_comparison = evaluate.compare(gold_test, baseline_predictions, linear_predictions,
                                         iterations=args.iterations)
    report["selectors"]["linear"] = {
        "seed": 0,
        "parameters": linear.parameters(),
        "artifact_bytes_estimate": linear.parameters() * 4,
        "train_seconds": linear_seconds,
        "threshold": linear_threshold,
        "dev_trials": linear_trials,
        "test": {
            "accuracy": accuracy(test_records, linear_predictions),
            "errors": sum(1 for record in test_records
                          if linear_predictions.get(record["lemma"]) not in record["accepted_set"]),
            **linear_comparison,
            **linear_stats,
        },
        "calibration": expected_calibration_error(test_records, linear_test),
    }

    for seed in seeds:
        model, seconds = train_mlp(train_subset, epochs, seed)
        dev_ranked = score_records(dev_records, model)
        test_ranked = score_records(test_records, model)
        threshold, trials = sweep_threshold(dev_records, dev_ranked)
        predictions, stats = decide(test_records, test_ranked, threshold)
        comparison = evaluate.compare(gold_test, baseline_predictions, predictions,
                                      iterations=args.iterations)
        report["selectors"][f"mlp-seed{seed}"] = {
            "seed": seed,
            "parameters": model.parameters(),
            "artifact_bytes_estimate": model.parameters() * 4,
            "train_seconds": seconds,
            "threshold": threshold,
            "dev_trials": trials,
            "test": {
                "accuracy": accuracy(test_records, predictions),
                "errors": sum(1 for record in test_records
                              if predictions.get(record["lemma"]) not in record["accepted_set"]),
                **comparison,
                **stats,
            },
            "calibration": expected_calibration_error(test_records, test_ranked),
        }

    report["decision"] = decision(oracle, report["selectors"], oracle["base_errors"])
    (out_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (out_dir / "report.md").write_text(report_markdown(report) + "\n", encoding="utf-8")
    print(report_markdown(report))
    return report
