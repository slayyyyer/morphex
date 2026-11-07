"""Unit tests for artifact compaction and the corrected evaluation contract."""

from __future__ import annotations

import unittest

from . import candidates, evaluate, lexicon


class ArtifactCompactionTest(unittest.TestCase):
    def test_regular_word_is_rule_reproducible(self):
        self.assertTrue(lexicon.is_rule_reproducible("cat", ["cats"]))
        self.assertTrue(lexicon.is_rule_reproducible("city", ["cities"]))

    def test_backoff_changes_compaction_for_sis_words(self):
        self.assertEqual("heterotrichoses", lexicon.productive_form("heterotrichosis"))
        self.assertFalse(lexicon.is_rule_reproducible("heterotrichosis", ["heterotrichosises"]))
        self.assertFalse(lexicon.is_rule_reproducible("phalaenopsis", ["phalaenopsises"]))
        self.assertFalse(lexicon.is_rule_reproducible("cassis", ["cassises"]))

    def test_woman_backoff_is_mirrored(self):
        self.assertEqual("women", lexicon.productive_form("woman"))
        self.assertEqual("aircraftwomen", lexicon.productive_form("aircraftwoman"))

    def test_proper_name_does_not_use_the_lowercase_man_backoff(self):
        self.assertEqual("Cartmans", lexicon.productive_form("Cartman"))
        preferred, _, alternatives = lexicon.derive_preferences(
            "Cartman", ["Cartman", "cartmen", "Cartmans"])
        self.assertEqual("Cartmans", preferred)
        self.assertEqual(["Cartman", "cartmen"], alternatives)

    def test_hyphenated_and_uppercase_entries_are_never_omitted(self):
        self.assertFalse(lexicon.is_rule_reproducible("credit-card", ["credit-cards"]))
        self.assertFalse(lexicon.is_rule_reproducible("CAT", ["CATS"]))


class PreferenceSelectionTest(unittest.TestCase):
    def test_attested_regular_plural_beats_identity(self):
        for lemma in ("robot", "plate", "way", "craft", "prawn", "wonton"):
            preferred, classical, alternatives = lexicon.derive_preferences(
                lemma, [lemma, lemma + "s"])
            self.assertEqual(lemma + "s", preferred)
            self.assertIsNone(classical)
            self.assertEqual([lemma], alternatives)

    def test_selection_is_independent_of_source_order(self):
        variants = [
            ["plate", "plates"],
            ["plates", "plate"],
        ]
        results = [lexicon.derive_preferences("plate", forms) for forms in variants]
        self.assertEqual(results[0], results[1])
        self.assertEqual("plates", results[0][0])

    def test_classical_form_is_not_the_default_and_stays_an_alternative(self):
        forms = ["cactus", "cactuses", "cacti"]
        for permutation in (forms, list(reversed(forms))):
            preferred, classical, alternatives = lexicon.derive_preferences("cactus", permutation)
            self.assertEqual("cactuses", preferred)
            self.assertEqual("cacti", classical)
            self.assertEqual(["cactus"], alternatives)

    def test_irregular_overt_plural_is_kept_when_no_productive_form_is_attested(self):
        preferred, classical, alternatives = lexicon.derive_preferences("child", ["child", "children"])
        self.assertEqual("children", preferred)
        self.assertIsNone(classical)
        self.assertEqual(["child"], alternatives)

    def test_lowercase_lemma_does_not_select_a_capitalized_source_variant(self):
        preferred, classical, alternatives = lexicon.derive_preferences("bomb", ["Bombs", "bombs"])
        self.assertEqual("bombs", preferred)
        self.assertIsNone(classical)
        self.assertEqual(["Bombs"], alternatives)

    def test_uppercase_identity_beats_a_lowercase_source_variant(self):
        for lemma, forms in (("FOSS", ["FOSS", "fosses"]), ("GO", ["GO", "goes"]),
                             ("MLEM", ["MLEM", "mlems"]), ("ROSC", ["ROSC", "roscada"])):
            preferred, classical, alternatives = lexicon.derive_preferences(lemma, forms)
            self.assertEqual(lemma, preferred)
            self.assertIsNone(classical)
            self.assertEqual([forms[1]], alternatives)

    def test_uppercase_case_preserving_initialism_suffix_wins(self):
        preferred, classical, alternatives = lexicon.derive_preferences("LES", ["LESs", "leses"])
        self.assertEqual("LESs", preferred)
        self.assertIsNone(classical)
        self.assertEqual(["leses"], alternatives)


class EvaluationContractTest(unittest.TestCase):
    def _record(self, record_id, *, countability="COUNTABLE", invariant=False, accepted=("cats",)):
        return {
            "id": record_id,
            "lemma": record_id,
            "family": record_id,
            "bucket": "simple",
            "countability": countability,
            "invariant": invariant,
            "accepted": tuple(accepted),
            "accepted_set": frozenset(accepted),
        }

    def test_primary_selector_excludes_invariants(self):
        self.assertTrue(evaluate.is_primary(self._record("cat")))
        self.assertFalse(evaluate.is_primary(self._record("sheep", invariant=True)))
        self.assertFalse(evaluate.is_primary(self._record("water", countability="MASS")))

    def test_relative_error_reduction_uses_baseline_errors(self):
        gold = {
            "a": self._record("a"),
            "b": self._record("b"),
            "c": self._record("c"),
            "d": self._record("d"),
        }
        baseline = {"a": "wrong", "b": "wrong", "c": "cats", "d": "cats"}
        candidate = {"a": "cats", "b": "wrong", "c": "cats", "d": "cats"}
        comparison = evaluate.compare(gold, baseline, candidate, iterations=10)
        self.assertEqual(2, comparison["baseline_errors"])
        self.assertEqual(1, comparison["candidate_errors"])
        self.assertEqual(0.5, comparison["relative_error_reduction"])
        self.assertEqual(1, comparison["corrections"])
        self.assertEqual(0, comparison["regressions"])


class CandidateInventoryTest(unittest.TestCase):
    def test_us_to_i_replaces_the_whole_suffix(self):
        forms = dict(candidates.candidates("cactus", "cactuses"))
        self.assertEqual("cacti", forms["us->i"])

    def test_city_base_edit_is_classified(self):
        self.assertEqual("base:ies", candidates.base_edit_id("city", "cities"))

    def test_base_identity_is_kept_separate_from_rule_identity(self):
        entries = candidates.candidates("cat", "cats")
        self.assertEqual("base:s", entries[0].edit_id)
        self.assertTrue(entries[0].is_base)
        self.assertIn("s", [entry.edit_id for entry in entries])


if __name__ == "__main__":
    unittest.main()
