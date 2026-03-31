# NotifyHub 部署文档

## 部署方式

NotifyHub 支持以下部署方式：

1. **Docker Compose**（本地开发）
2. **Kubernetes**（生产环境）

---

## 方式一：Docker Compose 部署（本地开发）

### 前置条件

- Docker 20.10+
- Docker Compose 2.0+

### 快速启动

```bash
# 进入项目目录
cd notify-hub

# 启动所有服务
./scripts/start-dev.sh

# 或手动启动
docker-compose up -d
```

### 服务列表

| 服务 | 端口 | 说明 |
|------|------|------|
| gateway | 8080 | API 网关 |
| worker | 8081 | 投递 Worker |
| mysql | 3306 | MySQL 数据库 |
| redis | 6379 | Redis 缓存 |
| prometheus | 9090 | Prometheus 监控 |
| grafana | 3000 | Grafana 监控面板 |

### 访问服务

```bash
# Gateway API
curl http://localhost:8080/api/notify/health

# Prometheus
open http://localhost:9090

# Grafana (admin/admin123)
open http://localhost:3000
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看 Gateway 日志
docker-compose logs -f gateway

# 查看 Worker 日志
docker-compose logs -f worker
```

### 停止服务

```bash
docker-compose down

# 删除数据卷（谨慎使用）
docker-compose down -v
```

---

## 方式二：Kubernetes 部署（生产环境）

### 前置条件

- Kubernetes 1.20+
- kubectl 已配置
- Docker 镜像仓库

### 1. 准备配置

#### 修改 Secret

编辑 `deploy/common/secret.yaml`：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: notify-db-secret
  namespace: notify-hub
type: Opaque
stringData:
  username: root
  password: your-actual-password  # 修改为实际密码
```

#### 修改 ConfigMap

编辑 `deploy/gateway/configmap.yaml` 和 `deploy/worker/configmap.yaml`：

```yaml
data:
  DB_HOST: "your-mysql-host"      # 修改为实际 MySQL 地址
  DB_PORT: "3306"
  DB_NAME: "notify_hub"
  REDIS_HOST: "your-redis-host"   # 修改为实际 Redis 地址
  REDIS_PORT: "6379"
```

### 2. 构建 Docker 镜像

```bash
# 构建 Gateway 镜像
docker build -f docker/Dockerfile.gateway \
  -t your-registry/notify-hub/gateway:1.0.0 \
  .

# 构建 Worker 镜像
docker build -f docker/Dockerfile.worker \
  -t your-registry/notify-hub/worker:1.0.0 \
  .

# 推送到镜像仓库
docker push your-registry/notify-hub/gateway:1.0.0
docker push your-registry/notify-hub/worker:1.0.0
```

### 3. 更新镜像版本

编辑 `deploy/gateway/deployment.yaml` 和 `deploy/worker/deployment.yaml`：

```yaml
spec:
  template:
    spec:
      containers:
      - name: gateway
        image: your-registry/notify-hub/gateway:1.0.0  # 更新镜像版本
```

### 4. 部署到 K8s

#### 使用脚本部署

```bash
./scripts/deploy-k8s.sh
```

#### 手动部署

```bash
# 创建命名空间和 Secret
kubectl apply -f deploy/common/

# 部署 Gateway
kubectl apply -f deploy/gateway/

# 部署 Worker
kubectl apply -f deploy/worker/

# 查看部署状态
kubectl get all -n notify-hub
```

### 5. 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n notify-hub

# 查看服务状态
kubectl get svc -n notify-hub

# 查看 Gateway 日志
kubectl logs -f deployment/notify-gateway -n notify-hub

# 查看 Worker 日志
kubectl logs -f deployment/notify-worker -n notify-hub

# 测试 API
kubectl port-forward svc/notify-gateway 8080:80 -n notify-hub
curl http://localhost:8080/api/notify/health
```

### 6. 扩缩容

#### 手动扩缩容

```bash
# 扩容 Gateway
kubectl scale deployment notify-gateway --replicas=5 -n notify-hub

# 扩容 Worker
kubectl scale deployment notify-worker --replicas=10 -n notify-hub
```

#### 自动扩缩容（HPA）

HPA 已配置，会自动根据 CPU 和内存使用率扩缩容：

```bash
# 查看 HPA 状态
kubectl get hpa -n notify-hub

# 查看 HPA 详情
kubectl describe hpa notify-gateway-hpa -n notify-hub
```

---

## 数据库初始化

### 方式一：Flyway 自动迁移

应用启动时会自动执行 Flyway 迁移脚本：

```
db/migration/
├── V1__init_schema.sql
└── V2__add_index_optimization.sql
```

### 方式二：手动执行

```bash
# 连接 MySQL
mysql -h localhost -u root -p

# 创建数据库
CREATE DATABASE notify_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行迁移脚本
use notify_hub;
source db/migration/V1__init_schema.sql;
source db/migration/V2__add_index_optimization.sql;

# 执行初始化数据
source db/seed/V3__init_supplier_config.sql;
```

---

## 配置说明

### 环境变量

#### Gateway 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | MySQL 地址 |
| DB_PORT | 3306 | MySQL 端口 |
| DB_NAME | notify_hub | 数据库名 |
| DB_USERNAME | root | MySQL 用户名 |
| DB_PASSWORD | root123 | MySQL 密码 |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |
| SERVER_PORT | 8080 | 服务端口 |
| JAVA_OPTS | -Xms512m -Xmx512m | JVM 参数 |

#### Worker 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | MySQL 地址 |
| REDIS_HOST | localhost | Redis 地址 |
| SERVER_PORT | 8081 | 服务端口 |
| JAVA_OPTS | -Xms1g -Xmx1g | JVM 参数 |
| WORKER_BATCH_SIZE | 100 | 批量获取任务数 |
| WORKER_POLL_INTERVAL_MS | 1000 | 轮询间隔 |

### 应用配置

#### Gateway 配置（application.yml）

```yaml
app:
  notify:
    default-timeout-ms: 30000         # 默认超时
    default-max-retry-count: 10       # 默认最大重试次数
    rate-limit:
      enabled: true                   # 限流开关
      permits-per-second: 100         # 每秒请求数限制
```

#### Worker 配置（application.yml）

```yaml
app:
  notify:
    worker:
      batch-size: 100                 # 批量获取任务数
      poll-interval-ms: 1000          # 轮询间隔
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50    # 失败率阈值
        wait-duration-in-open-state: 30000  # 熔断等待时间
```

---

## 监控配置

### Prometheus 配置

编辑 `docker/prometheus.yml`：

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'notify-gateway'
    static_configs:
      - targets: ['gateway:8080']
    metrics_path: '/api/actuator/prometheus'
    
  - job_name: 'notify-worker'
    static_configs:
      - targets: ['worker:8081']
    metrics_path: '/api/actuator/prometheus'
```

### Grafana 配置

1. 登录 Grafana：http://localhost:3000 (admin/admin123)
2. 添加 Prometheus 数据源：http://prometheus:9090
3. 导入 Dashboard（ID: 10280 - Spring Boot 通用监控）

---

## 日志配置

### 日志级别

```yaml
logging:
  level:
    root: INFO
    com.company.notify.gateway: DEBUG
    com.company.notify.worker: DEBUG
```

### 日志文件

- **Gateway**: `logs/gateway/notify-gateway.log`
- **Worker**: `logs/worker/notify-worker.log`

### 日志轮转

- 保留天数：30 天
- 单文件大小：100MB

---

## 常见问题

### Q: Pod 无法启动

```bash
# 查看 Pod 状态
kubectl describe pod <pod-name> -n notify-hub

# 查看日志
kubectl logs <pod-name> -n notify-hub
```

**常见原因**：
- 数据库连接失败：检查 MySQL 地址和密码
- Redis 连接失败：检查 Redis 地址
- 镜像拉取失败：检查镜像仓库权限

### Q: 数据库迁移失败

```bash
# 查看 Flyway 迁移历史
SELECT * FROM flyway_schema_history;

# 删除迁移历史重新执行
TRUNCATE TABLE flyway_schema_history;
```

### Q: Redis 队列堆积

```bash
# 查看 Redis 队列大小
redis-cli zcard notify:queue

# 查看 Worker 日志
kubectl logs -f deployment/notify-worker -n notify-hub

# 扩容 Worker
kubectl scale deployment notify-worker --replicas=10 -n notify-hub
```

---

## 运维脚本

### 健康检查

```bash
#!/bin/bash
echo "Gateway 健康检查:"
curl -s http://localhost:8080/api/notify/health

echo -e "\nWorker 健康检查:"
curl -s http://localhost:8081/api/notify/health
```

### 备份数据库

```bash
#!/bin/bash
mysqldump -h localhost -u root -p notify_hub > notify_hub_backup.sql
```

### 恢复数据库

```bash
mysql -h localhost -u root -p notify_hub < notify_hub_backup.sql
```

---

**文档版本**: v1.0  
**最后更新**: 2024-01-XX
