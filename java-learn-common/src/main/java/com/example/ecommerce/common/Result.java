package com.example.ecommerce.common;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一返回体。所有 HTTP 接口都返回此结构，前端按 code/message/data 解析。
 * 约定：code = 0 表示成功，非 0 表示业务/系统错误（见 ErrorCode）。
 */
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public Result() {
        this.timestamp = Instant.now().getEpochSecond();
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
