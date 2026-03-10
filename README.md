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

```bash
# 1. 创建数据库并执行 schema.sql
mysql -u root -p < src/main/resources/schema.sql

# 2. 修改 application.yml 中的数据库连接配置

# 3. 启动
mvn spring-boot:run
```

启动后访问 API 文档：http://localhost:8080/api/v1/doc.html

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
