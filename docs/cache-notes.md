# 缓存面试八股自测（M4）

> 配套代码：`com.example.ecommerce.cache.*`、`ProductController`、`docs/api/curl.md`。
> 本文件覆盖 04-cache.md 里"能讲清"的两块：Redis 数据结构适用场景 + RDB/AOF 持久化。

## 一、缓存三大问题与本项目解法

| 问题 | 现象 | 本项目解法（代码位置） |
| --- | --- | --- |
| **穿透** | 大量查**不存在**的 key，缓存与 DB 双双被打穿 | ① 布隆过滤器拦截"一定不存在"的 ID（`ProductIdBloomFilter.mightContain`）② 漏网查询的空结果缓存 60s 短 TTL（`CacheEnvelope.ofNull`） |
| **击穿** | 某个**热点 key 失效瞬间**，海量请求同时回源 | ① Redis 分布式锁 single-flight（`RedisLock` + `loadWithLock`）② 逻辑过期：物理未过期但逻辑过期时返回旧值并异步刷新（`RefreshCacheEvent`） |
| **雪崩** | 大量 key **同一时刻集体失效**，DB 被压垮 | 写缓存 TTL = 30min 基准 + 0~10min 随机抖动（`randomPhysicalTtlSeconds`），错开失效时间 |
| **一致性** | 写 DB 后缓存还是旧值 | 先更 DB 再删缓存 + 延迟双删（`evict` → `DelayedEvictEvent`） |

**缓存一致性方案对比（面试常问）：**
- **先删缓存，再更 DB**：并发读可能在更新完成前把旧值回填 → 脏数据，不推荐。
- **先更 DB，再删缓存（本项目）**：绝大多数情况 OK；极小窗口（删缓存后、读请求在更新前已拿到旧值并回填）可用**延迟双删**兜底。
- ** Canal/订阅 binlog 异步删**：更彻底，适合强一致要求高的场景。

## 二、Redis 数据结构与适用场景

| 结构 | 特点 | 典型场景 |
| --- | --- | --- |
| **String** | 二进制安全的 KV，最大 512MB | 缓存对象 JSON、计数器（`INCR`）、分布式锁（`SET NX`） |
| **Hash** | 字段-值映射，适合存对象 | 商品多字段、购物车（`HSET user:1 pid 3`） |
| **List** | 双向链表，有序 | 最新消息、简单队列（`LPUSH`/`RPOP`） |
| **Set** | 无序去重集合 | 点赞/收藏用户、共同关注（交集 `SINTER`） |
| **ZSet（有序集合）** | 带 score 的有序去重 | 排行榜（`ZADD`/`ZREVRANGE`）、延迟队列（score=执行时间） |
| **Bitmap** | 位操作 | 用户在线状态、签到（连续签到用 `BITCOUNT`） |
| **HyperLogLog** | 基数估算，误差 ~0.81% | UV 统计（省内存） |
| **Geo** | 经纬度索引 | 附近的人、门店距离 |
| **Stream** | 追加日志 | 消息队列、事件溯源（替代部分 MQ 场景） |

> 本项目缓存 value 用 **String + JSON**（`Jackson2JsonRedisSerializer(CacheEnvelope.class)` 序列化 `CacheEnvelope`，**不写 `@class` 类型信息**），分布式锁用 **String 的 `SET NX`**，契合上表。

## 三、Redis 持久化：RDB vs AOF

| 维度 | RDB（快照） | AOF（追加日志） |
| --- | --- | --- |
| 原理 | 定时把内存全量 dump 成 `.rdb` 二进制快照 | 把每个写命令追加到 `.aof` 日志（可配每秒/每次 fsync） |
| 恢复速度 | 快（直接加载快照） | 慢（重放命令） |
| 数据丢失 | 可能丢最后一次快照之后的数据 | 最多丢 1 秒（everysec）或 0（always） |
| 文件体积 | 小、压缩 | 大（但 Redis 7 有 multi-part + 自动重写压缩） |
| 性能影响 | fork 子进程时短暂阻塞（大数据集明显） | always 模式写放大；everysec 折中 |
| 适用 | 备份、容灾、对丢失不敏感 | 对数据完整性要求高 |

**混合持久化（Redis 4.0+，默认推荐）**：AOF 文件里既保留命令日志，也定期写入 RDB 快照，
重启时先加载 RDB 再重放增量 AOF，兼顾恢复速度与不丢数据。

**本项目怎么选**：学习/演示用默认配置即可；若缓存丢了能从 DB 回源（本项目就是 cache-aside），
所以即便 RDB 丢一点也无害——这也印证了"缓存是加速层，不是真相源，DB 才是"。

## 四、一句话自测

- 缓存穿透 / 击穿 / 雪崩的区别？分别用什么挡？（布隆+空值 / 锁+逻辑过期 / 随机 TTL+集群）
- 为什么先更 DB 再删缓存？延迟双删解决什么？（极小概率的回填脏数据窗口）
- RDB 和 AOF 取舍？混合持久化是什么？（上面表格）
- 项目里缓存 value 用什么结构存的？（String + JSON 信封，含逻辑过期时间戳与空值标记）
