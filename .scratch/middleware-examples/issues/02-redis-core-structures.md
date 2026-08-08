# 02 — Redis 5 种基础结构 demo + 讲解

**What to build:** Redis 五种基础数据结构的端到端可运行范例：String / Hash / List / Set / ZSet。每个结构一个 demo（用对应的 ops 封装），由 `redis` profile 的 runner 顺序执行并打印分段标题，支持 `-Dexample=<名字>` 只跑单个；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Lettuce 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] 5 个结构 demo 连 docker 跑通，输出与各结构命令语义一致
- [ ] runner 支持 `-Dexample=<名字>` 单跑某一个结构
- [ ] 库内 README 含讲解 + 双栏（Spring 封装 / 原生 Lettuce）静态片段 + 实跑真实输出
