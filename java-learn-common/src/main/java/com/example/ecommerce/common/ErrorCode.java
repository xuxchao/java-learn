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
    FORBIDDEN(2004, "无权访问该资源"),

    // 3xxx 商品&库存&下单（M3）
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    STOCK_NOT_INITIALIZED(3002, "库存未初始化"),
    STOCK_ALREADY_INITIALIZED(3003, "库存已初始化"),
    STOCK_NOT_ENOUGH(3004, "库存不足"),
    STOCK_CONFLICT(3005, "库存并发冲突，请重试"),
    ORDER_CREATE_FAILED(3006, "下单失败");

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
