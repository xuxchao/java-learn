# 并发 & 幂等 八股自测（M5）

> 配套代码：`com.example.ecommerce.concurrency`（三种 JVM 扣库存方案）+ `com.example.ecommerce.idempotency`（token 幂等）。
> 跑通验证：`mvn test -Dtest=StockDeductionConcurrencyTest,IdempotencyServiceTest,IdempotencyControllerTest`
> 真·接口演示：`POST /idempotency/echo`（带 `Idempotency-Key` 头，见 `docs/api/curl.md` 第 7 节）。

---

## 1. volatile：可见性 + 有序性，但**不是原子性**

`volatile` 解决两个问题，**唯独不解决第三个**：

| 维度 | volatile 是否保证 | 说明 |
| --- | --- | --- |
| **可见性**（一个线程改了，别的线程立刻能看到） | ✅ | 写操作立刻刷主内存、读操作从主内存取，跳过线程本地缓存 |
| **有序性**（防止编译/CPU 重排序破坏依赖） | ✅ | 在变量前后插入内存屏障（happens-before） |
| **原子性**（i++ 这种"读-改-写"整体不可分割） | ❌ | `i++` 在字节码是 3 步（getfield / iadd / putfield），volatile 拦不住交错 |

**边界（最常考的反例）**：

```java
// ❌ 错误：volatile 救不了 i++
private volatile int count = 0;
// 100 个线程各 count++ 一次，结果 < 100（丢失更新）
public void bad() { count++; }

// ✅ 正确：原子类靠 CAS（底层也是 volatile 变量 + 自旋）
private final AtomicInteger count = new AtomicInteger(0);
public void good() { count.incrementAndGet(); }   // 原子自增
```

**一句话记忆**：`volatile` 保证"你看到的都是最新值、且顺序不乱"，但不保证"两步操作之间没人插队"。需要原子性就用 `Atomic*` 或锁。

> JS 类比：JS 是单线程事件循环，没有"多线程同时改一个变量"的问题，所以你从没操心过这个；Java 多线程下这是头号坑。

---

## 2. JUC 常用类（java.util.concurrent）

| 类 / 接口 | 干嘛用 | 本仓库关联 |
| --- | --- | --- |
| `AtomicInteger` / `AtomicLong` | CAS 无锁原子操作（见上面 `CasStockDeductor`） | M5 扣库存方案 C |
| `ReentrantLock` | 显式锁，可 `tryLock(timeout)`/公平/多 `Condition` | M5 扣库存方案 B |
| `CountDownLatch` | 等 N 个线程都到位再继续（**发令枪**） | `StockDeductionConcurrencyTest` 的 startGate |
| `CyclicBarrier` | 多线程在栅栏处互相等齐再一起冲（可复用） | —— |
| `Semaphore` | 限流/控制并发数（许可证） | —— |
| `ConcurrentHashMap` | 线程安全的哈希表（分段/CAS） | `InMemoryIdempotencyStore` 的存储底座 |
| `BlockingQueue` | 生产者-消费者解耦（线程池底层） | 线程池的任务队列 |
| `ThreadLocal` | 线程私有变量（如把 LoginUser 绑在线程上） | 类似本项目 `request.setAttribute` 的思想 |
| `ExecutorService` | 线程池入口 | `Executors.newFixedThreadPool` 起压测 |

**CAS 的代价**：高争用下会自旋重试（CPU 空转），极端情况不如锁；且存在 ABA 问题（值被改回原值，CAS 误以为没变）——本仓库不需要处理，了解即可。

---

## 3. 线程池七大参数 + 四种拒绝策略（必考）

`ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler)`

| 参数 | 含义 | 直觉 |
| --- | --- | --- |
| `corePoolSize` | 核心线程数（常驻，不会被回收） | 食堂常开的窗口 |
| `maximumPoolSize` | 最大线程数（核心 + 临时扩容） | 高峰期最多开的窗口 |
| `keepAliveTime` + `unit` | 临时线程空闲多久后回收 | 临时工闲多久走人 |
| `workQueue` | 任务队列（核心线程满后先排队） | 排队区 |
| `threadFactory` | 创建线程的工厂（命名/守护） | 招人标准 |
| `handler` | 队列也满、线程也满时的拒绝策略 | 爆单处理预案 |

**提交任务的流转**：①核心线程没满 → 开核心线程；②核心满了 → 进队列；③队列也满了 → 开临时线程到 max；④连 max 也满了 → **触发拒绝策略**。

**四种拒绝策略**（`RejectedExecutionHandler`）：

| 策略 | 行为 | 适用 |
| --- | --- | --- |
| `AbortPolicy`（默认） | 直接抛 `RejectedExecutionException` | 要明确失败的场景 |
| `CallerRunsPolicy` | 让**提交任务的线程自己**去跑这个任务 | 不丢任务、自然反压（本项目 `AsyncConfig` 的 `cacheAsyncExecutor` 用的就是这个，避免延迟双删被静默丢弃） |
| `DiscardPolicy` | 静默丢弃新任务 | 日志等可丢场景 |
| `DiscardOldestPolicy` | 丢队列里最老的任务，腾位置给新的 | 只关心最新数据 |

> ⚠️ 别用 `Executors.newFixedThreadPool` / `newCachedThreadPool` 的无界队列（易 OOM）——面试常考，本项目一律手写 `ThreadPoolExecutor` 显式给队列容量。

---

## 4. volatile / JUC / 线程池 的"能讲清"自检

- 能说清 `volatile` 管可见性+有序性、**不管原子性**，并举例 `i++` 为何出错。
- 能列 `Atomic*` / `ReentrantLock` / `CountDownLatch` / `ConcurrentHashMap` 各自干嘛。
- 能背出七大参数和任务提交流转，以及四种拒绝策略名字与区别。
- 能说出为什么不用 `Executors` 的无界线程池。

---

## 5. 三种库存扣减方案对比（M5 主线）

目标一致：100 人并发抢同一行库存，**不超卖、不丢更新**。落点分两层：

| 方案 | 落点 | 机制 | 优点 | 缺点 | 适用 |
| --- | --- | --- | --- | --- | --- |
| `synchronized` | JVM 单实例 | 监视器锁，阻塞 | 写法最简单 | 不可中断/不可超时；高争用上下文切换重 | 单实例、求稳 |
| `ReentrantLock` | JVM 单实例 | 显式锁，阻塞 | 可超时/公平/多条件 | 须 `finally` 解锁，易漏写死锁 | 需要超时/公平 |
| `AtomicInteger` CAS | JVM 单实例 | 乐观无锁自旋 | 无锁、低争用最快 | 高争用自旋空转；仅单实例内有效 | 低中争用、单实例 |
| `UPDATE ... WHERE available >= ?`（M3） | **数据库** | DB 层 CAS | 跨实例、跨请求有效 | 冲突需重试 | 分布式/秒杀 |

> 关键认知：**JVM 层锁只在"同一进程内的多线程"之间有效**。多实例部署（多台应用服务器）时，实例 A 的 `synchronized` 管不住实例 B 的线程——此时必须靠 DB/Redis 的 CAS 或分布式锁。本项目 M3 下单就是"跨请求"的 DB 层方案，M5 这个 `concurrency` 包是"单实例内多线程"的 JVM 层对照实验。

压测结论（`StockDeductionConcurrencyTest`，100 线程各扣 1）：三种 JVM 方案都能做到"终态归零、成功数=初始、绝不为负"——**正确性一致**。

> 关于"表现/耗时对比"：本测试里的计时只是**示意**，不算严谨的微基准（建线程池、无预热、单轮，建池开销会淹没锁争用）。真正要讲的取舍不是"谁快几毫秒"，而是**语义差异**：
> - `synchronized`/`ReentrantLock`：线程**阻塞**（操作系统挂起/唤醒），高争用下上下文切换重；
> - `AtomicInteger` CAS：**无锁自旋**，低争用最快，极高争用下空转；
> - 三者都**只在单实例内有效**，跨实例必须上 DB/Redis 的 CAS 或分布式锁。

---

## 6. 接口幂等三方案（M5 主线）

"幂等"= 同一个写请求（网络重试、用户连点、网关重发）**重复提交多次，副作用只发生一次**。三种主流做法：

### ① Token 方案（本项目 `com.example.ecommerce.idempotency`，演示端点 `POST /idempotency/echo`）
- 客户端生成唯一 `Idempotency-Key`（或拿订单号当 key），每次请求带上。
- 服务端用 `IdempotencyService`：首次 `claim`（Redis `SET NX EX` 原子抢名额）→ 执行 → 存结果；重放 `claim` 失败 → 直接返回已存结果，**不重跑动作**。
- 用了 **claim/complete 两阶段** 杜绝 TOCTOU 竞态（先 get 再执行会有"两个相同 key 同时 get 到 null 都执行"的漏洞）。
- 适用：下单、支付等一切"不可重复"的写接口。

### ② 数据库唯一索引（幂等列 / 业务唯一键）
- 原理：在表里放一个**客户端提供的**唯一键（专门的 `idempotency_key` 列，或业务唯一键如 `(user_id, product_id, 日期)`），重复提交因唯一约束插入失败，直接抛错回滚。
- ⚠️ **易错点**：唯一索引能去重，前提是"重试时复用同一个唯一值"。本项目 `orders.order_no` 是**服务端随机生成**的（`"ORD"+时间戳+随机数`），客户端重试必然拿到新 `order_no`，所以**它挡不住"客户端连点导致的重复下单"**——它只保证不会因其它原因撞号。要让唯一索引真正做幂等，必须把**客户端带来的幂等键**存进唯一列（如 `uk_idem_key`），重试复用同值才能触发唯一冲突回滚。
- 优点：最简单、DB 兜底、绝对可靠；缺点：只能挡"重复插入"，挡不了"已支付又支付"这类状态类重复（那要状态机）。

### ③ 状态机（订单状态流转）
- 订单 `status` 从 `CREATED → PAID → SHIPPED`，每次变更前校验"当前状态是否允许这次转移"。
- 重复支付：`CREATED→PAID` 成功后，第二次支付请求发现已是 `PAID`，guard 拒绝（同乐观锁思想：状态未被改过才更新）。
- 优点：业务语义最清晰、天然防乱序；缺点：要设计好状态图。

> 生产里常**组合**使用：唯一索引兜底不重复落库 + token 挡住重复请求 + 状态机挡住重复业务动作。

---

## 7. 已知限制（教学取舍，非 bug）

- `IdempotencyService` 的重放等待用"轮询 + 短超时"，极端并发下首射失败会退化为重试一次；工业级可把"处理中"存成真正的 `Future` 让重放直接复用。
- `InMemoryIdempotencyStore` 仅单实例、重启即丢，生产用 `RedisIdempotencyStore`（已随应用装配）。
- JVM 三方案（synchronized/ReentrantLock/CAS）都是单实例内有效，跨实例必须上 DB/Redis 方案（见第 5、6 节）。
