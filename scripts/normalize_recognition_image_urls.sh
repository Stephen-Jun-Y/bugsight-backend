#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-dry-run}"
ENV_FILE="${ENV_FILE:-}"

if [[ -n "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_NAME="${DB_NAME:-bugsight}"
DB_USER="${DB_USER:-}"
DB_PASS="${DB_PASS:-}"
FILE_ACCESS_URL="${FILE_ACCESS_URL:-}"

if [[ -z "${DB_USER}" || -z "${DB_PASS}" || -z "${FILE_ACCESS_URL}" ]]; then
  echo "需要提供 DB_USER、DB_PASS、FILE_ACCESS_URL（可通过环境变量或 ENV_FILE 注入）" >&2
  exit 1
fi

BASE_URL="${FILE_ACCESS_URL%/}"

read -r -d '' PREVIEW_SQL <<SQL || true
SELECT id, image_url
FROM recognition_history
WHERE image_url LIKE 'http://127.0.0.1:8080/api/v1/files/%'
   OR image_url LIKE 'http://localhost:8080/api/v1/files/%';
SQL

read -r -d '' UPDATE_SQL <<SQL || true
UPDATE recognition_history
SET image_url = CONCAT('${BASE_URL}/', SUBSTRING_INDEX(image_url, '/', -1))
WHERE image_url LIKE 'http://127.0.0.1:8080/api/v1/files/%'
   OR image_url LIKE 'http://localhost:8080/api/v1/files/%';
SQL

MYSQL_CMD=(mysql -h "${DB_HOST}" -u "${DB_USER}" -p"${DB_PASS}" --default-character-set=utf8mb4 "${DB_NAME}")

echo "[preview] 受影响记录："
"${MYSQL_CMD[@]}" -e "${PREVIEW_SQL}"

if [[ "${MODE}" != "--apply" ]]; then
  echo
  echo "当前为 dry-run。执行方式："
  echo "  DB_HOST=${DB_HOST} DB_NAME=${DB_NAME} DB_USER=... DB_PASS=... FILE_ACCESS_URL=${BASE_URL} \\"
  echo "  bash scripts/normalize_recognition_image_urls.sh --apply"
  exit 0
fi

echo
echo "[apply] 开始回填 recognition_history.image_url ..."
"${MYSQL_CMD[@]}" -e "${UPDATE_SQL}"

echo
echo "[done] 更新后抽样："
"${MYSQL_CMD[@]}" -e "${PREVIEW_SQL}"
