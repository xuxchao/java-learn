package com.example.ecommerce.cache;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 缓存相关的异步任务（由 {@link ProductCacheService} 通过 Spring 事件触发）。
 *
 * <ul>
 *   <li>{@link DelayedEvictEvent}：延迟双删——写完 DB 删一次缓存后，隔一小段时间再删一次，
 *       兜底"删缓存与读并发导致旧值被回填"的极小概率窗口；</li>
 *   <li>{@link RefreshCacheEvent}：逻辑过期后异步回源刷新缓存。</li>
 * </ul>
 *
 * <p>用事件而非直接调用，是为了让 {@link ProductCacheService} 不反向依赖本类，
 * 同时 {@code @Async} 把这些耗时操作放到独立线程，不阻塞主请求。
 */
@Component
public class CacheAsyncTasks {

    private final ProductCacheService productCacheService;
    private final RedisTemplate<String, Object> redis;

    public CacheAsyncTasks(ProductCacheService productCacheService, RedisTemplate<String, Object> redis) {
        this.productCacheService = productCacheService;
        this.redis = redis;
    }

    @Async("cacheAsyncExecutor")
    @EventListener
    public void onDelayedEvict(DelayedEvictEvent event) throws InterruptedException {
        Thread.sleep(event.delayMs());
        redis.delete(event.key());
    }

    @Async("cacheAsyncExecutor")
    @EventListener
    public void onRefresh(RefreshCacheEvent event) {
        productCacheService.reloadFromDb(event.id());
    }
}
