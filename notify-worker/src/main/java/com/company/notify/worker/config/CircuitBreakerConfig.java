package com.company.notify.worker.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 熔断器配置
 */
@Configuration
public class CircuitBreakerConfig {
    
    @Value("${app.notify.worker.circuit-breaker.enabled:true}")
    private boolean enabled;
    
    @Value("${app.notify.worker.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;
    
    @Value("${app.notify.worker.circuit-breaker.slow-call-rate-threshold:50}")
    private float slowCallRateThreshold;
    
    @Value("${app.notify.worker.circuit-breaker.slow-call-duration-threshold:5000}")
    private long slowCallDurationThreshold;
    
    @Value("${app.notify.worker.circuit-breaker.permitted-number-of-calls-in-half-open-state:10}")
    private int permittedNumberOfCallsInHalfOpenState;
    
    @Value("${app.notify.worker.circuit-breaker.sliding-window-size:100}")
    private int slidingWindowSize;
    
    @Value("${app.notify.worker.circuit-breaker.wait-duration-in-open-state:30000}")
    private long waitDurationInOpenState;
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = 
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThreshold))
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .slidingWindowSize(slidingWindowSize)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState))
                .build();
        
        return CircuitBreakerRegistry.of(config);
    }
}