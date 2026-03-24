#!/usr/bin/env bash
# 用途：修复 "Unit bugsight-backend.service could not be found" 场景
# 用法：sudo bash scripts/bootstrap_backend_service.sh
set -euo pipefail

APP_DIR="/opt/bugsight"
SRC_DIR="${APP_DIR}/source"
SERVICE_FILE="/etc/systemd/system/bugsight-backend.service"
ENV_FILE="${APP_DIR}/.env"
UPLOAD_DIR="/var/bugsight/uploads"
LOG_DIR="/var/log/bugsight"
RUN_USER="bugsight"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 sudo/root 执行" >&2
  exit 1
fi

echo "[1/8] 安装基础依赖..."
apt-get update -q
apt-get install -y -q openjdk-17-jdk maven git curl

echo "[2/8] 准备目录..."
mkdir -p "${APP_DIR}" "${UPLOAD_DIR}" "${LOG_DIR}"

if ! id -u "${RUN_USER}" >/dev/null 2>&1; then
  echo "[3/8] 创建运行用户 ${RUN_USER}..."
  useradd -r -s /bin/false "${RUN_USER}"
else
  echo "[3/8] 运行用户 ${RUN_USER} 已存在，跳过"
fi

chown -R "${RUN_USER}:${RUN_USER}" "${APP_DIR}" /var/bugsight /var/log/bugsight

if [[ ! -d "${SRC_DIR}/.git" ]]; then
  echo "[4/8] 代码不存在，克隆仓库..."
  git clone https://github.com/Stephen-Jun-Y/bugsight-backend.git "${SRC_DIR}"
else
  echo "[4/8] 更新代码..."
  # 避免 root 在非本人 owner 目录执行 git 触发 dubious ownership
  git config --global --add safe.directory "${SRC_DIR}"
  git -C "${SRC_DIR}" pull --ff-only
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[5/8] 生成环境变量模板 ${ENV_FILE}（请手动填密码后重跑）..."
  cat > "${ENV_FILE}" <<'EOT'
DB_HOST=127.0.0.1
DB_NAME=bugsight
DB_USER=bugsight_user
DB_PASS=CHANGE_ME_DB_PASSWORD
JWT_SECRET=CHANGE_ME_JWT_SECRET
FILE_UPLOAD_PATH=/var/bugsight/uploads
FILE_ACCESS_URL=http://127.0.0.1:8080/api/v1/files
INFERENCE_URL=http://127.0.0.1:8000
STORAGE_PROVIDER=local
STORAGE_MINIO_ENDPOINT=http://127.0.0.1:9000
STORAGE_MINIO_ACCESS_KEY=CHANGE_ME_MINIO_ACCESS_KEY
STORAGE_MINIO_SECRET_KEY=CHANGE_ME_MINIO_SECRET_KEY
STORAGE_MINIO_BUCKET=bugsight-images
EOT
  chown "${RUN_USER}:${RUN_USER}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  echo "请先编辑 ${ENV_FILE}，将 DB_PASS/JWT_SECRET 改为真实值后再执行本脚本。"
  exit 2
fi

echo "[6/8] 构建 JAR..."
cd "${SRC_DIR}"
mvn -q clean package -DskipTests
cp target/bugsight-backend.jar "${APP_DIR}/bugsight-backend.jar"
chown "${RUN_USER}:${RUN_USER}" "${APP_DIR}/bugsight-backend.jar"

echo "[7/8] 写入 systemd 服务..."
cat > "${SERVICE_FILE}" <<EOT
[Unit]
Description=BugSight Backend Service
After=network.target mysql.service

[Service]
Type=simple
User=${RUN_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java \\
  -Xms256m -Xmx512m \\
  -Dspring.profiles.active=prod \\
  -DDB_HOST=\${DB_HOST} \\
  -DDB_NAME=\${DB_NAME} \\
  -DDB_USER=\${DB_USER} \\
  -DDB_PASS=\${DB_PASS} \\
  -DJWT_SECRET=\${JWT_SECRET} \\
  -DFILE_UPLOAD_PATH=\${FILE_UPLOAD_PATH} \\
  -DFILE_ACCESS_URL=\${FILE_ACCESS_URL} \\
  -DINFERENCE_URL=\${INFERENCE_URL} \\
  -jar ${APP_DIR}/bugsight-backend.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:${LOG_DIR}/app.log
StandardError=append:${LOG_DIR}/app.log

[Install]
WantedBy=multi-user.target
EOT

echo "[8/8] 启动服务..."
systemctl daemon-reload
systemctl enable bugsight-backend
systemctl restart bugsight-backend
systemctl --no-pager --full status bugsight-backend || true

echo "完成。健康检查：curl -i http://127.0.0.1:8080/api/v1/health"
