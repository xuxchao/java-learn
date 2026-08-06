# 05 — 并发 & 幂等

**What to build:** 在高并发下扣减库存既不超卖也不丢更新，并让写接口可安全重放——对比不同并发控制手段的表现，建立线程安全与幂等的设计直觉。

**Blocked by:** 03 — 商品 & 数据库持久化（以库存模型为并发载体）.

**Status:** resolved

- [x] 100 并发扣库存结果正确（无超卖、无丢失更新）
- [x] 对比 synchronized / ReentrantLock / 乐观锁 三种方案的表现与取舍
- [x] 接口幂等（token / 唯一索引 / 状态机）可重放而不产生副作用
- [x] 能讲清 volatile 的可见性/有序性边界、JUC 常用类、线程池七大参数与拒绝策略

## 交付内容

### 代码（java-learn-app）
- `concurrency/StockDeductor`：库存扣减器抽象；三个实现
  `SynchronizedStockDeductor` / `ReentrantLockStockDeductor` / `CasStockDeductor`（AtomicInteger CAS）。
- `concurrency/StockDeductionConcurrencyTest`：发令枪式 100 线程压测，验证三种方案都不超卖、不丢更新
  （库存=线程数 → 全成功归零；库存<线程数 → 只成功库存数、永不为负）；并打印三方案耗时对比。
- `idempotency/IdempotencyStore`（接口）+ `InMemoryIdempotencyStore`（测试用）+ `RedisIdempotencyStore`
  （生产，`StringRedisTemplate` SET NX EX，已随应用装配为唯一实现）。
- `idempotency/IdempotencyService`：claim/complete 两阶段 token 幂等，重放不重执行；`IdempotencyResult` 包装 `replayed` 标记。
- `idempotency/IdempotencyController`：`POST /idempotency/echo` 演示端点，读 `Idempotency-Key` 头，
  响应头 `Idempotency-Replayed` 标识是否重放；`/idempotency/**` 已在 `WebConfig` 排除登录拦截。
- `common/ErrorCode`：新增 `IDEMPOTENCY_KEY_REQUIRED(3007)`。

### 测试
- `StockDeductionConcurrencyTest`：纯 JUnit，无 Spring 依赖。
- `IdempotencyServiceTest`：纯 JUnit（InMemory 存储），覆盖首次执行 / 重放不重执行 / 不同 key 独立 / TTL 过期重执行。
- `IdempotencyControllerTest`：`@WebMvcTest`，覆盖缺 key→3007、带 key→透传重放头。

### 文档
- `docs/concurrency-notes.md`：volatile 边界 / JUC 常用类 / 线程池七大参数与四拒绝策略 / 三种扣库存方案对比 /
  接口幂等三方案（token / 唯一索引 / 状态机），均引用本仓库实际类。
- `docs/api/curl.md`：新增「7. 接口幂等（M5）」演示章节。

## 验收要点（自测）
1. `mvn test -Dtest=StockDeductionConcurrencyTest`：100 线程各扣 1，三方案均"终态 0、成功数=100、无负数"。
2. `mvn test -Dtest=IdempotencyServiceTest`：相同 key 第二次返回首次结果且动作只跑一次（replayed=true）。
3. `mvn test -Dtest=IdempotencyControllerTest`：缺 `Idempotency-Key` → 3007；带 key → `Idempotency-Replayed` 头正确。
4. 八股：能口述 volatile 三性边界、JUC 类、线程池七大参数与四拒绝策略、三种扣减方案取舍、幂等三方案。

## 面试八股自测（要能口述）
- volatile 保证可见性+有序性、**不保证原子性**，`i++` 仍会丢更新（用 AtomicInteger）。
- 线程池七大参数与任务提交流转（核心→队列→临时→拒绝）；四拒绝策略名字与区别；为何不用 Executors 无界池。
- synchronized / ReentrantLock / JVM-CAS / DB-CAS 的适用边界（关键：JVM 锁只在单实例内有效，跨实例靠 DB/Redis）。
- 接口幂等三方案：token（本项目演示）/ DB 唯一索引（orders.order_no）/ 状态机，可组合使用。

## 已知限制（教学取舍，非 bug）
- IdempotencyService 重放等待为"轮询+短超时"，极端并发首射失败退化为重试一次；工业级可存 Future 复用。
- InMemoryIdempotencyStore 单实例、重启即丢；生产用 RedisIdempotencyStore。
- JVM 三扣减方案均为单实例内有效，跨实例必须上 DB/Redis 方案。

## Comments
- 顺手修了 M4 一处既存 bug：`ProductCacheService` 物理 TTL 常量写成了 `1*60`（1 分钟），但注释与交付文档、
  `ProductCacheServiceTest.random_ttl_is_within_range` 均声明设计为 `[30min, 40min)`。已将常量改为 `30*60` / `10*60`，
  使该测试由红转绿（此失败与 M5 无关，属 M4 遗留不一致）。
