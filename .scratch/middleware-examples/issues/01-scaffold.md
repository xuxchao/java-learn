# 01 — Scaffold middleware-examples 工程骨架

**What to build:** 一个位于仓库根目录、与 `java-learn-app` 同级的独立 Maven 工程，作为长期承载 Redis / RabbitMQ / 未来其他库 API 范例的"中间件范例仓库"。用户能 `mvn spring-boot:run` 启动一个空 runner；激活 `redis` 或 `rabbitmq` profile 时只装配对应库的连接与例子、互不干扰；顶部 README 给出运行说明（如何激活 profile、`-Dexample=<名字>` 单跑用法）。工程不注册到现有父 pom，避免污染主工程构建与触发主工程集成测试。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] 工程可编译，`mvn spring-boot:run` 能启动空 runner
- [ ] 激活 `redis` profile 时不连接 RabbitMQ，激活 `rabbitmq` 时不连接 Redis（`@Profile` 隔离生效）
- [ ] 顶部 README 含两库导航、运行/单跑说明、本地 docker-compose 基建说明
- [ ] 连接配置指向根 docker-compose 的 Redis(6379) / RabbitMQ(5672)

