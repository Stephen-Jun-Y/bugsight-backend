#!/usr/bin/env python3
"""Validate balanced dataset constraints and print reproducibility hash."""

from __future__ import annotations

import argparse
import csv
import hashlib
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List, Tuple

DEFAULT_DATASET_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500"
EXPECTED_SPLIT = {"train": 350, "val": 75, "test": 75}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify balanced IP102 dataset integrity.")
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET_DIR)
    parser.add_argument("--check-files", action="store_true", help="Validate image files exist under dataset/images.")
    parser.add_argument(
        "--expected-hash",
        type=str,
        default="",
        help="Optional sha256 to compare against split_70_15_15.csv hash.",
    )
    return parser.parse_args()


def sha256_of_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    args = parse_args()

    labels_path = args.dataset_dir / "labels.csv"
    split_path = args.dataset_dir / "split_70_15_15.csv"
    if not labels_path.exists() or not split_path.exists():
        print(f"[ERROR] Missing labels/split CSV in {args.dataset_dir}")
        return 1

    labels: Dict[str, Tuple[str, int]] = {}
    class_counts = Counter()

    with labels_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            image_id = row["image_id"]
            rel_path = row["relative_path"]
            class_id = int(row["class_id"])
            if image_id in labels:
                print(f"[ERROR] Duplicate image_id in labels.csv: {image_id}")
                return 1
            labels[image_id] = (rel_path, class_id)
            class_counts[class_id] += 1

    if len(class_counts) != 25:
        print(f"[ERROR] Expected 25 classes, got {len(class_counts)}")
        return 1

    for class_id, count in sorted(class_counts.items()):
        if count != 500:
            print(f"[ERROR] class {class_id} has {count} images, expected 500")
            return 1

    seen_in_split = set()
    split_by_class: Dict[int, Counter] = defaultdict(Counter)

    with split_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            image_id = row["image_id"]
            class_id = int(row["class_id"])
            split = row["split"]

            if image_id in seen_in_split:
                print(f"[ERROR] Duplicate image_id in split file: {image_id}")
                return 1
            seen_in_split.add(image_id)

            if image_id not in labels:
                print(f"[ERROR] split has unknown image_id: {image_id}")
                return 1
            if labels[image_id][1] != class_id:
                print(f"[ERROR] class mismatch for {image_id}: labels={labels[image_id][1]}, split={class_id}")
                return 1
            if split not in EXPECTED_SPLIT:
                print(f"[ERROR] unexpected split value: {split}")
                return 1

            split_by_class[class_id][split] += 1

    if set(labels.keys()) != seen_in_split:
        missing = set(labels.keys()) - seen_in_split
        extra = seen_in_split - set(labels.keys())
        print(f"[ERROR] labels/split mismatch, missing={len(missing)}, extra={len(extra)}")
        return 1

    for class_id, counter in sorted(split_by_class.items()):
        for split_name, expected in EXPECTED_SPLIT.items():
            got = counter.get(split_name, 0)
            if got != expected:
                print(f"[ERROR] class {class_id} split {split_name}={got}, expected {expected}")
                return 1

    if args.check_files:
        missing_files: List[str] = []
        for image_id, (rel_path, _) in labels.items():
            full_path = args.dataset_dir / rel_path
            if not full_path.exists():
                missing_files.append(image_id)
                if len(missing_files) >= 10:
                    break
        if missing_files:
            print(f"[ERROR] Missing extracted image files, sample={missing_files}")
            return 1

    split_hash = sha256_of_file(split_path)
    if args.expected_hash and split_hash != args.expected_hash:
        print(f"[ERROR] split hash mismatch: expected={args.expected_hash}, got={split_hash}")
        return 1

    print("[OK] Dataset verification passed")
    print(f"[OK] class_count={len(class_counts)}, total_images={len(labels)}")
    print(f"[OK] split_sha256={split_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
