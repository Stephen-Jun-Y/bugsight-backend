# BugSight 前后端联调 Runbook（腾讯云）

> 目标：优先“跑通联调”，快速定位 API 可达性、CORS、认证、路由契约问题。

## 0. 你先在服务器执行这些命令（我未代执行）

```bash
# 1) 服务状态
sudo systemctl status bugsight-backend --no-pager

# 2) 最新日志
sudo journalctl -u bugsight-backend -n 200 --no-pager

# 3) 端口监听
sudo ss -lntp | rg 8080

# 4) 本机直连健康检查（新加）
curl -i http://127.0.0.1:8080/api/v1/health

# 5) 公网地址健康检查
curl -i http://124.221.209.129:8080/api/v1/health
```

若 `127.0.0.1` 可通但公网不通，优先检查腾讯云安全组 / ufw：

```bash
sudo ufw status
# 腾讯云控制台放行 TCP 8080 入站
```

---


## 0.1 你当前这类报错的结论（基于你贴的输出）

你提供的输出：
- `Unit bugsight-backend.service could not be found.`
- `journalctl -u bugsight-backend` 无记录
- `127.0.0.1:8080` 连接失败

这说明**服务根本没有注册到 systemd**（不是 CORS、不是 Token、不是接口路径问题）。

先执行：

```bash
cd /opt/bugsight/source
sudo bash scripts/bootstrap_backend_service.sh
```

如果提示 `.env` 未配置，会自动退出并提示你先填写 DB/JWT，再重跑脚本。

若你在第 4 步遇到 `detected dubious ownership`：

```bash
sudo git config --global --add safe.directory /opt/bugsight/source
```

然后再次执行：

```bash
cd /opt/bugsight/source
sudo bash scripts/bootstrap_backend_service.sh
```

---
## 1. API 可访问性诊断（最小闭环）

```bash
BASE=http://124.221.209.129:8080/api/v1

# 健康检查（免登录）
curl -sS "$BASE/health" | jq .

# 文档可访问
curl -I "$BASE/doc.html"

# 注册（需唯一邮箱）
curl -sS -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"联调用户","email":"debug_user_001@example.com","password":"12345678","agreePolicy":true}' | jq .

# 登录
LOGIN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"debug_user_001@example.com","password":"12345678"}')

echo "$LOGIN" | jq .
TOKEN=$(echo "$LOGIN" | jq -r '.data.accessToken')

# 获取当前用户
curl -sS "$BASE/users/me" -H "Authorization: Bearer $TOKEN" | jq .
```

---

## 2. CORS/认证排查

前端若跨域失败，先做预检请求：

```bash
curl -i -X OPTIONS "$BASE/users/me" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: Authorization,Content-Type'
```

预期应返回 `Access-Control-Allow-Origin`、`Access-Control-Allow-Headers`、`Access-Control-Allow-Methods`。

认证约定：
- Header 名：`Authorization`
- 格式：`Bearer <token>`

---

## 3. 前端本地连接远程后端配置

Vite 示例：

```bash
# .env.development
VITE_API_BASE=http://124.221.209.129:8080/api/v1
```

统一 API client 建议：

```ts
// src/services/http.ts
const BASE = import.meta.env.VITE_API_BASE;

export async function request(path: string, init: RequestInit = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = new Headers(init.headers || {});
  headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${BASE}${path}`, { ...init, headers, credentials: 'include' });
  if (!res.ok) throw new Error(`${res.status}`);
  return res.json();
}
```

---

## 4. 当前后端已对齐的关键接口

- 认证：`POST /auth/login`、`POST /auth/register`、`POST /auth/logout`
- 用户：`GET/PATCH /users/me`、`POST /users/me/password`、`DELETE /users/me`
- 识别：`POST /recognitions`、`GET /recognitions`、`GET/PATCH/DELETE /recognitions/{id}`
- 物种：`GET /species/search`、`GET /species/{id}`、`GET /species/{id}/similar`
- 社区：`GET /posts`、`GET /posts/{id}/comments`（支持 `pageSize`）
- 收藏：`GET /favorites`（支持 `pageSize`）

---

## 5. 仍需二期补齐（避免误判联调失败）

以下接口在你给的前端清单里有，但后端尚未完整实现：
- `/users/me/phone/*`
- `/exports/*`
- `/search/hot-tags`、`/search/history`
- `/notifications*`
- 通用 `/likes`、`/follows`

