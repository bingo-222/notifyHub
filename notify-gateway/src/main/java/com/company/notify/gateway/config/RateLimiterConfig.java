package com.company.notify.gateway.config;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 限流配置
 */
@Configuration
public class RateLimiterConfig {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);
    
    @Value("${app.notify.rate-limit.permits-per-second:100}")
    private int permitsPerSecond;
    
    @Value("${app.notify.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    private RateLimiter globalRateLimiter;
    
    // 按供应商维度的限流器
    private final ConcurrentMap<String, RateLimiter> supplierRateLimiters = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        if (rateLimitEnabled) {
            globalRateLimiter = RateLimiter.create(permitsPerSecond);
            log.info("全局限流器已初始化，QPS={}", permitsPerSecond);
        } else {
            log.info("限流功能已关闭");
        }
    }
    
    /**
     * 获取全局限流器
     */
    public RateLimiter getGlobalRateLimiter() {
        return globalRateLimiter;
    }
    
    /**
     * 获取供应商维度的限流器
     */
    public RateLimiter getSupplierRateLimiter(String supplierCode) {
        return supplierRateLimiters.computeIfAbsent(
                supplierCode, 
                k -> RateLimiter.create(permitsPerSecond / 10.0) // 供应商默认限流更严格
        );
    }
    
    /**
     * 尝试获取许可
     */
    public boolean tryAcquire() {
        if (!rateLimitEnabled || globalRateLimiter == null) {
            return true;
        }
        return globalRateLimiter.tryAcquire();
    }
    
    /**
     * 尝试获取许可（指定供应商）
     */
    public boolean tryAcquire(String supplierType) {
        if (!rateLimitEnabled) {
            return true;
        }
        
        // 先检查全局限流
        if (!globalRateLimiter.tryAcquire()) {
            return false;
        }
        
        // 再检查供应商限流
        RateLimiter supplierLimiter = getSupplierRateLimiter(supplierType);
        return supplierLimiter.tryAcquire();
    }
}