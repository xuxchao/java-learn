package com.example.ecommerce.cache;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 商品 ID 布隆过滤器（内存版，Kirsch-Mitzenmacher 双哈希）。
 *
 * <p>作用：在访问缓存 / 数据库之前，快速判断"某个商品 ID 是否可能存在"，
 * 拦截对不存在 ID 的查询，防止缓存穿透（穿透 = 大量查不存在的 key，缓存与 DB 双双被打穿）。
 *
 * <p>特性：
 * <ul>
 *   <li>判定为"可能存在" → 放行到缓存 / DB；</li>
 *   <li>判定为"一定不存在" → 直接拒绝（布隆过滤器不会误杀已存在的元素，即无假阴性）；</li>
 *   <li>存在假阳性（可能把不存在的判为存在），但概率可控，放行后由空值缓存兜底。</li>
 * </ul>
 *
 * <p>已知限制（面试考点）：布隆过滤器不支持删除（删了会误伤其它元素），
 * 故本项目删商品时只删缓存、不动过滤器。生产多实例应改用 RedisBloom（Redis 模块）或容量更大的位数组。
 */
@Component
public class ProductIdBloomFilter {

    private final int bitSize;
    private final int hashFunctions;
    private final AtomicLongArray bits;

    public ProductIdBloomFilter() {
        // 1M 位 ≈ 128KB，演示足够；k=5 在预期元素量级下误判率很低
        this.bitSize = 1 << 20;
        this.hashFunctions = 5;
        this.bits = new AtomicLongArray((bitSize + 63) / 64);
    }

    public void add(long value) {
        for (int i = 0; i < hashFunctions; i++) {
            int index = Math.floorMod(hash(value, i), bitSize);
            int slot = index >>> 6;
            long mask = 1L << (index & 63);
            // 原子读改写：并发 add / add 与 mightContain 之间都有 happens-before，
            // 否则普通 long[] 上的非原子 |= 可能丢位，造成"已存在元素被误判为不存在"（假阴性）。
            bits.getAndUpdate(slot, cur -> cur | mask);
        }
    }

    public void addAll(Collection<Long> values) {
        if (values == null) {
            return;
        }
        for (Long v : values) {
            if (v != null) {
                add(v);
            }
        }
    }

    public boolean mightContain(long value) {
        for (int i = 0; i < hashFunctions; i++) {
            int index = Math.floorMod(hash(value, i), bitSize);
            // get 是 volatile 读，能看到其他线程通过 getAndUpdate 写入的位
            if ((bits.get(index >>> 6) & (1L << (index & 63))) == 0) {
                return false; // 任一位为 0 → 一定不存在
            }
        }
        return true; // 所有位都为 1 → 可能存在（有假阳性可能）
    }

    // Kirsch-Mitzenmacher：用两个独立哈希派生 k 个索引
    private int hash(long value, int i) {
        int h1 = Long.hashCode(value);
        int h2 = Long.hashCode(value ^ 0x9e3779b97f4a7c15L);
        h2 |= 1; // 保证奇数，避免步长为 0 导致全部命中同一位
        return h1 + i * h2;
    }
}
