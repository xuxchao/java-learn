package com.example.ecommerce.idempotency;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯 JVM 幂等存储（用于单元测试 / 单实例演示）。
 *
 * <p>{@link #claim} 用 ConcurrentHashMap 的 {@code putIfAbsent} 语义保证原子："占位"写入成功才算首次。
 * 过期采用惰性清理（读取时发现已过期则视作不存在），避免额外起定时线程。生产环境请换成
 * {@link RedisIdempotencyStore}（跨实例、跨重启）。
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final String IN_FLIGHT = "__IN_FLIGHT__";
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public boolean claim(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        Entry fresh = new Entry(IN_FLIGHT, now + ttlSeconds * 1000L);
        Entry prev = map.compute(key, (k, old) -> {
            if (old == null || old.expireAt <= now) {
                return fresh;            // 不存在或已过期 -> 占位成功（首次）
            }
            return old;                  // 未过期 -> 保留旧值（重放）
        });
        return prev == fresh;           // prev==fresh 表示本次写入生效 -> 首次
    }

    @Override
    public void complete(String key, String value, long ttlSeconds) {
        map.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    @Override
    public String get(String key) {
        Entry e = map.get(key);
        if (e == null) {
            return null;
        }
        if (e.expireAt <= System.currentTimeMillis()) {
            map.remove(key, e);
            return null;
        }
        return e.value;
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    private static final class Entry {
        final String value;
        final long expireAt;

        Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
