package com.company.notify.common.exception;

/**
 * 通知服务基础异常类
 */
public class NotifyException extends RuntimeException {
    
    private String code;
    
    public NotifyException(String message) {
        super(message);
    }
    
    public NotifyException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public NotifyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public NotifyException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
}
