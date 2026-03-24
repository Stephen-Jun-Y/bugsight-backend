#!/usr/bin/env bash
# 用途：部署 BugSight ONNX 推理服务（二进制模型 + FastAPI + systemd）
# 用法：sudo bash scripts/bootstrap_inference_service.sh
set -euo pipefail

APP_DIR="/opt/bugsight"
SRC_DIR="${APP_DIR}/source"
INFERENCE_DIR="${APP_DIR}/inference"
ENV_FILE="${APP_DIR}/inference.env"
SERVICE_FILE="/etc/systemd/system/bugsight-inference.service"
RUN_USER="bugsight"
LOG_DIR="/var/log/bugsight"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 sudo/root 执行" >&2
  exit 1
fi

echo "[1/6] 安装 Python 依赖..."
apt-get update -q
apt-get install -y -q python3 python3-venv python3-pip

echo "[2/6] 准备目录..."
mkdir -p "${INFERENCE_DIR}" "${LOG_DIR}"
chown -R "${RUN_USER}:${RUN_USER}" "${INFERENCE_DIR}" "${LOG_DIR}"

echo "[3/6] 准备虚拟环境..."
if [[ ! -d "${INFERENCE_DIR}/.venv" ]]; then
  sudo -u "${RUN_USER}" python3 -m venv "${INFERENCE_DIR}/.venv"
fi
sudo -u "${RUN_USER}" "${INFERENCE_DIR}/.venv/bin/pip" install --upgrade pip
sudo -u "${RUN_USER}" "${INFERENCE_DIR}/.venv/bin/pip" install -r "${SRC_DIR}/scripts/ml/requirements.txt"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[4/6] 生成推理环境模板 ${ENV_FILE} ..."
  cat > "${ENV_FILE}" <<EOT
MODEL_PATH=${SRC_DIR}/scripts/data/models/resnet50_ip102_positive/resnet50_ip102_positive.onnx
LABELS_PATH=${SRC_DIR}/scripts/data/models/resnet50_ip102_positive/labels.json
HOST=127.0.0.1
PORT=8000
WORKERS=2
EOT
  chmod 600 "${ENV_FILE}"
  chown "${RUN_USER}:${RUN_USER}" "${ENV_FILE}"
fi

echo "[5/6] 写入 systemd 服务..."
cat > "${SERVICE_FILE}" <<EOT
[Unit]
Description=BugSight ONNX Inference Service
After=network.target

[Service]
Type=simple
User=${RUN_USER}
WorkingDirectory=${SRC_DIR}/scripts/ml
EnvironmentFile=${ENV_FILE}
ExecStart=${INFERENCE_DIR}/.venv/bin/uvicorn predict_api:app --host \${HOST} --port \${PORT} --workers \${WORKERS}
Restart=always
RestartSec=5
StandardOutput=append:${LOG_DIR}/inference.log
StandardError=append:${LOG_DIR}/inference.log

[Install]
WantedBy=multi-user.target
EOT

echo "[6/6] 启动推理服务..."
systemctl daemon-reload
systemctl enable bugsight-inference
systemctl restart bugsight-inference
systemctl --no-pager --full status bugsight-inference || true

echo "完成。健康检查：curl -i http://127.0.0.1:8000/health"
