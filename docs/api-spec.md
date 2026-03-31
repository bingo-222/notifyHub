# NotifyHub API 文档

## 概述

本文档描述了 NotifyHub 通知服务的所有 REST API 接口。

**Base URL**: `http://localhost:8080/api`

## 认证

当前版本暂不需要认证。

## 接口列表

### 1. 提交通知任务

提交一个新的通知任务到系统。

**请求**：

```http
POST /notify/submit
Content-Type: application/json
```

**请求参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| bizId | String | ✅ | 业务 ID，用于幂等性校验 |
| supplierType | String | ✅ | 供应商类型（AD_SYSTEM/CRM/INVENTORY/EMAIL/SMS） |
| targetUrl | String | ✅ | 目标 URL |
| httpMethod | String | ❌ | 请求方法，默认 POST |
| headers | String | ❌ | 请求头（JSON 字符串） |
| body | String | ✅ | 请求体（JSON 字符串） |
| timeoutMs | Integer | ❌ | 超时时间（毫秒），默认 30000 |
| maxRetryCount | Integer | ❌ | 最大重试次数，默认 10 |
| extData | String | ❌ | 扩展数据（JSON 字符串） |

**请求示例**：

```json
{
  "bizId": "order-123456",
  "supplierType": "INVENTORY",
  "targetUrl": "http://inventory.internal/api/notify",
  "httpMethod": "POST",
  "headers": "{\"Authorization\":\"Bearer xxx\",\"Content-Type\":\"application/json\"}",
  "body": "{\"orderId\":\"123456\",\"status\":\"completed\",\"items\":[{\"sku\":\"A001\",\"qty\":2}]}",
  "timeoutMs": 30000,
  "maxRetryCount": 10
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": 123456
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 响应码，200 表示成功 |
| message | String | 响应消息 |
| data | Long | 任务 ID |

**错误响应**：

```json
{
  "code": 400,
  "message": "参数校验失败：bizId 不能为空"
}
```

**常见错误码**：

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 429 | 请求过于频繁（限流） |
| 500 | 系统异常 |

---

### 2. 查询任务状态

根据任务 ID 查询通知任务的当前状态。

**请求**：

```http
GET /notify/status/{taskId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | Long | 任务 ID |

**请求示例**：

```http
GET /notify/status/123456
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123456,
    "bizId": "order-123456",
    "supplierType": "INVENTORY",
    "targetUrl": "http://inventory.internal/api/notify",
    "httpMethod": "POST",
    "status": "SUCCESS",
    "retryCount": 0,
    "maxRetryCount": 10,
    "nextRetryAt": null,
    "lastDeliveredAt": "2024-01-15 10:30:00",
    "failureReason": null,
    "lastErrorMessage": null,
    "createdAt": "2024-01-15 10:29:58",
    "updatedAt": "2024-01-15 10:30:00"
  }
}
```

**响应字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 任务 ID |
| bizId | String | 业务 ID |
| supplierType | String | 供应商类型 |
| targetUrl | String | 目标 URL |
| httpMethod | String | 请求方法 |
| status | String | 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/DEAD_LETTER/IGNORED） |
| retryCount | Integer | 已重试次数 |
| maxRetryCount | Integer | 最大重试次数 |
| nextRetryAt | DateTime | 下次重试时间 |
| lastDeliveredAt | DateTime | 最后投递时间 |
| failureReason | String | 失败原因 |
| lastErrorMessage | String | 最后错误信息 |
| createdAt | DateTime | 创建时间 |
| updatedAt | DateTime | 更新时间 |

**任务状态说明**：

| 状态 | 说明 |
|------|------|
| PENDING | 待投递 |
| PROCESSING | 投递中 |
| SUCCESS | 投递成功 |
| FAILED | 投递失败（可重试） |
| DEAD_LETTER | 死信（超过最大重试次数） |
| IGNORED | 已忽略（人工干预） |

**错误响应**：

```json
{
  "code": 404,
  "message": "任务不存在"
}
```

---

### 3. 健康检查

检查服务是否正常运行。

**请求**：

```http
GET /notify/health
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": "UP"
}
```

---

### 4. 批量提交通知（TODO）

批量提交多个通知任务。

**请求**：

```http
POST /notify/batch-submit
Content-Type: application/json
```

**TODO**: 此接口尚未实现。

---

## 供应商类型说明

| 供应商代码 | 说明 | 默认限流 QPS |
|-----------|------|------------|
| AD_SYSTEM | 广告系统 | 100 |
| CRM | CRM 系统 | 100 |
| INVENTORY | 库存系统 | 100 |
| EMAIL | 邮件服务 | 50 |
| SMS | 短信服务 | 50 |
| CUSTOM | 自定义 | 100 |

---

## 重试策略说明

系统会自动重试失败的通知，重试策略如下：

| 重试次数 | 延迟时间 | 累计时间 |
|---------|---------|---------|
| 1 | 1 秒 | 1 秒 |
| 2 | 5 秒 | 6 秒 |
| 3 | 30 秒 | 36 秒 |
| 4 | 1 分钟 | 1 分 36 秒 |
| 5 | 5 分钟 | 6 分 36 秒 |
| 6 | 30 分钟 | 36 分 36 秒 |
| 7-10 | 30 分钟 | 约 24 小时 |

**失败原因分类**：

| HTTP 状态码 | 失败原因 | 是否重试 |
|------------|---------|---------|
| 2xx | 成功 | - |
| 400/401/403 | 客户端错误 | ❌ 不重试 |
| 429 | 被限流 | ✅ 重试 |
| 5xx | 服务端错误 | ✅ 重试 |
| 网络异常 | 连接失败 | ✅ 重试 |

---

## 限流说明

系统实现了全局限流和供应商维度限流：

- **全局限流**：默认 100 QPS
- **供应商限流**：默认 10 QPS

**限流响应**：

```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后重试"
}
```

---

## 错误码汇总

| 错误码 | 说明 | 解决方案 |
|--------|------|---------|
| 200 | 成功 | - |
| 400 | 参数错误 | 检查请求参数 |
| 404 | 资源不存在 | 检查任务 ID |
| 429 | 限流 | 降低请求频率 |
| 500 | 系统异常 | 联系运维人员 |

---

## 使用示例

### Java 示例

```java
// 提交通知
SubmitNotifyRequest request = SubmitNotifyRequest.builder()
    .bizId("order-123456")
    .supplierType("INVENTORY")
    .targetUrl("http://inventory.internal/api/notify")
    .body("{\"orderId\":\"123456\",\"status\":\"completed\"}")
    .build();

ApiResponse<Long> response = restTemplate.postForObject(
    "http://localhost:8080/api/notify/submit",
    request,
    ApiResponse.class
);

Long taskId = response.getData();
System.out.println("任务 ID: " + taskId);

// 查询状态
ApiResponse<TaskInfo> statusResponse = restTemplate.getForObject(
    "http://localhost:8080/api/notify/status/" + taskId,
    ApiResponse.class
);
```

### cURL 示例

```bash
# 提交通知
curl -X POST http://localhost:8080/api/notify/submit \
  -H "Content-Type: application/json" \
  -d '{
    "bizId": "order-123456",
    "supplierType": "INVENTORY",
    "targetUrl": "http://inventory.internal/api/notify",
    "body": "{\"orderId\":\"123456\"}"
  }'

# 查询状态
curl http://localhost:8080/api/notify/status/123456

# 健康检查
curl http://localhost:8080/api/notify/health
```

---

## 更新日志

### v1.0 (2024-01)

- ✅ 提交通知接口
- ✅ 查询状态接口
- ✅ 健康检查接口
- ❌ 批量提交接口（规划中）
- ❌ 管理后台接口（规划中）

---

**文档版本**: v1.0  
**最后更新**: 2024-01-XX
