package com.company.notify.common.enums;

/**
 * 通知任务状态枚举
 */
public enum TaskStatus {
    
    /**
     * 待投递
     */
    PENDING("PENDING", "待投递"),
    
    /**
     * 投递中
     */
    PROCESSING("PROCESSING", "投递中"),
    
    /**
     * 投递成功
     */
    SUCCESS("SUCCESS", "投递成功"),
    
    /**
     * 投递失败（可重试）
     */
    FAILED("FAILED", "投递失败"),
    
    /**
     * 死信（超过最大重试次数）
     */
    DEAD_LETTER("DEAD_LETTER", "死信"),
    
    /**
     * 已忽略（人工干预）
     */
    IGNORED("IGNORED", "已忽略");
    
    private final String code;
    private final String description;
    
    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}
