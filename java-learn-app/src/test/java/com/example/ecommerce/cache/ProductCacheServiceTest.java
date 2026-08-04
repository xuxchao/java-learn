package com.example.ecommerce.cache;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductCacheService 单元测试：mock RedisTemplate / ProductService / 布隆过滤器 / RedisLock / 事件发布器，
 * 聚焦缓存读写与三大问题的分支逻辑，不连 Redis、不连库。
 */
class ProductCacheServiceTest {

    private final ProductService productService = mock(ProductService.class);
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> ops = mock(ValueOperations.class);
    private final ProductIdBloomFilter bloomFilter = mock(ProductIdBloomFilter.class);
    private final RedisLock redisLock = mock(RedisLock.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ProductCacheService cacheService =
            new ProductCacheService(productService, redis, bloomFilter, redisLock, eventPublisher);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.delete(anyString())).thenReturn(true);
        when(bloomFilter.mightContain(anyLong())).thenReturn(true);
        when(redisLock.tryLock(anyString(), anyLong())).thenReturn("tok");
        doNothing().when(redisLock).unlock(anyString(), anyString());
        doNothing().when(eventPublisher).publishEvent(any());
    }

    private Product sample() {
        return new Product("手机", new BigDecimal("1999"), "desc");
    }

    @Test
    void cache_hit_returns_from_cache() {
        Product p = sample();
        when(ops.get("product:1")).thenReturn(CacheEnvelope.of(p, 0));

        ProductCacheService.CacheResult r = cacheService.getProductWithCacheInfo(1L);

        assertEquals(true, r.fromCache());
        assertEquals(p, r.product());
        verify(productService, times(0)).getProduct(anyLong());
    }

    @Test
    void bloom_reject_throws_not_found() {
        when(bloomFilter.mightContain(999L)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> cacheService.getProductWithCacheInfo(999L));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void cache_miss_loads_from_db_and_fills() {
        Product p = sample();
        when(ops.get(anyString())).thenReturn(null); // 缓存未命中
        when(productService.getProduct(1L)).thenReturn(p);

        ProductCacheService.CacheResult r = cacheService.getProductWithCacheInfo(1L);

        assertEquals(false, r.fromCache());
        assertEquals(p, r.product());

        var captor = forClass(CacheEnvelope.class);
        verify(ops).set(anyString(), captor.capture(), anyLong(), any());
        assertEquals(p, captor.getValue().getData());
        verify(redisLock).unlock("product:1", "tok");
    }

    @Test
    void db_miss_caches_null_and_throws() {
        when(ops.get(anyString())).thenReturn(null);
        when(productService.getProduct(1L)).thenThrow(new ApiException(ErrorCode.PRODUCT_NOT_FOUND));

        ApiException ex = assertThrows(ApiException.class, () -> cacheService.getProductWithCacheInfo(1L));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());

        var captor = forClass(CacheEnvelope.class);
        verify(ops).set(anyString(), captor.capture(), anyLong(), any());
        assertTrue(captor.getValue().isNullValue());
    }

    @Test
    void logical_expired_returns_stale_and_triggers_refresh() {
        Product p = sample();
        long past = System.currentTimeMillis() - 1000;
        when(ops.get("product:1")).thenReturn(CacheEnvelope.of(p, past));

        ProductCacheService.CacheResult r = cacheService.getProductWithCacheInfo(1L);

        assertEquals(p, r.product());
        assertEquals(true, r.fromCache());
        verify(eventPublisher).publishEvent(any(RefreshCacheEvent.class));
    }

    @Test
    void random_ttl_is_within_range() {
        // 物理 TTL = 基准(30min) + 随机[0,10min)
        for (int i = 0; i < 200; i++) {
            long ttl = cacheService.randomPhysicalTtlSeconds();
            assertTrue(ttl >= 30 * 60, "ttl 不应低于基准 30min");
            assertTrue(ttl < 40 * 60, "ttl 不应达到 基准+抖动 = 40min");
        }
    }

    @Test
    void update_evicts_cache_immediately_and_schedules_delayed_delete() {
        Product p = sample();
        p.setId(1L);
        when(productService.updateProduct(1L, null, null, null)).thenReturn(p);

        cacheService.updateProduct(1L, null, null, null);

        verify(redis).delete("product:1");
        verify(eventPublisher).publishEvent(any(DelayedEvictEvent.class));
    }

    @Test
    void create_adds_id_to_bloom_filter() {
        Product p = sample();
        p.setId(5L);
        when(productService.createProduct("x", null, null)).thenReturn(p);

        cacheService.createProduct("x", null, null);

        verify(bloomFilter).add(5L);
    }
}
