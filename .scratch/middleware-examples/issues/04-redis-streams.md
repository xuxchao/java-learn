# 04 — Redis Streams demo + 讲解

**What to build:** Redis Streams 的端到端可运行范例：演示 `XADD` 生产、`XREAD` 消费、以及基于消费组 `XREADGROUP` 的至少一次投递与消费组内竞争。由 `redis` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Lettuce 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] Streams demo 连 docker 跑通，消费组能正确分发与重投
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏静态片段 + 实跑真实输出
