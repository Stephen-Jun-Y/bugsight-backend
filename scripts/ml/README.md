# ResNet50 IP102 Pipeline

This folder provides a full local pipeline for the graduation requirement:
- balanced dataset: 25 species x 500 images each
- per-class split: 70/15/15 (350/75/75)
- model: ImageNet-pretrained ResNet50
- production recommendation: 25-class positive-only optimization + low-confidence reject hint

## 1) Environment setup

```bash
cd /Users/Zhuanz1/Documents/Playground/bugsight-backend/scripts/ml
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 2) Build balanced dataset

```bash
python build_balanced_ip102_dataset.py \
  --ip102-tar ../ip102_v1.1-001.tar \
  --classes-zip ../Classification-20251201T040049Z-1-002.zip \
  --output-dir ../data/ip102_balanced_25x500
```

Generated files:
- `../data/ip102_balanced_25x500/labels.csv`
- `../data/ip102_balanced_25x500/split_70_15_15.csv`
- `../data/ip102_balanced_25x500/class_distribution.csv`
- `../data/ip102_balanced_25x500/images/*.jpg`

## 3) Verify dataset constraints

```bash
python verify_dataset.py \
  --dataset-dir ../data/ip102_balanced_25x500 \
  --check-files
```

## 4) Training (recommended: 25-class positive-only)

### Smoke test (quick sanity check)

```bash
python train_resnet50_ip102.py \
  --dataset-dir ../data/ip102_balanced_25x500 \
  --output-dir ../data/models/resnet50_ip102_positive \
  --artifact-prefix resnet50_ip102_positive \
  --smoke
```

### Full training (recommended positive-only profile)

```bash
python train_resnet50_ip102.py \
  --dataset-dir ../data/ip102_balanced_25x500 \
  --output-dir ../data/models/resnet50_ip102_positive \
  --artifact-prefix resnet50_ip102_positive \
  --stage1-epochs 5 \
  --stage2-epochs 60 \
  --stage2-lr 2e-4 \
  --reject-threshold 0.60 \
  --margin-threshold 0.10
```

Outputs:
- `best.pth`
- `resnet50_ip102_positive.onnx`
- `labels.json`
- `train_log.csv`
- `metrics_report.json`
- `metrics_report.md`

## 5) Verify ONNX contract

```bash
python verify_onnx.py \
  --dataset-dir ../data/ip102_balanced_25x500 \
  --onnx-model ../data/models/resnet50_ip102_positive/resnet50_ip102_positive.onnx \
  --labels-json ../data/models/resnet50_ip102_positive/labels.json
```

## 6) Run inference API (for Spring Boot backend)

```bash
MODEL_PATH=/Users/Zhuanz1/Documents/Playground/bugsight-backend/scripts/data/models/resnet50_ip102_positive/resnet50_ip102_positive.onnx \
LABELS_PATH=/Users/Zhuanz1/Documents/Playground/bugsight-backend/scripts/data/models/resnet50_ip102_positive/labels.json \
uvicorn predict_api:app --host 127.0.0.1 --port 8000 --workers 2
```

Response schema:

```json
{
  "isUnknown": false,
  "top1": {"classIndex": 101, "confidence": 0.94},
  "top3": [
    {"classIndex": 101, "confidence": 0.94},
    {"classIndex": 67, "confidence": 0.03},
    {"classIndex": 70, "confidence": 0.01}
  ]
}
```

Reject rules:
- `max_prob < 0.60`
- or `(top1 - top2) < 0.10`

## 7) Optional open-set extension

If you later decide to add a dedicated `unknown / non-insect` class, the repo still provides:

```bash
python build_ip102_unknown_dataset.py \
  --base-dataset-dir ../data/ip102_balanced_25x500 \
  --negative-dir ../data/negative_non_insect \
  --output-dir ../data/ip102_balanced_25x500_unknown \
  --unknown-count 1500
```

And the AutoDL helper:

```bash
bash autodl_train_open_set.sh /root/autodl-tmp/bugsight-train/data/negative_non_insect
```
