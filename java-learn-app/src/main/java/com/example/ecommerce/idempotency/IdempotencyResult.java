package com.example.ecommerce.idempotency;

/**
 * 幂等执行的结果包装：携带真实返回值，并标识本次是"首次执行"还是"重放"。
 */
public class IdempotencyResult<T> {

    private final T value;
    private final boolean replayed;

    public IdempotencyResult(T value, boolean replayed) {
        this.value = value;
        this.replayed = replayed;
    }

    public T getValue() {
        return value;
    }

    public boolean isReplayed() {
        return replayed;
    }
}
