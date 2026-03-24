#!/usr/bin/env bash
# 用途：在腾讯云服务器上安装 MinIO（二进制 + systemd）
# 用法：sudo bash scripts/bootstrap_minio_service.sh
set -euo pipefail

MINIO_USER="minio"
MINIO_GROUP="minio"
MINIO_BIN="/usr/local/bin/minio"
MINIO_DATA_DIR="/var/lib/minio"
MINIO_CONFIG_DIR="/etc/minio"
MINIO_ENV_FILE="${MINIO_CONFIG_DIR}/bugsight-minio.env"
MINIO_SERVICE_FILE="/etc/systemd/system/bugsight-minio.service"
MINIO_LOG_DIR="/var/log/minio"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 sudo/root 执行" >&2
  exit 1
fi

echo "[1/6] 创建运行用户..."
if ! id -u "${MINIO_USER}" >/dev/null 2>&1; then
  useradd -r -s /usr/sbin/nologin "${MINIO_USER}"
fi

echo "[2/6] 准备目录..."
mkdir -p "${MINIO_DATA_DIR}" "${MINIO_CONFIG_DIR}" "${MINIO_LOG_DIR}"
chown -R "${MINIO_USER}:${MINIO_GROUP}" "${MINIO_DATA_DIR}" "${MINIO_LOG_DIR}"

echo "[3/6] 下载 MinIO 二进制..."
curl -fsSL https://dl.min.io/server/minio/release/linux-amd64/minio -o "${MINIO_BIN}"
chmod +x "${MINIO_BIN}"

if [[ ! -f "${MINIO_ENV_FILE}" ]]; then
  echo "[4/6] 生成环境变量模板 ${MINIO_ENV_FILE} ..."
  cat > "${MINIO_ENV_FILE}" <<'EOT'
MINIO_ROOT_USER=CHANGE_ME_MINIO_ACCESS_KEY
MINIO_ROOT_PASSWORD=CHANGE_ME_MINIO_SECRET_KEY
MINIO_VOLUMES=/var/lib/minio
MINIO_OPTS=--address :9000 --console-address :9001
EOT
  chmod 600 "${MINIO_ENV_FILE}"
  chown "${MINIO_USER}:${MINIO_GROUP}" "${MINIO_ENV_FILE}"
  echo "请先编辑 ${MINIO_ENV_FILE}，填入真实 MinIO 凭据后再重新执行本脚本。"
  exit 2
fi

echo "[5/6] 写入 systemd 服务..."
cat > "${MINIO_SERVICE_FILE}" <<EOT
[Unit]
Description=BugSight MinIO Service
After=network.target

[Service]
User=${MINIO_USER}
Group=${MINIO_GROUP}
EnvironmentFile=${MINIO_ENV_FILE}
ExecStart=${MINIO_BIN} server \${MINIO_VOLUMES} --address :9000 --console-address :9001
WorkingDirectory=${MINIO_DATA_DIR}
Restart=always
RestartSec=5
StandardOutput=append:${MINIO_LOG_DIR}/minio.log
StandardError=append:${MINIO_LOG_DIR}/minio.log

[Install]
WantedBy=multi-user.target
EOT

echo "[6/6] 启动 MinIO ..."
systemctl daemon-reload
systemctl enable bugsight-minio
systemctl restart bugsight-minio
systemctl --no-pager --full status bugsight-minio || true

echo "完成。"
echo "API 端口：9000"
echo "控制台端口：9001"
