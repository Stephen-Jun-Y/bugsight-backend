# 🐛 BugSight Backend

基于 **Spring Boot 3 + MyBatis-Plus + MySQL 8** 的昆虫识别系统后端服务。

## 技术栈

| 层级 | 技术 |
|---|---|
| 框架 | Spring Boot 3.2 |
| ORM | MyBatis-Plus 3.5 |
| 鉴权 | Sa-Token + JWT |
| 数据库 | MySQL 8.0 |
| 文档 | Knife4j (OpenAPI 3) |
| 工具 | Hutool |

## 快速启动（本地开发）

如果你要跑完整联调（前端 + Spring Boot + FastAPI 推理），优先使用仓库外的统一脚本：`/Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh`

```bash
# 启动全部本地服务
DB_HOST=localhost DB_NAME=bugsight DB_USER=your_user DB_PASS=your_pass /Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh start

# 查看状态
/Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh status

# 查看最近日志
/Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh logs

# 重启全部服务
DB_HOST=localhost DB_NAME=bugsight DB_USER=your_user DB_PASS=your_pass /Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh restart

# 停止全部服务
/Users/Zhuanz1/Documents/Playground/bugsight-local-dev.sh stop
```

脚本会统一完成：
- 写入前端 `VITE_API_BASE`
- 启动前端 `Vite`
- 启动后端 `Spring Boot`
- 启动本地 FastAPI + ONNX 推理服务
- 执行健康检查并输出日志位置

如果你只想单独跑后端，再使用下面这套：

```bash
# 1. 创建数据库并执行 schema.sql
mysql -u root -p < src/main/resources/schema.sql

# 2. 单独启动后端
mvn spring-boot:run
```

启动后访问 API 文档：http://localhost:8080/api/v1/doc.html

## ResNet50 模型训练与推理（毕业设计）

模型流水线脚本位于 `scripts/ml`，包含：
- 平衡数据集构建（25 类 * 500 张，70/15/15）
- ImageNet 预训练 ResNet50 两阶段训练（当前主路线：25 类正样本优化）
- ONNX 导出与推理服务（FastAPI `/predict`，支持 `isUnknown`）

快速说明见：`scripts/ml/README.md`

如果你要在 AutoDL 上直接开训当前主方案（25 类正样本优化），仓库已经附带：

```bash
bash scripts/ml/autodl_train_positive_only.sh
```

如果后续你又想回到开放集方案，再使用：

```bash
bash scripts/ml/autodl_train_open_set.sh
```

## 物种百科底稿导入

已新增 25 个与 ResNet50 类别对应的物种/类群双语底稿：`scripts/data/insect_catalog_seed.json`。

特点：
- 支持重复导入（upsert），不会覆盖已有 `recognition_count`
- 兼容 `class_id = 0`
- 对模型中的类群标签（如 `Miridae`、`Cicadellidae`、`Locustoidea`）按类群层级建档，不强行伪装成单一物种
- 中文主展示 + 英文副展示所需字段已一起维护并可重复导库

本地导入命令：

```bash
DB_HOST=localhost DB_NAME=bugsight DB_USER=your_user DB_PASS=your_pass \
python3 scripts/load_insect_catalog.py --apply
```

说明文档见：`scripts/data/insect_catalog_seed.md`

## 项目结构

```
src/main/java/com/bugsight/
├── BugSightApplication.java     # 启动类
├── config/                      # 配置（Web/MyBatis/Knife4j）
├── controller/                  # 控制器层
├── service/                     # 业务逻辑层
├── mapper/                      # 数据访问层
├── entity/                      # 数据库实体
├── dto/                         # 请求/响应 DTO
└── common/                      # 公共模块（Result/Exception/Utils）
```

## CI/CD

推送到 `main` 分支自动触发 GitHub Actions：
1. Maven 构建打包
2. SCP 上传 JAR 到服务器
3. systemctl 重启服务

详见 [DEPLOY.md](./DEPLOY.md)
