package com.example.ecommerce.common;

/**
 * 业务异常。在 service 层按需抛出，由 GlobalExceptionHandler 统一转换为
 * {@link Result} 错误体返回给调用方，避免在每个接口里手写 try/catch。
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
