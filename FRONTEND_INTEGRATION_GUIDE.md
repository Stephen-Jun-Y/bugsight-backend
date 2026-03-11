# BugSight 前端对接指南（基于当前已部署后端）

> 适用于前端仓库联调同学，目标：最短路径接通登录/注册/鉴权和核心业务接口。

## 1. 环境配置

- 后端 Base URL：`http://124.221.209.129:8080/api/v1`
- 健康检查：`GET /health`

Vite `.env.development`：

```bash
VITE_API_BASE=http://124.221.209.129:8080/api/v1
```

## 2. 统一响应结构

所有接口统一返回：

```json
{
  "code": 200,
  "message": "ok",
  "data": { }
}
```

前端必须从 `data` 取业务数据。

---

## 3. 登录/注册对接（重点）

### 3.1 注册

- **接口**：`POST /auth/register`
- **请求体**：

```json
{
  "nickname": "自然探索者",
  "email": "user@example.com",
  "password": "12345678",
  "agreePolicy": true
}
```

> 说明：后端会把 `nickname` 映射到 `username` 字段。

### 3.2 登录

- **接口**：`POST /auth/login`
- **请求体**：

```json
{
  "email": "user@example.com",
  "password": "12345678"
}
```

### 3.3 登录/注册响应（两者一致）

`data` 字段包含：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "token": "...",
  "userId": 1,
  "nickname": "自然探索者",
  "avatarUrl": "",
  "user": {
    "id": 1,
    "nickname": "自然探索者",
    "avatarUrl": ""
  }
}
```

> 当前阶段建议前端统一使用 `accessToken`；`token` 仅为兼容保留字段。

### 3.4 鉴权头

后续需要登录态的接口，统一加：

```http
Authorization: Bearer <accessToken>
```

---

## 4. 用户中心接口

- `GET /users/me`：获取当前用户
- `PATCH /users/me`：更新资料
- `POST /users/me/password`：修改密码
- `DELETE /users/me`：注销账号

`PATCH /users/me` 请求体：

```json
{
  "nickname": "新昵称",
  "bio": "简介",
  "location": "北京",
  "avatarUrl": "https://..."
}
```

---

## 5. 识别/物种等核心接口

### 识别
- `POST /recognitions`（支持 `image` 字段，也兼容 `file`）
- `GET /recognitions`
- `GET /recognitions/{id}`
- `PATCH /recognitions/{id}`
- `DELETE /recognitions/{id}`

### 物种
- `GET /species/search`
- `GET /species/{id}`
- `GET /species/{id}/similar`

### 收藏
- `GET /favorites`
- `POST /favorites/{insectId}`
- `DELETE /favorites/{insectId}`
- `POST /favorites/{insectId}/toggle`
- `GET /favorites/{insectId}/status`

### 社区
- `GET /posts`
- `GET /posts/{id}/comments`
- `POST /posts/{id}/comments`

---

## 6. 分页参数与返回

### 请求参数
- 推荐用：`page` + `pageSize`
- 兼容：`size`

### 返回结构（在 `data` 内）

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "size": 20
}
```

---

## 7. 前端需要修改/确认的点（行动清单）

1. API 客户端统一加 `baseURL = VITE_API_BASE`。
2. 请求拦截器统一注入 `Authorization: Bearer ${accessToken}`。
3. 登录成功后把 `data.accessToken` 存本地（建议 localStorage 或状态管理）。
4. 所有页面解析接口时从 `res.data` 取业务值，而不是根对象。
5. 分页统一使用 `page/pageSize`，读取 `data.list` 渲染列表。
6. 注册页提交字段名使用 `nickname` + `agreePolicy`。
7. 若还在读旧字段 `username`，请改读 `nickname`（或兼容双读）。

---

## 8. 尚未完全覆盖的接口（前端先 mock）

以下接口仍建议前端继续 mock 或容错：
- `/users/me/phone/*`
- `/exports/*`
- `/search/hot-tags`、`/search/history`
- `/notifications*`
- 通用 `/likes`、`/follows`

---

## 9. 快速自测示例（前端/测试同学可用）

```bash
BASE=http://124.221.209.129:8080/api/v1

# 健康检查
curl -sS "$BASE/health"

# 注册
curl -sS -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"联调用户","email":"debug_user_002@example.com","password":"12345678","agreePolicy":true}'

# 登录
curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"debug_user_002@example.com","password":"12345678"}'
```

