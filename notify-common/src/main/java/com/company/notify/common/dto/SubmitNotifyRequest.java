package com.company.notify.common.dto;

import java.io.Serializable;

/**
 * 提交通知请求 DTO
 */
public class SubmitNotifyRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 业务 ID（用于幂等性校验）
     */
    private String bizId;
    
    /**
     * 供应商代码
     */
    private String supplierCode;
    
    /**
     * 目标 URL
     */
    private String targetUrl;
    
    /**
     * 请求方法 (POST/GET/PUT/DELETE)
     */
    private String httpMethod = "POST";
    
    /**
     * 请求头（JSON 字符串）
     */
    private String headers;
    
    /**
     * 请求体（JSON 字符串）
     */
    private String body;
    
    /**
     * 超时时间（毫秒），为空则使用默认值
     */
    private Integer timeoutMs;
    
    /**
     * 最大重试次数，为空则使用默认值
     */
    private Integer maxRetryCount;
    
    /**
     * 扩展字段（JSON 字符串）
     */
    private String extData;
    
    public SubmitNotifyRequest() {
        super();
    }
    
    public String getBizId() {
        return bizId;
    }
    
    public void setBizId(String bizId) {
        this.bizId = bizId;
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
    
    public String getHeaders() {
        return headers;
    }
    
    public void setHeaders(String headers) {
        this.headers = headers;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
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
    
    public String getExtData() {
        return extData;
    }
    
    public void setExtData(String extData) {
        this.extData = extData;
    }
}