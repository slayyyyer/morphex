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

    def test_subordinate_uncountable_sense_does_not_outweigh_attested_plural(self):
        # "account (plural accounts)": the archaic uncountable sense must not make a
        # lemma with an attested plural mass. Without an attested plural it stands.
        record, reason = extract.normalize(noun(
            "account", "account (plural accounts)",
            forms=[{"form": "accounts", "tags": ["plural"]}],
            senses=[{"id": "account-noun-1"},
                    {"id": "account-noun-2", "tags": ["archaic", "uncountable"]}]))
        self.assertEqual("", reason)
        self.assertEqual("COUNTABLE", record.countability)
        self.assertEqual(["accounts"], record.forms)

    def test_usually_uncountable_with_attested_plural_is_mixed(self):
        # "practice (usually uncountable, plural practices)": the hedge admits
        # countable use, and the attested plural proves it.
        record, reason = extract.normalize(noun(
            "practice", "practice (usually uncountable, plural practices)",
            forms=[{"form": "practices", "tags": ["plural"]}],
            senses=[{"id": "practice-noun-1", "tags": ["uncountable", "usually"]}]))
        self.assertEqual("", reason)
        self.assertEqual("MIXED", record.countability)
        self.assertEqual(["practices"], record.forms)

    def test_usually_uncountable_without_plural_stays_mass(self):
        record, reason = extract.normalize(noun(
            "furnituremaking", "furnituremaking (usually uncountable)", forms=[]))
        self.assertEqual("", reason)
        self.assertEqual("MASS", record.countability)
        self.assertEqual(["furnituremaking"], record.forms)

    def test_reconcile_prefers_any_licensed_plural(self):
        # plate: one plural-only etymology, others with an attested plural.
        self.assertEqual(
            "MIXED",
            extract.reconcile_countability(
                ["PLURAL_ONLY", "MIXED", "COUNTABLE"], ["plates"], "plate", False))
        # pants/scissors: plural-only votes but no non-lemma plural anywhere.
        self.assertEqual(
            "PLURAL_ONLY",
            extract.reconcile_countability(
                ["PLURAL_ONLY", "COUNTABLE"], ["pants"], "pants", False))
        self.assertEqual(
            "PLURAL_ONLY",
            extract.reconcile_countability(["PLURAL_ONLY"], ["scissors"], "scissors", True))
        # Single entries are unchanged.
        self.assertEqual(
            "MASS", extract.reconcile_countability(["MASS"], ["robot"], "robot", False))
        self.assertEqual(
            "MIXED", extract.reconcile_countability(["MIXED"], ["water", "waters"], "water", False))

    def test_subordinate_plural_only_sense_does_not_mark_lemma(self):
        # accent: ordinary countable senses plus accents, while one subordinate
        # "expressions in general" sense is tagged plural-only.
        record, reason = extract.normalize(noun(
            "accent", "accent (countable and uncountable, plural accents)",
            forms=[{"form": "accents", "tags": ["plural"]}],
            senses=[{"id": "accent-noun-1", "tags": ["countable", "uncountable"]},
                    {"id": "accent-noun-2", "tags": ["countable", "plural", "plural-only"]}]))
        self.assertEqual("", reason)
        self.assertEqual("MIXED", record.countability)
        self.assertFalse(record.plural_only)
        self.assertFalse(record.invariant)
        self.assertEqual(["accents"], record.forms)

    def test_work_like_subordinate_plural_only_sense_keeps_works(self):
        record, reason = extract.normalize(noun(
            "work", "work (countable and uncountable, plural works)",
            forms=[{"form": "works", "tags": ["plural"]}],
            senses=[{"id": "work-noun-1", "tags": ["countable", "uncountable"]},
                    {"id": "work-noun-2", "tags": ["plural-only", "slang"]}]))
        self.assertEqual("", reason)
        self.assertEqual("MIXED", record.countability)
        self.assertEqual(["works"], record.forms)

    def test_secondary_head_template_plural_only_is_recognized(self):
        # times: the first template is a bare noun-form head; the reviewed
        # plural-only template is second. Both must be inspected.
        obj = noun("times", "times", forms=[], senses=[{"id": "times-noun-1"}])
        obj["head_templates"] = [
            {"expansion": "times (en noun form)", "args": {"1": "en", "2": "noun form"}},
            {"expansion": "times pl (plural only)", "args": {"1": "p"}},
        ]
        record, reason = extract.normalize(obj)
        self.assertEqual("", reason)
        self.assertEqual("PLURAL_ONLY", record.countability)
        self.assertEqual(["times"], record.forms)

    def test_sense_plural_only_without_attested_plural_is_kept(self):
        # contents: a plural-form headword whose plural-only use has no separate
        # plural; removing it would make the engine invent "contentses".
        record, reason = extract.normalize(noun(
            "contents", "contents", forms=[],
            senses=[{"id": "contents-noun-1", "tags": ["plural-normally"]},
                    {"id": "contents-noun-2", "tags": ["plural", "plural-only"]}]))
        self.assertEqual("", reason)
        self.assertEqual("PLURAL_ONLY", record.countability)
        self.assertEqual(["contents"], record.forms)

    def test_every_sense_plural_only_is_whole_lemma_evidence(self):
        record, reason = extract.normalize(noun(
            "trousers", "trousers", forms=[],
            senses=[{"id": "trousers-noun-1", "tags": ["plural-only"]},
                    {"id": "trousers-noun-2", "tags": ["plural-only"]}]))
        self.assertEqual("", reason)
        self.assertEqual("PLURAL_ONLY", record.countability)
        self.assertEqual(["trousers"], record.forms)

    def test_plural_normally_sense_is_not_lemma_plural_only(self):
        # "book"/"bag": a sense normally used in the plural does not make the
        # whole lemma plural-only when a regular plural is attested.
        record, reason = extract.normalize(noun(
            "book", "book (plural books)",
            forms=[{"form": "books", "tags": ["plural"]}],
            senses=[{"id": "book-noun-1"},
                    {"id": "book-noun-2", "tags": ["slang", "plural-normally"]}]))
        self.assertEqual("", reason)
        self.assertEqual("COUNTABLE", record.countability)
        self.assertFalse(record.plural_only)
        self.assertEqual(["books"], record.forms)


if __name__ == "__main__":
    unittest.main()
