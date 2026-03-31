package com.company.notify.gateway.compensation;

import com.company.notify.common.constants.NotifyConstants;
import com.company.notify.common.enums.TaskStatus;
import com.company.notify.common.model.NotifyTask;
import com.company.notify.gateway.mapper.NotifyTaskMapper;
import com.company.notify.gateway.service.MessageQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 补偿任务
 */
@Component
public class CompensationTask {
    
    private static final Logger log = LoggerFactory.getLogger(CompensationTask.class);
    
    @Autowired
    private NotifyTaskMapper notifyTaskMapper;
    
    @Autowired
    private MessageQueueService messageQueueService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.notify.queue.key:notify:queue}")
    private String queueKey;
    
    /**
     * 补偿任务：扫描 DB 中 PENDING 但不在队列的任务
     */
    @Scheduled(fixedDelay = 5000) // 每 5 秒执行一次
    public void compensateMissingInQueue() {
        try {
            // 扫描最近 1 分钟内创建的 PENDING 任务
            LocalDateTime since = LocalDateTime.now().minusMinutes(
                    NotifyConstants.COMPENSATION_SCAN_WINDOW_MINUTES);
            List<NotifyTask> pendingTasks = notifyTaskMapper.findRecentPendingTasks(since);
            
            if (pendingTasks.isEmpty()) {
                return;
            }
            
            // 检查队列中是否存在（简化处理，直接补偿）
            int compensatedCount = 0;
            for (NotifyTask task : pendingTasks) {
                double score = task.getNextRetryAt()
                        .atZone(ZoneId.systemDefault())
                        .toEpochSecond();
                
                // 存储完整任务 JSON 到 Redis
                String message = objectMapper.writeValueAsString(task);
                messageQueueService.send(queueKey, message, score);
                compensatedCount++;
                log.info("补偿任务到队列：taskId={}, bizId={}", task.getId(), task.getBizId());
            }
            
            if (compensatedCount > 0) {
                log.info("补偿任务完成：补偿 {} 个任务", compensatedCount);
            }
        } catch (Exception e) {
            log.error("补偿任务执行失败", e);
        }
    }
    
    /**
     * 健康检查：定期检查队列连接
     */
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void healthCheck() {
        try {
            // 简单的健康检查，能读取队列即可
            messageQueueService.receive(queueKey, System.currentTimeMillis() / 1000, 1);
            log.debug("队列连接正常");
        } catch (Exception e) {
            log.error("队列连接异常，请检查配置", e);
        }
    }
}