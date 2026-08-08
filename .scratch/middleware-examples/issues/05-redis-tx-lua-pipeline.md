# 05 — Redis 事务/WATCH + Lua + Pipeline demo + 讲解

**What to build:** Redis 三类"进阶原语"的端到端可运行范例：①事务 `MULTI`/`EXEC` 与 `WATCH` 乐观锁（演示竞态下事务中止）；②Lua 脚本 `EVAL`（原子化复合操作，如秒杀扣减原型）；③Pipeline 批量提交（演示减少 RTT）。由 `redis` profile 的 runner 执行并打印分段标题，支持 `-Dexample=<名字>` 单跑；库内 README 含概念讲解 + 双栏静态片段（Spring 封装 / 原生 Lettuce 对照）+ demo 类说明；连 docker 实跑并把真实输出抓进 README。

**Blocked by:** 01 — Scaffold middleware-examples 工程骨架

**Status:** ready-for-agent

- [ ] 三类原语 demo 连 docker 跑通，行为符合预期（WATCH 中止 / Lua 原子 / Pipeline 批量）
- [ ] runner 支持 `-Dexample=<名字>` 单跑
- [ ] 库内 README 含讲解 + 双栏静态片段 + 实跑真实输出
