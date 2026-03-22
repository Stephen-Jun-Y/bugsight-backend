# BugSight Backend — 部署说明

## 服务器环境要求
- Ubuntu 22.04 LTS
- Java 17（`apt install openjdk-17-jdk`）
- MySQL 8.0
- Python 3.10+（用于 FastAPI 推理服务）

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

## 四、FastAPI 推理服务启动
```bash
cd /opt/bugsight/source/scripts/ml
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

MODEL_PATH=/opt/bugsight/source/scripts/data/models/resnet50_ip102_balanced/resnet50_ip102_balanced.onnx \
LABELS_PATH=/opt/bugsight/source/scripts/data/models/resnet50_ip102_balanced/labels.json \
uvicorn predict_api:app --host 127.0.0.1 --port 8000 --workers 2
```

---

## 五、Nginx 配置
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
