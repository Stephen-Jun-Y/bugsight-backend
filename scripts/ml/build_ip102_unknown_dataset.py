#!/usr/bin/env python3
"""Build a 25-class IP102 dataset plus an unknown/non-insect class."""

from __future__ import annotations

import argparse
import csv
import os
import random
import shutil
from collections import Counter
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

DEFAULT_BASE_DATASET_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500"
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500_unknown"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Merge IP102 balanced dataset with non-insect negatives.")
    parser.add_argument("--base-dataset-dir", type=Path, default=DEFAULT_BASE_DATASET_DIR)
    parser.add_argument("--negative-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--unknown-class-id", type=int, default=-1)
    parser.add_argument("--unknown-class-name", default="unknown")
    parser.add_argument("--unknown-count", type=int, default=1500)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--copy-files", action="store_true", help="Copy images instead of creating symlinks.")
    return parser.parse_args()


def compute_split_counts(total: int) -> Tuple[int, int, int]:
    train = int(total * 0.7)
    val = int(total * 0.15)
    test = total - train - val
    return train, val, test


def list_images(root: Path) -> List[Path]:
    return sorted(
        path for path in root.rglob("*")
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES
    )


def read_labels(labels_csv_path: Path) -> Dict[str, Dict[str, str]]:
    with labels_csv_path.open("r", encoding="utf-8") as f:
        return {row["image_id"]: row for row in csv.DictReader(f)}


def read_splits(split_csv_path: Path) -> Dict[str, str]:
    with split_csv_path.open("r", encoding="utf-8") as f:
        return {row["image_id"]: row["split"] for row in csv.DictReader(f)}


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def materialize_file(src: Path, dst: Path, copy_files: bool) -> None:
    ensure_parent(dst)
    if dst.exists() or dst.is_symlink():
        return
    if copy_files:
        shutil.copy2(src, dst)
        return
    os.symlink(src, dst)


def write_csv(path: Path, fieldnames: Sequence[str], rows: Iterable[Dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def main() -> int:
    args = parse_args()
    labels_path = args.base_dataset_dir / "labels.csv"
    split_path = args.base_dataset_dir / "split_70_15_15.csv"

    if not labels_path.exists() or not split_path.exists():
        raise FileNotFoundError("Base dataset is missing labels.csv or split_70_15_15.csv")
    if not args.negative_dir.exists():
        raise FileNotFoundError(f"Negative directory not found: {args.negative_dir}")

    labels = read_labels(labels_path)
    split_map = read_splits(split_path)
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    label_rows: List[Dict[str, object]] = []
    split_rows: List[Dict[str, object]] = []

    for image_id, row in labels.items():
        relative_path = row["relative_path"]
        source_path = args.base_dataset_dir / relative_path
        target_path = output_dir / relative_path
        materialize_file(source_path, target_path, args.copy_files)

        label_rows.append(
            {
                "image_id": image_id,
                "relative_path": relative_path,
                "class_id": int(row["class_id"]),
                "class_name": row["class_name"],
            }
        )
        split_rows.append(
            {
                "image_id": image_id,
                "class_id": int(row["class_id"]),
                "split": split_map[image_id],
            }
        )

    negative_images = list_images(args.negative_dir)
    if len(negative_images) < args.unknown_count:
        raise ValueError(
            f"Negative images are insufficient: need {args.unknown_count}, got {len(negative_images)}"
        )

    rng = random.Random(args.seed)
    rng.shuffle(negative_images)
    selected_negatives = negative_images[: args.unknown_count]
    train_count, val_count, test_count = compute_split_counts(len(selected_negatives))

    for index, src_path in enumerate(selected_negatives):
        split = "train" if index < train_count else "val" if index < train_count + val_count else "test"
        image_id = f"unknown_{index:05d}"
        relative_path = f"images/{image_id}{src_path.suffix.lower()}"
        target_path = output_dir / relative_path
        materialize_file(src_path, target_path, args.copy_files)

        label_rows.append(
            {
                "image_id": image_id,
                "relative_path": relative_path,
                "class_id": args.unknown_class_id,
                "class_name": args.unknown_class_name,
            }
        )
        split_rows.append(
            {
                "image_id": image_id,
                "class_id": args.unknown_class_id,
                "split": split,
            }
        )

    write_csv(
        output_dir / "labels.csv",
        fieldnames=["image_id", "relative_path", "class_id", "class_name"],
        rows=label_rows,
    )
    write_csv(
        output_dir / "split_70_15_15.csv",
        fieldnames=["image_id", "class_id", "split"],
        rows=split_rows,
    )

    distribution = Counter((int(row["class_id"]), str(row["class_name"])) for row in label_rows)
    write_csv(
        output_dir / "class_distribution.csv",
        fieldnames=["class_id", "class_name", "count"],
        rows=[
            {"class_id": class_id, "class_name": class_name, "count": count}
            for (class_id, class_name), count in sorted(distribution.items(), key=lambda item: item[0][0])
        ],
    )

    print(f"[OK] Built open-set dataset at {output_dir}")
    print(f"[OK] Unknown samples: {len(selected_negatives)} ({train_count}/{val_count}/{test_count})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
