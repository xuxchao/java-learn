# MQ 消息队列笔记（M6 下单异步化）

> 配套代码：`com.example.ecommerce.mq` 包（生产者 / 消费者 / 配置 / 事件体）。
> 触发入口：`POST /products/{id}/order` 下单成功后发消息，下游 `@RabbitListener` 异步处理。
> 面试八股三连：不丢 / 不重 / 有序 + 事务消息原理。

## 1. 为什么要用 MQ（削峰 + 解耦）

下单主链路（M3）只做"扣库存 + 落订单"，把"发通知 / 扣下游库存 / 积分"等副作用
异步丢给 MQ：

- **削峰**：瞬时万单，DB 扛不住，MQ 当缓冲，消费者按自己节奏消费。
- **解耦**：下单服务不依赖通知/积分服务，对方挂了也不影响下单。

本站点的实现：下单事务提交后，`OrderEventPublisher` 把 `OrderCreatedEvent` 发到
`order.exchange` → `order.created.queue`，消费者 `OrderEventConsumer` 异步处理
（发通知等可幂重入的副作用，不写 `orders.status`，避免与 M7 的 `CREATED → PAID` 状态机冲突）。

## 2. 不丢消息

消息从生产者到消费者有 4 个可能丢的点，逐一堵：

| 环节 | 风险 | 本站点的保障 |
| --- | --- | --- |
| 生产者 → 交换机 | 网络抖、交换机不存在 | `publisher-confirm-type: correlated` + `ConfirmCallback`，broker 落盘后 ack，nack 可感知 |
| 交换机 → 队列 | routingKey 写错，无匹配队列 | `publisher-returns: true` + `mandatory=true` + `ReturnsCallback`，无法路由被退回 |
| 队列自身 | broker 重启丢队列 | 队列 / 交换机 `durable=true`（元数据持久化）；消息 `deliveryMode=2`（持久化） |
| 队列 → 消费者 | 消费中宕机、未 ack 就被删 | Spring 默认 AUTO ack（方法正常返回即 ack）；**处理抛异常时 `defaultRequeueRejected=true` 让消息重回队列重试**（不丢），但生产应加重试上限 + 死信队列 DLQ，避免毒消息无限重投 |

> 注意：本站点的发布发生在"订单事务提交之后、返回响应之前"，存在极小窗口——
> "订单已落库但消息还没发出就宕机"会丢一条。生产级用**本地消息表 / 事务消息**兜底（见第 5 节）。

## 3. 不重消息（幂等消费）

MQ 是 **at-least-once** 投递：网络重投、消费者重启前未 ack 的消息都会重复到达。
RabbitMQ 没有"恰好一次"投递，所以"不重"必须在**消费端**做：

- 本站点的幂等键 = `orderNo`（订单号天然唯一）。
- 复用 M5 的 `IdempotencyService`（底层 Redis `SET NX`）：
  - 首次到达 → claim 成功 → 执行下游副作用 → 缓存结果；
  - 重复到达 → claim 失败 → 直接返回首次结果，**不再执行副作用**。
- 结果：**at-least-once 投递 + 幂等消费 = effectively-once**（不重复扣减 / 不重复通知）。

> 关键认知：库存扣减已经在下单事务（M3）里完成，消费者**绝不能重复扣减**。
> 消费者的副作用（通知 / 状态推进 CREATED → PROCESSED）必须可幂等重入，而"只跑一次"由幂等键保证。

验证：单测 `OrderEventConsumerTest` 把同一条事件投 3 次，断言下游 `process` 只执行 1 次。

## 4. 有序消息

- **单队列 + 单消费者**：FIFO，同一条消息严格顺序。
- **同订单有序**：把同一 `orderNo` 路由到同一队列（本例只有 1 个队列，天然满足）。
- **提升并发**：按 `orderNo` 哈希分片到多个队列，每片单消费者 → 片内有序、整体并行。
- 本例为演示保持单队列单消费者，足够讲清"单 key 有序"即可。

## 5. 事务消息原理（面试高频）

"本地事务（落库）"与"发 MQ"是两个资源，无法用单机 @Transactional 包住，经典难题。
三种主流解法：

1. **本地消息表（最常用、本站点的推荐升级）**
   - 订单事务里额外插一条 `outbox` 记录（状态=待发送），与订单**同库同事务**；
   - 独立线程 / 定时任务扫 `outbox` 未发送记录，发 MQ，成功后置"已发送"；
   - 发 MQ 失败就重试，保证最终发出 → 解决第 2 节那个极小窗口。
2. **RocketMQ 事务消息（half message）**
   - 先发"半消息"（对消费者不可见）→ 执行本地事务 → 成功发 Commit / 失败发 Rollback；
   - broker 回查（check）本地事务状态兜底。
3. **最大努力通知**：本地事务完成后尽力发 MQ，接收方定期核对补偿。

> 本站点为了"最小功能"没上 outbox，但在 `OrderEventPublisher` 注释里点明了这个窗口，
> 作为后续 M7（分布式事务）的伏笔。

## 6. 与 M5 的关系

- M5 的 `IdempotencyService` / `RedisIdempotencyStore` 是**通用幂等原语**；
- M6 直接复用它做"消费端幂等"，无需新写一套去重——体现了"先把基础件做扎实，上层即插即用"。
