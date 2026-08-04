package com.example.ecommerce.cache;

import com.example.ecommerce.model.Product;

/**
 * 缓存信封：包在真正数据外面的元数据容器。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code data}：被缓存的商品（非泛型字段，避免 JSON 反序列化时因类型擦除拿不到具体类型）；</li>
 *   <li>{@code nullValue}：是否为"空值缓存"（数据库里根本没有这条记录，用短 TTL 存一个空标记防穿透）；</li>
 *   <li>{@code logicalExpireAt}：逻辑过期时间戳（epoch 毫秒）。物理 TTL 到期后整条 key 被 Redis 回收；
 *       在此之前若逻辑过期，则"返回旧值 + 异步刷新"，用于缓存击穿的"逻辑过期"方案。</li>
 * </ul>
 *
 * <p>整条 value 由 {@code RedisConfig} 用 {@code Jackson2JsonRedisSerializer(CacheEnvelope.class)} 序列化，
 * 反序列化时显式指定目标类型，<b>无需 {@code @class} 类型信息</b>，读取回来一定是 {@code CacheEnvelope} 实例，可直接强转。
 */
public class CacheEnvelope {

    private Product data;
    private boolean nullValue;
    private long logicalExpireAt;

    public CacheEnvelope() {
    }

    public static CacheEnvelope of(Product data, long logicalExpireAt) {
        CacheEnvelope e = new CacheEnvelope();
        e.data = data;
        e.logicalExpireAt = logicalExpireAt;
        return e;
    }

    public static CacheEnvelope ofNull(long logicalExpireAt) {
        CacheEnvelope e = new CacheEnvelope();
        e.nullValue = true;
        e.logicalExpireAt = logicalExpireAt;
        return e;
    }

    public Product getData() {
        return data;
    }

    public void setData(Product data) {
        this.data = data;
    }

    public boolean isNullValue() {
        return nullValue;
    }

    public void setNullValue(boolean nullValue) {
        this.nullValue = nullValue;
    }

    public long getLogicalExpireAt() {
        return logicalExpireAt;
    }

    public void setLogicalExpireAt(long logicalExpireAt) {
        this.logicalExpireAt = logicalExpireAt;
    }
}
