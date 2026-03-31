package com.company.notify.worker.service;

import com.company.notify.common.enums.FailureReason;
import com.company.notify.common.exception.DeliveryException;
import com.company.notify.common.model.NotifyTask;
import com.company.notify.worker.config.SupplierConfig;
import com.company.notify.worker.config.SupplierConfigService;
import com.company.notify.worker.util.TemplateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 投递服务
 */
@Service
public class DeliveryService {
    
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private SupplierConfigService supplierConfigService;
    
    @Value("${app.notify.worker.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    
    @Value("${app.notify.worker.http.read-timeout-ms:30000}")
    private int readTimeoutMs;
    
    @Value("${app.notify.worker.http.write-timeout-ms:30000}")
    private int writeTimeoutMs;
    
    @Value("${app.notify.worker.use-supplier-config:true}")
    private boolean useSupplierConfig;
    
    private final OkHttpClient httpClient;
    
    public DeliveryService(@Value("${app.notify.worker.http.connect-timeout-ms:5000}") int connectTimeoutMs,
                          @Value("${app.notify.worker.http.read-timeout-ms:30000}") int readTimeoutMs,
                          @Value("${app.notify.worker.http.write-timeout-ms:30000}") int writeTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
    
    /**
     * 投递通知
     * 
     * @return true-成功，false-失败
     */
    @CircuitBreaker(name = "deliveryService", fallbackMethod = "deliverFallback")
    public boolean deliver(NotifyTask task) {
        log.info("【Worker.Delivery】开始投递任务 - Task 结构体：bizId={}, taskId={}, url={}, method={}, headers={}, body={}, retryCount={}/{}, timeout={}", 
                task.getBizId(), task.getId(), task.getTargetUrl(), task.getHttpMethod(), 
                truncate(task.getHeaders(), 200), truncate(task.getBody(), 200), 
                task.getRetryCount(), task.getMaxRetryCount(), task.getMaxRetryCount() != null ? task.getMaxRetryCount() * 1000 : 30000);
        
        try {
            // 构建请求
            Request request;
            if (useSupplierConfig) {
                log.info("【Worker.Delivery】使用供应商配置模式：bizId={}, supplierCode={}", task.getBizId(), task.getSupplierCode());
                request = buildRequestWithConfig(task);
            } else {
                log.info("【Worker.Delivery】使用任务自带配置：bizId={}, url={}", task.getBizId(), task.getTargetUrl());
                request = buildRequest(task);
            }
            
            // 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                
                log.info("【Worker.Delivery】投递响应：bizId={}, taskId={}, statusCode={}, response={}", 
                        task.getBizId(), task.getId(), statusCode, truncate(responseBody, 500));
                
                // 判断是否成功
                if (statusCode >= 200 && statusCode < 300) {
                    log.info("【Worker.Delivery】投递成功：bizId={}, taskId={}, statusCode={}", task.getBizId(), task.getId(), statusCode);
                    return true;
                } else if (statusCode >= 400 && statusCode < 500) {
                    // 4xx 错误，不重试
                    if (statusCode == 429) {
                        log.warn("【Worker.Delivery】被限流：bizId={}, taskId={}, statusCode={}", task.getBizId(), task.getId(), statusCode);
                        throw new DeliveryException("被限流", statusCode, task.getRetryCount());
                    }
                    log.error("【Worker.Delivery】客户端错误，不重试：bizId={}, taskId={}, statusCode={}, response={}", 
                            task.getBizId(), task.getId(), statusCode, truncate(responseBody, 500));
                    throw new DeliveryException("客户端错误：" + statusCode, statusCode, task.getRetryCount());
                } else {
                    // 5xx 错误，可重试
                    log.error("【Worker.Delivery】服务端错误，将重试：bizId={}, taskId={}, statusCode={}", task.getBizId(), task.getId(), statusCode);
                    throw new DeliveryException("服务端错误：" + statusCode, statusCode, task.getRetryCount());
                }
            }
        } catch (DeliveryException e) {
            // 包装后的异常，直接抛出
            log.error("【Worker.Delivery】投递失败：bizId={}, taskId={}, httpCode={}, retryCount={}, error={}", 
                    task.getBizId(), task.getId(), e.getHttpStatusCode(), e.getRetryCount(), e.getMessage());
            throw e;
        } catch (IOException e) {
            // 网络异常
            log.error("【Worker.Delivery】网络异常：bizId={}, taskId={}, error={}", task.getBizId(), task.getId(), e.getMessage(), e);
            throw new DeliveryException("网络异常：" + e.getMessage(), null, task.getRetryCount(), e);
        } catch (Exception e) {
            // 其他异常
            log.error("【Worker.Delivery】投递异常：bizId={}, taskId={}, error={}", task.getBizId(), task.getId(), e.getMessage(), e);
            throw new DeliveryException("投递异常：" + e.getMessage(), null, task.getRetryCount(), e);
        }
    }
    
    /**
     * 熔断降级处理
     */
    public boolean deliverFallback(NotifyTask task, Throwable t) {
        log.warn("【Worker.Delivery】熔断降级：bizId={}, taskId={}, reason={}", task.getBizId(), task.getId(), t.getMessage());
        // 返回 false 表示失败，会触发重试逻辑
        return false;
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
     * 构建 HTTP 请求（使用供应商配置）
     */
    private Request buildRequestWithConfig(NotifyTask task) throws IOException {
        // 获取供应商配置
        SupplierConfig config = supplierConfigService.getSupplierConfig(task.getSupplierCode());
        
        if (config == null) {
            log.warn("未找到供应商配置，使用默认方式投递：supplierCode={}", task.getSupplierCode());
            return buildRequest(task);
        }
        
        log.info("使用供应商配置投递：supplierCode={}, targetUrl={}", 
                config.getSupplierCode(), config.getTargetUrl());
        
        // 准备模板变量
        Map<String, Object> variables = buildTemplateVariables(task);
        
        // 替换 URL（如果配置中有变量）
        String url = TemplateUtil.replaceVariables(config.getTargetUrl(), variables);
        
        // 构建请求
        Request.Builder builder = new Request.Builder().url(url);
        
        // 添加默认请求头
        builder.addHeader("Content-Type", "application/json;charset=UTF-8");
        builder.addHeader("User-Agent", "NotifyHub/1.0");
        
        // 替换并添加配置中的请求头
        if (config.getHeadersTemplate() != null) {
            for (Map.Entry<String, String> entry : config.getHeadersTemplate().entrySet()) {
                String headerValue = TemplateUtil.replaceVariables(entry.getValue(), variables);
                builder.addHeader(entry.getKey(), headerValue);
            }
        }
        
        // 添加任务中自定义的请求头
        if (StringUtils.hasText(task.getHeaders())) {
            Map<String, String> taskHeaders = objectMapper.readValue(
                    task.getHeaders(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
            );
            for (Map.Entry<String, String> entry : taskHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        
        // 替换请求体模板
        String bodyContent;
        if (config.getBodyTemplate() != null) {
            bodyContent = TemplateUtil.replaceVariables(config.getBodyTemplate(), variables);
        } else if (task.getBody() != null) {
            bodyContent = task.getBody();
        } else {
            bodyContent = "";
        }
        
        log.info("【Worker.Delivery】使用配置构建请求：url={}, method={}, body={}", 
                url, config.getHttpMethod(), truncate(bodyContent, 200));
        
        // 构建请求体
        RequestBody body = RequestBody.create(
                bodyContent, 
                MediaType.parse("application/json;charset=UTF-8")
        );
        
        // 根据请求方法构建请求
        String method = config.getHttpMethod() != null ? config.getHttpMethod().toUpperCase() : "POST";
        switch (method) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(body);
                break;
            case "PUT":
                builder.put(body);
                break;
            case "DELETE":
                builder.delete(body);
                break;
            case "PATCH":
                builder.patch(body);
                break;
            default:
                builder.method(method, body);
        }
        
        return builder.build();
    }
    
    /**
     * 构建模板变量
     */
    private Map<String, Object> buildTemplateVariables(NotifyTask task) {
        Map<String, Object> variables = new HashMap<>();
        
        // 添加任务字段作为模板变量
        variables.put("taskId", task.getId() != null ? task.getId() : "");
        variables.put("bizId", task.getBizId() != null ? task.getBizId() : "");
        variables.put("supplierCode", task.getSupplierCode() != null ? task.getSupplierCode() : "");
        variables.put("targetUrl", task.getTargetUrl() != null ? task.getTargetUrl() : "");
        variables.put("status", task.getStatus() != null ? task.getStatus() : "");
        variables.put("retryCount", task.getRetryCount() != null ? task.getRetryCount() : 0);
        variables.put("timestamp", System.currentTimeMillis());
        
        // 尝试解析任务 body 中的字段
        if (StringUtils.hasText(task.getBody())) {
            try {
                Map<String, Object> bodyMap = objectMapper.readValue(
                        task.getBody(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );
                variables.putAll(bodyMap);
            } catch (Exception e) {
                log.debug("解析任务 body 失败，忽略：{}", e.getMessage());
            }
        }
        
        // 尝试解析扩展数据
        if (StringUtils.hasText(task.getExtData())) {
            try {
                Map<String, Object> extDataMap = objectMapper.readValue(
                        task.getExtData(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );
                variables.putAll(extDataMap);
            } catch (Exception e) {
                log.debug("解析扩展数据失败，忽略：{}", e.getMessage());
            }
        }
        
        return variables;
    }
    
    /**
     * 构建 HTTP 请求（默认方式，兼容旧代码）
     */
    private Request buildRequest(NotifyTask task) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(task.getTargetUrl());
        
        // 添加默认请求头
        builder.addHeader("Content-Type", "application/json;charset=UTF-8");
        builder.addHeader("User-Agent", "NotifyHub/1.0");
        
        // 添加自定义请求头
        if (StringUtils.hasText(task.getHeaders())) {
            Map<String, String> headers = objectMapper.readValue(
                    task.getHeaders(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
            );
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        
        // 构建请求体
        RequestBody body = null;
        if (task.getBody() != null) {
            body = RequestBody.create(
                    task.getBody(), 
                    MediaType.parse("application/json;charset=UTF-8")
            );
        }
        
        // 根据请求方法构建请求
        String method = task.getHttpMethod() != null ? task.getHttpMethod().toUpperCase() : "POST";
        switch (method) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(body != null ? body : RequestBody.create("", MediaType.parse("text/plain")));
                break;
            case "PUT":
                builder.put(body != null ? body : RequestBody.create("", MediaType.parse("text/plain")));
                break;
            case "DELETE":
                builder.delete(body);
                break;
            case "PATCH":
                builder.patch(body != null ? body : RequestBody.create("", MediaType.parse("text/plain")));
                break;
            default:
                builder.method(method, body);
        }
        
        return builder.build();
    }
}