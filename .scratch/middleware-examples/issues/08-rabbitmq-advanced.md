# 08 — RabbitMQ 高级模式 demo + 讲解

**What to build:** RabbitMQ 三类高级模式的端到端可运行范例：①RPC（request/reply 双通道回调模型）；②TTL（消息 / 队列过期，与死信联动）；③优先级队列（优先级分发）。每个模式用模板 + 监听容器搭出自有拓扑并收发，由 `rabbitmq` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Java AMQP 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] 三类高级模式 demo 连 docker 跑通，RPC 回执 / TTL 过期 / 优先级分发行为符合预期
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏静态片段 + 实跑真实输出
- [ ] JSON 消息体转换器注册 `JavaTimeModule` 且 `trustedPackages` 含消息体包（沿用 M6 经验）
