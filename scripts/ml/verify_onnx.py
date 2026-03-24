#!/usr/bin/env python3
"""Validate ONNX output shape and class-id mapping contract."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import onnxruntime as ort
from PIL import Image
from predict_api import resolve_prediction

IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 1, 3)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 1, 3)

DEFAULT_DATASET_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500"
DEFAULT_MODEL_DIR = Path(__file__).resolve().parents[1] / "data" / "models" / "resnet50_ip102_balanced"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify ONNX inference contract.")
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET_DIR)
    parser.add_argument("--onnx-model", type=Path, default=DEFAULT_MODEL_DIR / "resnet50_ip102_balanced.onnx")
    parser.add_argument("--labels-json", type=Path, default=DEFAULT_MODEL_DIR / "labels.json")
    return parser.parse_args()


def preprocess(image_path: Path) -> np.ndarray:
    image = Image.open(image_path).convert("RGB")
    width, height = image.size
    if width < height:
        new_width = 256
        new_height = int(round(height * 256 / width))
    else:
        new_height = 256
        new_width = int(round(width * 256 / height))

    image = image.resize((new_width, new_height), Image.BILINEAR)
    left = (new_width - 224) // 2
    top = (new_height - 224) // 2
    image = image.crop((left, top, left + 224, top + 224))

    arr = np.asarray(image, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    arr = np.transpose(arr, (2, 0, 1))
    arr = np.expand_dims(arr, axis=0)
    return arr.astype(np.float32)


def softmax(x: np.ndarray) -> np.ndarray:
    x = x - np.max(x, axis=1, keepdims=True)
    exp = np.exp(x)
    return exp / np.sum(exp, axis=1, keepdims=True)


def first_image_from_labels(dataset_dir: Path) -> Tuple[str, Path]:
    labels_path = dataset_dir / "labels.csv"
    with labels_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        row = next(reader)
    image_id = row["image_id"]
    image_path = dataset_dir / row["relative_path"]
    return image_id, image_path


def main() -> int:
    args = parse_args()
    if not args.onnx_model.exists():
        print(f"[ERROR] ONNX model missing: {args.onnx_model}")
        return 1
    if not args.labels_json.exists():
        print(f"[ERROR] labels.json missing: {args.labels_json}")
        return 1

    labels_payload = json.loads(args.labels_json.read_text(encoding="utf-8"))
    model_index_to_class_id: List[int] = [int(x) for x in labels_payload["model_index_to_class_id"]]

    image_id, image_path = first_image_from_labels(args.dataset_dir)
    if not image_path.exists():
        print(f"[ERROR] image missing: {image_path}")
        return 1

    session = ort.InferenceSession(str(args.onnx_model), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name

    x = preprocess(image_path)
    outputs = session.run(None, {input_name: x})
    if not outputs:
        print("[ERROR] ONNX output is empty")
        return 1

    logits = outputs[0]
    if logits.ndim != 2 or logits.shape[0] != 1:
        print(f"[ERROR] Unexpected logits shape: {logits.shape}")
        return 1

    num_classes = logits.shape[1]
    if num_classes != len(model_index_to_class_id):
        print(
            "[ERROR] labels/model mismatch: "
            f"onnx_classes={num_classes}, labels_classes={len(model_index_to_class_id)}"
        )
        return 1

    output_contract: Dict[str, object] = resolve_prediction(
        probabilities=softmax(logits)[0].tolist(),
        class_ids=model_index_to_class_id,
        unknown_class_id=int(labels_payload.get("unknown_class_id", -1)),
        reject_threshold=float(labels_payload.get("reject_threshold", 0.55)),
        margin_threshold=float(labels_payload.get("margin_threshold", 0.12)),
    )

    print("[OK] ONNX verification passed")
    print(f"[OK] sample_image={image_id}")
    print(json.dumps(output_contract, ensure_ascii=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
