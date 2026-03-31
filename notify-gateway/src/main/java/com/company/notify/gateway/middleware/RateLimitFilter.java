package com.company.notify.gateway.middleware;

import com.company.notify.common.dto.ApiResponse;
import com.company.notify.gateway.config.RateLimiterConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 限流过滤器
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    
    @Autowired
    private RateLimiterConfig rateLimiterConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 仅对提交通知接口限流
        String uri = request.getRequestURI();
        if (!uri.contains("/notify/submit")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 尝试获取许可
        if (!rateLimiterConfig.tryAcquire()) {
            log.warn("请求被限流：uri={}, remoteAddr={}", uri, request.getRemoteAddr());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> errorResponse = ApiResponse.error(429, "请求过于频繁，请稍后重试");
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}