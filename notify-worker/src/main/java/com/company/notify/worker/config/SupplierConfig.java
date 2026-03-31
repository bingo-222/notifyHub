package com.company.notify.worker.config;

import java.util.Map;

/**
 * 供应商配置
 */
public class SupplierConfig {
    
    /**
     * 供应商代码
     */
    private String supplierCode;
    
    /**
     * 目标 URL
     */
    private String targetUrl;
    
    /**
     * 请求方法
     */
    private String httpMethod = "POST";
    
    /**
     * 请求头模板
     */
    private Map<String, String> headersTemplate;
    
    /**
     * 请求体模板（JSON 字符串）
     */
    private String bodyTemplate;
    
    /**
     * 超时时间（毫秒）
     */
    private Integer timeoutMs = 30000;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount = 10;
    
    public SupplierConfig() {
        super();
    }
    
    public SupplierConfig(String supplierCode, String targetUrl, String httpMethod, 
                         Map<String, String> headersTemplate, String bodyTemplate,
                         Integer timeoutMs, Integer maxRetryCount) {
        this.supplierCode = supplierCode;
        this.targetUrl = targetUrl;
        this.httpMethod = httpMethod;
        this.headersTemplate = headersTemplate;
        this.bodyTemplate = bodyTemplate;
        this.timeoutMs = timeoutMs;
        this.maxRetryCount = maxRetryCount;
    }
    
    public String getSupplierCode() {
        return supplierCode;
    }
    
    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }
    
    public String getTargetUrl() {
        return targetUrl;
    }
    
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }
    
    public String getHttpMethod() {
        return httpMethod;
    }
    
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
    
    public Map<String, String> getHeadersTemplate() {
        return headersTemplate;
    }
    
    public void setHeadersTemplate(Map<String, String> headersTemplate) {
        this.headersTemplate = headersTemplate;
    }
    
    public String getBodyTemplate() {
        return bodyTemplate;
    }
    
    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }
    
    public Integer getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }
    
    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
}
