# Kafka 消息队列实现示例

本文档展示如何将 Redis 消息队列切换到 Kafka。

## 当前架构

```
NotifyService → MessageQueueService (接口)
                     ↓
            RedisMessageQueueService (当前实现)
```

## 切换到 Kafka 的步骤

### 1. 添加 Kafka 依赖

在 `notify-gateway/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 2. 创建 Kafka 实现

创建 `KafkaMessageQueueService.java`：

```java
package com.company.notify.gateway.service.impl;

import com.company.notify.gateway.service.MessageQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka 消息队列服务实现
 * 注意：Kafka 本身不支持延迟队列，需要配合其他组件（如 Kafka + Redis）
 */
@Service("kafkaMessageQueueService")
public class KafkaMessageQueueService implements MessageQueueService {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageQueueService.class);
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public void send(String queueKey, String message, double score) {
        // queueKey 作为 topic，message 作为消息内容
        // score 可以放在消息头中用于延迟处理
        CompletableFuture<SendResult<String, String>> future = 
            kafkaTemplate.send(queueKey, message);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("消息已发送到 Kafka：topic={}, message={}, offset={}", 
                    queueKey, message, result.getRecordMetadata().offset());
            } else {
                log.error("发送消息到 Kafka 失败：topic={}, message={}", queueKey, message, ex);
            }
        });
    }
    
    @Override
    public List<String> receive(String queueKey, long maxScore, int count) {
        // Kafka 消费者应该在监听器中消费，这个方法可能不适用
        // 需要重新设计 Worker 的消费模式
        throw new UnsupportedOperationException("Kafka 使用监听器模式消费，不支持主动拉取");
    }
    
    @Override
    public void acknowledge(String queueKey, List<String> messages) {
        // Kafka 自动提交 offset，无需手动确认
        log.debug("Kafka 自动确认消息：topic={}, count={}", queueKey, messages.size());
    }
    
    @Override
    public void resend(String queueKey, String message, long delaySeconds) {
        // 重新发送到同一个 topic
        send(queueKey, message, 0);
        log.debug("消息已重新发送到 Kafka：topic={}, message={}", queueKey, message);
    }
}
```

### 3. 配置 Kafka

在 `application.yml` 中添加：

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      group-id: notify-worker-group
      auto-offset-reset: latest
      enable-auto-commit: true
```

### 4. 切换实现

修改 `RedisMessageQueueService.java` 的 `@Service` 注解：

```java
// 注释掉 Redis 实现
// @Service("redisMessageQueueService")
public class RedisMessageQueueService implements MessageQueueService {
    // ...
}

// 启用 Kafka 实现
@Service("messageQueueService")
public class KafkaMessageQueueService implements MessageQueueService {
    // ...
}
```

或者使用 `@Primary` 注解：

```java
@Service
@Primary  // 标记为主要实现
public class KafkaMessageQueueService implements MessageQueueService {
    // ...
}
```

### 5. Worker 适配

Worker 模块也需要相应修改，使用 Kafka 消费者：

```java
package com.company.notify.worker.consumer;

import com.company.notify.worker.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    
    @Autowired
    private DeliveryService deliveryService;
    
    @KafkaListener(topics = "notify-queue", groupId = "notify-worker-group")
    public void consume(String taskId) {
        log.info("从 Kafka 接收到任务：taskId={}", taskId);
        // 执行投递逻辑
        deliveryService.deliver(Long.parseLong(taskId));
    }
}
```

## 注意事项

### Redis vs Kafka 对比

| 特性 | Redis ZSet | Kafka |
|------|------------|-------|
| **延迟队列** | ✅ 天然支持（score=时间戳） | ❌ 需要额外组件 |
| **消息顺序** | ✅ 按 score 排序 | ✅ 分区内有序 |
| **消息持久化** | ⚠️ 依赖 RDB/AOF | ✅ 持久化存储 |
| **吞吐量** | 高（10w+ QPS） | 超高（100w+ QPS） |
| **消费模式** | 主动拉取 | 监听器推送 |
| **消息回溯** | ❌ 不支持 | ✅ 支持 |
| **运维复杂度** | 低 | 高 |

### 延迟队列处理

**Redis 方案**：
- 使用 ZSet，score = 执行时间戳
- Worker 轮询：`ZRANGEBYSCORE key 0 now`

**Kafka 方案**：
- 方案 1：Kafka + Redis（延迟消息先存 Redis，到期发到 Kafka）
- 方案 2：Kafka 定时主题（多个主题对应不同延迟级别）
- 方案 3：使用支持延迟的 MQ（如 RocketMQ）

## 混合方案（推荐）

对于高可靠场景，可以使用 **Redis + Kafka 双写**：

```java
@Service
public class HybridMessageQueueService implements MessageQueueService {
    
    @Autowired
    @Qualifier("redisMessageQueueService")
    private MessageQueueService redisService;
    
    @Autowired
    @Qualifier("kafkaMessageQueueService")
    private MessageQueueService kafkaService;
    
    @Override
    public void send(String queueKey, String message, double score) {
        // 先写 Redis（保证延迟队列）
        redisService.send(queueKey, message, score);
        
        // 异步写 Kafka（保证消息回溯）
        kafkaService.send(queueKey, message, score);
    }
    
    // ... 其他方法
}
```

## 总结

通过 `MessageQueueService` 接口抽象，我们可以：

1. ✅ **灵活切换**：Redis ↔ Kafka ↔ 其他 MQ
2. ✅ **平滑迁移**：支持双写过渡
3. ✅ **易于测试**：可以编写 Mock 实现
4. ✅ **解耦业务**：NotifyService 不依赖具体实现

**当前选择**：使用 Redis ZSet（简单、支持延迟队列、运维成本低）

**未来演进**：
- QPS < 5000：继续使用 Redis
- QPS > 5000：考虑切换到 Kafka 或 RocketMQ
- 需要消息回溯：切换到 Kafka
