package com.example.ecommerce.common;

/**
 * 业务错误码。建议按模块分段，方便排查与对齐前端：
 * 0    成功
 * 500  系统级未预期异常
 * 1xxx 通用
 * 2xxx 用户/鉴权（M2）
 * 3xxx 商品（M3）
 * 4xxx 订单（M6）
 * 5xxx 支付（M7）
 * 8xxx 秒杀（M8）
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),
    SYSTEM_ERROR(500, "系统异常"),
    DEMO_BROKEN(1001, "演示用的故意异常"),

    // 2xxx 用户/鉴权（M2）
    USERNAME_ALREADY_EXISTS(2001, "用户名已存在"),
    INVALID_CREDENTIALS(2002, "用户名或密码错误"),
    UNAUTHORIZED(2003, "未登录或登录已过期"),
    FORBIDDEN(2004, "无权访问该资源");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
