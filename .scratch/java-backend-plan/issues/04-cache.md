# 04 — 商品缓存 & 三大问题

**What to build:** 商品详情从数据库改为走 Redis 缓存，并解决缓存场景下的三大经典问题（穿透 / 击穿 / 雪崩）与缓存一致性——热点数据读命中缓存、未命中回源并回填，写后保持与数据库一致。

**Blocked by:** 03 — 商品 & 数据库持久化（缓存一致性依赖 DB 写入）.

**Status:** resolved

- [x] 热点商品详情读命中缓存、未命中回源并回填
- [x] 缓存穿透（布隆过滤器 / 空值）、击穿（互斥 / 逻辑过期）、雪崩（随机 TTL / 集群）均有防护
- [x] 写后一致性策略落地（先更 DB 再删缓存 + 延迟双删）
- [x] 能讲清 Redis 数据结构适用场景与 RDB / AOF 持久化

## 交付内容

### 代码（java-learn-app，`com.example.ecommerce.cache` 包）
- `ProductIdBloomFilter`：商品 ID 内存布隆过滤器（双哈希），拦截"一定不存在"的 ID 防穿透。
- `CacheEnvelope`：缓存信封，包 `Product` + `nullValue`（空值标记）+ `logicalExpireAt`（逻辑过期时间戳）。
- `RedisLock`：基于 Redis `SET NX PX` + Lua 原子释放的分布式互斥锁（击穿 single-flight）。
- `ProductCacheService`：核心。读路径 = 布隆拦截 → 缓存命中/逻辑过期返回旧值并异步刷新 → 未命中走锁 single-flight 回源并回填（随机 TTL 防雪崩）；写路径 = 委托 `ProductService` 落库，更新/删除后 `evict`（立即删 + 延迟双删事件）。
- `CacheAsyncTasks` + 两个事件（`DelayedEvictEvent` / `RefreshCacheEvent`）：`@Async` 处理延迟双删与逻辑过期异步刷新。
- `BloomFilterInitializer`（`CommandLineRunner`）：启动时把已有商品 ID 灌入布隆过滤器，避免重启误判。
- `config/RedisConfig`：JSON `RedisTemplate<String,Object>`（含 JavaTime 模块；value 用 `Jackson2JsonRedisSerializer(CacheEnvelope.class)` 显式指定目标类型，**不写 `@class`**）。
- `config/AsyncConfig`：`@EnableAsync` + 独立线程池。
- `controller/ProductController`：商品读写改走 `ProductCacheService`；`GET /products/{id}` 返回 `X-Cache: HIT/MISS` 头。

### 测试
- `ProductCacheServiceTest`（新增，单元）：缓存命中 / 布隆拦截 / 未命中回源回填 / DB 缺失缓存空值 / 逻辑过期返回旧值并触发刷新 / 随机 TTL 范围 / 更新即删缓存 + 延迟双删 / 新建入布隆过滤器。
- `ProductControllerTest`（更新）：适配 `ProductCacheService`，并断言 `X-Cache` 头 HIT/MISS。
- `scripts/api-smoke.mjs`：新增"二次读命中缓存（X-Cache: HIT）"断言。

### 文档
- `docs/cache-notes.md`：Redis 数据结构适用场景 + RDB/AOF 持久化 + 三大问题/一致性方案对比（"能讲清"载体）。
- `docs/api/curl.md`：新增「2.5 商品缓存（M4）」章节，演示 `X-Cache` 头与穿透拦截。

## 验收要点（自测）

1. **缓存命中**：`GET /products/{id}` 首次 `X-Cache: MISS`（回源并回填），第二次 `X-Cache: HIT`。
   跑 `node scripts/api-smoke.mjs` 可自动验证。
2. **穿透防护**：查不存在的 ID（如 999999）→ 布隆过滤器拦截 → 直接 `3001`，不回源。
3. **击穿防护**：热点 key 失效瞬间，只有抢到 Redis 锁的线程回源，其余等缓存回填或返回旧值（逻辑过期）。
4. **雪崩防护**：写缓存 TTL 带随机抖动，代码层 `randomPhysicalTtlSeconds()` ∈ [30min, 40min)。
5. **一致性**：更新商品后缓存立即失效，后续读重新回源拿到新值；延迟双删兜底并发窗口。
6. **八股**：能口述 Redis 九大数据结构适用场景、RDB vs AOF 取舍与混合持久化（见 `docs/cache-notes.md`）。

## 面试八股自测（要能口述）
- 穿透 / 击穿 / 雪崩的区别与各自解法（布隆+空值 / 锁+逻辑过期 / 随机 TTL+集群）。
- 缓存一致性为什么"先更 DB 再删缓存"，延迟双删解决什么极小概率窗口。
- RDB vs AOF 取舍、混合持久化原理；缓存丢了能否从 DB 回源（cache-aside 下缓存不是真相源）。
- 本项目缓存 value 用什么结构存（String + JSON 信封，含逻辑过期时间戳与空值标记），分布式锁怎么实现（SET NX PX + Lua 释放）。

## 已知限制（面试延伸，非 bug，属设计取舍）

- **single-flight 超时降级**：没抢到 Redis 锁时只等 `LOCK_WAIT_MS=200ms`，超时后降级为本实例内的本地锁（`localLocks`）兜底回源。正常回源 < 200ms 时严格互斥；极端慢回源（>200ms）下等待线程会落到本地锁，多实例部署时不保证跨实例互斥（分布式锁只在拿到锁的那一跳生效）。生产应拉大 `LOCK_WAIT_MS` 或加 `KEYS` 层级锁。
- **延迟双删仅存进程内**：`DelayedEvictEvent` 的延迟由内存计时器承担，应用重启即丢；已把异步线程池的拒绝策略从 `DiscardPolicy` 改为 `CallerRunsPolicy`，保证高并发下第二次删除**不会被静默丢弃**（打满时由发布事件的线程同步执行）。需跨重启可靠的双删应改用延时队列 / RabbitMQ 死信或 Redis 键过期事件。
- **逻辑过期未做热点识别**：所有 key 统一 `LOGICAL_TTL_SECONDS=30`，没有热点探测 / 预热，即"任意 key 存活 30s 后都触发异步刷新"而非只对真热点。教学演示足够，生产应对热点 key 单独配置更长的逻辑 TTL。
- **布隆过滤器是单实例内存版**：已用 `AtomicLongArray` 的原子 `|=` + volatile 读消除"非原子位操作丢位导致假阴性"的并发隐患，且启动时 `BloomFilterInitializer` 灌入已有 ID。多实例需各自预热，或改用 RedisBloom（Redis 模块）让过滤器成为共享真相源。
