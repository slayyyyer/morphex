"""Two candidate rankers over the shared inventory: a sparse linear selector and a compact neural one.

Both optimise the same multi-positive softmax objective, so accepted alternatives are rewarded rather than
penalised. Neither sees the test split: training is train-only and threshold selection is dev-only.
"""

from __future__ import annotations

import math
import random
import zlib

from .candidates import EDIT_INDEX, candidates


def bucket(key: str, dims: int) -> int:
    return zlib.crc32(key.encode("utf-8")) % dims


class LinearRanker:
    """Regularised sparse linear classifier over suffix-class x edit-id features."""

    def __init__(self, dims: int = 4096, learning_rate: float = 0.35, l2: float = 1e-6):
        self.dims = dims
        self.learning_rate = learning_rate
        self.l2 = l2
        self.weights = [0.0] * dims

    def features(self, lemma: str, edit_id: str) -> list[int]:
        low = lemma.lower()
        out = [bucket(f"e:{edit_id}", self.dims)]
        for size in (1, 2, 3):
            if len(low) >= size:
                out.append(bucket(f"s{size}:{low[-size:]}|e:{edit_id}", self.dims))
        out.append(bucket(f"len:{min(len(low) // 3, 5)}|e:{edit_id}", self.dims))
        return out

    def score(self, lemma: str, edit_id: str) -> float:
        return sum(self.weights[index] for index in self.features(lemma, edit_id))

    def train(self, records: list[dict], epochs: int, seed: int) -> None:
        rng = random.Random(seed)
        order = list(records)
        for _ in range(epochs):
            rng.shuffle(order)
            for record in order:
                pairs = candidates(record["lemma"], record["base"])
                scores = [self.score(record["lemma"], edit_id) for edit_id, _ in pairs]
                positives = [index for index, (_, form) in enumerate(pairs)
                             if form in record["accepted_set"]]
                if not positives:
                    continue
                maximum = max(scores)
                exponentials = [math.exp(value - maximum) for value in scores]
                total = sum(exponentials)
                for index, (edit_id, _) in enumerate(pairs):
                    probability = exponentials[index] / total
                    target = 1.0 / len(positives) if index in positives else 0.0
                    gradient = probability - target
                    if abs(gradient) < 1e-7:
                        continue
                    for feature in self.features(record["lemma"], edit_id):
                        self.weights[feature] -= self.learning_rate * gradient
                        self.weights[feature] *= 1.0 - self.learning_rate * self.l2

    def predict_all(self, lemma: str, base: str) -> list[tuple[str, str, float]]:
        pairs = candidates(lemma, base)
        scores = [self.score(lemma, edit_id) for edit_id, _ in pairs]
        maximum = max(scores)
        exponentials = [math.exp(value - maximum) for value in scores]
        total = sum(exponentials)
        return [(edit_id, form, exponentials[index] / total) for index, (edit_id, form) in enumerate(pairs)]

    def parameters(self) -> int:
        return len(self.weights)


class MlpRanker:
    """Compact one-hidden-layer tanh network: hashed character trigrams plus an edit embedding."""

    def __init__(self, dims: int = 1024, width: int = 12, learning_rate: float = 0.08, seed: int = 0):
        self.dims = dims
        self.width = width
        self.learning_rate = learning_rate
        rng = random.Random(seed)
        self.w1 = [[0.0] * width for _ in range(dims)]
        self.edit = [[rng.gauss(0.0, 0.05) for _ in range(width)] for _ in range(len(EDIT_INDEX))]
        self.b1 = [0.0] * width
        self.w2 = [rng.gauss(0.0, 0.1) for _ in range(width)]
        self.b2 = 0.0

    def grams(self, lemma: str) -> list[int]:
        low = lemma.lower()
        padded = f"^{low}$"
        if len(padded) < 3:
            return [bucket(f"g:{padded}", self.dims)]
        return [bucket(f"g:{padded[index:index + 3]}", self.dims) for index in range(len(padded) - 2)]

    def _hidden(self, grams: list[int], edit_id: str) -> tuple[list[float], list[float]]:
        width = self.width
        pre = list(self.b1)
        for gram in grams:
            row = self.w1[gram]
            for j in range(width):
                pre[j] += row[j]
        edit_row = self.edit[EDIT_INDEX[edit_id]]
        for j in range(width):
            pre[j] += edit_row[j]
        hidden = [math.tanh(value) for value in pre]
        return hidden, pre

    def scores(self, lemma: str, pairs: list[tuple[str, str]]) -> list[tuple[list[float], list[float], float]]:
        grams = self.grams(lemma)
        out = []
        for edit_id, _ in pairs:
            hidden, pre = self._hidden(grams, edit_id)
            score = self.b2 + sum(self.w2[j] * hidden[j] for j in range(self.width))
            out.append((hidden, pre, score))
        return out

    def train(self, records: list[dict], epochs: int, seed: int) -> None:
        rng = random.Random(seed)
        order = list(records)
        width = self.width
        for _ in range(epochs):
            rng.shuffle(order)
            for record in order:
                pairs = candidates(record["lemma"], record["base"])
                grams = self.grams(record["lemma"])
                computed = self.scores(record["lemma"], pairs)
                raw = [item[2] for item in computed]
                positives = [index for index, (_, form) in enumerate(pairs)
                             if form in record["accepted_set"]]
                if not positives:
                    continue
                maximum = max(raw)
                exponentials = [math.exp(value - maximum) for value in raw]
                total = sum(exponentials)
                old_w2 = list(self.w2)
                deltas = []
                for index, (edit_id, _) in enumerate(pairs):
                    probability = exponentials[index] / total
                    target = 1.0 / len(positives) if index in positives else 0.0
                    delta = probability - target
                    if abs(delta) < 1e-6:
                        continue
                    deltas.append((index, edit_id, delta))
                # Output-layer updates use the forward activations, then the hidden gradients use the
                # pre-update weights: mixing the two orders makes the gradient inconsistent.
                for index, _, delta in deltas:
                    hidden = computed[index][0]
                    for j in range(width):
                        self.w2[j] -= self.learning_rate * delta * hidden[j]
                    self.b2 -= self.learning_rate * delta
                for index, edit_id, delta in deltas:
                    hidden = computed[index][0]
                    for j in range(width):
                        dpre = delta * old_w2[j] * (1.0 - hidden[j] * hidden[j])
                        if dpre == 0.0:
                            continue
                        self.b1[j] -= self.learning_rate * dpre
                        edit_row = self.edit[EDIT_INDEX[edit_id]]
                        edit_row[j] -= self.learning_rate * dpre
                        for gram in grams:
                            self.w1[gram][j] -= self.learning_rate * dpre

    def predict_all(self, lemma: str, base: str) -> list[tuple[str, str, float]]:
        pairs = candidates(lemma, base)
        computed = self.scores(lemma, pairs)
        raw = [item[2] for item in computed]
        maximum = max(raw)
        exponentials = [math.exp(value - maximum) for value in raw]
        total = sum(exponentials)
        return [(edit_id, form, exponentials[index] / total) for index, (edit_id, form) in enumerate(pairs)]

    def parameters(self) -> int:
        return self.dims * self.width + len(EDIT_INDEX) * self.width + self.width * 3 + 1
