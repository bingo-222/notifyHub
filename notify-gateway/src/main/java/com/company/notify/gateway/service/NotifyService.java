package com.company.notify.gateway.service;

import com.company.notify.common.constants.NotifyConstants;
import com.company.notify.common.dto.SubmitNotifyRequest;
import com.company.notify.common.enums.TaskStatus;
import com.company.notify.common.exception.BizException;
import com.company.notify.common.model.NotifyTask;
import com.company.notify.gateway.mapper.NotifyTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 通知服务
 */
@Service
public class NotifyService {
    
    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    
    @Autowired
    private NotifyTaskMapper notifyTaskMapper;
    
    @Autowired
    private MessageQueueService messageQueueService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.notify.queue.key:notify:queue}")
    private String queueKey;
    
    /**
     * 提交通知任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(SubmitNotifyRequest request) {
        try {
            // 1. 构建任务对象
            NotifyTask task = new NotifyTask();
            task.setBizId(request.getBizId());
            task.setSupplierCode(request.getSupplierCode());
            task.setTargetUrl(request.getTargetUrl());
            task.setHttpMethod(request.getHttpMethod());
            task.setHeaders(request.getHeaders());
            task.setBody(request.getBody());
            task.setStatus(TaskStatus.PENDING.getCode());
            task.setRetryCount(0);
            task.setMaxRetryCount(request.getMaxRetryCount() != null 
                    ? request.getMaxRetryCount() 
                    : com.company.notify.common.constants.NotifyConstants.DEFAULT_MAX_RETRY_COUNT);
            task.setNextRetryAt(LocalDateTime.now()); // 立即执行
            task.setExtData(request.getExtData());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            
            // 2. 写入数据库
            notifyTaskMapper.insert(task);
            log.info("【Gateway】任务已持久化 - Task 结构体：bizId={}, taskId={}, supplierCode={}, url={}, method={}, body={}, status={}, retryCount={}, maxRetryCount={}", 
                    task.getBizId(), task.getId(), task.getSupplierCode(), task.getTargetUrl(), 
                    task.getHttpMethod(), truncate(task.getBody(), 200), task.getStatus(), 
                    task.getRetryCount(), task.getMaxRetryCount());
            
            // 3. 推送到消息队列（事务外，允许失败）
            try {
                pushToQueue(task);
            } catch (Exception e) {
                log.warn("消息队列推送失败，将触发补偿机制：taskId={}", task.getId(), e);
                // 这里可以记录到补偿表，或者依靠定时任务扫描
            }
            
            return task.getId();
        } catch (DuplicateKeyException e) {
            // 捕获唯一索引冲突，返回友好的错误提示
            log.info("重复提交被拦截：bizId={}, supplierCode={}", request.getBizId(), request.getSupplierCode());
            throw new BizException("任务已存在，请勿重复提交（bizId: " + request.getBizId() + "）");
        }
    }
    
    /**
     * 推送到消息队列
     */
    private void pushToQueue(NotifyTask task) {
        double score = task.getNextRetryAt()
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();
        
        // 存储完整任务 JSON 到 Redis，而不仅仅是 taskId
        String message;
        try {
            message = objectMapper.writeValueAsString(task);
        } catch (Exception e) {
            log.error("序列化任务失败：taskId={}", task.getId(), e);
            throw new RuntimeException("序列化任务失败", e);
        }
        
        messageQueueService.send(queueKey, message, score);
        log.info("【Gateway】任务已推送到队列 - Task 结构体：bizId={}, taskId={}, queueKey={}, score={}, nextRetryAt={}, messageSize={} bytes", 
                task.getBizId(), task.getId(), queueKey, score, task.getNextRetryAt(), message.length());
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
    
    /**
     * 获取任务状态
     */
    public Object getTaskStatus(Long taskId) {
        return notifyTaskMapper.selectById(taskId);
    }
}