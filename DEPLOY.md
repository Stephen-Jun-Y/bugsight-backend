# BugSight Backend — 部署说明

## 服务器环境要求
- Ubuntu 22.04 LTS
- Java 17（`apt install openjdk-17-jdk`）
- MySQL 8.0
- Python 3.10+（用于 FastAPI 推理服务）
- MinIO（二进制方式，可选，用于正式环境图片存储）

---

## 一、首次部署（手动执行一次）

### 1. 创建目录 & 系统服务
```bash
sudo mkdir -p /opt/bugsight /var/bugsight/uploads /var/log/bugsight
sudo useradd -r -s /bin/false bugsight
sudo chown bugsight:bugsight /opt/bugsight /var/bugsight /var/log/bugsight
```

### 2. 初始化数据库
```bash
mysql -u root -p < schema.sql
```

### 2.1 导入物种百科底稿（推荐）
```bash
cd /opt/bugsight/source
DB_HOST=127.0.0.1 DB_NAME=bugsight DB_USER=bugsight_user DB_PASS=your_db_password \
python3 scripts/load_insect_catalog.py --apply
```

该步骤会将与模型 25 个类别对应的 `insect_info` 基础百科档案导入数据库。

### 3. 创建环境变量文件
```bash
sudo nano /opt/bugsight/.env
```
填入：
```
DB_HOST=127.0.0.1
DB_NAME=bugsight
DB_USER=bugsight_user
DB_PASS=your_db_password
JWT_SECRET=your_jwt_secret_here
FILE_UPLOAD_PATH=/var/bugsight/uploads
FILE_ACCESS_URL=http://your-server-ip/api/v1/files
INFERENCE_URL=http://127.0.0.1:8000
STORAGE_PROVIDER=minio
STORAGE_MINIO_ENDPOINT=http://127.0.0.1:9000
STORAGE_MINIO_ACCESS_KEY=your_minio_access_key
STORAGE_MINIO_SECRET_KEY=your_minio_secret_key
STORAGE_MINIO_BUCKET=bugsight-images
```

### 4. 注册 systemd 服务
```bash
sudo cp bugsight-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable bugsight-backend
sudo systemctl start bugsight-backend
```

---

## 二、GitHub Actions 自动部署配置

在 GitHub 仓库 → Settings → Secrets → Actions 中添加：

| Secret 名称 | 说明 |
|---|---|
| `SERVER_HOST` | 服务器公网 IP |
| `SERVER_USER` | SSH 用户名（如 root / ubuntu）|
| `SERVER_PASSWORD` | SSH 密码 |

配置好后，每次 `git push main` 自动完成：构建 → 上传 JAR → 重启服务

---

## 三、查看日志
```bash
# 实时日志
journalctl -u bugsight-backend -f

# 应用日志文件
tail -f /var/log/bugsight/app.log
```

---

## 四、MinIO 图片存储（推荐正式环境）

```bash
sudo bash scripts/bootstrap_minio_service.sh
```

首次执行会生成 `/etc/minio/bugsight-minio.env` 模板，请填好 `MINIO_ROOT_USER / MINIO_ROOT_PASSWORD` 后重跑。

建议创建私有桶：

```bash
mc alias set bugsight http://127.0.0.1:9000 MINIO_ACCESS_KEY MINIO_SECRET_KEY
mc mb bugsight/bugsight-images
```

---

## 五、FastAPI 推理服务启动（当前主路线：25 类正样本优化）
```bash
sudo bash scripts/bootstrap_inference_service.sh
```

如果你已经训练完当前主方案模型，把 `/opt/bugsight/inference.env` 设成：

```bash
MODEL_PATH=/opt/bugsight/source/scripts/data/models/resnet50_ip102_positive/resnet50_ip102_positive.onnx
LABELS_PATH=/opt/bugsight/source/scripts/data/models/resnet50_ip102_positive/labels.json
```

---

## 六、AutoDL 正样本优化训练

直接在 AutoDL 上执行：

```bash
cd /root/autodl-tmp/bugsight-train
bash scripts/ml/autodl_train_positive_only.sh
```

默认会：
- 使用现有 25 类平衡数据集
- 训练 `ResNet50`
- 导出 `resnet50_ip102_positive.onnx`
- 写出带 `reject_threshold / margin_threshold` 的 `labels.json`

如果你以后想切回开放集方案，再使用：

```bash
bash scripts/ml/autodl_train_open_set.sh /root/autodl-tmp/bugsight-train/data/negative_non_insect
```

---

## 七、Nginx 配置
```nginx
server {
    listen 80;
    server_name your-domain-or-ip;

    # Vue 前端静态文件
    location / {
        root /var/www/bugsight-web/dist;
        try_files $uri $uri/ /index.html;
    }

    # Spring Boot API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 10m;
    }
}
```
