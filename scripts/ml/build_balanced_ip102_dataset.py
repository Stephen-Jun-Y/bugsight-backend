#!/usr/bin/env python3
"""Build a balanced IP102 dataset for ResNet50 training.

Outputs:
- labels.csv: image_id,relative_path,class_id,class_name
- split_70_15_15.csv: image_id,class_id,split
- class_distribution.csv: class_id,class_name,raw_total,selected_total,train,val,test
"""

from __future__ import annotations

import argparse
import csv
import random
import re
import sys
import tarfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Set, Tuple

DEFAULT_TAR = Path(__file__).resolve().parents[1] / "ip102_v1.1-001.tar"
DEFAULT_CLASSES_ZIP = Path(__file__).resolve().parents[1] / "Classification-20251201T040049Z-1-002.zip"
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500"

SPLIT_FILES = ("train", "val", "test")
CLASSES_TXT_PATH = "Classification/classes.txt"
IP102_IMAGE_PREFIX = "ip102_v1.1/images/"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build balanced IP102 dataset (25 classes x 500 images).")
    parser.add_argument("--ip102-tar", type=Path, default=DEFAULT_TAR, help="Path to ip102 tar file.")
    parser.add_argument("--classes-zip", type=Path, default=DEFAULT_CLASSES_ZIP, help="Path to classes zip file.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR, help="Output directory.")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducibility.")
    parser.add_argument("--num-classes", type=int, default=25, help="Number of classes to keep.")
    parser.add_argument("--samples-per-class", type=int, default=500, help="Samples per selected class.")
    parser.add_argument("--train-ratio", type=float, default=0.70, help="Train ratio per class.")
    parser.add_argument("--val-ratio", type=float, default=0.15, help="Validation ratio per class.")
    parser.add_argument("--test-ratio", type=float, default=0.15, help="Test ratio per class.")
    parser.add_argument(
        "--class-ids",
        type=str,
        default="",
        help="Optional comma separated class ids to override auto top-N selection (example: 0,3,24).",
    )
    parser.add_argument("--skip-extract", action="store_true", help="Do not extract selected images to output/images.")
    parser.add_argument("--dry-run", action="store_true", help="Only print summary, do not write files.")
    return parser.parse_args()


def read_split_entries(tar_path: Path) -> Dict[int, List[str]]:
    class_to_images: Dict[int, List[str]] = defaultdict(list)
    with tarfile.open(tar_path, "r") as tar:
        for split in SPLIT_FILES:
            member_name = f"ip102_v1.1/{split}.txt"
            member = tar.getmember(member_name)
            file_obj = tar.extractfile(member)
            if file_obj is None:
                raise RuntimeError(f"Unable to read {member_name} from {tar_path}")

            for raw_line in file_obj.read().decode("utf-8", errors="ignore").splitlines():
                line = raw_line.strip()
                if not line:
                    continue
                parts = line.split()
                if len(parts) < 2:
                    continue
                image_id = parts[0]
                class_id = int(parts[1])
                class_to_images[class_id].append(image_id)

    for class_id in list(class_to_images.keys()):
        # Ensure deterministic order and remove accidental duplicates.
        class_to_images[class_id] = sorted(set(class_to_images[class_id]))

    return class_to_images


def read_class_names(classes_zip_path: Path) -> Dict[int, str]:
    class_names: Dict[int, str] = {}
    with zipfile.ZipFile(classes_zip_path, "r") as zip_file:
        content = zip_file.read(CLASSES_TXT_PATH).decode("utf-8", errors="ignore")

    for raw_line in content.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = re.match(r"^(\d+)\s+(.*)$", line)
        if not match:
            continue
        class_index_1based = int(match.group(1))
        class_name = match.group(2).strip()
        class_names[class_index_1based - 1] = class_name

    return class_names


def parse_class_override(raw: str) -> List[int]:
    if not raw.strip():
        return []
    result: List[int] = []
    for item in raw.split(","):
        stripped = item.strip()
        if not stripped:
            continue
        result.append(int(stripped))
    return result


def select_classes(
    class_to_images: Dict[int, List[str]],
    num_classes: int,
    samples_per_class: int,
    forced_class_ids: Sequence[int],
) -> List[int]:
    class_counts = {class_id: len(images) for class_id, images in class_to_images.items()}

    if forced_class_ids:
        selected = sorted(set(forced_class_ids))
        if len(selected) != num_classes:
            raise ValueError(f"--class-ids count must equal --num-classes ({num_classes}), got {len(selected)}")
        for class_id in selected:
            count = class_counts.get(class_id, 0)
            if count < samples_per_class:
                raise ValueError(
                    f"class {class_id} only has {count} images, less than required {samples_per_class}"
                )
        return selected

    eligible = [
        (class_id, count)
        for class_id, count in class_counts.items()
        if count >= samples_per_class
    ]
    eligible.sort(key=lambda x: (-x[1], x[0]))

    if len(eligible) < num_classes:
        raise ValueError(
            f"Not enough eligible classes: need {num_classes}, only {len(eligible)} with >= {samples_per_class} images"
        )

    return [class_id for class_id, _ in eligible[:num_classes]]


def sample_images_per_class(
    class_to_images: Dict[int, List[str]],
    selected_classes: Sequence[int],
    samples_per_class: int,
    seed: int,
) -> Dict[int, List[str]]:
    rng = random.Random(seed)
    sampled: Dict[int, List[str]] = {}
    for class_id in selected_classes:
        population = class_to_images[class_id]
        chosen = rng.sample(population, k=samples_per_class)
        sampled[class_id] = sorted(chosen)
    return sampled


def stratified_split(
    sampled: Dict[int, List[str]],
    train_count: int,
    val_count: int,
    test_count: int,
    seed: int,
) -> Dict[int, Dict[str, List[str]]]:
    result: Dict[int, Dict[str, List[str]]] = {}

    for class_id, image_ids in sampled.items():
        local_rng = random.Random(seed + class_id)
        shuffled = list(image_ids)
        local_rng.shuffle(shuffled)

        train_ids = sorted(shuffled[:train_count])
        val_ids = sorted(shuffled[train_count : train_count + val_count])
        test_ids = sorted(shuffled[train_count + val_count : train_count + val_count + test_count])

        if not (len(train_ids) == train_count and len(val_ids) == val_count and len(test_ids) == test_count):
            raise RuntimeError(f"Split failed for class {class_id}")

        result[class_id] = {
            "train": train_ids,
            "val": val_ids,
            "test": test_ids,
        }

    return result


def extract_images(tar_path: Path, image_ids: Iterable[str], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    image_ids_set: Set[str] = set(image_ids)

    with tarfile.open(tar_path, "r") as tar:
        for image_id in sorted(image_ids_set):
            member_name = f"{IP102_IMAGE_PREFIX}{image_id}"
            try:
                member = tar.getmember(member_name)
            except KeyError as exc:
                raise FileNotFoundError(f"Image not found in tar: {member_name}") from exc

            src = tar.extractfile(member)
            if src is None:
                raise RuntimeError(f"Cannot extract image bytes: {member_name}")

            dst_path = output_dir / image_id
            with dst_path.open("wb") as dst:
                dst.write(src.read())


def write_csv(path: Path, header: Sequence[str], rows: Iterable[Sequence[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as file_obj:
        writer = csv.writer(file_obj)
        writer.writerow(header)
        for row in rows:
            writer.writerow(row)


def main() -> int:
    args = parse_args()

    if not args.ip102_tar.exists():
        print(f"[ERROR] Missing tar file: {args.ip102_tar}", file=sys.stderr)
        return 1
    if not args.classes_zip.exists():
        print(f"[ERROR] Missing classes zip: {args.classes_zip}", file=sys.stderr)
        return 1

    ratio_sum = args.train_ratio + args.val_ratio + args.test_ratio
    if abs(ratio_sum - 1.0) > 1e-6:
        print("[ERROR] train/val/test ratio must sum to 1.0", file=sys.stderr)
        return 1

    train_count = int(args.samples_per_class * args.train_ratio)
    val_count = int(args.samples_per_class * args.val_ratio)
    test_count = args.samples_per_class - train_count - val_count

    if (train_count, val_count, test_count) != (350, 75, 75):
        print(
            "[ERROR] Current config does not produce 350/75/75 per class. "
            f"Got {train_count}/{val_count}/{test_count}",
            file=sys.stderr,
        )
        return 1

    class_to_images = read_split_entries(args.ip102_tar)
    class_names = read_class_names(args.classes_zip)

    forced_class_ids = parse_class_override(args.class_ids)
    selected_classes = select_classes(
        class_to_images=class_to_images,
        num_classes=args.num_classes,
        samples_per_class=args.samples_per_class,
        forced_class_ids=forced_class_ids,
    )

    sampled = sample_images_per_class(
        class_to_images=class_to_images,
        selected_classes=selected_classes,
        samples_per_class=args.samples_per_class,
        seed=args.seed,
    )

    splits = stratified_split(
        sampled=sampled,
        train_count=train_count,
        val_count=val_count,
        test_count=test_count,
        seed=args.seed,
    )

    # Materialize row tables.
    labels_rows: List[Tuple[str, str, int, str]] = []
    split_rows: List[Tuple[str, int, str]] = []
    dist_rows: List[Tuple[int, str, int, int, int, int, int]] = []

    for class_id in selected_classes:
        class_name = class_names.get(class_id, f"class_{class_id}")
        selected_ids = sampled[class_id]
        split_map = splits[class_id]

        selected_set = set(selected_ids)
        if len(selected_set) != args.samples_per_class:
            raise RuntimeError(f"Duplicate samples found in class {class_id}")

        combined = split_map["train"] + split_map["val"] + split_map["test"]
        if set(combined) != selected_set:
            raise RuntimeError(f"Split mismatch in class {class_id}")

        for image_id in selected_ids:
            labels_rows.append((image_id, f"images/{image_id}", class_id, class_name))

        for split_name, image_ids in split_map.items():
            for image_id in image_ids:
                split_rows.append((image_id, class_id, split_name))

        dist_rows.append(
            (
                class_id,
                class_name,
                len(class_to_images[class_id]),
                len(selected_ids),
                len(split_map["train"]),
                len(split_map["val"]),
                len(split_map["test"]),
            )
        )

    labels_rows.sort(key=lambda x: (x[2], x[0]))
    split_rows.sort(key=lambda x: (x[1], x[2], x[0]))
    dist_rows.sort(key=lambda x: x[0])

    total_selected = len(labels_rows)
    class_count = len(selected_classes)

    print("[INFO] Selected classes:", selected_classes)
    print(f"[INFO] Total selected images: {total_selected}")
    print(f"[INFO] Per-class split: train={train_count}, val={val_count}, test={test_count}")

    if args.dry_run:
        print("[INFO] Dry run complete. No files written.")
        return 0

    args.output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(
        args.output_dir / "labels.csv",
        header=("image_id", "relative_path", "class_id", "class_name"),
        rows=labels_rows,
    )
    write_csv(
        args.output_dir / "split_70_15_15.csv",
        header=("image_id", "class_id", "split"),
        rows=split_rows,
    )
    write_csv(
        args.output_dir / "class_distribution.csv",
        header=("class_id", "class_name", "raw_total", "selected_total", "train", "val", "test"),
        rows=dist_rows,
    )

    if not args.skip_extract:
        extract_images(
            tar_path=args.ip102_tar,
            image_ids=(row[0] for row in labels_rows),
            output_dir=args.output_dir / "images",
        )
        print(f"[INFO] Extracted {total_selected} images to {args.output_dir / 'images'}")
    else:
        print("[INFO] Skipped image extraction (--skip-extract).")

    split_counter = Counter(row[2] for row in split_rows)
    print(
        "[OK] Dataset generated: "
        f"{class_count} classes x {args.samples_per_class} images = {total_selected}; "
        f"split totals train/val/test = "
        f"{split_counter['train']}/{split_counter['val']}/{split_counter['test']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
