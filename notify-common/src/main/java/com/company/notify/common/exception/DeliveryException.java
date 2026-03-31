package com.company.notify.common.exception;

/**
 * 投递失败异常
 */
public class DeliveryException extends NotifyException {
    
    private Integer httpStatusCode;
    private int retryCount;
    
    public DeliveryException(String message) {
        super(message);
    }
    
    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DeliveryException(String message, Integer httpStatusCode, int retryCount) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.retryCount = retryCount;
    }
    
    public DeliveryException(String message, Integer httpStatusCode, int retryCount, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
        this.retryCount = retryCount;
    }
    
    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
}
