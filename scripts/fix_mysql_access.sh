#!/usr/bin/env bash
# 诊断并修复：Access denied for user 'bugsight_user'@'localhost'
# 用法：
# 1) 先编辑 /opt/bugsight/.env 确认 DB_NAME/DB_USER/DB_PASS
# 2) sudo bash scripts/fix_mysql_access.sh
set -euo pipefail

ENV_FILE="/opt/bugsight/.env"
SCHEMA_FILE="/opt/bugsight/source/src/main/resources/schema.sql"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 sudo/root 执行" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "未找到 $ENV_FILE，请先创建环境文件" >&2
  exit 2
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

DB_NAME=${DB_NAME:-bugsight}
DB_USER=${DB_USER:-bugsight_user}
DB_PASS=${DB_PASS:-}
DB_HOST=${DB_HOST:-127.0.0.1}

if [[ -z "$DB_PASS" ]]; then
  echo "DB_PASS 为空，请先在 $ENV_FILE 中配置" >&2
  exit 3
fi

echo "[1/6] 检查 MySQL 服务状态..."
systemctl is-active --quiet mysql || { echo "mysql 未运行，正在启动..."; systemctl start mysql; }

echo "[2/6] 创建数据库与用户权限..."
mysql <<SQL
CREATE DATABASE IF NOT EXISTS \\`${DB_NAME}\\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \\`${DB_NAME}\\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "[3/6] 验证应用账号连接..."
mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" -e "SELECT 'ok' AS mysql_conn;" "$DB_NAME"

echo "[4/6] 初始化表结构（如 schema.sql 存在）..."
if [[ -f "$SCHEMA_FILE" ]]; then
  mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SCHEMA_FILE"
else
  echo "未找到 $SCHEMA_FILE，跳过 schema 导入"
fi

echo "[5/6] 验证关键表是否存在..."
mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" -e "SHOW TABLES;" "$DB_NAME"

echo "[6/6] 重启后端并检查状态..."
systemctl restart bugsight-backend
sleep 3
systemctl --no-pager -l status bugsight-backend || true

echo "完成。建议再执行："
echo "  curl -i http://127.0.0.1:8080/api/v1/health"
