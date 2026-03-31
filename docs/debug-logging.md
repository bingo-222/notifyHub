# 调试日志说明

本文档说明 NotifyHub 中的调试日志输出格式和位置。

## 日志级别说明

项目中使用以下日志级别：

- **INFO**: 关键业务流程节点
- **DEBUG**: 详细调试信息
- **WARN**: 警告信息（不影响流程）
- **ERROR**: 错误信息（影响流程）

## Gateway 日志

### 1. 任务提交流水线

```
【Gateway】任务已持久化 - Task 结构体：taskId=123, bizId=order-123456, supplierType=INVENTORY, 
  url=http://inventory.internal/api/notify, method=POST, 
  body={"orderId":"123456","status":"completed"}...(100 chars), 
  status=PENDING, retryCount=0, maxRetryCount=10

【Gateway】任务已推送到队列 - Task 结构体：taskId=123, queueKey=notify:queue, 
  score=1711881600.0, nextRetryAt=2024-03-31T10:00:00
```

**日志位置**：`NotifyService.submit()` 和 `pushToQueue()`

**包含字段**：
- taskId: 任务 ID
- bizId: 业务 ID
- supplierType: 供应商类型
- url: 目标 URL
- method: HTTP 方法
- body: 请求体（截断到 200 字符）
- status: 任务状态
- retryCount: 当前重试次数
- maxRetryCount: 最大重试次数
- queueKey: Redis 队列键
- score: Redis ZSet 分数（执行时间戳）
- nextRetryAt: 下次重试时间

### 2. 日志文件位置

```
logs/gateway/notify-gateway.log
```

## Worker 日志

### 1. 任务投递流水线

```
【Worker】从 DB 加载任务 - Task 结构体：taskId=123, bizId=order-123456, 
  supplierType=INVENTORY, url=http://inventory.internal/api/notify, 
  method=POST, body={"orderId":"123456"}...(50 chars), 
  status=PENDING, retryCount=0/10, nextRetryAt=2024-03-31T10:00:00

【Worker.Delivery】开始投递任务 - Task 结构体：taskId=123, 
  url=http://inventory.internal/api/notify, method=POST, 
  headers={"Content-Type":"application/json"}...(50 chars), 
  body={"orderId":"123456"}...(50 chars), retryCount=0/10, timeout=30000

【Worker.Delivery】投递响应：taskId=123, statusCode=200, response={"code":200,"msg":"success"}

【Worker】任务投递成功：taskId=123, bizId=order-123456
```

**日志位置**：
- `DeliveryScheduler.deliverTask()` - 从 DB 加载任务
- `DeliveryService.deliver()` - HTTP 投递
- `DeliveryScheduler` - 投递结果

**包含字段**：
- taskId: 任务 ID
- bizId: 业务 ID
- supplierType: 供应商类型
- url: 目标 URL
- method: HTTP 方法
- headers: 请求头（截断到 200 字符）
- body: 请求体（截断到 200 字符）
- status: 任务状态
- retryCount/maxRetryCount: 重试进度
- nextRetryAt: 下次重试时间
- timeout: 超时时间（毫秒）
- statusCode: HTTP 响应码
- response: HTTP 响应体（截断到 500 字符）

### 2. 失败重试日志

```
【Worker】任务投递失败，已安排重试：taskId=123, retryCount=1, delay=5s

【Worker.Delivery】服务端错误，将重试：taskId=123, statusCode=503

【Worker.Delivery】投递失败：taskId=123, httpCode=503, retryCount=1, error=服务端错误：503
```

### 3. 死信日志

```
【Worker】任务投递失败，已转入死信：taskId=123, bizId=order-123456, retryCount=10
```

### 4. 日志文件位置

```
logs/worker/notify-worker.log
```

## 日志前缀说明

| 前缀 | 说明 | 位置 |
|------|------|------|
| 【Gateway】 | Gateway 服务关键流程 | NotifyService |
| 【Worker】 | Worker 服务关键流程 | DeliveryScheduler |
| 【Worker.Delivery】 | Worker 投递详细流程 | DeliveryService |

## 调试技巧

### 1. 实时查看日志

```bash
# 查看 Gateway 日志
tail -f logs/gateway/notify-gateway.log | grep "Task 结构体"

# 查看 Worker 日志
tail -f logs/worker/notify-worker.log | grep "Task 结构体"

# 查看投递失败日志
tail -f logs/worker/notify-worker.log | grep "投递失败"
```

### 2. 追踪单个任务

```bash
# 根据 taskId 追踪
tail -f logs/gateway/notify-gateway.log | grep "taskId=123"
tail -f logs/worker/notify-worker.log | grep "taskId=123"
```

### 3. 根据业务 ID 追踪

```bash
# 根据 bizId 追踪
tail -f logs/gateway/notify-gateway.log | grep "bizId=order-123456"
```

### 4. Docker Compose 环境查看日志

```bash
# 查看 Gateway 日志
docker-compose logs -f gateway | grep "Task 结构体"

# 查看 Worker 日志
docker-compose logs -f worker | grep "Task 结构体"
```

### 5. K8s 环境查看日志

```bash
# 查看 Gateway 日志
kubectl logs -f deployment/notify-gateway -n notify-hub | grep "Task 结构体"

# 查看 Worker 日志
kubectl logs -f deployment/notify-worker -n notify-hub | grep "Task 结构体"
```

## 日志字段详解

### NotifyTask 结构体

```java
NotifyTask {
    id: Long                    // 任务 ID（日志：taskId）
    bizId: String               // 业务 ID（日志：bizId）
    supplierType: String        // 供应商类型（日志：supplierType）
    targetUrl: String           // 目标 URL（日志：url）
    httpMethod: String          // HTTP 方法（日志：method）
    headers: String             // 请求头 JSON（日志：headers）
    body: String                // 请求体 JSON（日志：body）
    status: String              // 任务状态（日志：status）
    retryCount: Integer         // 当前重试次数（日志：retryCount）
    maxRetryCount: Integer      // 最大重试次数（日志：maxRetryCount）
    nextRetryAt: LocalDateTime  // 下次重试时间（日志：nextRetryAt）
    lastDeliveredAt: LocalDateTime  // 最后投递时间
    failureReason: String       // 失败原因
    lastErrorMessage: String    // 最后错误信息
    extData: String             // 扩展数据
    createdAt: LocalDateTime    // 创建时间
    updatedAt: LocalDateTime    // 更新时间
}
```

### 任务状态流转

```
PENDING → PROCESSING → SUCCESS
                     → FAILED → (重试) → PENDING
                              → (超过最大重试) → DEAD_LETTER
```

**日志中的状态说明**：
- PENDING: 待投递
- PROCESSING: 投递中
- SUCCESS: 投递成功
- FAILED: 投递失败（等待重试）
- DEAD_LETTER: 死信（不再重试）
- IGNORED: 已忽略（人工干预）

## 常见问题排查

### 问题 1：任务提交后未投递

**排查步骤**：

1. 检查 Gateway 日志，确认任务已持久化
   ```
   grep "任务已持久化" logs/gateway/notify-gateway.log
   ```

2. 检查 Gateway 日志，确认任务已推送到队列
   ```
   grep "任务已推送到队列" logs/gateway/notify-gateway.log
   ```

3. 检查 Worker 日志，确认任务被加载
   ```
   grep "从 DB 加载任务" logs/worker/notify-worker.log
   ```

### 问题 2：任务反复重试失败

**排查步骤**：

1. 查看投递响应
   ```
   grep "投递响应" logs/worker/notify-worker.log | grep "taskId=123"
   ```

2. 查看失败原因
   ```
   grep "投递失败" logs/worker/notify-worker.log | grep "taskId=123"
   ```

3. 查看重试次数
   ```
   grep "retryCount" logs/worker/notify-worker.log | grep "taskId=123"
   ```

### 问题 3：任务进入死信

**排查步骤**：

1. 查找死信日志
   ```
   grep "转入死信" logs/worker/notify-worker.log
   ```

2. 查看完整重试历史
   ```
   grep "taskId=123" logs/worker/notify-worker.log | grep "retryCount"
   ```

## 日志优化建议

### 1. 生产环境日志级别

建议生产环境使用 INFO 级别：

```yaml
logging:
  level:
    root: INFO
    com.company.notify: INFO
```

### 2. 开发环境日志级别

建议开发环境使用 DEBUG 级别：

```yaml
logging:
  level:
    root: INFO
    com.company.notify: DEBUG
```

### 3. 日志截断

为防止日志过长，以下字段会被截断：
- body: 200 字符
- headers: 200 字符
- response: 500 字符

如需调整，修改 `truncate()` 方法的 `maxLength` 参数。

---

**文档版本**: v1.0  
**最后更新**: 2026-03-31
