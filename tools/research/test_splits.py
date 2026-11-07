"""Tests for the conservative derivational-family split key."""

from __future__ import annotations

import unittest

from . import splits


class StrictFamilyKeyTest(unittest.TestCase):
    def test_derivational_variants_share_a_stem(self):
        self.assertEqual(splits.strict_family_key("teacher"), splits.strict_family_key("teaching"))
        self.assertEqual(splits.strict_family_key("teacher"), splits.strict_family_key("teach"))
        self.assertEqual(splits.strict_family_key("runner"), splits.strict_family_key("running"))
        self.assertEqual(splits.strict_family_key("runner"), splits.strict_family_key("run"))
        self.assertEqual(splits.strict_family_key("creation"), splits.strict_family_key("create"))
        self.assertEqual(splits.strict_family_key("organization"), splits.strict_family_key("organ"))
        self.assertEqual(splits.strict_family_key("happiness"), splits.strict_family_key("happy"))

    def test_short_words_are_not_over_stripped(self):
        self.assertEqual("water", splits.strict_family_key("water"))
        self.assertEqual("cross", splits.strict_family_key("cross"))

    def test_compound_keeps_the_existing_first_token_rule(self):
        self.assertEqual("credit", splits.strict_family_key("credit-card"))


class StrictAssignmentTest(unittest.TestCase):
    def test_variants_are_assigned_to_one_family_and_split(self):
        rows = splits.assign(["teacher", "teaching", "teach"], mode="strict")
        by_lemma = {row[0]: row for row in rows}
        self.assertEqual(1, len({row[1] for row in rows}))
        self.assertEqual(1, len({row[2] for row in rows}))
        self.assertEqual("teach", by_lemma["teacher"][1])

    def test_lexical_mode_is_unchanged(self):
        self.assertEqual("teacher", splits.family_key("teacher"))
        self.assertNotEqual("teacher", splits.strict_family_key("teacher"))


if __name__ == "__main__":
    unittest.main()
