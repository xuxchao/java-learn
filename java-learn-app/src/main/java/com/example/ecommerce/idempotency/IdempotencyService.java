package com.example.ecommerce.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 接口幂等服务（token 方案核心）。
 *
 * <p>用法：把"会产出副作用的动作"包进 {@link #execute(String, Class, Supplier)}，
 * 用业务方自带的幂等键（如 HTTP 头 {@code Idempotency-Key}、订单号）作为 key。
 *
 * <pre>
 *   第一次（key 不存在）：claim 成功 → 执行动作 → complete 存结果 → 返回结果（replayed=false）
 *   重放（key 已存在）  ：claim 失败 → 不执行动作 → 直接返回已存结果（replayed=true，无副作用）
 * </pre>
 *
 * <p><b>为什么需要 claim/complete 两阶段？</b> 若先 get 判断再执行，存在"两个相同 key 的请求
 * 同时 get 到 null、都执行动作"的竞态（TOCTOU），副作用会发生两次，幂等失效。claim 用一条原子
 * 命令抢"首次名额"，从根本上杜绝重复执行。
 *
 * <p><b>已知限制（教学取舍，非 bug）</b>：若两个相同 key 的请求"严格同时"到达，第二个会进
 * {@link #waitForComplete} 短暂等待首个完成；极端情况下首个执行失败已 {@code remove} 该 key，
 * 第二个会重新执行一次（失败重试语义，符合预期，且最多重试一次避免无限递归）。
 * 更严谨的工业实现可把"处理中"的值存为真正的 Future，重放时直接复用该 Future。详见 docs/concurrency-notes.md。
 */
@Service
public class IdempotencyService {

    /** 重放时若仍在"处理中"，最多等多久拿结果（纳秒）。 */
    private static final long REPLAY_WAIT_NANOS = TimeUnit.SECONDS.toNanos(2);
    /** 首射失败/超时后的最大重试次数（避免无限递归）。 */
    private static final int MAX_RETRY = 1;
    private static final String IN_FLIGHT = "__IN_FLIGHT__";

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final long defaultTtlSeconds;

    @Autowired
    public IdempotencyService(IdempotencyStore store) {
        this(store, 300); // 默认 5 分钟
    }

    public IdempotencyService(IdempotencyStore store, long defaultTtlSeconds) {
        this.store = store;
        this.objectMapper = new ObjectMapper();
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    /**
     * 幂等执行：相同 key 只让动作真正跑一次，后续重放直接返回首次的结果。
     *
     * @param key        幂等键（业务方保证唯一，如 Idempotency-Key / orderNo）
     * @param resultType 返回值类型（用于 JSON 反序列化结果）
     * @param action     真正产生副作用的动作（下单、扣款等）
     * @param <T>        结果类型
     */
    public <T> IdempotencyResult<T> execute(String key, Class<T> resultType, Supplier<T> action) {
        return execute(key, resultType, action, 0);
    }

    private <T> IdempotencyResult<T> execute(String key, Class<T> resultType, Supplier<T> action, int attempt) {
        if (store.claim(key, defaultTtlSeconds)) {
            try {
                T result = action.get();
                store.complete(key, serialize(result), defaultTtlSeconds);
                return new IdempotencyResult<>(result, false);
            } catch (RuntimeException e) {
                store.remove(key);   // 首次失败：清掉占位，允许同 key 重试
                throw e;
            }
        } else {
            // 重放：等首个完成（若仍在处理中），再返回已存结果，绝不重跑 action
            String raw = waitForComplete(key);
            if (raw != null && !IN_FLIGHT.equals(raw)) {
                return new IdempotencyResult<>(deserialize(raw, resultType), true);
            }
            // 首射失败已 remove，或等待超时：最多再重试一次（避免无限递归）
            if (attempt >= MAX_RETRY) {
                throw new IllegalStateException("幂等键 " + key + " 处理超时或首射失败，已停止重试");
            }
            return execute(key, resultType, action, attempt + 1);
        }
    }

    private String waitForComplete(String key) {
        long deadline = System.nanoTime() + REPLAY_WAIT_NANOS;
        while (System.nanoTime() < deadline) {
            String v = store.get(key);
            if (v != null && !IN_FLIGHT.equals(v)) {
                return v;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return store.get(key);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("幂等结果序列化失败", e);
        }
    }

    private <T> T deserialize(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("幂等结果反序列化失败", e);
        }
    }
}
