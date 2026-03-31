package com.company.notify.gateway.service;

import java.util.List;

/**
 * 消息队列服务接口
 * 用于解耦通知任务与具体的消息队列实现
 */
public interface MessageQueueService {
    
    /**
     * 发送消息到队列
     * @param queueKey 队列键
     * @param message 消息内容
     * @param score 分数（用于延迟队列，非延迟队列可忽略）
     */
    void send(String queueKey, String message, double score);
    
    /**
     * 从队列获取消息
     * @param queueKey 队列键
     * @param maxScore 最大分数（获取分数 <= maxScore 的消息）
     * @param count 最大数量
     * @return 消息列表
     */
    List<String> receive(String queueKey, long maxScore, int count);
    
    /**
     * 确认消息已处理（从队列中移除）
     * @param queueKey 队列键
     * @param messages 消息列表
     */
    void acknowledge(String queueKey, List<String> messages);
    
    /**
     * 重新发送消息（用于重试）
     * @param queueKey 队列键
     * @param message 消息内容
     * @param delaySeconds 延迟秒数
     */
    void resend(String queueKey, String message, long delaySeconds);
}
