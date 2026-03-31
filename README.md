# NotifyHub

高可靠、异步的内部通知服务，用于解耦内部业务系统与外部供应商 API 之间的通信。

## 1. 项目概述

### 1.1 问题本质

从需求上来看，这是一个**异步可靠消息投递**问题，需要解决：
- **解耦**：业务系统不需要等待外部 API 响应
- **可靠性**：确保通知不丢失、最终送达
- **容错**：处理网络波动、外部系统故障
- **可观测**：知道哪些成功了、哪些失败了

### 1.2 设计原则

| 维度 | 我的选择 | 理由 |
|------|----------|------|
| 可靠性 | 至少一次 + 补偿 | 平衡成本与效果 |
| 一致性 | 最终一致 | 可用性优先 |
| 架构 | 简单够用 | 不过度设计 |
| 扩展性 | 接口抽象 | 预留演进空间 |
| 监控 | 实用主义 | 能解决问题就行 |

> **核心思想**：用最简单的方案，解决最本质的问题。不为假设的未来付费。

### 1.3 系统架构

```
业务系统 → Gateway(API) → DB + Redis → Worker → 外部供应商 API
                ↓                        ↓
            管理后台                  监控告警
```

DB在这里主要是用于结果的审计和查询。如果在少数极端情况，比如秒杀，可以临时修改代码断开DB来保证QPS

**核心组件说明**：

- **Gateway**：接收业务系统请求，写入 DB + Redis，保证事务性.当外部压力相对大时，可以横向增加gateway的实例数目，
- **Worker**：轮询 Redis，执行 HTTP 投递，支持重试和熔断。当发现有太多的任务积压时，可以横向增加worker的实例数目

**关键机制**：

1. **幂等性**：用户提交请求时需指定业务id `bizId`，Gateway 用其做幂等校验。重复提交返回：
   ```json
   {"code":500,"message":"提交失败：任务已存在，请勿重复提交（bizId: test-123451）","data":null}
   ```

2. **供应商配置**：通过供应商代码 `supplierCode` 关联 `supplier-config.yml` 中的模板配置，实现不同供应商的header/body差异化处理.有新的供应商只要修改这个配置文件即可。这里要求用户在提交请求时就要提供相应的供应商id和header/body相应的值，worker需要根据这个id来找到target URL和body/header模板并填入相应的值。这里的原则是谁提交谁负责目标信息。本系统只保证从消息从用户到供应商之间的可靠送达，并不会根据业务规则来处理源到目标的匹配工作，这不是本系统的目的。如果后续模版太多，可以将配置文件放入配置中心。

3. **消息队列抽象**：定义 `MessageQueueService` 接口，当前实现 `RedisMessageQueueService`，便于后续扩展到 Kafka 等中间件

4. **部署方式**：支持 Docker Compose（本地开发）和 Kubernetes（生产环境）


### 1.4 AI使用说明
在刚开始做本项目的架构设计时，
- 作者已经有了将gateway和worker分开实现的想法，除了将需求和AI讨论之外，将这个想法也和AI进行了讨论，也得到了AI的认可，不仅是功能分离，并且分开实现确实有利于对gateway和worker的单独横向扩展
- 对于运行环境的选择，我和AI讨论提出是用云还是k8s，从几个方面，比如成本结构，中间件选型灵活性和供应商锁定等方面进行比较，最后AI提出采用本地调试采用docker + 生产环境用k8s的方案，这是一个相对稳妥且比较可性的方案，因为考虑到大多公司都有自己的k8s, 它对Java友好、成本可控、无供应商锁定、中间件灵活，我于是就采用了这个方案
- 在技术栈的选择上，AI提出用GO,因为它对高并发I/O友好，编译型单二进制部署简单，适合网络密集型任务。但我本人对GO不熟悉，只能选择springBoot + java的方案。另外AI还提出消息中间件用kafka, 但我考虑到kafaka的模式比较重，维护成本相对较高，所以这里对于初期阶段采用了更轻量的但支持高并发的redis来做为消息中间件，而且Redis ZSet天然支持延迟队列。但这里同时保留了后面可以改用kafka的接口。
- 对于可靠性的讨论，我和AI都认为最少一次（at least once）已经足够，『exactly once』的实现太过复杂，而且下游可能还用不上,所以这里就只是实现了最少一次的功能。

在功能的具体实现时，
- 在幂等方面，我先前提出用业务id + supplierCode做幂等，但AI认为每一笔业务有其独一无二的id，已经足够，没必要再加上supllierCode.这里我采用了它的建议。
- 虽然AI编写了大部分的代码和配置文件，但是还是遇到了如下的显著问题：
  - AI没有将消息中间件和具体的实现做解耦，这个问题经过专门的指出才后续修正，重新定义了`MessageQueueService` 接口，并实现 `RedisMessageQueueService`。
  - AI没有将supplierCode和header/body模版做匹配，对于所有的供应商代码，只使用了一个模版。后经指出后变了相应的更改。
  - AI建议业务系统可以实时接收投递结果，但我没有采纳这个建议。需求明确业务系统不关心返回值。轮询 API 或仅通过日志/告警查看即可，减少长连接维护成本。


## 2. 系统边界

### 选择在这个系统中解决的问题

| 问题 | 解决方案 | 为什么解决 |
|------|----------|------------|
| 异步解耦 | Gateway 接收请求后立即返回，Worker 异步投递 | 业务系统不需要等待外部 API 响应，降低延迟 |
| 可靠持久化 | 立即写入 MySQL | 确保任务不丢失，有兜底 |
| 延迟队列 | Redis ZSet + score 时间戳 | 支持精确的延迟投递（如 5 分钟后重试） |
| 自动重试 | 指数退避策略（1s, 5s, 30s...） | 处理网络抖动和临时故障 |
| 状态追踪 | 状态机（PENDING → PROCESSING → SUCCESS/FAILED） | 知道每个任务的当前状态 |
| 熔断隔离 | Resilience4j 按供应商维度熔断 | 防止单个供应商拖垮整个服务 |
| 补偿机制 | 定时扫描 DB 补偿 Redis 缺失任务 | 防御性设计，Redis 丢数据也能恢复 |
| 可观测性 | Prometheus 指标 + Grafana 面板 | 能看到系统运行状态 |

### ❌ 我明确选择不解决的问题

| 问题 | 不解决的原因 |
|------|--------------|
| 精确一次投递（Exactly Once） | ❌ 过度设计<br>- 外部供应商 API 本身不保证幂等<br>- 即使我们做到精确一次，对方重复接收也没意义<br>- 选择：至少一次（At Least Once） + 业务侧幂等 |
| 消息顺序性 | ❌ 场景不需要<br>- 通知类任务没有严格的先后顺序要求<br>- 强顺序会严重限制并发能力<br>- 例外： 如果有"创建→更新→删除"的顺序需求，会在 bizId 中体现 |
| 事务消息（2PC/TCC） | ❌ 复杂度过高<br>- 需要分布式事务协调器<br>- 性能损耗大（同步阻塞）<br>- 选择：最终一致性 + 补偿机制足够 |
| 多租户/权限管理 | ❌ 第一版不需要<br>- 内部系统，调用方可信<br>- 未来需要时再加 OAuth2/JWT |
| 消息压缩/加密 | ❌ 内网环境不需要<br>- 内网通信，安全性要求低<br>- 流量不大（QPS < 5000），带宽不是瓶颈 |
| 批量消费 ACK | ❌ 增加复杂度<br>- 单个任务独立 ACK 更简单<br>- Redis 没有原生批量 ACK 支持 |
| 动态路由规则 | ❌ 过早优化<br>- 供应商信息在提交时就确定<br>- 不需要运行时动态路由 |

**判断依据：**
- ✅ 解决核心痛点：可靠投递 > 其他功能
- ❌ 不引入不必要的复杂度：能手动处理的就不自动化
- ❌ 不为假设的需求设计：等有真实需求再加


## 3. 可靠性与失败处理

### 3.1 投递语义选择

📌 **选择：至少一次（At Least Once）**
**理由：**

| 语义 | 实现成本 | 适用场景 | 我的选择 |
|------|----------|----------|----------|
| 至多一次 | 低 | 日志收集、监控上报 | ❌ 不适合（可能丢消息） |
| 精确一次 | 极高 | 金融转账、订单支付 | ❌ 过度设计 |
| 至少一次 | 中等 | 通知、邮件、短信 | ✅ 最佳平衡 |

**实现方式：**
```java
// Worker 投递逻辑
@Scheduled(fixedDelay = 1000)
public void pollAndDeliver() {
    // 1. 先更新状态为 PROCESSING（防止其他 Worker 重复消费）
    updateTaskStatus(taskId, PROCESSING);
    
    // 2. 执行 HTTP 投递
    try {
        boolean success = httpClient.execute(request);
        
        // 3. 成功 → SUCCESS
        if (success) {
            updateTaskStatus(taskId, SUCCESS);
        } 
    } catch (Exception e) {
        // 4. 失败 → FAILED + 重试
        if (retryCount < maxRetryCount) {
            reschedule(taskId, backoffDelay);
        } else {
            updateTaskStatus(taskId, DEAD_LETTER);
        }
    }
}
```

**为什么不是精确一次？**
- 外部供应商 API 本身不保证幂等性
- 即使我们做到精确一次，对方服务器重启也可能导致重复处理
- **正确做法**：让业务方在 bizId 中携带唯一标识，供应商侧做幂等校验

### 外部系统失败或长期不可用的处理策略

#### 短期失败（< 30 分钟）
```
第 1 次失败 → 1 秒后重试
第 2 次失败 → 5 秒后重试
第 3 次失败 → 30 秒后重试
第 4 次失败 → 1 分钟后重试
第 5 次失败 → 5 分钟后重试
第 6 次失败 → 30 分钟后重试
```
**策略**：指数退避，给足恢复时间

#### 中期失败（30 分钟 - 24 小时）
- 继续重试，但间隔固定在 30 分钟
- 最多重试 10 次（覆盖约 24 小时）
- **假设**：大部分故障能在 24 小时内恢复

#### 长期失败（> 24 小时 / 超过最大重试次数）
```sql
-- 转入死信队列
UPDATE notify_task
SET status = 'DEAD_LETTER',
    failure_reason = 'MAX_RETRY_EXCEEDED'
WHERE id = ?;
```

**处理方式：**
- **告警通知**：发送钉钉/企业微信告警
- **人工介入**：运维人员查看死信任务
- **手动重发**：
  ```sql
  UPDATE notify_task
  SET status = 'PENDING', retry_count = 0
  WHERE id = ?;
  ```
- **批量降级**：如果是某个供应商长期挂掉，临时关闭该供应商的入口

#### 极端情况：Redis 完全不可用
```java
// 补偿任务检测到 Redis 不可用时
@Scheduled(fixedDelay = 5000)
public void compensateMissingInQueue() {
    try {
        redisTemplate.opsForValue().get("health:check");
    } catch (Exception e) {
        log.error("Redis 连接异常！");
        // 降级方案：直接轮询 DB
        // 性能差，但能保证基本可用
        pollFromDatabase();
    }
}
```

**降级策略：**
- **正常模式**：DB 持久化 → Redis 队列 → Worker 消费
- **降级模式**：DB 轮询 → Worker 消费
- **代价**：性能下降，但不会丢消息

#### 最极端情况：MySQL + Redis 都挂了
**答案**：不处理，直接宕机

**理由：**
- 这种概率极低
- 如果数据库和缓存同时挂，说明基础设施有严重问题
- 应该优先修复基础设施，而不是在应用层打补丁

## 4. 取舍与演进

### 我认为"过度设计"而不采纳的方案

| 过度设计 | 为什么不采纳 | 我的替代方案 |
|----------|--------------|--------------|
| Kafka/RocketMQ | ❌ 运维成本高<br>❌ 学习曲线陡<br>❌ 对于 QPS<5000 杀鸡用牛刀 | ✅ Redis ZSet<br>简单够用，团队熟悉 |
| 分布式事务（Seata） | ❌ 性能损耗大<br>❌ 引入额外依赖<br>❌ 调试困难 | ✅ 最终一致性<br>DB + Redis + 补偿 |
| 精确一次语义 | ❌ 需要两阶段提交<br>❌ 外部系统不支持<br>❌ 实现复杂度极高 | ✅ 至少一次 + 业务幂等<br>简单有效 |
| 动态配置中心（Nacos） | ❌ 第一版不需要热更新<br>❌ 增加部署复杂度 | ✅ 配置文件 + 重启<br>够用 |
| 链路追踪（SkyWalking） | ❌ 内部系统调用链简单<br>❌ 性能开销 | ✅ 日志 + Prometheus<br>能定位问题即可 |
| 容器服务网格（Istio） | ❌ 过于重量级<br>❌ 学习成本极高 | ✅ Spring Cloud + Docker<br>简单直接 |
| CQRS 架构 | ❌ 读写比例不悬殊<br>❌ 增加维护成本 | ✅ 单一数据源<br>MySQL 兼顾读写 |

**判断标准：**
- 是否解决当前痛点？ （是 → 采纳，否 → 拒绝）
- 投入产出比如何？ （ROI > 3 → 考虑，否则 → 拒绝）
- 团队能否 Hold 住？ （不能 → 坚决拒绝）

### 📈 未来演进的路线

#### 阶段 1：当前（QPS < 5000）
- ✅ DB + Redis ZSet
- ✅ 单机 Redis + 主从 MySQL
- ✅ 手动部署

#### 阶段 2：增长期（QPS 5000-20000）

**瓶颈：**
- Redis 单点内存不足
- MySQL 写入压力大

**演进方案：**
```yaml
# 1. Redis Cluster 分片
redis:
  nodes: 6  # 3 主 3 从
  slots: 16384

# 2. MySQL 读写分离
mysql:
  master: 1  # 写
  slaves: 2  # 读

# 3. Worker 水平扩展
worker:
  replicas: 5  # K8s HPA 自动扩缩容
```

**关键改造：**
- Redis 分片后，任务 ID 需要 hash 到不同节点
- Worker 需要支持分布式锁（防止重复消费）

#### 阶段 3：大规模（QPS > 20000）

**瓶颈：**
- Redis 延迟成为瓶颈
- MySQL 写入跟不上

**演进方案 A：引入 Kafka**
```
业务系统 → Gateway → Kafka → Worker → 外部 API
                ↓
             MySQL (异步批量写入)
```

**优势：**
- Kafka 吞吐量大（百万级 TPS）
- 解耦更彻底
- 支持回溯消费

**代价：**
- 运维复杂度增加
- 需要 Zookeeper

**演进方案 B：分库分表**
```yaml
# 按供应商类型分库
notify_inventory_db   # 库存通知
notify_crm_db         # CRM 通知
notify_ad_db          # 广告通知

# 按时间分表
notify_task_202603    # 2026 年 3 月
notify_task_202604    # 2026 年 4 月
```

#### 阶段 4：超大规模（QPS > 100000）

**可能的方向：**
- **边缘计算**：在业务系统就近部署 Edge Worker
- **智能路由**：根据供应商地理位置选择最优路由
- **AI 预测**：基于历史数据预测高峰期，提前扩容
- **Serverless**：用 AWS Lambda/阿里云函数计算替代 Worker

### 🎯 我的演进原则

- **不预先优化**
  - 等有明确的性能瓶颈再改
  - 现在的代码能支撑未来 2-3 倍增长

- **可逆决策优先**
  - 如果发现选错了，能低成本回退
  - 例如：Redis → Kafka 容易，Kafka → Redis 难

- **保持接口抽象**
  - MessageQueueService 就是为演进准备的
  - 切换底层实现不影响业务逻辑

- **数据驱动决策**


## 5. 核心特性与技术栈

### 5.1 核心特性

- ✅ **可靠性优先**：消息持久化 + 重试机制，确保通知不丢失
- ✅ **异步投递**：将 HTTP 请求从业务主流程中剥离，降低业务系统延迟
- ✅ **自动重试**：指数退避策略，处理网络波动和外部系统临时故障
- ✅ **熔断限流**：防止单个供应商故障拖垮整个服务
- ✅ **可观测性**：完整的状态追踪和监控指标（Prometheus）
- ✅ **死信处理**：最终失败的消息支持人工干预

### 5.2 技术栈

- **运行环境**: Java 11
- **框架**: Spring Boot 2.7.18
- **数据库**: MySQL 8.0
- **缓存/队列**: Redis 7 (ZSet 延迟队列)
- **ORM**: MyBatis Plus
- **HTTP 客户端**: OkHttp 4
- **熔断器**: Resilience4j
- **监控**: Prometheus + Grafana
- **部署**: Docker + Kubernetes

## 6. 快速开始

### 本地开发（Docker Compose）

```bash
mkdir -p logs/gateway logs/worker

# 启动所有服务（MySQL + Redis + Gateway + Worker 
docker-compose up -d mysql redis gateway worker

# 查看日志
docker-compose logs -f gateway
docker-compose logs -f worker

# 停止服务
docker-compose down
```

### 访问服务

| 服务 | 地址 | 说明 |
|------|------|------|
| Gateway API | http://localhost:8080/api | 通知提交接口 |
| Worker | http://localhost:8081/api | 内部服务 |
| Prometheus | http://localhost:9090 | 监控指标 |
| Grafana | http://localhost:3000 | 监控面板 (admin/admin123) |

### 提交通知示例

```bash
curl -X POST http://localhost:8080/api/notify/submit \
  -H "Content-Type: application/json" \
  -d '{
    "bizId": "biz-123456",
    "supplierType": "INVENTORY",
    "headers": "{\"Authorization\":\"Bearer xxx\"}",
    "body": "{\"orderId\":\"123456\",\"status\":\"completed\"}"
  }'
```

### 查询任务状态

```bash
curl http://localhost:8080/api/notify/status/1
```

## 7. 项目结构

```
notify-hub/
├── notify-common/          # 公共模块（模型、DTO、枚举、工具类）
├── notify-gateway/         # 网关服务（接收请求、持久化、推 Redis）
├── notify-worker/          # 投递服务（轮询 Redis、HTTP 投递、重试）
├── db/
│   ├── migration/          # Flyway 数据库迁移脚本
│   └── seed/               # 初始化数据
├── deploy/                 # K8s 部署配置
│   ├── gateway/
│   ├── worker/
│   └── common/
├── docker/                 # Docker 相关配置
│   ├── Dockerfile.gateway
│   ├── Dockerfile.worker
│   ├── prometheus.yml
│   └── grafana/
├── docker-compose.yml      # 本地开发环境
├── pom.xml                 # 父 POM
└── scripts/                # 运维脚本
```

## 核心设计

### 可靠性保障

1. **消息持久化**：所有通知任务立即写入 MySQL，确保不丢失
2. **Redis 延迟队列**：使用 ZSet 实现高效的延迟队列，支持精确调度
3. **补偿机制**：定时扫描 DB，补偿 Redis 中缺失的任务
4. **指数退避重试**：1s, 5s, 30s, 1m, 5m, 30m... 最大 10 次
5. **熔断隔离**：按供应商维度实现熔断，防止故障扩散

### 状态机

```
PENDING → PROCESSING → SUCCESS
                     → FAILED → (重试) → PENDING
                              → (超过最大重试) → DEAD_LETTER
```

### 重试策略

| 重试次数 | 延迟时间 | 累计时间 |
|---------|---------|---------|
| 1 | 1s | 1s |
| 2 | 5s | 6s |
| 3 | 30s | 36s |
| 4 | 1m | 1m36s |
| 5 | 5m | 6m36s |
| 6 | 30m | 36m36s |
| ... | ... | ... |
| 10 | 30m | ~24h |

### 失败处理

| HTTP 状态码 | 处理方式 |
|------------|---------|
| 2xx | 成功，更新状态为 SUCCESS |
| 400/401/403 | 配置错误，不重试，标记为 FAILED |
| 429 | 被限流，触发退避重试 |
| 5xx | 服务端错误，触发退避重试 |
| 网络异常 | 触发退避重试 |

## 9. 配置说明

### Gateway 配置

```yaml
app:
  notify:
    default-timeout-ms: 30000         # 默认超时
    default-max-retry-count: 10       # 默认最大重试次数
    rate-limit:
      enabled: true                   # 限流开关
      permits-per-second: 100         # 每秒请求数限制
```

### Worker 配置

```yaml
app:
  notify:
    worker:
      batch-size: 100                 # 批量获取任务数
      poll-interval-ms: 1000          # 轮询间隔
      http:
        connect-timeout-ms: 5000
        read-timeout-ms: 30000
        write-timeout-ms: 30000
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50    # 失败率超过 50% 触发熔断
        wait-duration-in-open-state: 30000  # 熔断后 30s 恢复
```

## K8s 部署

### 部署到 Kubernetes

```bash
# 创建命名空间和 Secret
kubectl apply -f deploy/common/

# 部署 Gateway
kubectl apply -f deploy/gateway/

# 部署 Worker
kubectl apply -f deploy/worker/

# 查看状态
kubectl get pods -n notify-hub
kubectl get hpa -n notify-hub
```

### 扩缩容

Gateway 和 Worker 都配置了 HPA（Horizontal Pod Autoscaler），会根据 CPU 和内存使用率自动扩缩容：

- **Gateway**: 2-10 个副本
- **Worker**: 3-20 个副本

## 11. 监控告警

### Prometheus 指标

- `notify_task_submitted_total`: 提交的任务总数
- `notify_task_delivered_total`: 成功投递的任务数
- `notify_task_failed_total`: 投递失败的任务数
- `notify_task_dead_letter_total`: 转入死信的任务数
- `notify_delivery_duration_seconds`: 投递耗时分布
- `notify_redis_queue_size`: Redis 队列中的任务数

### Grafana 面板

访问 http://localhost:3000 查看预置的监控面板。

## 运维指南

### 查看死信任务

```sql
SELECT * FROM notify_task 
WHERE status = 'DEAD_LETTER' 
ORDER BY updated_at DESC 
LIMIT 100;
```

### 人工重发死信任务

```sql
UPDATE notify_task 
SET status = 'PENDING', 
    retry_count = 0, 
    next_retry_at = NOW() 
WHERE id = <task_id>;
```

### 日志位置

- Gateway: `logs/gateway/notify-gateway.log`
- Worker: `logs/worker/notify-worker.log`

## 13. 常见问题

### Q: 为什么不用 Kafka？

A: 对于内部通知服务（QPS < 5000），DB + Redis 方案更简单可靠：
- 单事务保证 DB 和 Redis 的最终一致性
- 降低运维复杂度
- Redis ZSet 天然支持延迟队列

### Q: 如何保证幂等性？

A: 业务系统提交时需携带唯一的 `bizId`，Gateway 层会进行幂等性校验。

### Q: Redis 挂了怎么办？

A: 
1. 消息不丢失（DB 有持久化）
2. 补偿任务会检测到 Redis 不可用
3. 可临时降级为 DB 轮询模式

### Q: 如何调整重试策略？

A: 修改 `SubmitNotifyRequest` 中的 `maxRetryCount` 参数，或在代码中调整 `calculateBackoffDelay` 方法。
