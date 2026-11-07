"""Tests for external membership labelling and source preparation. Stdlib only."""

import json
import tempfile
import unittest
from pathlib import Path

from tools.research import external_eval


class BootstrapTest(unittest.TestCase):
    def test_binary_bootstrap_endpoints(self):
        self.assertEqual((1.0, 1.0), external_eval.bootstrap([True] * 10))
        self.assertEqual((0.0, 0.0), external_eval.bootstrap([False] * 10))


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


class AgidPrepareTest(unittest.TestCase):
    def test_clean_lowercase_preferred_nouns_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            agid = Path(tmp) / "infl.txt"
            agid.write_text(
                "A N: As\n"
                "cat N: cats\n"
                "dog N: dogs 1\n"
                "box N: boxes, boxen 1\n"
                "deer N: deer\n"
                "mouse N: mice\n"
                "axis N: axes?\n",
                encoding="utf-8",
            )
            external_eval.prepare(None, tmp, source="agid", agid_path=str(agid))

            gold = (Path(tmp) / "agid-gold.tsv").read_text(encoding="utf-8").splitlines()
            self.assertEqual(
                ["lemma\tforms\tcounts", "box\tboxes\t1", "cat\tcats\t1",
                 "mouse\tmice\t1"],
                gold,
            )
            manifest = json.loads((Path(tmp) / "agid-manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("agid", external_eval.source_config("agid")["prefix"])
            self.assertEqual("lexicon", manifest["source_kind"])
            self.assertEqual(7, manifest["noun_entries"])
            self.assertEqual(3, manifest["lemmas"])
            self.assertEqual(3, manifest["forms"])
            self.assertEqual(1, manifest["skipped"]["capitalized_or_initialism"])
            self.assertEqual(1, manifest["skipped"]["nonpreferred_variant"])
            self.assertEqual(1, manifest["skipped"]["identity"])
            self.assertEqual(1, manifest["skipped"]["uncertain_marker"])
            self.assertEqual(64, len(manifest["source_sha256"]))
            self.assertEqual(
                "15d2d792d309d2dc838bf75d8abcd3feb36708c219dc5158d4fff70b89e601d1",
                manifest["archive_sha256"],
            )


if __name__ == "__main__":
    unittest.main()
