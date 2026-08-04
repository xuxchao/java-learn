package com.example.ecommerce.cache;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import com.example.ecommerce.service.ProductService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 商品缓存服务（M4 缓存 & 三大问题）。
 *
 * <p>职责：在 {@link ProductService}（纯 DB 层）之上加一层缓存，落实面试常考的四件事：
 * <ol>
 *   <li><b>读路径缓存优先</b>：热点商品详情命中缓存直接返回，未命中回源 DB 并回填；</li>
 *   <li><b>缓存穿透</b>：布隆过滤器拦截"一定不存在"的 ID + 空值短 TTL 缓存兜底；</li>
 *   <li><b>缓存击穿</b>：热点 key 失效瞬间用 Redis 分布式锁做 single-flight（互斥），
 *       另提供"逻辑过期"方案——物理未过期但逻辑过期时返回旧值并异步刷新；</li>
 *   <li><b>缓存雪崩</b>：写缓存时给 TTL 加随机抖动，避免大量 key 同时失效把 DB 压垮；</li>
 *   <li><b>缓存一致性</b>：写后"先更 DB 再删缓存 + 延迟双删"（见 {@link #evict}）。</li>
 * </ol>
 *
 * <p>读接口返回的 {@link CacheResult#fromCache()} 供 Controller 打 {@code X-Cache: HIT/MISS} 头，
 * 方便 curl / 脚本直观验证缓存是否生效。
 */
@Service
public class ProductCacheService {

    public static final String CACHE_KEY_PREFIX = "product:";

    /** 物理 TTL 基准：30 分钟 */
    private static final long PHYSICAL_TTL_BASE_SECONDS = 30 * 60;
    /** 物理 TTL 随机抖动：0~10 分钟，避免大量 key 同一时刻集体失效（防雪崩） */
    private static final long PHYSICAL_TTL_JITTER_SECONDS = 10 * 60;
    /** 逻辑 TTL：值物理仍存活，但逻辑上过期即触发异步刷新（击穿-逻辑过期方案） */
    private static final long LOGICAL_TTL_SECONDS = 30;
    /** 空值缓存 TTL：很短，防止对不存在 ID 的反复回源（防穿透） */
    private static final long NULL_TTL_SECONDS = 60;
    /** 互斥锁过期时间，必须 > 一次 DB 回源耗时，否则锁提前释放会失去互斥意义 */
    private static final long LOCK_EXPIRE_MS = 3000;
    /** 没抢到锁时最多等待别的线程回填的时间 */
    private static final long LOCK_WAIT_MS = 200;
    /** 延迟双删的第二次删除延迟：需大于一次普通读回填的耗时，否则第二次删除后旧值可能又被回填 */
    private static final long DELAYED_DELETE_MS = 1000;

    private final ProductService productService;
    private final RedisTemplate<String, Object> redis;
    private final ProductIdBloomFilter bloomFilter;
    private final RedisLock redisLock;
    private final ApplicationEventPublisher eventPublisher;

    /** 单实例下的本地互斥锁：Redis 锁没抢到时的兜底 single-flight，保证本实例内不重复回源 */
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    public ProductCacheService(ProductService productService,
                               RedisTemplate<String, Object> redis,
                               ProductIdBloomFilter bloomFilter,
                               RedisLock redisLock,
                               ApplicationEventPublisher eventPublisher) {
        this.productService = productService;
        this.redis = redis;
        this.bloomFilter = bloomFilter;
        this.redisLock = redisLock;
        this.eventPublisher = eventPublisher;
    }

    /** 读结果包装：商品 + 是否来自缓存（用于 X-Cache 头） */
    public record CacheResult(Product product, boolean fromCache) {
    }

    // ---------------------------------------------------------------- 读

    public Product getProduct(Long id) {
        return getProductWithCacheInfo(id).product();
    }

    public CacheResult getProductWithCacheInfo(Long id) {
        // 1) 布隆过滤器：一定不存在 → 直接拦截（穿透防护-布隆过滤器，无假阴性）
        if (!bloomFilter.mightContain(id)) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        String key = keyOf(id);
        CacheEnvelope env = readEnvelope(key);

        // 2) 命中（含空值缓存命中）
        if (env != null) {
            if (env.isNullValue()) {
                throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND); // 命中"空值缓存"（穿透防护）
            }
            // 逻辑过期：物理仍存活，但逻辑寿命到了 → 返回旧值 + 异步刷新（击穿-逻辑过期）
            if (env.getLogicalExpireAt() > 0 && System.currentTimeMillis() > env.getLogicalExpireAt()) {
                eventPublisher.publishEvent(new RefreshCacheEvent(id));
            }
            return new CacheResult(env.getData(), true); // 正常缓存命中
        }

        // 3) 未命中 → single-flight 回源（互斥锁解决击穿）
        return loadWithLock(id, key);
    }

    // ---------------------------------------------------------------- 写（一致性）

    public List<Product> listProducts() {
        return productService.listProducts();
    }

    public Product createProduct(String name, BigDecimal price, String description) {
        Product p = productService.createProduct(name, price, description);
        bloomFilter.add(p.getId()); // 新商品入布隆过滤器，保证后续读能被放行
        return p;
    }

    public Product updateProduct(Long id, String name, BigDecimal price, String description) {
        Product p = productService.updateProduct(id, name, price, description);
        evict(id); // 先更 DB（已做），再删缓存 + 延迟双删
        return p;
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        evict(id);
        // 布隆过滤器不支持删除（已知限制），这里只删缓存，接受过滤器里残留该 ID
    }

    public Stock getStock(Long productId) {
        return productService.getStock(productId);
    }

    public Stock initStock(Long productId, Integer total) {
        return productService.initStock(productId, total);
    }

    /** 写后一致性：立即删缓存 + 延迟双删（极小概率的"删缓存与读并发回填"窗口兜底） */
    private void evict(Long id) {
        String key = keyOf(id);
        redis.delete(key);
        eventPublisher.publishEvent(new DelayedEvictEvent(key, DELAYED_DELETE_MS));
    }

    // ---------------------------------------------------------------- 回源（single-flight）

    private CacheResult loadWithLock(Long id, String key) {
        String token = redisLock.tryLock(key, LOCK_EXPIRE_MS);
        if (token != null) {
            try {
                // 双重检查：拿到锁后再看一眼，别的线程可能已回填
                CacheEnvelope env = readEnvelope(key);
                if (env != null) {
                    return hit(env);
                }
                return loadFromDbAndFill(id, key);
            } finally {
                redisLock.unlock(key, token);
            }
        }
        // 没抢到锁：等别的线程回填；超时则本地兜底加载，保证不挂起（单实例安全）
        CacheEnvelope env = waitForFill(key);
        if (env != null) {
            return hit(env);
        }
        return loadWithLocalLock(id, key);
    }

    private CacheEnvelope waitForFill(String key) {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            CacheEnvelope env = readEnvelope(key);
            if (env != null) {
                return env;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private CacheResult loadWithLocalLock(Long id, String key) {
        Object lock = localLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            CacheEnvelope env = readEnvelope(key);
            if (env != null) {
                return hit(env);
            }
            return loadFromDbAndFill(id, key);
        }
    }

    private CacheResult loadFromDbAndFill(Long id, String key) {
        try {
            Product product = productService.getProduct(id); // 不存在会抛 PRODUCT_NOT_FOUND
            CacheEnvelope env = CacheEnvelope.of(product, logicalExpireAt());
            redis.opsForValue().set(key, env, randomPhysicalTtlSeconds(), TimeUnit.SECONDS);
            return new CacheResult(product, false);
        } catch (ApiException ex) {
            if (ex.getCode() == ErrorCode.PRODUCT_NOT_FOUND.getCode()) {
                // 缓存空值（短 TTL），防穿透：下次同样的无效 ID 直接命中空值，不再回源
                CacheEnvelope env = CacheEnvelope.ofNull(logicalExpireAt());
                redis.opsForValue().set(key, env, NULL_TTL_SECONDS, TimeUnit.SECONDS);
            }
            throw ex;
        }
    }

    /**
     * 异步刷新（逻辑过期触发）。由 {@link CacheAsyncTasks} 在独立线程调用。
     * 抢到锁才刷，避免并发刷新互相打架；刷新失败（商品已删）则缓存空值。
     */
    public void reloadFromDb(Long id) {
        String key = keyOf(id);
        String token = redisLock.tryLock(key, LOCK_EXPIRE_MS);
        if (token == null) {
            return; // 别的线程在刷，跳过
        }
        try {
            CacheEnvelope env = readEnvelope(key);
            if (env != null && env.isNullValue()) {
                return; // 空值不刷新
            }
            try {
                Product product = productService.getProduct(id);
                redis.opsForValue().set(key, CacheEnvelope.of(product, logicalExpireAt()),
                        randomPhysicalTtlSeconds(), TimeUnit.SECONDS);
            } catch (ApiException ex) {
                if (ex.getCode() == ErrorCode.PRODUCT_NOT_FOUND.getCode()) {
                    redis.opsForValue().set(key, CacheEnvelope.ofNull(logicalExpireAt()),
                            NULL_TTL_SECONDS, TimeUnit.SECONDS);
                }
            }
        } finally {
            redisLock.unlock(key, token);
        }
    }

    // ---------------------------------------------------------------- 工具

    @SuppressWarnings("unchecked")
    private CacheEnvelope readEnvelope(String key) {
        return (CacheEnvelope) redis.opsForValue().get(key);
    }

    /** 缓存 key 拼装（Primitive Obsession：避免裸拼 CACHE_KEY_PREFIX + id 散落各处） */
    private String keyOf(Long id) {
        return CACHE_KEY_PREFIX + id;
    }

    /** 命中分支统一处理：空值缓存 → 视为不存在抛错；否则返回缓存结果 */
    private CacheResult hit(CacheEnvelope env) {
        if (env.isNullValue()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return new CacheResult(env.getData(), true);
    }

    private long logicalExpireAt() {
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(LOGICAL_TTL_SECONDS);
    }

    /** 物理 TTL = 基准 + 随机抖动（包可见，便于单测断言范围） */
    long randomPhysicalTtlSeconds() {
        return PHYSICAL_TTL_BASE_SECONDS + (long) (Math.random() * PHYSICAL_TTL_JITTER_SECONDS);
    }
}
