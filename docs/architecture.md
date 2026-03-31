# NotifyHub 架构设计文档

## 1. 系统概述

NotifyHub 是一个高可靠、异步的内部通知服务，用于解耦内部业务系统与外部供应商 API 之间的通信。

### 1.1 核心目标

- **可靠性优先**：宁可重复投递，不可丢失消息
- **简单性**：避免过度设计，降低运维复杂度
- **可观测性**：所有投递行为必须可追踪、可审计

### 1.2 核心场景

```
用户注册  → 通知广告系统
支付成功  → 通知 CRM
订单完成  → 通知库存系统
```

## 2. 整体架构

### 2.1 架构图（逻辑视图）

```
┌─────────────┐
│  业务系统    │
└────────────┘
       │ 1. Submit API
       ▼
┌─────────────────┐
│ 通知服务 API 层  │─────┐
│  (Gateway)      │     │ 5. Lock & Fetch
└────────────────┘     │
       │ 2. Persist      ▼
       │            ┌──────────┐
       ├───────────→│  MySQL   │
       │ 3. Publish │          │
       │            └──────────┘
       ▼
┌─────────────────┐
│    Redis ZSet   │
│  (延迟队列)      │
└────────────────┘
       │ 4. Consume
       ▼
┌─────────────────┐
│ 投递 Worker 集群  │
│   (Worker)      │
└──────┬──────────┘
       │ 6. HTTP Request
       ▼
┌─────────────────┐
│ 外部供应商 API   │
└─────────────────┘
```

### 2.2 组件职责

| 组件 | 职责 | 技术选型 |
|------|------|---------|
| **Gateway** | 接收请求、持久化、推 Redis | Spring Boot + MyBatis |
| **Worker** | 轮询 Redis、HTTP 投递、重试 | Spring Boot + OkHttp |
| **MySQL** | 消息持久化、状态追踪 | MySQL 8.0 |
| **Redis** | 延迟队列、调度 | Redis 7 (ZSet) |

## 3. 核心设计

### 3.1 为什么选择 DB + Redis 而不是 Kafka？

| 维度 | DB + Redis | Kafka | 选择理由 |
|------|-----------|-------|---------|
| **可靠性** | ✅ 单事务保证 | ⚠️ 双写一致性复杂 | DB 更可靠 |
| **简单性** | ✅ 仅需 Redis | ❌ 需运维 Kafka | 符合简单性原则 |
| **QPS 支撑** | ~5000 | 10w+ | 内部服务足够 |
| **延迟** | 秒级 | 毫秒级 | 通知场景可接受 |
| **运维成本** | 低 | 高 | 降低运维负担 |

**结论**：对于内部通知服务（QPS < 5000），DB + Redis 方案更简单可靠。

### 3.2 数据一致性保障

#### 问题：如何保证 DB 和 Redis 的一致性？

```
场景：写 DB 成功，写 Redis 失败 → 消息丢失 ❌
```

**解决方案：先写 DB + 异步补偿**

```java
@Transactional
public Long submit(SubmitNotifyRequest request) {
    // 1. 写 DB（事务保证）
    NotifyTask task = buildTask(request);
    notifyTaskMapper.insert(task);
    
    // 2. 尝试写 Redis（事务外，允许失败）
    try {
        redisTemplate.zadd("notify:queue", executeAt, task.getId());
    } catch (Exception e) {
        // 记录日志，依靠补偿任务修复
        log.warn("Redis 写入失败，将触发补偿", e);
    }
    
    return task.getId();
}

// 补偿任务：每 5 秒执行一次
@Scheduled(fixedDelay = 5000)
public void compensate() {
    // 扫描 DB 中 PENDING 但不在 Redis 的任务
    List<NotifyTask> pendingTasks = findPendingTasks();
    Set<String> redisTasks = redisTemplate.zrange("notify:queue", 0, -1);
    
    for (NotifyTask task : pendingTasks) {
        if (!redisTasks.contains(task.getId().toString())) {
            redisTemplate.zadd("notify:queue", task.getExecuteAt(), task.getId());
            log.info("补偿任务：taskId={}", task.getId());
        }
    }
}
```

**一致性分析**：

| 场景 | 结果 | 影响 |
|------|------|------|
| DB 成功，Redis 成功 | ✅ 一致 | 无 |
| DB 成功，Redis 失败 | ⚠️ 短暂不一致 | 补偿任务修复（秒级延迟） |
| DB 失败，Redis 不执行 | ✅ 一致 | 无 |

**核心原则**：DB 是真实数据源，Redis 是性能优化。即使 Redis 挂了，消息也不丢失。

### 3.3 延迟队列设计

**为什么用 Redis ZSet？**

- 天然支持延迟队列（score = 执行时间戳）
- 高性能（Redis 内存操作）
- 简单可靠（无需额外组件）

**实现**：

```java
// API 层：推送到延迟队列
double score = executeAt.toEpochSecond();
redisTemplate.zadd("notify:queue", score, taskId);

// Worker 层：轮询到期任务
long now = System.currentTimeMillis() / 1000;
Set<String> taskIds = redisTemplate.zrangeByScore("notify:queue", 0, now);

// 移除已执行的任务
redisTemplate.zrem("notify:queue", taskIds);
```

### 3.4 重试策略

**指数退避算法**：

```java
private long calculateBackoffDelay(int retryCount) {
    // 1s, 5s, 30s, 1m, 5m, 30m...
    double base = 1.0; // 1 秒
    double delay = base * Math.pow(5, retryCount - 1);
    
    // 限制最大延迟 30 分钟
    return Math.min((long) delay, 30 * 60);
}
```

| 重试次数 | 延迟时间 | 累计时间 |
|---------|---------|---------|
| 1 | 1s | 1s |
| 2 | 5s | 6s |
| 3 | 30s | 36s |
| 4 | 1m | 1m36s |
| 5 | 5m | 6m36s |
| 6 | 30m | 36m36s |
| ... | ... | ~24h |

### 3.5 熔断隔离

**为什么需要熔断？**

防止单个供应商故障拖垮整个服务。

**Resilience4j 配置**：

```yaml
circuit-breaker:
  enabled: true
  failure-rate-threshold: 50      # 失败率超过 50% 触发
  slow-call-rate-threshold: 50    # 慢调用超过 50% 触发
  wait-duration-in-open-state: 30s # 熔断后 30s 恢复
  sliding-window-size: 100        # 统计窗口大小
```

**状态机**：

```
CLOSED → OPEN（失败率超过阈值）
OPEN → HALF_OPEN（等待时间结束）
HALF_OPEN → CLOSED（成功）或 OPEN（失败）
```

## 4. 数据库设计

### 4.1 核心表结构

#### notify_task（通知任务表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 任务 ID |
| biz_id | VARCHAR(64) | 业务 ID（幂等性） |
| supplier_type | VARCHAR(32) | 供应商类型 |
| target_url | VARCHAR(512) | 目标 URL |
| status | VARCHAR(20) | 任务状态 |
| retry_count | INT | 重试次数 |
| max_retry_count | INT | 最大重试次数 |
| next_retry_at | DATETIME | 下次重试时间 |
| created_at | DATETIME | 创建时间 |

**索引设计**：

```sql
-- 幂等性查询
UNIQUE KEY `uk_biz_id` (`biz_id`)

-- 轮询查询
KEY `idx_status_next_retry` (`status`, `next_retry_at`)

-- 死信查询
KEY `idx_status_dead_letter` (`status`, `updated_at`)
```

### 4.2 状态机

```
PENDING → PROCESSING → SUCCESS
                     → FAILED → (重试) → PENDING
                              → (超过最大重试) → DEAD_LETTER
```

## 5. API 设计

### 5.1 提交通知

```http
POST /api/notify/submit
Content-Type: application/json

{
  "bizId": "order-123456",
  "supplierType": "INVENTORY",
  "targetUrl": "http://inventory.internal/api/notify",
  "httpMethod": "POST",
  "headers": "{\"Authorization\":\"Bearer xxx\"}",
  "body": "{\"orderId\":\"123456\",\"status\":\"completed\"}",
  "timeoutMs": 30000,
  "maxRetryCount": 10
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": 123456  // 任务 ID
}
```

### 5.2 查询状态

```http
GET /api/notify/status/{taskId}
```

## 6. 监控设计

### 6.1 核心指标

```prometheus
# 提交总数
notify_task_submitted_total

# 投递成功数
notify_task_delivered_total

# 投递失败数
notify_task_failed_total

# 死信数
notify_task_dead_letter_total

# 投递耗时
notify_delivery_duration_seconds

# Redis 队列大小
notify_redis_queue_size
```

### 6.2 告警规则

```yaml
groups:
- name: notify-hub
  rules:
  - alert: HighFailureRate
    expr: rate(notify_task_failed_total[5m]) > 0.1
    for: 5m
    annotations:
      summary: "投递失败率过高"
  
  - alert: DeadLetterAccumulation
    expr: notify_task_dead_letter_total > 100
    for: 10m
    annotations:
      summary: "死信任务堆积"
```

## 7. 部署架构

### 7.1 K8s 部署

```yaml
# Gateway Deployment
replicas: 2
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"

# Worker Deployment
replicas: 3
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

### 7.2 扩缩容策略

**HPA 配置**：

- **Gateway**: 2-10 副本（CPU 70%，内存 80%）
- **Worker**: 3-20 副本（CPU 70%，内存 80%）

## 8. 容灾设计

### 8.1 故障场景

| 故障 | 影响 | 应对措施 |
|------|------|---------|
| Redis 宕机 | 投递延迟 | 降级 DB 轮询，消息不丢失 |
| MySQL 宕机 | 无法提交 | 直接失败，不写 Redis |
| Worker 宕机 | 投递暂停 | K8s 自动重启，消息不丢失 |
| 外部 API 宕机 | 投递失败 | 熔断 + 重试，转入死信 |

### 8.2 降级策略

```java
// Redis 不可用时，切换到 DB 轮询
@Scheduled(fixedDelay = 10000)
public void fallbackToDB() {
    if (!redisHealthCheck()) {
        switchToDBPollingMode();
        log.warn("已降级为 DB 轮询模式");
    }
}
```

## 9. 演进路线

### Phase 1（当前）

- ✅ 基础投递功能
- ✅ 重试机制
- ✅ 监控指标

### Phase 2（规划中）

- [ ] 管理后台
- [ ] 批量提交
- [ ] 模板渲染

### Phase 3（未来）

- [ ] 多租户支持
- [ ] 消息优先级
- [ ] 支持多种通知类型（邮件、短信）

## 10. 设计决策总结

| 决策 | 选择 | 理由 |
|------|------|------|
| **消息队列** | Redis ZSet | 简单、支持延迟队列、运维成本低 |
| **持久化** | MySQL | 可靠、事务保证、易查询 |
| **重试策略** | 指数退避 | 平衡实时性和外部系统压力 |
| **熔断器** | Resilience4j | 轻量、与 Spring Boot 集成好 |
| **部署方式** | K8s | 自动扩缩容、故障自愈 |

---

**文档版本**: v1.0  
**最后更新**: 2024-01-XX
