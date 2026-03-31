package com.company.notify.common.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知任务数据模型
 */
@TableName("notify_task")
public class NotifyTask implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 任务 ID
     */
    private Long id;
    
    /**
     * 业务 ID（用于幂等性校验）
     */
    private String bizId;
    
    /**
     * 供应商代码
     */
    @TableField("supplier_type")
    private String supplierCode;
    
    /**
     * 目标 URL
     */
    private String targetUrl;
    
    /**
     * 请求方法
     */
    private String httpMethod;
    
    /**
     * 请求头
     */
    private String headers;
    
    /**
     * 请求体
     */
    private String body;
    
    /**
     * 任务状态
     */
    private String status;
    
    /**
     * 重试次数
     */
    private Integer retryCount = 0;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryAt;
    
    /**
     * 最后投递时间
     */
    private LocalDateTime lastDeliveredAt;
    
    /**
     * 失败原因
     */
    private String failureReason;
    
    /**
     * 最后错误信息
     */
    private String lastErrorMessage;
    
    /**
     * 扩展数据
     */
    private String extData;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    public NotifyTask() {
        super();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    
    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }
    
    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
    
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }
    
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
    
    public LocalDateTime getLastDeliveredAt() {
        return lastDeliveredAt;
    }
    
    public void setLastDeliveredAt(LocalDateTime lastDeliveredAt) {
        this.lastDeliveredAt = lastDeliveredAt;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    
    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
    
    public String getExtData() {
        return extData;
    }
    
    public void setExtData(String extData) {
        this.extData = extData;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}