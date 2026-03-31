# 供应商配置使用文档

## 概述

Worker 服务现在支持基于配置文件的供应商模板配置。每个供应商可以定义自己的 URL、请求头模板、请求体模板等配置。

## 配置文件位置

```
notify-worker/src/main/resources/supplier-config.yml
```

Worker 启动时会自动读取该配置文件。

## 配置格式

### 完整示例

```yaml
suppliers:
  - supplierCode: "INVENTORY"
    targetUrl: "http://inventory.internal/api/notify"
    httpMethod: "POST"
    headersTemplate:
      Content-Type: "application/json"
      Authorization: "Bearer {{accessToken}}"
    bodyTemplate: |
      {
        "orderId": "{{orderId}}",
        "status": "{{status}}"
      }
    timeoutMs: 30000
    maxRetryCount: 10
```

### 配置字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| supplierCode | String | ✅ | - | 供应商代码（如：INVENTORY, CRM） |
| targetUrl | String | ✅ | - | 目标 URL，支持模板变量 |
| httpMethod | String | ❌ | POST | HTTP 方法（POST/GET/PUT/DELETE 等） |
| headersTemplate | Map | ❌ | - | 请求头模板，支持模板变量 |
| bodyTemplate | String/Map | ❌ | - | 请求体模板，支持模板变量 |
| timeoutMs | Integer | ❌ | 30000 | 超时时间（毫秒） |
| maxRetryCount | Integer | ❌ | 10 | 最大重试次数 |

## 模板变量

### 自动提供的变量

系统会自动从任务中提取以下变量：

| 变量名 | 来源 | 示例 |
|--------|------|------|
| taskId | 任务 ID | 123 |
| bizId | 业务 ID | "order-123456" |
| supplierCode | 供应商代码 | "INVENTORY" |
| status | 任务状态 | "PENDING" |
| retryCount | 重试次数 | 0 |
| timestamp | 系统时间戳 | 1711881600000 |

### 从任务 Body 中提取变量

如果任务 body 是 JSON 格式，系统会自动解析并提取字段作为模板变量：

**任务 Body**：
```json
{
  "orderId": "ORD-123",
  "customerId": "CUST-456",
  "amount": 99.99
}
```

**模板中可使用**：
```yaml
bodyTemplate: |
  {
    "order_id": "{{orderId}}",
    "customer_id": "{{customerId}}",
    "total_amount": {{amount}}
  }
```

### 从扩展数据中提取变量

如果任务有 extData 字段（JSON 格式），也会被解析为模板变量。

## 使用示例

### 示例 1：库存系统通知

**配置**：
```yaml
- supplierCode: "INVENTORY"
  targetUrl: "http://inventory.internal/api/notify"
  bodyTemplate: |
    {
      "orderId": "{{orderId}}",
      "status": "{{status}}",
      "timestamp": {{timestamp}}
    }
```

**提交任务**：
```json
{
  "bizId": "order-123",
  "supplierCode": "INVENTORY",
  "body": "{\"orderId\":\"ORD-123\",\"items\":[{\"sku\":\"A001\",\"qty\":2}]}"
}
```

**实际投递的 Body**：
```json
{
  "orderId": "ORD-123",
  "status": "PENDING",
  "timestamp": 1711881600000
}
```

### 示例 2：CRM 系统通知

**配置**：
```yaml
- supplierCode: "CRM"
  targetUrl: "http://crm.internal/api/customer/notify"
  headersTemplate:
    Authorization: "Bearer crm-token-xxx"
    X-Source: "NotifyHub"
  bodyTemplate: |
    {
      "eventType": "payment_success",
      "customerId": "{{customerId}}",
      "amount": {{amount}}
    }
```

**提交任务**：
```json
{
  "bizId": "payment-456",
  "supplierCode": "CRM",
  "body": "{\"customerId\":\"CUST-789\",\"amount\":199.99}"
}
```

**实际投递**：
- URL: `http://crm.internal/api/customer/notify`
- Headers: `Authorization: Bearer crm-token-xxx`, `X-Source: NotifyHub`
- Body: `{"eventType":"payment_success","customerId":"CUST-789","amount":199.99}`

### 示例 3：带变量替换的 URL

**配置**：
```yaml
- supplierCode: "AD_SYSTEM"
  targetUrl: "http://ad.internal/api/{{env}}/conversion"
  headersTemplate:
    X-API-Key: "{{apiKey}}"
```

**提交任务**：
```json
{
  "bizId": "ad-conv-123",
  "supplierCode": "AD_SYSTEM",
  "body": "{\"userId\":\"U001\",\"env\":\"prod\",\"apiKey\":\"secret-key\"}"
}
```

**实际投递的 URL**：`http://ad.internal/api/prod/conversion`

## 模板语法

### 基本语法

```
{{变量名}}
```

### 带默认值

```
{{变量名：默认值}}
```

**示例**：
```yaml
bodyTemplate: |
  {
    "status": "{{status:UNKNOWN}}",
    "retryCount": {{retryCount:0}}
  }
```

如果任务中没有 `status` 字段，会使用 `UNKNOWN` 作为默认值。

## 开关配置

可以通过配置文件控制是否使用供应商配置：

**application.yml**：
```yaml
app:
  notify:
    worker:
      # 是否使用供应商配置
      # true: 使用 supplier-config.yml 中的配置
      # false: 使用任务自带的 targetUrl 和 body
      use-supplier-config: true
```

## 动态刷新配置

### 方式 1：重启 Worker

修改 `supplier-config.yml` 后重启 Worker 服务。

### 方式 2：调用 API（待实现）

未来可以实现一个管理 API 来重新加载配置：

```bash
POST /api/admin/config/reload
```

## 调试技巧

### 1. 查看加载的配置

启动日志会输出：

```
加载供应商配置：INVENTORY -> http://inventory.internal/api/notify
加载供应商配置：CRM -> http://crm.internal/api/customer/notify
成功加载 5 个供应商配置
```

### 2. 查看投递日志

```
使用供应商配置投递：supplierCode=INVENTORY, targetUrl=http://inventory.internal/api/notify
【Worker.Delivery】使用配置构建请求：url=..., method=POST, body={...}
```

### 3. 临时禁用配置

如果遇到问题，可以临时禁用供应商配置：

```yaml
app:
  notify:
    worker:
      use-supplier-config: false
```

## 最佳实践

### 1. 配置分离

将经常变化的配置（如 token、API Key）放在配置文件中，业务逻辑不变。

### 2. 使用模板变量

尽量使用模板变量，减少硬编码：

```yaml
# ✅ 好
bodyTemplate: |
  {
    "orderId": "{{orderId}}",
    "status": "{{status}}"
  }

# ❌ 不推荐
bodyTemplate: |
  {
    "orderId": "ORD-123",
    "status": "SUCCESS"
  }
```

### 3. 设置合理的超时

根据供应商的响应速度设置不同的超时：

```yaml
# 内部服务 - 快速
- supplierCode: "CRM"
  timeoutMs: 10000

# 外部服务 - 慢速
- supplierCode: "SMS"
  timeoutMs: 30000
```

### 4. 设置合理的重试次数

根据业务重要性设置重试次数：

```yaml
# 关键业务 - 多次重试
- supplierCode: "PAYMENT"
  maxRetryCount: 10

# 非关键业务 - 少次重试
- supplierCode: "ANALYTICS"
  maxRetryCount: 3
```

## 故障排查

### 问题 1：配置未生效

**检查**：
1. 确认 `use-supplier-config: true`
2. 检查 `supplier-config.yml` 格式是否正确
3. 查看启动日志，确认配置已加载

### 问题 2：模板变量未替换

**检查**：
1. 确认变量名拼写正确
2. 检查任务 body 中是否包含所需字段
3. 查看日志，确认变量已提取

### 问题 3：JSON 格式错误

**检查**：
1. 确保 bodyTemplate 是合法的 JSON
2. 注意转义字符
3. 数值类型不需要引号

## 配置文件管理

### 开发环境

```yaml
suppliers:
  - supplierCode: "INVENTORY"
    targetUrl: "http://localhost:8082/api/notify"  # 本地地址
```

### 生产环境

```yaml
suppliers:
  - supplierCode: "INVENTORY"
    targetUrl: "http://inventory.internal/api/notify"  # 生产地址
```

建议使用 K8s ConfigMap 管理不同环境的配置。

---

**文档版本**: v1.0  
**最后更新**: 2026-03-31
