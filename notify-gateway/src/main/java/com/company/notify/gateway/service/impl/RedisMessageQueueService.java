package com.company.notify.gateway.service.impl;

import com.company.notify.gateway.service.MessageQueueService;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Redis 消息队列服务实现
 * 使用 Redis ZSet 实现延迟队列
 */
@Service("redisMessageQueueService")
public class RedisMessageQueueService implements MessageQueueService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisMessageQueueService.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Override
    public void send(String queueKey, String message, double score) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(queueKey);
        sortedSet.add(score, message);
        log.debug("消息已发送到 Redis：queue={}, message={}, score={}", queueKey, message, score);
    }
    
    @Override
    public List<String> receive(String queueKey, long maxScore, int count) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(queueKey);
        Collection<String> allMessages = sortedSet.readAll();
        
        if (allMessages == null || allMessages.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 过滤出符合条件的消息
        List<String> result = new ArrayList<>();
        for (String msg : allMessages) {
            Double score = sortedSet.getScore(msg);
            if (score != null && score <= maxScore) {
                result.add(msg);
                if (result.size() >= count) {
                    break;
                }
            }
        }
        
        log.debug("从 Redis 接收到 {} 条消息：queue={}", result.size(), queueKey);
        return result;
    }
    
    @Override
    public void acknowledge(String queueKey, List<String> messages) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(queueKey);
        for (String message : messages) {
            sortedSet.remove(message);
        }
        log.debug("消息已确认：queue={}, count={}", queueKey, messages.size());
    }
    
    @Override
    public void resend(String queueKey, String message, long delaySeconds) {
        double newScore = (System.currentTimeMillis() / 1000.0) + delaySeconds;
        send(queueKey, message, newScore);
        log.debug("消息已重新发送：queue={}, message={}, delay={}s", queueKey, message, delaySeconds);
    }
}
