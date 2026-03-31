package com.company.notify.common.enums;

/**
 * 失败原因枚举
 */
public enum FailureReason {
    
    NETWORK_TIMEOUT("NETWORK_TIMEOUT", "网络超时"),
    CONNECTION_REFUSED("CONNECTION_REFUSED", "连接拒绝"),
    HTTP_4XX("HTTP_4XX", "客户端错误"),
    HTTP_5XX("HTTP_5XX", "服务端错误"),
    RATE_LIMITED("RATE_LIMITED", "被限流"),
    INVALID_CONFIG("INVALID_CONFIG", "配置错误"),
    UNKNOWN("UNKNOWN", "未知错误");
    
    private final String code;
    private final String description;
    
    FailureReason(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据 HTTP 状态码判断失败原因
     */
    public static FailureReason fromHttpStatusCode(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            if (statusCode == 429) {
                return RATE_LIMITED;
            }
            return HTTP_4XX;
        } else if (statusCode >= 500) {
            return HTTP_5XX;
        }
        return UNKNOWN;
    }
}
