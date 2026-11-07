"""Tests for external membership labelling. Stdlib only."""

import unittest

from tools.research import external_eval


class MembershipLabelTest(unittest.TestCase):
    @staticmethod
    def family_key(lemma):
        return lemma

    def test_regular_compacted_words_are_seen_but_not_artifact_hits(self):
        labels = external_eval.membership_sets(
            ["cat", "dog", "zorp"],
            {"cat", "dog", "mouse"},
            {"cat"},
            self.family_key,
        )
        self.assertEqual({"cat", "dog"}, labels["seen"])
        self.assertEqual({"zorp"}, labels["unseen"])
        self.assertEqual({"cat"}, labels["artifact_hit"])
        self.assertEqual({"dog", "zorp"}, labels["artifact_miss"])

    def test_family_labels_use_the_full_vocabulary(self):
        labels = external_eval.membership_sets(
            ["cat", "dog", "zorp"],
            {"cat", "dog"},
            {"cat"},
            lambda lemma: "feline" if lemma in {"cat", "dog"} else lemma,
        )
        self.assertEqual({"cat", "dog"}, labels["family_seen"])
        self.assertEqual({"zorp"}, labels["family_unseen"])
        self.assertEqual({"cat", "dog"}, labels["artifact_family_hit"])
        self.assertEqual({"zorp"}, labels["artifact_family_miss"])


if __name__ == "__main__":
    unittest.main()
