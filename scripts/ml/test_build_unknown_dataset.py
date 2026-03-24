import unittest

from build_ip102_unknown_dataset import compute_split_counts


class ComputeSplitCountsTest(unittest.TestCase):
    def test_uses_70_15_15_for_1500_unknown_samples(self):
        self.assertEqual((1050, 225, 225), compute_split_counts(1500))


if __name__ == "__main__":
    unittest.main()
