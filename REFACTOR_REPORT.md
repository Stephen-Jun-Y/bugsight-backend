# 后端重构报告（按前端 API 文档对齐）

## 1. 代码结构现状分析

后端整体采用标准分层：`controller -> service -> mapper -> entity`，具备可维护基础；统一响应为 `Result<T>`。主要问题是部分接口命名和返回结构与前端文档不一致（如 `/users/me`、`/recognitions`、`pageSize` 参数、认证返回 token 字段命名等）。

## 2. 本轮重构内容

### 2.1 路由对齐

- 新增用户模块路由：
  - `GET /users/me`
  - `PATCH /users/me`
  - `POST /users/me/password`
  - `DELETE /users/me`
- 识别模块统一并兼容：
  - `POST /recognitions`（同时兼容旧 `/recognize`）
  - `GET /recognitions`
  - `GET /recognitions/{id}`
  - `PATCH /recognitions/{id}`
  - `DELETE /recognitions/{id}`
  - 保留旧 `/history`、`/recognitions/history` 兼容路径
- 新增物种模块路由：
  - `GET /species/search`
  - `GET /species/{id}`
  - `GET /species/{id}/similar`
- 分页接口统一支持 `pageSize`（并兼容旧 `size`）。

### 2.2 数据格式对齐

- 登录/注册响应新增：`accessToken`、`refreshToken`、`user.nickname`，并保留旧 `token` 字段兼容。
- 识别上传与识别详情响应改为前端友好结构：
  - `recognitionId`
  - `species`（`id/name/latinName`）
  - `confidence`
  - `similar`（候选相似物种）
- 分页响应继续统一为 `{ list, total, page, size }`。

### 2.3 代码结构优化

- 新增 DTO：
  - `ChangePasswordRequest`、`UpdateMeRequest`
  - `UserProfileResponse`、`RecognitionResponse`
- `RecognitionService` 增加 `RecognitionHistory -> RecognitionResponse` 转换方法，控制器不再直接返回数据库实体。
- `RegisterRequest` 支持 `nickname` 别名映射到 `username`，并增加 `agreePolicy` 校验。

## 3. 文件变更清单

### 新增文件

- `src/main/java/com/bugsight/controller/UserController.java`
- `src/main/java/com/bugsight/controller/SpeciesController.java`
- `src/main/java/com/bugsight/dto/request/ChangePasswordRequest.java`
- `src/main/java/com/bugsight/dto/request/UpdateMeRequest.java`
- `src/main/java/com/bugsight/dto/response/UserProfileResponse.java`
- `src/main/java/com/bugsight/dto/response/RecognitionResponse.java`

### 修改文件

- `src/main/java/com/bugsight/controller/AuthController.java`
- `src/main/java/com/bugsight/controller/RecognitionController.java`
- `src/main/java/com/bugsight/controller/PostController.java`
- `src/main/java/com/bugsight/controller/FavoriteController.java`
- `src/main/java/com/bugsight/service/AuthService.java`
- `src/main/java/com/bugsight/service/RecognitionService.java`
- `src/main/java/com/bugsight/dto/request/RegisterRequest.java`
- `src/main/java/com/bugsight/dto/response/AuthTokenResponse.java`

## 4. API 变更列表

### 新增 API

- `GET /users/me`
- `PATCH /users/me`
- `POST /users/me/password`
- `DELETE /users/me`
- `GET /recognitions`
- `GET /recognitions/{id}`
- `PATCH /recognitions/{id}`
- `DELETE /recognitions/{id}`
- `GET /species/search`
- `GET /species/{id}`
- `GET /species/{id}/similar`

### 修改 API（兼容升级）

- `POST /auth/login`, `POST /auth/register`：新增 `accessToken/refreshToken/user.nickname`。
- `POST /recognitions`：支持 `image` 文件字段（兼容 `file`）。
- `GET /posts`, `GET /posts/{id}/comments`, `GET /favorites`, `GET /recognitions`：支持 `pageSize` 参数（兼容 `size`）。

### 保留兼容 API

- `/auth/profile`、`/auth/profile(put)`
- `/history` 与 `/recognitions/history/**`
- `/recognize`

## 5. 与前端对接验收清单

- [ ] 登录/注册返回包含 `accessToken`、`refreshToken`、`user.nickname`。
- [ ] 个人中心可用 `/users/me` 获取并 `PATCH` 更新资料。
- [ ] 设置页可调用 `/users/me/password` 修改密码。
- [ ] 注销账号按钮可调用 `DELETE /users/me`。
- [ ] 识别上传使用 `POST /recognitions` + `image` 字段可成功返回结构化结果。
- [ ] 识别列表/详情可用 `/recognitions`、`/recognitions/{id}`。
- [ ] 搜索页可用 `/species/search`、详情页可用 `/species/{id}`、相似页可用 `/species/{id}/similar`。
- [ ] 动态、评论、收藏、识别列表分页统一接受 `page + pageSize`。

## 6. 尚未覆盖（需二期补齐）

- 手机绑定流程：`/users/me/phone/*`
- 导出流程：`/exports/*`
- 搜索热门词/历史：`/search/*`
- 通知中心：`/notifications*`
- 通用点赞接口：`/likes`
- 关注接口：`/follows`
