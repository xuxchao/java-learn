# 03 — Redis Pub/Sub demo + 讲解

**What to build:** Redis 发布/订阅的端到端可运行范例：使用监听容器订阅频道、用模板向频道发布消息，演示消息广播与接收。由 `redis` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Lettuce 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] Pub/Sub demo 连 docker 跑通，订阅方收到发布方消息
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏静态片段 + 实跑真实输出
