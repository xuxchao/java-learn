# 06 — RabbitMQ 路由模式 demo + 讲解

**What to build:** RabbitMQ 六种路由拓扑的端到端可运行范例：simple queue、work queue（prefetch + 手动 ack）、fanout、direct、topic、headers。每个拓扑用模板 + 监听容器搭出自有交换机/队列并收发消息，由 `rabbitmq` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Java AMQP 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] 6 种拓扑 demo 连 docker 跑通，路由行为符合各模式语义
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏（Spring 封装 / 原生 Java AMQP）静态片段 + 实跑真实输出
- [ ] JSON 消息体转换器注册 `JavaTimeModule` 且 `trustedPackages` 含消息体包（沿用 M6 经验，避免反序列化报错）
