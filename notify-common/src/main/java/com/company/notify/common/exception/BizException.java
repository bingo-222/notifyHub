package com.company.notify.common.exception;

/**
 * 业务异常类（用于友好的业务错误提示）
 */
public class BizException extends RuntimeException {
    
    public BizException(String message) {
        super(message);
    }
    
    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
