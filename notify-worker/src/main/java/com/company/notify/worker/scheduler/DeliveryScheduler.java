package com.company.notify.worker.scheduler;

import com.company.notify.common.constants.NotifyConstants;
import com.company.notify.common.enums.TaskStatus;
import com.company.notify.common.model.NotifyTask;
import com.company.notify.worker.mapper.NotifyTaskMapper;
import com.company.notify.worker.service.DeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 投递调度器
 * 使用 Lua 脚本保证从 Redis 取任务的原子性，支持多 Worker 并发
 */
@Component
public class DeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryScheduler.class);

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private NotifyTaskMapper notifyTaskMapper;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.notify.worker.batch-size:100}")
    private int batchSize;

    /**
     * Lua 脚本：原子性弹出到期任务
     * 功能：找到 score <= maxScore 的第一个元素并删除，返回该元素
     */
    private static final String POP_EXPIRED_TASK_LUA =
            "local key = KEYS[1]; " +
            "local maxScore = tonumber(ARGV[1]); " +
            "local items = redis.call('zrangebyscore', key, '-inf', maxScore, 'LIMIT', 0, 1); " +
            "if #items == 0 then return nil; end; " +
            "redis.call('zrem', key, items[1]); " +
            "return items[1];";

    /**
     * 轮询 Redis 队列并投递任务（使用 Lua 脚本保证原子性）
     * 多个 Worker 可以并发执行，Redis 单线程保证不会重复取到同一任务
     */
    @Scheduled(fixedDelayString = "${app.notify.worker.poll-interval-ms:1000}")
    public void pollAndDeliver() {
        try {
            String queueKey = NotifyConstants.REDIS_QUEUE_KEY;
            long now = System.currentTimeMillis() / 1000;

            int fetchedCount = 0;

            // 循环获取到期任务，直到达到批量大小或没有更多任务
            while (fetchedCount < batchSize) {
                // 使用 Lua 脚本原子弹出到期任务
                String messageJson = popExpiredTask(queueKey, now);

                if (messageJson == null) {
                    break; // 没有更多到期任务
                }

                fetchedCount++;

                try {
                    // 解析 JSON 为任务对象
                    NotifyTask task = objectMapper.readValue(messageJson, NotifyTask.class);
                    deliverTaskFromQueue(task);
                } catch (Exception e) {
                    log.error("投递任务异常：message={}", messageJson, e);
                    // 投递失败，重新加入队列（延迟重试）
                    rescheduleMessage(messageJson, 5);
                }
            }

            if (fetchedCount > 0) {
                log.info("本次轮询获取并处理 {} 个待投递任务", fetchedCount);
            }
        } catch (Exception e) {
            log.error("轮询投递任务异常", e);
        }
    }

    /**
     * 使用 Lua 脚本从 Redis 原子性弹出到期任务
     * 保证查询和删除操作的原子性，避免多个 Worker 重复处理同一任务
     *
     * @param queueKey 队列 key
     * @param maxScore 最大 score（当前时间戳）
     * @return 任务 JSON 字符串，如果没有到期任务返回 null
     */
    private String popExpiredTask(String queueKey, long maxScore) {
        RScript script = redissonClient.getScript();
        return script.eval(
                RScript.Mode.READ_WRITE,
                POP_EXPIRED_TASK_LUA,
                RScript.ReturnType.VALUE,
                Collections.singletonList(queueKey),
                String.valueOf(maxScore)
        );
    }

    /**
     * 投递单个任务（从队列直接获取任务对象）
     */
    private void deliverTaskFromQueue(NotifyTask task) {
        if (task == null) {
            log.warn("【Worker】任务对象为空");
            return;
        }

        log.info("【Worker】从 Redis 加载任务 - Task 结构体：bizId={}, taskId={}, supplierCode={}, url={}, method={}, body={}, status={}, retryCount={}/{}, nextRetryAt={}",
                task.getBizId(), task.getId(), task.getSupplierCode(), task.getTargetUrl(),
                task.getHttpMethod(), truncate(task.getBody(), 200), task.getStatus(),
                task.getRetryCount(), task.getMaxRetryCount(), task.getNextRetryAt());

        // 检查任务状态（防止被其他 Worker 处理）
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus()) &&
            !TaskStatus.FAILED.getCode().equals(task.getStatus())) {
            log.warn("【Worker】任务状态异常，跳过：bizId={}, taskId={}, status={}", task.getBizId(), task.getId(), task.getStatus());
            return;
        }

        // 更新状态为 PROCESSING
        updateTaskStatus(task.getId(), TaskStatus.PROCESSING, null);

        // 执行投递
        boolean success = deliveryService.deliver(task);

        // 更新任务状态
        if (success) {
            updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
            log.info("【Worker】任务投递成功：bizId={}, taskId={}", task.getBizId(), task.getId());
        } else {
            // 投递失败，判断是否需要重试
            int retryCount = task.getRetryCount() + 1;
            if (retryCount >= task.getMaxRetryCount()) {
                // 超过最大重试次数，转入死信
                updateTaskStatus(task.getId(), TaskStatus.DEAD_LETTER, null);
                log.warn("【Worker】任务投递失败，已转入死信：bizId={}, taskId={}, retryCount={}",
                        task.getBizId(), task.getId(), retryCount);
            } else {
                // 计算下次重试时间（指数退避），重新加入队列
                long delaySeconds = calculateBackoffDelay(retryCount);
                rescheduleTaskById(task.getId(), delaySeconds);
                log.info("【Worker】任务投递失败，已安排重试：bizId={}, taskId={}, retryCount={}, delay={}s",
                        task.getBizId(), task.getId(), retryCount, delaySeconds);
            }
        }
    }

    /**
     * 重新调度任务（通过 ID 重新查询 DB）
     */
    private void rescheduleTaskById(Long taskId, long delaySeconds) {
        NotifyTask task = notifyTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        // 更新重试次数和下次重试时间
        task.setRetryCount(task.getRetryCount() + 1);
        task.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        task.setStatus(TaskStatus.FAILED.getCode());
        task.setUpdatedAt(LocalDateTime.now());
        notifyTaskMapper.updateById(task);

        // 加入 Redis 延迟队列（存储完整 JSON）
        String queueKey = NotifyConstants.REDIS_QUEUE_KEY;
        double score = task.getNextRetryAt()
                .atZone(java.time.ZoneId.systemDefault())
                .toEpochSecond();

        try {
            String messageJson = objectMapper.writeValueAsString(task);
            redissonClient.getScoredSortedSet(queueKey).add(score, messageJson);
        } catch (Exception e) {
            log.error("重新加入队列失败：taskId={}", taskId, e);
        }
    }

    /**
     * 重新调度消息（直接重新加入 JSON 消息）
     */
    private void rescheduleMessage(String messageJson, long delaySeconds) {
        try {
            // 解析消息获取 taskId
            NotifyTask task = objectMapper.readValue(messageJson, NotifyTask.class);

            // 更新重试次数和下次重试时间
            task.setRetryCount(task.getRetryCount() + 1);
            task.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
            task.setStatus(TaskStatus.FAILED.getCode());
            task.setUpdatedAt(LocalDateTime.now());

            // 更新 DB
            notifyTaskMapper.updateById(task);

            // 加入 Redis 延迟队列
            String queueKey = NotifyConstants.REDIS_QUEUE_KEY;
            double score = task.getNextRetryAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();

            String updatedJson = objectMapper.writeValueAsString(task);
            redissonClient.getScoredSortedSet(queueKey).add(score, updatedJson);
        } catch (Exception e) {
            log.error("重新加入队列失败：message={}", messageJson, e);
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(Long taskId, TaskStatus status, String errorMessage) {
        NotifyTask task = new NotifyTask();
        task.setId(taskId);
        task.setStatus(status.getCode());
        task.setUpdatedAt(LocalDateTime.now());

        if (TaskStatus.SUCCESS.equals(status)) {
            task.setLastDeliveredAt(LocalDateTime.now());
        } else if (errorMessage != null) {
            task.setLastErrorMessage(errorMessage);
        }

        notifyTaskMapper.updateById(task);
    }

    /**
     * 计算指数退避延迟
     */
    private long calculateBackoffDelay(int retryCount) {
        // 指数退避：1s, 5s, 30s, 1m, 5m, 30m...
        double base = NotifyConstants.BACKOFF_BASE_MS / 1000.0; // 1 秒
        double delay = base * Math.pow(5, retryCount - 1);

        // 限制最大延迟
        long maxBackoffSeconds = NotifyConstants.MAX_BACKOFF_MS / 1000;
        return Math.min((long) delay, maxBackoffSeconds);
    }

    /**
     * 截断字符串，防止日志过长
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(" + (str.length() - maxLength) + " chars)";
    }
}
