#!/usr/bin/env bash
# 用途：在 AutoDL 上构建 25+unknown 数据集并启动 ResNet50 开放集训练
# 用法：bash scripts/ml/autodl_train_open_set.sh /root/autodl-tmp/bugsight-train/data/negative_non_insect
set -euo pipefail

ROOT_DIR="/root/autodl-tmp/bugsight-train"
ML_DIR="${ROOT_DIR}/scripts/ml"
BASE_DATASET_DIR="${ROOT_DIR}/data/ip102_balanced_25x500"
NEGATIVE_DIR="${1:-${ROOT_DIR}/data/negative_non_insect}"
OPEN_DATASET_DIR="${ROOT_DIR}/data/ip102_balanced_25x500_unknown"
OUTPUT_DIR="${ROOT_DIR}/data/models/resnet50_ip102_unknown"

cd "${ML_DIR}"
source .venv/bin/activate

python build_ip102_unknown_dataset.py \
  --base-dataset-dir "${BASE_DATASET_DIR}" \
  --negative-dir "${NEGATIVE_DIR}" \
  --output-dir "${OPEN_DATASET_DIR}" \
  --unknown-count 1500

python train_resnet50_ip102.py \
  --dataset-dir "${OPEN_DATASET_DIR}" \
  --output-dir "${OUTPUT_DIR}" \
  --artifact-prefix resnet50_ip102_unknown \
  --reject-threshold 0.55 \
  --margin-threshold 0.12
