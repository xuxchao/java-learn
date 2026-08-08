# 07 — RabbitMQ 可靠性 demo + 讲解

**What to build:** RabbitMQ 可靠性三机制的端到端可运行范例：①手动 ack / nack / requeue（可靠消费与失败重试）；②发布确认 `publisher confirm` + `returns`（broker 端确认，做到不丢）；③死信队列 DLX + 重试（失败消息转发与延迟重试）。每个机制用模板 + 监听容器搭出自有拓扑并收发，由 `rabbitmq` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Java AMQP 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] 三个可靠性机制 demo 连 docker 跑通，确认/重试/死信行为符合预期
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏静态片段 + 实跑真实输出
- [ ] JSON 消息体转换器注册 `JavaTimeModule` 且 `trustedPackages` 含消息体包（沿用 M6 经验）
