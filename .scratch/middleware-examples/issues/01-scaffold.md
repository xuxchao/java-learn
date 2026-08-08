# 01 — Scaffold middleware-examples 工程骨架

**What to build:** 一个位于仓库根目录、与 `java-learn-app` 同级的独立 Maven 工程，作为长期承载 Redis / RabbitMQ / 未来其他库 API 范例的"中间件范例仓库"。用户能 `mvn spring-boot:run` 启动一个空 runner；激活 `redis` 或 `rabbitmq` profile 时只装配对应库的连接与例子、互不干扰；顶部 README 给出运行说明（如何激活 profile、`-Dexample=<名字>` 单跑用法）。工程不注册到现有父 pom，避免污染主工程构建与触发主工程集成测试。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] 工程可编译，`spring-boot:run` 能启动（指定 profile 时跑对应空 runner，退出 0）
- [x] 激活 `redis` profile 时不连接 RabbitMQ，激活 `rabbitmq` 时不连接 Redis（`@Profile` + lazy 连接，日志无跨库 TCP 连接）
- [x] 顶部 README 含两库导航、运行/单跑说明、本地 docker-compose 基建说明
- [x] 连接配置指向根 docker-compose 的 Redis(6379) / RabbitMQ(5672)

## Comments

- 实现 commit：`3cbe6d0`（middleware-examples/ 脚手架 + spec + 拆票）。
- 实跑验证：两 profile 均退出码 0；日志中无 `Connected to` / `Created new connection` / `amqp` / `lettuce` 等跨库连接迹象。
- 单跑参数修正：`spring-boot:run` 会 fork 新 JVM，Maven 的 `-Dexample=...` 到不了应用进程；改用 `--example=<名字>` 经 `-Dspring-boot.run.arguments=--example=<名字>` 传入（BaseExampleRunner 用 `env.getProperty("example")` 读取，已验证 `--example=string` 能被读到）。
- 隔离机制采用 spec 规定的 `@Profile`（未加 `spring.autoconfigure.exclude`，避免双 profile 同时激活时排除键不合并的坑）。

