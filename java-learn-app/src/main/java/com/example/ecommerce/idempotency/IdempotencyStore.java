package com.example.ecommerce.idempotency;

/**
 * 幂等存储抽象：幂等键 → 已计算结果 的"原子写入/读取"。
 *
 * <p>生产用 Redis（{@link RedisIdempotencyStore}，跨实例共享）；单元测试用
 * {@link InMemoryIdempotencyStore}（纯 JVM，无需基础设施）。两者语义一致，便于复用同一套断言。
 */
public interface IdempotencyStore {

    /**
     * 原子 claim：若 key 不存在则写入"处理中"占位并返回 true（表示本线程是首次、应执行动作）；
     * 若已存在则返回 false（表示重放，动作不应再执行）。
     */
    boolean claim(String key, long ttlSeconds);

    /** 写入最终计算结果（覆盖占位）。 */
    void complete(String key, String value, long ttlSeconds);

    /** 读取已存结果（重放路径返回给调用方）。 */
    String get(String key);

    /** 删除 key（首次执行失败时调用，允许客户端用同一 key 重试）。 */
    void remove(String key);
}
