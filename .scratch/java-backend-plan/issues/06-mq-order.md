# 06 — 下单 & 消息队列

**What to build:** 把下单动作异步化：下单成功后向 MQ 发消息，下游消费者异步处理（如扣减、通知），且同一订单的重复消息不会造成重复处理——让系统具备削峰与解耦能力。

**Blocked by:** 03 — 商品 & 数据库持久化（下单依赖商品/库存）.

**Status:** resolved

- [x] 下单成功后向 MQ 发出消息，消费者异步处理订单（`OrderEventPublisher` + `OrderEventConsumer`，复用 `POST /products/{id}/order` 接缝）
- [x] 消费者幂等，同一订单的重复投递不重复处理（通知等副作用）（复用 M5 `IdempotencyService`，`orderNo` 作幂等键；`OrderEventConsumerTest` 断言重复投递只处理一次）
- [x] 能讲清 MQ 的不丢 / 不重 / 有序保障，以及事务消息原理（`docs/mq-notes.md`；`application.yml` 开 `publisher-confirm-type`/`publisher-returns`，容器工厂 `defaultRequeueRejected=true`）

## Comments

- 2026-08-08 实现：新增 `com.example.ecommerce.mq` 包（event/config/publisher/consumer/downstream），
  生产者开启 publisher confirm/returns 实现"不丢"，消费端用 M5 幂等键实现"不重"。为保持"最小功能"，
  发布在订单事务提交之后、未上本地消息表（已在 mq-notes 标注窗口，留待 M7 分布式事务）。
- 消费者副作用聚焦"发通知"等可幂重入动作，**不写 `orders.status`**（避免与 M7 的 `CREATED → PAID` 状态机冲突）；
  库存扣减已在 M3 下单事务完成，消费者绝不重复扣。
- code-review 后修两处正确性 bug：①事件含 `LocalDateTime` 需给 JSON 转换器注册 `JavaTimeModule`，否则运行期序列化失败；
  ②消费失败默认丢弃与"不丢"矛盾 → 容器工厂 `defaultRequeueRejected=true` 让失败消息重回队列重试（生产建议加 DLQ）。
- 全量测试 57 个全绿（含新增 2 个消费者幂等单测）。
