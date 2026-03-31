package com.company.notify.common.enums;

/**
 * 供应商类型枚举
 */
public enum SupplierType {
    
    AD_SYSTEM("AD_SYSTEM", "广告系统"),
    CRM("CRM", "CRM 系统"),
    INVENTORY("INVENTORY", "库存系统"),
    EMAIL("EMAIL", "邮件服务"),
    SMS("SMS", "短信服务"),
    CUSTOM("CUSTOM", "自定义");
    
    private final String code;
    private final String description;
    
    SupplierType(String code, String description) {
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
