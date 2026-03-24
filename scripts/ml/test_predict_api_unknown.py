import json
import tempfile
import unittest
from pathlib import Path

from predict_api import (
    DEFAULT_MARGIN_THRESHOLD,
    DEFAULT_REJECT_THRESHOLD,
    DEFAULT_UNKNOWN_CLASS_ID,
    Predictor,
    resolve_prediction,
)


class ResolvePredictionTest(unittest.TestCase):
    def test_marks_unknown_when_unknown_class_wins(self):
        payload = resolve_prediction(
            probabilities=[0.75, 0.20, 0.05],
            class_ids=[-1, 69, 70],
            unknown_class_id=-1,
            reject_threshold=0.55,
            margin_threshold=0.12,
        )

        self.assertTrue(payload["isUnknown"])
        self.assertIsNone(payload["top1"])
        self.assertEqual(69, payload["top3"][0]["classIndex"])

    def test_marks_unknown_when_confidence_or_margin_is_too_low(self):
        low_conf = resolve_prediction(
            probabilities=[0.40, 0.35, 0.25],
            class_ids=[69, 70, 101],
            unknown_class_id=-1,
            reject_threshold=0.55,
            margin_threshold=0.12,
        )
        low_margin = resolve_prediction(
            probabilities=[0.58, 0.51, 0.11],
            class_ids=[69, 70, 101],
            unknown_class_id=-1,
            reject_threshold=0.55,
            margin_threshold=0.12,
        )

        self.assertTrue(low_conf["isUnknown"])
        self.assertIsNone(low_conf["top1"])
        self.assertTrue(low_margin["isUnknown"])
        self.assertIsNone(low_margin["top1"])

    def test_returns_known_species_when_thresholds_pass(self):
        payload = resolve_prediction(
            probabilities=[0.81, 0.10, 0.09],
            class_ids=[69, 70, 101],
            unknown_class_id=-1,
            reject_threshold=0.55,
            margin_threshold=0.12,
        )

        self.assertFalse(payload["isUnknown"])
        self.assertEqual(69, payload["top1"]["classIndex"])
        self.assertEqual(3, len(payload["top3"]))

    def test_load_labels_accepts_null_unknown_metadata(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            labels_path = Path(tmpdir) / "labels.json"
            labels_path.write_text(
                json.dumps(
                    {
                        "model_index_to_class_id": [0, 3, 69],
                        "unknown_class_id": None,
                        "reject_threshold": None,
                        "margin_threshold": None,
                    }
                ),
                encoding="utf-8",
            )

            payload = Predictor._load_labels(labels_path)

        self.assertEqual([0, 3, 69], payload["class_ids"])
        self.assertEqual(DEFAULT_UNKNOWN_CLASS_ID, payload["unknown_class_id"])
        self.assertEqual(DEFAULT_REJECT_THRESHOLD, payload["reject_threshold"])
        self.assertEqual(DEFAULT_MARGIN_THRESHOLD, payload["margin_threshold"])


if __name__ == "__main__":
    unittest.main()
