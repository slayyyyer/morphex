"""Corrected residual-learning experiment.

The first experiment trained on predictions from a lexicon that already contained the training words. Its
base was correct by lookup on most records, so the candidate selector learned to keep a lexical answer that
would not exist on an unseen word. This version uses inner-fold out-of-fold predictions from ``oof.py``:
each training family is predicted by an artifact built without that family. Thresholds and configuration
choices are made on outer development data only; the frozen selector is scored once on test.

The experiment is research code. It is not packaged with the library and no runtime model is claimed; a
shipped selector would require a dependency-free Java implementation or a static-table export.
"""

from __future__ import annotations

import json
import math
import random
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from . import candidates, dataset, evaluate, learners, splits

PRIMARY = {"COUNTABLE", "MIXED"}
REQUIRED_RELATIVE_ERROR_REDUCTION = 0.20
REQUIRED_ABSOLUTE_GAIN = 0.0005


def load_base_predictions(path: Path) -> dict[str, tuple[str, str]]:
    base: dict[str, tuple[str, str]] = {}
    target = Path(path)
    if not target.exists():
        raise SystemExit(f"prediction file missing: {target}")
    for row in dataset.read_tsv(target):
        if row["system"] == "lexicon":
            base[row["id"]] = (row["prediction"], row["source"])
    return base


def build_records(normalized: dict, assignments: dict, split: str,
                  base: dict[str, tuple[str, str]]) -> list[dict]:
    records: list[dict] = []
    for lemma, row in normalized.items():
        assignment = assignments.get(lemma)
        if not assignment or assignment["split"] != split:
            continue
        if row["countability"] not in PRIMARY or row["invariant"] == "1":
            continue
        forms = [form for form in row["forms"].split(",") if form]
        if not forms:
            continue
        prediction, source = base.get(lemma, ("", ""))
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
    return sum(1 for record in records if record["base"] in record["accepted_set"]) / len(records)


def gate(record: dict) -> bool:
    """Pre-inference eligibility: only a dictionary-miss rule/fallback result can be repaired."""
    return bool(record["base"]) and (record["source"].endswith(":rule")
                                     or record["source"].endswith(":fallback"))


def oracle_analysis(records: list[dict]) -> dict:
    hits = 0
    gated = 0
    base_wrong = 0
    by_edit: Counter[str] = Counter()
    by_bucket: dict[str, list[int]] = {}
    for record in records:
        pairs = candidates.candidates(record["lemma"], record["base"], record["source"])
        winner = candidates.oracle(pairs, record["accepted_set"])
        entry = by_bucket.setdefault(record["bucket"], [0, 0])
        entry[1] += 1
        if record["base"] not in record["accepted_set"]:
            base_wrong += 1
        if gate(record):
            gated += 1
        if winner is not None:
            hits += 1
            by_edit[winner] += 1
            entry[0] += 1
    base = base_accuracy(records)
    oracle = hits / len(records) if records else 0.0
    errors = int(round((1.0 - base) * len(records)))
    required = max(REQUIRED_RELATIVE_ERROR_REDUCTION * errors / max(1, len(records)), REQUIRED_ABSOLUTE_GAIN)
    return {
        "records": len(records),
        "gated_records": gated,
        "base_wrong_records": base_wrong,
        "candidate_coverage": hits / len(records) if records else 0.0,
        "base_accuracy": base,
        "base_errors": errors,
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


def score_records(records: list[dict], model) -> dict[str, tuple[str, float]]:
    ranked: dict[str, tuple[str, float]] = {}
    for record in records:
        if not gate(record):
            continue
        items = model.predict_all(record["lemma"], record["base"], record["source"])
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
    return sum(1 for record in records
               if predictions.get(record["lemma"]) in record["accepted_set"]) / len(records)


def errors(records: list[dict], predictions: dict[str, str]) -> int:
    return sum(1 for record in records
               if predictions.get(record["lemma"]) not in record["accepted_set"])


def sweep_threshold(records: list[dict], ranked: dict[str, tuple[str, float]]) -> tuple[float, list[dict]]:
    trials = []
    best_threshold = 1.0
    best_accuracy = -1.0
    best_overrides = 10**9
    for step in range(0, 21):
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


def expected_calibration_error(records: list[dict], ranked: dict[str, tuple[str, float]]) -> dict:
    bins = [[0, 0.0, 0] for _ in range(10)]
    by_lemma = {record["lemma"]: record for record in records}
    for lemma, (form, probability) in ranked.items():
        record = by_lemma[lemma]
        if form == record["base"]:
            continue
        index = min(9, int(probability * 10))
        bins[index][0] += 1
        bins[index][1] += probability
        bins[index][2] += int(form in record["accepted_set"])
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
    return {"ece": ece, "bins": result}


def as_gold(records: list[dict]) -> dict[str, dict]:
    return {
        record["lemma"]: {
            "id": record["lemma"],
            "lemma": record["lemma"],
            "family": record["family"],
            "bucket": record["bucket"],
            "countability": record["countability"],
            "invariant": record["invariant"],
            "accepted": tuple(record["accepted"]),
            "accepted_set": frozenset(record["accepted_set"]),
        }
        for record in records
    }


def delta_by_bucket(records: list[dict], baseline: dict[str, str], candidate: dict[str, str]) -> dict:
    result: dict[str, dict] = {}
    for record in records:
        entry = result.setdefault(record["bucket"], {"corrections": 0, "regressions": 0})
        base_correct = baseline.get(record["lemma"]) in record["accepted_set"]
        cand_correct = candidate.get(record["lemma"]) in record["accepted_set"]
        if cand_correct and not base_correct:
            entry["corrections"] += 1
        elif base_correct and not cand_correct:
            entry["regressions"] += 1
    return result


def selector_metrics(records: list[dict], base_predictions: dict[str, str], predictions: dict[str, str],
                     iterations: int) -> dict:
    comparison = evaluate.compare(as_gold(records), base_predictions, predictions, iterations=iterations)
    return {
        "accuracy": accuracy(records, predictions),
        "errors": errors(records, predictions),
        "corrections": comparison["corrections"],
        "regressions": comparison["regressions"],
        "net_corrections": comparison["net_corrections"],
        "relative_error_reduction": comparison["relative_error_reduction"],
        "ci95_pp": comparison["ci95_pp"],
        "by_bucket": delta_by_bucket(records, base_predictions, predictions),
    }


def train_linear(records: list[dict], epochs: int, seed: int):
    start = time.time()
    model = learners.LinearRanker()
    model.train(records, epochs=epochs, seed=seed)
    return model, time.time() - start


def train_mlp(records: list[dict], epochs: int, seed: int):
    start = time.time()
    model = learners.MlpRanker(seed=seed)
    model.train(records, epochs=epochs, seed=seed)
    return model, time.time() - start


def subsample(records: list[dict], maximum: int) -> list[dict]:
    if len(records) <= maximum:
        return records
    stride = len(records) / maximum
    return [records[int(index * stride)] for index in range(maximum)]


def decision(oracle: dict, results: dict, selected: str, runtime: dict | None = None) -> dict:
    reasons: list[str] = []
    if not oracle["ceiling_clears_required_gain"]:
        return {
            "decision": "reject",
            "headline": "candidate ceiling cannot clear the required gain; ranker work stopped",
            "reasons": [
                f"oracle gain {oracle['oracle_gain_pp']:.3f}pp < required {oracle['required_gain_pp']:.3f}pp",
            ],
        }
    if not selected or selected == "keep_base":
        return {
            "decision": "reject",
            "headline": "no learned selector beat the disabled-repair baseline on dev",
            "reasons": ["dev selection chose keep-base; no override was justified"],
        }
    test = results[selected]["test"]
    gain = test["accuracy"] - oracle["base_accuracy"]
    required = oracle["required_gain_pp"] / 100.0
    if gain < required:
        reasons.append(f"test gain {gain * 100:.4f}pp below required {required * 100:.4f}pp")
    if test["relative_error_reduction"] < REQUIRED_RELATIVE_ERROR_REDUCTION:
        reasons.append(f"residual errors reduced by only {test['relative_error_reduction'] * 100:.1f}% (need 20%)")
    if test["ci95_pp"][0] <= 0:
        reasons.append(f"95% CI includes zero: [{test['ci95_pp'][0]:+.4f}, {test['ci95_pp'][1]:+.4f}]")
    if runtime is None:
        reasons.append("runtime gate results were not supplied to this run")
    elif runtime.get("overall_pass"):
        reasons.append("runtime gates passed")
    else:
        verdict = runtime.get("verdict", {})
        reasons.append(f"runtime gates failed: p99={verdict.get('latency')}, "
                       f"size={verdict.get('size')}, init={verdict.get('initialization')}")
    return {
        "decision": "reject" if reasons else "offline-only",
        "headline": ("learned selector did not clear the frozen gates; deterministic library ships"
                     if reasons else
                     "learned selector clears accuracy gates offline; only an exported artifact could ship"),
        "selected_on_dev": selected,
        "reasons": reasons,
        "measured_gain_pp": gain * 100,
        "required_gain_pp": required * 100,
        "test": test,
    }


def report_markdown(report: dict) -> str:
    lines = [f"# Learning experiment ({report['split']} split)", ""]
    lines.append(f"Generated {report['generated']}. Research code only; no runtime model is shipped.")
    lines.append("")
    lines.append("## Protocol")
    lines.append("")
    lines.append(f"* {report['protocol']}")
    lines.append(f"* primary records: {report['oracle']['records']}")
    lines.append(f"* training examples: {report['training']['records']} out-of-fold records "
                 f"({report['training']['gated']} eligible rule/fallback records)")
    lines.append(f"* OOF manifest: `{report['training']['manifest']}`")
    lines.append("")
    lines.append("## Candidate ceiling")
    lines.append("")
    oracle = report["oracle"]
    lines.append(f"* base accuracy: {oracle['base_accuracy'] * 100:.3f}% ({oracle['base_errors']} errors)")
    lines.append(f"* candidate-oracle accuracy: {oracle['oracle_accuracy'] * 100:.3f}% "
                 f"(+{oracle['oracle_gain_pp']:.3f}pp)")
    lines.append(f"* required gain: {oracle['required_gain_pp']:.3f}pp -> "
                 f"{'cleared' if oracle['ceiling_clears_required_gain'] else 'NOT cleared'}")
    lines.append("")
    lines.append("## Selectors (threshold chosen on dev; test scored once)")
    lines.append("")
    lines.append("| selector | seed | params | threshold | dev accuracy | dev overrides | test accuracy | "
                 "test errors | corrections | regressions | 95% CI pp |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for name, result in report["selectors"].items():
        dev = result["dev"]
        test = result["test"]
        ci = test["ci95_pp"]
        lines.append(
            f"| {name} | {result['seed']} | {result['parameters']} | {result['threshold']:.2f} | "
            f"{dev['accuracy'] * 100:.3f}% | {result['dev']['overrides']} | "
            f"{test['accuracy'] * 100:.3f}% | {test['errors']} | {test['corrections']} | "
            f"{test['regressions']} | [{ci[0]:+.4f}, {ci[1]:+.4f}] |")
    lines.append("")
    if report.get("runtime"):
        runtime = report["runtime"]
        median = runtime.get("median", {})
        gate = runtime.get("gate", {})
        lines.append("## Runtime gates")
        lines.append("")
        lines.append(f"* model bytes: {runtime.get('model_bytes')} (gate {gate.get('size_kib')} KiB)")
        lines.append(f"* p99: {median.get('p99_us')} us (gate {gate.get('p99_us')} us)")
        lines.append(f"* initialization: {median.get('init_ms')} ms (gate {gate.get('init_ms')} ms)")
        lines.append(f"* overall: {'PASS' if runtime.get('overall_pass') else 'FAIL'}")
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

    oof_path = Path(args.oof)
    if not oof_path.exists():
        raise SystemExit(
            f"out-of-fold predictions missing: {oof_path}\n"
            "Run `py -3 -m tools.research.oof --folds 5` after building `inflector-eval`."
        )
    train_records = build_records(normalized, assignments, "train", load_base_predictions(oof_path))
    dev_records = build_records(normalized, assignments, "dev",
                                load_base_predictions(Path(args.dev_preds)))
    test_records = build_records(normalized, assignments, args.split,
                                 load_base_predictions(Path(args.test_preds)))
    selector_input = Path(getattr(args, "selector_input", "results/learning/java-test-input.tsv"))
    selector_input.parent.mkdir(parents=True, exist_ok=True)
    dataset.write_tsv(selector_input, ["id", "lemma", "base", "source"],
                      [[record["lemma"], record["lemma"], record["base"], record["source"]]
                       for record in test_records])

    runtime_path = getattr(args, "runtime", None)
    runtime = None
    if runtime_path:
        path = Path(runtime_path)
        if path.exists():
            runtime = json.loads(path.read_text(encoding="utf-8"))

    train_gated = [record for record in train_records if gate(record)]
    oracle = oracle_analysis(test_records)
    oracle_dev = oracle_analysis(dev_records)
    report = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "split": args.split,
        "protocol": ("P2 family holdout; inner-fold OOF training; outer dev threshold selection; "
                     "test scored once. Primary selector = COUNTABLE/MIXED, non-invariant."),
        "budget": {"epochs": 1 if args.fast else 3,
                   "train_records": len(subsample(train_gated, 3000 if args.fast else 20000)),
                   "available_train_records": len(train_gated),
                   "seeds": 1 if args.fast else 3,
                   "folds": 5},
        "training": {
            "records": len(train_records),
            "gated": len(train_gated),
            "base_accuracy": base_accuracy(train_records),
            "base_wrong": sum(1 for record in train_records if record["base"] not in record["accepted_set"]),
            "sources": dict(Counter(record["source"] for record in train_records).most_common()),
            "manifest": str(oof_path.parent / "manifest.json"),
        },
        "oracle": oracle,
        "oracle_dev": oracle_dev,
        "base": {"test_accuracy": oracle["base_accuracy"], "base_errors": oracle["base_errors"]},
        "selector_input": str(selector_input),
        "runtime": runtime,
        "selectors": {},
    }

    base_dev = {record["lemma"]: record["base"] for record in dev_records}
    base_test = {record["lemma"]: record["base"] for record in test_records}
    report["selectors"]["keep_base"] = {
        "seed": 0,
        "parameters": 0,
        "threshold": 1.0,
        "dev": {**selector_metrics(dev_records, base_dev, base_dev, args.iterations), "overrides": 0},
        "test": selector_metrics(test_records, base_test, base_test, args.iterations),
    }

    if not oracle["ceiling_clears_required_gain"]:
        report["decision"] = decision(oracle, report["selectors"], "keep_base", runtime)
        (out_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        (out_dir / "report.md").write_text(report_markdown(report) + "\n", encoding="utf-8")
        return report

    train_subset = subsample(train_gated, 3000 if args.fast else 20000)
    linear, linear_seconds = train_linear(train_subset, report["budget"]["epochs"], seed=0)
    linear_dev_ranked = score_records(dev_records, linear)
    linear_test_ranked = score_records(test_records, linear)
    linear_threshold, linear_trials = sweep_threshold(dev_records, linear_dev_ranked)
    linear_dev_predictions, linear_dev_stats = decide(dev_records, linear_dev_ranked, linear_threshold)
    linear_test_predictions, linear_test_stats = decide(test_records, linear_test_ranked, linear_threshold)
    model_path = Path(getattr(args, "model_out", "results/learning/linear-v1.bin"))
    linear.save(model_path, linear_threshold)
    report["selectors"]["linear"] = {
        "seed": 0,
        "parameters": linear.parameters(),
        "artifact_bytes": model_path.stat().st_size,
        "model": str(model_path),
        "train_seconds": linear_seconds,
        "threshold": linear_threshold,
        "dev_trials": linear_trials,
        "dev": {**selector_metrics(dev_records, base_dev, linear_dev_predictions, args.iterations),
                **linear_dev_stats},
        "test": {**selector_metrics(test_records, base_test, linear_test_predictions, args.iterations),
                 **linear_test_stats},
        "calibration": expected_calibration_error(test_records, linear_test_ranked),
    }

    for seed in ([] if args.linear_only else ([0] if args.fast else [0, 1, 2])):
        model, seconds = train_mlp(train_subset, report["budget"]["epochs"], seed)
        dev_ranked = score_records(dev_records, model)
        test_ranked = score_records(test_records, model)
        threshold, trials = sweep_threshold(dev_records, dev_ranked)
        dev_predictions, dev_stats = decide(dev_records, dev_ranked, threshold)
        test_predictions, test_stats = decide(test_records, test_ranked, threshold)
        report["selectors"][f"mlp-seed{seed}"] = {
            "seed": seed,
            "parameters": model.parameters(),
            "artifact_bytes_estimate": model.parameters() * 4,
            "train_seconds": seconds,
            "threshold": threshold,
            "dev_trials": trials,
            "dev": {**selector_metrics(dev_records, base_dev, dev_predictions, args.iterations), **dev_stats},
            "test": {**selector_metrics(test_records, base_test, test_predictions, args.iterations),
                     **test_stats},
            "calibration": expected_calibration_error(test_records, test_ranked),
        }

    candidates_for_selection = {
        name: result for name, result in report["selectors"].items()
        if name == "keep_base" or result["dev"]["accuracy"] > 0
    }
    selected = max(
        candidates_for_selection,
        key=lambda name: (candidates_for_selection[name]["dev"]["accuracy"],
                          -candidates_for_selection[name]["dev"].get("overrides", 0)),
    )
    report["selected_on_dev"] = selected
    report["decision"] = decision(oracle, report["selectors"], selected, runtime)
    (out_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (out_dir / "report.md").write_text(report_markdown(report) + "\n", encoding="utf-8")
    print(report_markdown(report))
    return report
