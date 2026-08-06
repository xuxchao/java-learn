package com.example.ecommerce.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 生产用幂等存储 Bean（随应用启动注册为唯一的 {@link IdempotencyStore} 实现，
 * 被 {@link IdempotencyService} 自动装配）。单元测试用 {@link InMemoryIdempotencyStore} 替代。
 *
 * <p>基于 Redis 的 {@code SET key value NX EX}（SET IF NOT EXIST + 过期）：
 * <ul>
 *   <li>{@link #claim}：{@code setIfAbsent(key, IN_FLIGHT, EX)}，返回 true 即抢到首次名额（原子）。</li>
 *   <li>{@link #complete}：普通 {@code set(key, value, EX)} 覆盖占位，写入真实结果。</li>
 *   <li>{@link #get}：{@code get(key)} 拿到已存结果（重放时返回给调用方）。</li>
 * </ul>
 *
 * <p>Redis 单命令的 NX 保证"同一 key 只有一个客户端能 claim 成功"，天然跨实例、跨重启（数据在 Redis）。
 * 与 M4 的 {@code RedisLock} 同源思路（SET NX），但目的不同：锁为了"互斥执行"，幂等键为了"去重结果"。
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String IN_FLIGHT = "__IN_FLIGHT__";
    /** key 加命名空间，避免客户端传入的 Idempotency-Key 与缓存等其它 Redis key 撞车。 */
    private static final String PREFIX = "idem:";
    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean claim(String key, long ttlSeconds) {
        Boolean ok = redis.opsForValue().setIfAbsent(PREFIX + key, IN_FLIGHT, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void complete(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(PREFIX + key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(PREFIX + key);
    }

    @Override
    public void remove(String key) {
        redis.delete(PREFIX + key);
    }
}
