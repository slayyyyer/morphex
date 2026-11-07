"""Fixture tests for the offline extractor. Stdlib only: `py -3 -m unittest tools.research.test_extract`."""

import unittest

from tools.research import extract


def noun(word="cat", expansion="cat (countable and uncountable, plural cats)", forms=None, args=None, **extra):
    obj = {
        "pos": "noun",
        "word": word,
        "head_templates": [{"expansion": expansion, "args": args or {}}],
        "forms": forms if forms is not None else [{"form": word + "s", "tags": ["plural"]}],
        "senses": [{"id": word + "-noun"}],
    }
    obj.update(extra)
    return obj


class NormalizeTest(unittest.TestCase):
    def test_plural_only_directive_is_not_a_form(self):
        record, reason = extract.normalize(noun(
            "scissors", "scissors pl (plural only)", forms=[{"form": "scissors", "tags": ["plural"]}],
            args={"1": "p"}))
        self.assertEqual("", reason)
        self.assertEqual("PLURAL_ONLY", record.countability)
        self.assertEqual(["scissors"], record.forms)

    def test_double_plus_is_quarantined_not_read_as_a_plural(self):
        record, reason = extract.normalize(noun(
            "quiz", "quiz (countable and uncountable, plural quizzes)",
            forms=[{"form": "++", "tags": ["plural"]},
                   {"form": "quizzes", "tags": ["plural"]}]))
        self.assertEqual("", reason)
        self.assertEqual(["quizzes"], record.forms)
        self.assertIn("++", [surface for surface, _ in record.restricted])

    def test_identity_form_is_invariant(self):
        record, _ = extract.normalize(noun(
            "sheep", "sheep (countable and uncountable, plural sheep)",
            forms=[{"form": "sheep", "tags": ["plural"]}]))
        self.assertTrue(record.invariant)
        self.assertEqual(["sheep"], record.forms)

    def test_mass_without_attested_plural_is_kept_as_lemma(self):
        record, reason = extract.normalize(noun(
            "information", "information (uncountable)", forms=[]))
        self.assertEqual("", reason)
        self.assertEqual("MASS", record.countability)
        self.assertEqual(["information"], record.forms)

    def test_mixed_use_is_classified_and_pluralized(self):
        record, _ = extract.normalize(noun(
            "water", "water (countable and uncountable, plural waters)",
            forms=[{"form": "waters", "tags": ["plural"]}]))
        self.assertEqual("MIXED", record.countability)
        self.assertEqual(["waters"], record.forms)

    def test_restricted_qualifier_is_recorded_separately(self):
        record, _ = extract.normalize(noun(
            "cactus", "cactus (countable and uncountable, plural cactuses or cacti)",
            forms=[{"form": "cactuses", "tags": ["plural"]},
                   {"form": "cacti", "tags": ["plural", "archaic"]}]))
        self.assertEqual(["cactuses"], record.forms)
        self.assertIn("cacti", [surface for surface, tags in record.restricted])
        self.assertTrue(any("archaic" in tags for _, tags in record.restricted))

    def test_template_junk_never_becomes_a_form(self):
        record, reason = extract.normalize(noun(
            "cat", "cat (countable and uncountable, plural cats)",
            forms=[{"form": "{{l|en|cats}}", "tags": ["plural"]}]))
        self.assertEqual("no-attested-plural", reason)
        self.assertIsNone(record)

    def test_uppercase_and_non_ascii_lemmas(self):
        self.assertEqual("", extract.normalize(noun("UFO", forms=[{"form": "UFOs", "tags": ["plural"]}]))[1])
        self.assertEqual("", extract.normalize(noun("café", forms=[{"form": "cafés", "tags": ["plural"]}]))[1])
        self.assertEqual("lemma-surface", extract.normalize(noun("日本"))[1])
        self.assertEqual("lemma-surface", extract.normalize(noun("user_id"))[1])

    def test_non_nouns_are_skipped(self):
        obj = noun()
        obj["pos"] = "verb"
        self.assertEqual("not-noun", extract.normalize(obj)[1])

    def test_countability_classifier(self):
        self.assertEqual("MASS", extract.classify_countability("water (uncountable)", []))
        self.assertEqual("MIXED", extract.classify_countability("water (countable and uncountable)", []))
        self.assertEqual("COUNTABLE", extract.classify_countability("cat (countable)", []))
        self.assertEqual("MASS", extract.classify_countability("", [["uncountable"]]))
        self.assertEqual("COUNTABLE", extract.classify_countability("", []))


if __name__ == "__main__":
    unittest.main()
