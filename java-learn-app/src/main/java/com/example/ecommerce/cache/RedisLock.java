package com.example.ecommerce.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式互斥锁（SET NX PX + Lua 原子释放）。
 *
 * <p>用于缓存击穿的"互斥"方案：热点 key 失效瞬间，只允许一个线程回源 DB，其余线程等待或拿旧值，
 * 避免大量请求同时打到数据库。区别于 {@code synchronized}（仅单实例有效），本锁跨实例生效。
 *
 * <p>释放用 Lua 脚本做"取值比对 + 删除"原子操作，避免误删别人的锁（锁过期后被别的线程拿到，
 * 自己却把别人的锁删了）。
 */
@Component
public class RedisLock {

    private static final String PREFIX = "lock:";
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redis;

    public RedisLock(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /**
     * 尝试加锁。成功返回非空 token（释放时凭它对账），失败（已被别人持有）返回 null。
     */
    public String tryLock(String key, long expireMs) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(PREFIX + key, token, expireMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        redis.execute(UNLOCK_SCRIPT, Collections.singletonList(PREFIX + key), token);
    }
}
