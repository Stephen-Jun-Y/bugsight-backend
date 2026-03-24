#!/usr/bin/env bash
# 用途：在 AutoDL 上直接启动 25 类正样本优化训练
# 用法：bash scripts/ml/autodl_train_positive_only.sh
set -euo pipefail

ROOT_DIR="/root/autodl-tmp/bugsight-train"
ML_DIR="${ROOT_DIR}/scripts/ml"
DATASET_DIR="${ROOT_DIR}/data/ip102_balanced_25x500"
OUTPUT_DIR="${ROOT_DIR}/data/models/resnet50_ip102_positive"

cd "${ML_DIR}"
source .venv/bin/activate

python train_resnet50_ip102.py \
  --dataset-dir "${DATASET_DIR}" \
  --output-dir "${OUTPUT_DIR}" \
  --artifact-prefix resnet50_ip102_positive \
  --stage1-epochs 5 \
  --stage2-epochs 60 \
  --stage1-lr 1e-3 \
  --stage2-lr 2e-4 \
  --reject-threshold 0.60 \
  --margin-threshold 0.10
