"""Regression tests for the corrected candidate inventory and learner integration."""

from __future__ import annotations

import unittest

from . import candidates, learners


class CandidateTests(unittest.TestCase):
    def test_initialism_candidates_have_edit_embeddings(self) -> None:
        for lemma in ("API", "UFO", "SMS"):
            for candidate in candidates.candidates(lemma, lemma + "S"):
                self.assertIn(candidate.edit_id, candidates.EDIT_INDEX)

    def test_us_to_i_keeps_the_stem(self) -> None:
        forms = {candidate.form for candidate in candidates.candidates("cactus", "cactuss")}
        self.assertIn("cacti", forms)
        self.assertNotIn("cactui", forms)

    def test_base_identity_is_kept_separate_from_same_rule(self) -> None:
        pairs = candidates.candidates("cat", "cats")
        self.assertTrue(pairs[0].is_base)
        self.assertEqual(pairs[0].form, "cats")
        self.assertTrue(any(candidate.edit_id == "s" and candidate.form == "cats" for candidate in pairs))

    def test_city_base_edit_is_classified(self) -> None:
        self.assertEqual(candidates.base_edit_id("city", "cities"), "base:ies")

    def test_man_edit_requires_the_man_suffix(self) -> None:
        forms = {candidate.form for candidate in candidates.candidates("workman", "workmans")}
        self.assertIn("workmen", forms)
        self.assertNotIn("workmen", {candidate.form for candidate in candidates.candidates("human", "humans")})

    def test_linear_predict_all_accepts_source(self) -> None:
        model = learners.LinearRanker()
        result = model.predict_all("cat", "cats", "lexicon:rule")
        self.assertTrue(result)


if __name__ == "__main__":
    unittest.main()
