#!/usr/bin/env python3
"""Train ImageNet-pretrained ResNet50 on balanced IP102 dataset."""

from __future__ import annotations

import argparse
import copy
import csv
import json
import random
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

import numpy as np
import torch
from PIL import Image
from torch import nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.models import ResNet50_Weights, resnet50

IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]

DEFAULT_DATASET_DIR = Path(__file__).resolve().parents[1] / "data" / "ip102_balanced_25x500"
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parents[1] / "data" / "models" / "resnet50_ip102_balanced"
DEFAULT_UNKNOWN_CLASS_ID = -1
DEFAULT_REJECT_THRESHOLD = 0.55
DEFAULT_MARGIN_THRESHOLD = 0.12


@dataclass(frozen=True)
class Sample:
    image_id: str
    relative_path: str
    class_id: int
    class_name: str
    model_index: int


class CsvImageDataset(Dataset):
    def __init__(self, root_dir: Path, samples: Sequence[Sample], transform: transforms.Compose):
        self.root_dir = root_dir
        self.samples = list(samples)
        self.transform = transform

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int):
        sample = self.samples[index]
        image_path = self.root_dir / sample.relative_path
        image = Image.open(image_path).convert("RGB")
        tensor = self.transform(image)
        target = sample.model_index
        return tensor, target


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train ResNet50 on balanced IP102 dataset.")
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--img-size", type=int, default=224)
    parser.add_argument("--num-workers", type=int, default=4)
    parser.add_argument("--stage1-epochs", type=int, default=5)
    parser.add_argument("--stage2-epochs", type=int, default=45)
    parser.add_argument("--stage1-lr", type=float, default=1e-3)
    parser.add_argument("--stage2-lr", type=float, default=3e-4)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--label-smoothing", type=float, default=0.1)
    parser.add_argument("--early-stopping", type=int, default=8)
    parser.add_argument("--onnx-opset", type=int, default=17)
    parser.add_argument("--artifact-prefix", default="resnet50_ip102_balanced")
    parser.add_argument("--unknown-class-id", type=int, default=DEFAULT_UNKNOWN_CLASS_ID)
    parser.add_argument("--reject-threshold", type=float, default=DEFAULT_REJECT_THRESHOLD)
    parser.add_argument("--margin-threshold", type=float, default=DEFAULT_MARGIN_THRESHOLD)
    parser.add_argument("--smoke", action="store_true", help="Use a small subset and 1+1 epochs for smoke test.")
    return parser.parse_args()


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def read_labels(labels_csv_path: Path) -> Dict[str, Tuple[str, int, str]]:
    records: Dict[str, Tuple[str, int, str]] = {}
    with labels_csv_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            image_id = row["image_id"]
            records[image_id] = (
                row["relative_path"],
                int(row["class_id"]),
                row["class_name"],
            )
    return records


def read_splits(split_csv_path: Path) -> Dict[str, Tuple[int, str]]:
    split_map: Dict[str, Tuple[int, str]] = {}
    with split_csv_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            split_map[row["image_id"]] = (int(row["class_id"]), row["split"])
    return split_map


def limit_per_class(samples: Sequence[Sample], per_class_limit: int, seed: int) -> List[Sample]:
    grouped: Dict[int, List[Sample]] = defaultdict(list)
    for sample in samples:
        grouped[sample.class_id].append(sample)

    rng = random.Random(seed)
    kept: List[Sample] = []
    for class_id in sorted(grouped.keys()):
        entries = list(grouped[class_id])
        rng.shuffle(entries)
        kept.extend(entries[: min(per_class_limit, len(entries))])
    return kept


def build_samples(dataset_dir: Path, smoke: bool, seed: int) -> Tuple[List[Sample], List[Sample], List[Sample], List[int], Dict[int, str]]:
    labels_path = dataset_dir / "labels.csv"
    split_path = dataset_dir / "split_70_15_15.csv"

    if not labels_path.exists() or not split_path.exists():
        raise FileNotFoundError(
            f"Missing labels/split CSV in {dataset_dir}. Run build_balanced_ip102_dataset.py first."
        )

    labels = read_labels(labels_path)
    splits = read_splits(split_path)

    class_ids = sorted({int(v[1]) for v in labels.values()})
    if len(class_ids) not in {25, 26}:
        raise ValueError(f"Expected 25 or 26 classes, got {len(class_ids)}")

    class_id_to_model_index = {class_id: idx for idx, class_id in enumerate(class_ids)}
    class_id_to_name: Dict[int, str] = {}

    train_samples: List[Sample] = []
    val_samples: List[Sample] = []
    test_samples: List[Sample] = []

    for image_id, (relative_path, class_id, class_name) in labels.items():
        if image_id not in splits:
            raise ValueError(f"image_id {image_id} in labels.csv missing from split CSV")

        split_class_id, split_name = splits[image_id]
        if split_class_id != class_id:
            raise ValueError(f"class mismatch for {image_id}: labels={class_id}, split={split_class_id}")

        class_id_to_name[class_id] = class_name
        sample = Sample(
            image_id=image_id,
            relative_path=relative_path,
            class_id=class_id,
            class_name=class_name,
            model_index=class_id_to_model_index[class_id],
        )

        if split_name == "train":
            train_samples.append(sample)
        elif split_name == "val":
            val_samples.append(sample)
        elif split_name == "test":
            test_samples.append(sample)
        else:
            raise ValueError(f"Unknown split name: {split_name}")

    if smoke:
        train_samples = limit_per_class(train_samples, per_class_limit=20, seed=seed)
        val_samples = limit_per_class(val_samples, per_class_limit=5, seed=seed + 1)
        test_samples = limit_per_class(test_samples, per_class_limit=5, seed=seed + 2)

    return train_samples, val_samples, test_samples, class_ids, class_id_to_name


def topk_correct(logits: torch.Tensor, target: torch.Tensor, ks: Sequence[int]) -> List[int]:
    with torch.no_grad():
        max_k = max(ks)
        _, pred = logits.topk(max_k, 1, True, True)
        pred = pred.t()
        correct = pred.eq(target.view(1, -1).expand_as(pred))
        return [int(correct[:k].reshape(-1).float().sum().item()) for k in ks]


def run_train_epoch(
    model: nn.Module,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    criterion: nn.Module,
    device: torch.device,
    scaler: torch.cuda.amp.GradScaler | None,
) -> Dict[str, float]:
    model.train()
    total_loss = 0.0
    total_count = 0
    top1_sum = 0
    top5_sum = 0

    for images, target in loader:
        images = images.to(device, non_blocking=True)
        target = target.to(device, non_blocking=True)

        optimizer.zero_grad(set_to_none=True)

        if scaler is not None:
            with torch.cuda.amp.autocast():
                logits = model(images)
                loss = criterion(logits, target)
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
        else:
            logits = model(images)
            loss = criterion(logits, target)
            loss.backward()
            optimizer.step()

        batch_size = target.size(0)
        top1, top5 = topk_correct(logits, target, ks=(1, 5))

        total_loss += float(loss.item()) * batch_size
        top1_sum += top1
        top5_sum += top5
        total_count += batch_size

    return {
        "loss": total_loss / max(total_count, 1),
        "top1": top1_sum / max(total_count, 1),
        "top5": top5_sum / max(total_count, 1),
    }


def run_eval_epoch(model: nn.Module, loader: DataLoader, criterion: nn.Module, device: torch.device) -> Dict[str, float]:
    model.eval()
    total_loss = 0.0
    total_count = 0
    top1_sum = 0
    top5_sum = 0

    with torch.no_grad():
        for images, target in loader:
            images = images.to(device, non_blocking=True)
            target = target.to(device, non_blocking=True)

            logits = model(images)
            loss = criterion(logits, target)

            batch_size = target.size(0)
            top1, top5 = topk_correct(logits, target, ks=(1, 5))

            total_loss += float(loss.item()) * batch_size
            top1_sum += top1
            top5_sum += top5
            total_count += batch_size

    return {
        "loss": total_loss / max(total_count, 1),
        "top1": top1_sum / max(total_count, 1),
        "top5": top5_sum / max(total_count, 1),
    }


def freeze_backbone(model: nn.Module) -> None:
    for name, param in model.named_parameters():
        param.requires_grad = name.startswith("fc.")


def unfreeze_all(model: nn.Module) -> None:
    for param in model.parameters():
        param.requires_grad = True


def benchmark_latency_ms(model: nn.Module, device: torch.device, img_size: int, runs: int = 100) -> float:
    model.eval()
    input_tensor = torch.randn(1, 3, img_size, img_size, device=device)

    with torch.no_grad():
        for _ in range(10):
            _ = model(input_tensor)
        if device.type == "cuda":
            torch.cuda.synchronize()

        start = time.perf_counter()
        for _ in range(runs):
            _ = model(input_tensor)
        if device.type == "cuda":
            torch.cuda.synchronize()
        end = time.perf_counter()

    return (end - start) * 1000.0 / runs


def benchmark_onnx_cpu(onnx_path: Path, img_size: int, runs: int = 100) -> float | None:
    try:
        import onnxruntime as ort
    except Exception:
        return None

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    x = np.random.randn(1, 3, img_size, img_size).astype(np.float32)

    for _ in range(10):
        session.run(None, {input_name: x})

    start = time.perf_counter()
    for _ in range(runs):
        session.run(None, {input_name: x})
    end = time.perf_counter()
    return (end - start) * 1000.0 / runs


def select_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def main() -> int:
    args = parse_args()
    set_seed(args.seed)

    if args.smoke:
        args.stage1_epochs = 1
        args.stage2_epochs = 1

    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    train_samples, val_samples, test_samples, class_ids, class_id_to_name = build_samples(
        dataset_dir=args.dataset_dir,
        smoke=args.smoke,
        seed=args.seed,
    )

    train_transform = transforms.Compose(
        [
            transforms.RandomResizedCrop(args.img_size, scale=(0.8, 1.0)),
            transforms.RandomHorizontalFlip(p=0.5),
            transforms.RandomVerticalFlip(p=0.2),
            transforms.RandomRotation(degrees=25),
            transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2, hue=0.05),
            transforms.ToTensor(),
            transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ]
    )
    eval_transform = transforms.Compose(
        [
            transforms.Resize(256),
            transforms.CenterCrop(args.img_size),
            transforms.ToTensor(),
            transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ]
    )

    train_ds = CsvImageDataset(args.dataset_dir, train_samples, train_transform)
    val_ds = CsvImageDataset(args.dataset_dir, val_samples, eval_transform)
    test_ds = CsvImageDataset(args.dataset_dir, test_samples, eval_transform)

    loader_kwargs = {
        "batch_size": args.batch_size,
        "num_workers": args.num_workers,
        "pin_memory": torch.cuda.is_available(),
    }
    train_loader = DataLoader(train_ds, shuffle=True, **loader_kwargs)
    val_loader = DataLoader(val_ds, shuffle=False, **loader_kwargs)
    test_loader = DataLoader(test_ds, shuffle=False, **loader_kwargs)

    device = select_device()
    model = resnet50(weights=ResNet50_Weights.IMAGENET1K_V2)
    model.fc = nn.Linear(model.fc.in_features, len(class_ids))
    model = model.to(device)

    criterion = nn.CrossEntropyLoss(label_smoothing=args.label_smoothing)
    scaler = torch.cuda.amp.GradScaler() if device.type == "cuda" else None

    logs: List[Dict[str, float | int | str]] = []
    best_val_top1 = -1.0
    best_epoch = -1
    best_state = None
    patience = 0
    global_epoch = 0
    total_epochs = args.stage1_epochs + args.stage2_epochs

    print(
        f"[Train] mode={'smoke' if args.smoke else 'full'} "
        f"device={device.type} "
        f"epochs={total_epochs} "
        f"stage1={args.stage1_epochs} "
        f"stage2={args.stage2_epochs} "
        f"train={len(train_samples)} "
        f"val={len(val_samples)} "
        f"test={len(test_samples)}",
        flush=True,
    )

    # Stage 1: freeze backbone, train classification head.
    freeze_backbone(model)
    stage1_optimizer = AdamW(
        [p for p in model.parameters() if p.requires_grad],
        lr=args.stage1_lr,
        weight_decay=args.weight_decay,
    )

    for epoch in range(args.stage1_epochs):
        global_epoch += 1
        train_metrics = run_train_epoch(model, train_loader, stage1_optimizer, criterion, device, scaler)
        val_metrics = run_eval_epoch(model, val_loader, criterion, device)

        row = {
            "epoch": global_epoch,
            "stage": "frozen",
            "train_loss": train_metrics["loss"],
            "train_top1": train_metrics["top1"],
            "train_top5": train_metrics["top5"],
            "val_loss": val_metrics["loss"],
            "val_top1": val_metrics["top1"],
            "val_top5": val_metrics["top5"],
            "lr": stage1_optimizer.param_groups[0]["lr"],
        }
        logs.append(row)

        if val_metrics["top1"] > best_val_top1:
            best_val_top1 = val_metrics["top1"]
            best_epoch = global_epoch
            best_state = copy.deepcopy(model.state_dict())
            patience = 0
        else:
            patience += 1

        print(
            f"[Epoch {global_epoch}/{total_epochs}] "
            f"stage={row['stage']} "
            f"train_loss={row['train_loss']:.4f} "
            f"train_top1={row['train_top1']:.4f} "
            f"train_top5={row['train_top5']:.4f} "
            f"val_loss={row['val_loss']:.4f} "
            f"val_top1={row['val_top1']:.4f} "
            f"val_top5={row['val_top5']:.4f} "
            f"lr={row['lr']:.6f} "
            f"best_val_top1={best_val_top1:.4f}",
            flush=True,
        )

        if patience >= args.early_stopping:
            break

    # Stage 2: full fine-tuning.
    unfreeze_all(model)
    stage2_optimizer = AdamW(model.parameters(), lr=args.stage2_lr, weight_decay=args.weight_decay)
    stage2_scheduler = CosineAnnealingLR(stage2_optimizer, T_max=max(1, args.stage2_epochs))

    for epoch in range(args.stage2_epochs):
        if patience >= args.early_stopping:
            break

        global_epoch += 1
        train_metrics = run_train_epoch(model, train_loader, stage2_optimizer, criterion, device, scaler)
        val_metrics = run_eval_epoch(model, val_loader, criterion, device)
        stage2_scheduler.step()

        row = {
            "epoch": global_epoch,
            "stage": "finetune",
            "train_loss": train_metrics["loss"],
            "train_top1": train_metrics["top1"],
            "train_top5": train_metrics["top5"],
            "val_loss": val_metrics["loss"],
            "val_top1": val_metrics["top1"],
            "val_top5": val_metrics["top5"],
            "lr": stage2_optimizer.param_groups[0]["lr"],
        }
        logs.append(row)

        if val_metrics["top1"] > best_val_top1:
            best_val_top1 = val_metrics["top1"]
            best_epoch = global_epoch
            best_state = copy.deepcopy(model.state_dict())
            patience = 0
        else:
            patience += 1

        print(
            f"[Epoch {global_epoch}/{total_epochs}] "
            f"stage={row['stage']} "
            f"train_loss={row['train_loss']:.4f} "
            f"train_top1={row['train_top1']:.4f} "
            f"train_top5={row['train_top5']:.4f} "
            f"val_loss={row['val_loss']:.4f} "
            f"val_top1={row['val_top1']:.4f} "
            f"val_top5={row['val_top5']:.4f} "
            f"lr={row['lr']:.6f} "
            f"best_val_top1={best_val_top1:.4f}",
            flush=True,
        )

    if best_state is None:
        best_state = copy.deepcopy(model.state_dict())
        best_epoch = global_epoch

    model.load_state_dict(best_state)

    checkpoint_path = output_dir / "best.pth"
    torch.save(
        {
            "state_dict": best_state,
            "class_ids": class_ids,
            "class_id_to_name": class_id_to_name,
            "best_epoch": best_epoch,
            "best_val_top1": best_val_top1,
            "seed": args.seed,
            "img_size": args.img_size,
        },
        checkpoint_path,
    )

    test_metrics = run_eval_epoch(model, test_loader, criterion, device)

    onnx_path = output_dir / f"{args.artifact_prefix}.onnx"
    model.eval()
    dummy_input = torch.randn(1, 3, args.img_size, args.img_size, device=device)
    torch.onnx.export(
        model,
        dummy_input,
        str(onnx_path),
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch_size"}, "logits": {0: "batch_size"}},
        opset_version=args.onnx_opset,
    )

    unknown_class_id = args.unknown_class_id if args.unknown_class_id in class_ids else None
    labels_json_path = output_dir / "labels.json"
    labels_payload = {
        "model_name": "resnet50",
        "weights": "IMAGENET1K_V2",
        "num_classes": len(class_ids),
        "model_index_to_class_id": class_ids,
        "class_id_to_model_index": {str(class_id): idx for idx, class_id in enumerate(class_ids)},
        "class_id_to_name": {str(class_id): class_id_to_name[class_id] for class_id in class_ids},
        "normalization": {"mean": IMAGENET_MEAN, "std": IMAGENET_STD},
        "input_size": args.img_size,
        "unknown_class_id": unknown_class_id,
        "reject_threshold": args.reject_threshold,
        "margin_threshold": args.margin_threshold,
    }
    labels_json_path.write_text(json.dumps(labels_payload, indent=2, ensure_ascii=True), encoding="utf-8")

    train_log_path = output_dir / "train_log.csv"
    with train_log_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["epoch", "stage", "train_loss", "train_top1", "train_top5", "val_loss", "val_top1", "val_top5", "lr"],
        )
        writer.writeheader()
        for row in logs:
            writer.writerow(row)

    model_size_mb = checkpoint_path.stat().st_size / (1024 * 1024)
    onnx_size_mb = onnx_path.stat().st_size / (1024 * 1024)

    cpu_model = copy.deepcopy(model).to("cpu")
    cpu_latency = benchmark_latency_ms(cpu_model, torch.device("cpu"), args.img_size)
    gpu_latency = None
    if torch.cuda.is_available():
        gpu_latency = benchmark_latency_ms(model, torch.device("cuda"), args.img_size)

    onnx_cpu_latency = benchmark_onnx_cpu(onnx_path, args.img_size)

    report = {
        "mode": "smoke" if args.smoke else "full",
        "seed": args.seed,
        "num_classes": len(class_ids),
        "dataset": {
            "train": len(train_samples),
            "val": len(val_samples),
            "test": len(test_samples),
        },
        "training": {
            "stage1_epochs": args.stage1_epochs,
            "stage2_epochs": args.stage2_epochs,
            "best_epoch": best_epoch,
            "best_val_top1": best_val_top1,
        },
        "test_metrics": {
            "top1": test_metrics["top1"],
            "top5": test_metrics["top5"],
            "loss": test_metrics["loss"],
        },
        "latency_ms": {
            "pytorch_cpu": cpu_latency,
            "pytorch_gpu": gpu_latency,
            "onnx_cpu": onnx_cpu_latency,
        },
        "model_size_mb": {
            "best_pth": model_size_mb,
            "onnx": onnx_size_mb,
        },
        "artifacts": {
            "checkpoint": str(checkpoint_path),
            "onnx": str(onnx_path),
            "labels": str(labels_json_path),
            "train_log": str(train_log_path),
        },
    }

    report_json_path = output_dir / "metrics_report.json"
    report_json_path.write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding="utf-8")

    report_md_path = output_dir / "metrics_report.md"
    report_md_path.write_text(
        "\n".join(
            [
                "# ResNet50 IP102 Open-set Training Report",
                "",
                f"- Mode: {'smoke' if args.smoke else 'full'}",
                f"- Classes: {len(class_ids)}",
                f"- Unknown class id: {unknown_class_id}" if unknown_class_id is not None else "- Unknown class id: N/A",
                f"- Reject threshold: {args.reject_threshold:.2f}",
                f"- Margin threshold: {args.margin_threshold:.2f}",
                f"- Dataset size (train/val/test): {len(train_samples)}/{len(val_samples)}/{len(test_samples)}",
                f"- Best epoch: {best_epoch}",
                f"- Best val top1: {best_val_top1:.4f}",
                f"- Test top1: {test_metrics['top1']:.4f}",
                f"- Test top5: {test_metrics['top5']:.4f}",
                f"- Test loss: {test_metrics['loss']:.6f}",
                f"- Pytorch CPU latency (ms): {cpu_latency:.3f}",
                f"- Pytorch GPU latency (ms): {gpu_latency:.3f}" if gpu_latency is not None else "- Pytorch GPU latency (ms): N/A",
                f"- ONNX CPU latency (ms): {onnx_cpu_latency:.3f}" if onnx_cpu_latency is not None else "- ONNX CPU latency (ms): N/A",
                f"- best.pth size (MB): {model_size_mb:.2f}",
                f"- onnx size (MB): {onnx_size_mb:.2f}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    print("[OK] Training finished.")
    print(f"[OK] Artifacts saved to {output_dir}")
    print(f"[OK] Test Top1={test_metrics['top1']:.4f}, Top5={test_metrics['top5']:.4f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
