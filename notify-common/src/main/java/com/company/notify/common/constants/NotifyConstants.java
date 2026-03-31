package com.company.notify.common.constants;

/**
 * 系统常量
 */
public final class NotifyConstants {
    
    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRY_COUNT = 10;
    
    /**
     * 默认超时时间（毫秒）
     */
    public static final int DEFAULT_TIMEOUT_MS = 30000;
    
    /**
     * Redis 队列 Key
     */
    public static final String REDIS_QUEUE_KEY = "notify:queue";
    
    /**
     * Redis 分布式锁 Key 前缀
     */
    public static final String REDIS_LOCK_PREFIX = "notify:lock:";
    
    /**
     * 补偿任务扫描时间窗口（分钟）
     */
    public static final int COMPENSATION_SCAN_WINDOW_MINUTES = 1;
    
    /**
     * 批量获取任务数量
     */
    public static final int BATCH_FETCH_SIZE = 100;
    
    /**
     * 指数退避基数（毫秒）
     */
    public static final long BACKOFF_BASE_MS = 1000;
    
    /**
     * 最大退避时间（毫秒）
     */
    public static final long MAX_BACKOFF_MS = 30 * 60 * 1000; // 30 分钟
    
    private NotifyConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
