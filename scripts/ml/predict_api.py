#!/usr/bin/env python3
"""FastAPI service for ONNX inference.

Contract:
POST /predict -> {
  "isUnknown": bool,
  "top1": {"classIndex": int, "confidence": float} | null,
  "top3": [...]
}
"""

from __future__ import annotations

import io
import json
import os
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image

IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 1, 3)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 1, 3)

DEFAULT_MODEL_PATH = Path(__file__).resolve().parents[1] / "data" / "models" / "resnet50_ip102_balanced" / "resnet50_ip102_balanced.onnx"
DEFAULT_LABELS_PATH = Path(__file__).resolve().parents[1] / "data" / "models" / "resnet50_ip102_balanced" / "labels.json"
DEFAULT_UNKNOWN_CLASS_ID = -1
DEFAULT_REJECT_THRESHOLD = 0.55
DEFAULT_MARGIN_THRESHOLD = 0.12


def _coerce_optional_number(value: object, caster, default):
    if value is None:
        return default
    return caster(value)


def resolve_prediction(
    probabilities: List[float],
    class_ids: List[int],
    unknown_class_id: int = DEFAULT_UNKNOWN_CLASS_ID,
    reject_threshold: float = DEFAULT_REJECT_THRESHOLD,
    margin_threshold: float = DEFAULT_MARGIN_THRESHOLD,
) -> Dict[str, object]:
    if not probabilities:
        raise ValueError("Empty probabilities")
    if len(probabilities) != len(class_ids):
        raise ValueError("Probabilities and class_ids length mismatch")

    top_indices = np.argsort(-np.asarray(probabilities, dtype=np.float32)).tolist()
    top_candidates = [
        {
            "classIndex": int(class_ids[model_index]),
            "confidence": float(probabilities[model_index]),
        }
        for model_index in top_indices
        if class_ids[model_index] != unknown_class_id
    ][:3]

    best_index = top_indices[0]
    best_class_id = int(class_ids[best_index])
    best_confidence = float(probabilities[best_index])

    next_confidence = 0.0
    for model_index in top_indices[1:]:
        candidate_class_id = int(class_ids[model_index])
        if candidate_class_id != unknown_class_id:
            next_confidence = float(probabilities[model_index])
            break

    is_unknown = (
        best_class_id == unknown_class_id
        or best_confidence < reject_threshold
        or (best_confidence - next_confidence) < margin_threshold
    )

    return {
        "isUnknown": is_unknown,
        "top1": None if is_unknown else {
            "classIndex": best_class_id,
            "confidence": best_confidence,
        },
        "top3": top_candidates,
    }


class Predictor:
    def __init__(self, model_path: Path, labels_path: Path):
        self.model_path = model_path
        self.labels_path = labels_path

        providers = ["CPUExecutionProvider"]
        available = ort.get_available_providers()
        if "CUDAExecutionProvider" in available:
            providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]

        self.session = ort.InferenceSession(str(self.model_path), providers=providers)
        self.input_name = self.session.get_inputs()[0].name

        labels_meta = self._load_labels(self.labels_path)
        self.model_index_to_class_id = labels_meta["class_ids"]
        self.unknown_class_id = labels_meta["unknown_class_id"]
        self.reject_threshold = labels_meta["reject_threshold"]
        self.margin_threshold = labels_meta["margin_threshold"]

    @staticmethod
    def _load_labels(labels_path: Path) -> Dict[str, object]:
        if not labels_path.exists():
            raise FileNotFoundError(f"labels.json not found: {labels_path}")

        payload = json.loads(labels_path.read_text(encoding="utf-8"))
        class_ids = payload.get("model_index_to_class_id")
        if not isinstance(class_ids, list) or not class_ids:
            raise ValueError("labels.json missing model_index_to_class_id list")

        return {
            "class_ids": [int(item) for item in class_ids],
            "unknown_class_id": _coerce_optional_number(
                payload.get("unknown_class_id"),
                int,
                DEFAULT_UNKNOWN_CLASS_ID,
            ),
            "reject_threshold": _coerce_optional_number(
                payload.get("reject_threshold"),
                float,
                DEFAULT_REJECT_THRESHOLD,
            ),
            "margin_threshold": _coerce_optional_number(
                payload.get("margin_threshold"),
                float,
                DEFAULT_MARGIN_THRESHOLD,
            ),
        }

    @staticmethod
    def _preprocess(image_bytes: bytes) -> np.ndarray:
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        except Exception as exc:  # noqa: BLE001
            raise ValueError("Invalid image file") from exc

        # Match eval pipeline: resize short side to 256 then center crop 224.
        width, height = image.size
        if width <= 0 or height <= 0:
            raise ValueError("Invalid image size")
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

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        logits = logits - np.max(logits, axis=1, keepdims=True)
        exp = np.exp(logits)
        return exp / np.sum(exp, axis=1, keepdims=True)

    def predict(self, image_bytes: bytes) -> Dict[str, object]:
        x = self._preprocess(image_bytes)
        outputs = self.session.run(None, {self.input_name: x})
        if not outputs:
            raise RuntimeError("Empty ONNX output")

        logits = outputs[0]
        if logits.ndim != 2 or logits.shape[0] != 1:
            raise RuntimeError(f"Unexpected logits shape: {logits.shape}")

        probs = self._softmax(logits)[0].tolist()
        if len(probs) != len(self.model_index_to_class_id):
            raise RuntimeError("Model output size does not match labels mapping")

        return resolve_prediction(
            probabilities=probs,
            class_ids=self.model_index_to_class_id,
            unknown_class_id=self.unknown_class_id,
            reject_threshold=self.reject_threshold,
            margin_threshold=self.margin_threshold,
        )


app = FastAPI(title="BugSight ONNX Predictor", version="1.0.0")

_model_path = Path(os.getenv("MODEL_PATH", str(DEFAULT_MODEL_PATH)))
_labels_path = Path(os.getenv("LABELS_PATH", str(DEFAULT_LABELS_PATH)))
_predictor: Predictor | None = None


@app.on_event("startup")
def startup_event() -> None:
    global _predictor
    _predictor = Predictor(model_path=_model_path, labels_path=_labels_path)


@app.get("/health")
def health() -> Dict[str, object]:
    return {
        "status": "ok",
        "modelPath": str(_model_path),
        "labelsPath": str(_labels_path),
        "ready": _predictor is not None,
    }


@app.post("/predict")
async def predict(file: UploadFile = File(...)) -> Dict[str, object]:
    if _predictor is None:
        raise HTTPException(status_code=503, detail="Predictor not ready")

    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image files are supported")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty file")

    try:
        return _predictor.predict(image_bytes)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"Inference failed: {exc}") from exc
