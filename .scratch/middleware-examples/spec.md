# Spec: Redis / RabbitMQ API 使用范例与讲解（middleware-examples）

Status: ready-for-agent

> 状态：基于 `/grill-me` 会话中已对齐的设计决策综合而成，发布至本地 issue tracker（`.scratch/middleware-examples/spec.md`），标签 `ready-for-agent`。
> 关联：主工程 `java-learn-app` 的 M4 缓存（Redis）与 M6 消息队列（RabbitMQ）；根 `docker-compose.yml`（Redis 7.2 / RabbitMQ 3.13-management）。
> 说明：本 spec 仅做综合，不重新采访用户；所有决策均来自 grill 阶段 8 个已确认问题 + 用户追加"还要调整"前的最新共识。

## Problem Statement

用户是 Node/JS 转 Java 后端的候选人，对 Redis / RabbitMQ 的"各种 API 与模式"不熟，想拥有一份**能跑 + 带讲解**的范例集合，放在与 `java-learn-app` 同级的根目录，方便边看边跑、按库扩展。痛点有三：①官方文档零散、按命令罗列，缺少"可运行且带真实输出"的中文教学材料；②Spring 封装（`RedisTemplate` / `RabbitTemplate`）盖住了底层命令，导致"会调封装、不懂 API"；③主工程的 M4/M6 是生产风格代码，夹杂业务，不适合当纯 API 教学材料。

## Solution

在根目录新建一个**独立 Maven 工程 `middleware-examples`**，用 Spring Boot 封装（`RedisTemplate` / `RabbitTemplate`）写可运行 demo；每个 API 同时配**静态片段（Spring 封装 + 原生客户端双栏对照）+ 中文讲解**；按库分包并用 `@Profile` 隔离，连接现有 `docker-compose` 里的 Redis / RabbitMQ 实跑，把**真实输出抓取进工程内分库 README**。工程定位为"中间件范例仓库"，后续可加 Kafka 等其他库而不改现有结构。

## User Stories

1. As a 学习者, I want Redis 5 种基础结构的运行例子（String/Hash/List/Set/ZSet）, so that 我掌握各结构的命令语义与 Spring `opsForX` 映射。
2. As a 学习者, I want Redis Pub/Sub 例子, so that 我理解频道发布/订阅模型与 Spring 的监听容器。
3. As a 学习者, I want Redis Streams 例子（XADD/XREAD/XREADGROUP）, so that 我理解消费组与至少一次投递。
4. As a 学习者, I want Redis 事务（MULTI/EXEC）与 WATCH 乐观锁例子, so that 我理解 Redis 事务边界与竞态防护。
5. As a 学习者, I want Redis Lua 脚本（EVAL）例子, so that 我理解原子化复合操作（秒杀扣减原型）。
6. As a 学习者, I want Redis Pipeline（批量）例子, so that 我理解减少 RTT 的批量提交。
7. As a 学习者, I want 每个例子的静态片段同时给 Spring 封装与原生 Lettuce 对照, so that 我既能用于项目又能看懂底层命令。
8. As a 学习者, I want RabbitMQ simple queue 例子, so that 我理解基本 publish/consume。
9. As a 学习者, I want RabbitMQ work queue + prefetch + 手动 ack 例子, so that 我理解公平分发与可靠消费。
10. As a 学习者, I want RabbitMQ fanout 例子, so that 我理解发布/订阅广播。
11. As a 学习者, I want RabbitMQ direct 例子, so that 我理解路由键绑定。
12. As a 学习者, I want RabbitMQ topic 例子, so that 我理解通配路由。
13. As a 学习者, I want RabbitMQ headers 例子, so that 我理解头属性路由（少用的一种）。
14. As a 学习者, I want RabbitMQ 手动 ack/nack/requeue 例子, so that 我理解投递确认与失败重试。
15. As a 学习者, I want RabbitMQ 发布确认（publisher confirm + returns）例子, so that 我理解 broker 端确认、做到不丢。
16. As a 学习者, I want RabbitMQ 死信队列（DLX）+ 重试例子, so that 我理解失败消息转发与延迟重试。
17. As a 学习者, I want RabbitMQ RPC（request/reply）例子, so that 我理解双通道回调模型。
18. As a 学习者, I want RabbitMQ TTL（消息/队列过期）例子, so that 我理解过期与死信联动。
19. As a 学习者, I want RabbitMQ 优先级队列例子, so that 我理解优先级分发。
20. As a 学习者, I want RabbitMQ 每个例子的静态片段同时给 Spring（RabbitTemplate/@RabbitListener）与原生 Java AMQP 对照, so that 我既能用于项目又能看懂 AMQP 协议。
21. As a 学习者, I want 工程按库分包 + `@Profile` 隔离, so that 跑 Redis 例子时不会触发 RabbitMQ 连接、两库互不影响。
22. As a 学习者, I want 每库一个 runner 跑完全部例子并打印分段标题, so that 我可以一次总览该库全貌。
23. As a 学习者, I want `-Dexample=<名字>` 只跑单个例子, so that 我可以单点调试某个 API。
24. As a 学习者, I want 工程内分库 README + 顶部索引 README, so that 讲解、静态片段、demo 类说明集中可查。
25. As a 学习者, I want 代码放在根目录与 `java-learn-app` 同级且独立成工程, so that 不污染主工程构建。
26. As a 学习者, I want 后续能加 Kafka 等其他库到同一工程, so that 我有一个统一的中间件范例仓库。
27. As a 学习者, I want 例子连 docker-compose 实跑并把真实输出贴进文档, so that 我看到的讲解是验证过的、不是空谈。
28. As a 面试候选人, I want 这些范例能直接当 M4/M6 的延伸阅读材料, so that 我把缓存/MQ 八股讲得更扎实。

## Implementation Decisions

- **工程形态**：独立 Maven 工程 `middleware-examples`（Spring Boot），**不**注册到现有父 pom，避免污染主构建与触发 `InfraConnectionTest` 等主工程集成测试。
- **基础包与分包**：基础包 `com.example.middleware`；按库分包 `redis` / `rabbitmq`；未来 lib（如 `kafka`）直接加包，无需改动现有结构。
- **隔离机制**：每库用 `@Profile`（`redis` / `rabbitmq`）隔离其配置与 `@CommandLineRunner`；激活某库 profile 时，另一库的连接工厂与 runner 不被装配，做到"代码互不干扰"。
- **运行封装（可运行 demo 技术栈）**：统一用 Spring Boot 封装——Redis 走 `RedisTemplate` / `StringRedisTemplate` 的 `opsForValue/opsForHash/opsForList/opsForSet/opsForZSet`；RabbitMQ 走 `RabbitTemplate` + `@RabbitListener` 容器。JSON 转换器沿用 M6 经验：`Jackson2JsonMessageConverter` 需注册 `JavaTimeModule` 且 `DefaultClassMapper` 的 `trustedPackages` 显式包含消息体包（否则反序列化报 "not in the trusted packages"）。
- **运行粒度**：每库一个 `@CommandLineRunner`，读取 `example` 属性；为空则依次运行该库全部例子并打印分段标题，非空则只运行名字匹配的例子。
- **连接配置**：指向根 `docker-compose.yml` 的 Redis（6379）/ RabbitMQ（5672，管理台 15672），与现有 M4/M6 共用同一套本地基建。
- **文档形态**：工程内分库 README（redis、rabbitmq 各一份）+ 顶部 `README` 索引；每库 README 含：概念讲解 + 静态片段（Spring 封装 / 原生客户端双栏对照）+ 对应 demo 类说明 + 实跑真实输出。
- **验证方式**：连 docker 实跑，把真实输出捕获进文档；**不写单测**。
- **API 覆盖集合（Redis）**：`{ String, Hash, List, Set, ZSet, Pub/Sub, Streams, 事务 MULTI/EXEC/WATCH, Lua EVAL, Pipeline }`。
- **API 覆盖集合（RabbitMQ）**：`{ simple, work(prefetch+手动ack), fanout, direct, topic, headers, 手动 ack/nack/requeue, publisher confirm+returns, DLX+重试, RPC, TTL, 优先级队列 }`。

## Testing Decisions

- **Seams（测试切入点，集中为单一类）**：
  1. **可运行性 seam**：每库 `@CommandLineRunner` 在激活对应 profile 时连 docker 实跑并打印分段输出；验证判据 = 进程退出码 0 且输出含各例子的预期标记文字。这是本工程唯一外部行为测试面。
  2. **文档一致性 seam**：README 中的"静态片段"与"实跑真实输出"必须与实际代码/运行结果一致（agent/人工复核）。
- **什么算好测试**：只验证外部可观察行为（连真实 Redis / RabbitMQ 的输出），不测试 Spring 内部装配细节；不为私有方法写单测。
- **哪些模块被测**：所有 demo 例子通过"实跑"统一验证；不单独对某个 ops 写单测（已明确不在范围内）。
- **Prior art（已有范式）**：主工程 `OrderMqSerializationTest`（MQ 消息 JSON 往返）与 `ProductDbIntegrationTest`（连 docker 真实 DB 集成测试）是本仓库最接近的外部行为测试范式；本工程沿用"连 docker 真实服务验证"的思路，但以 README 捕获输出为主要交付，而非 JUnit 断言。

## Out of Scope

- 不修改主工程 `java-learn-app` / `java-learn-common`，不把 `middleware-examples` 挂到父 pom。
- 不引入分布式事务、消息加密、鉴权；不改动现有 M4/M6 实现。
- 不覆盖 Redis Cluster / Sentinel / RedisJSON / RedisSearch 等高级模块；不覆盖 RabbitMQ Shovel / Federation / 镜像队列等插件。
- 本 spec **不含单测套件**（已决定用实跑 + 输出捕获代替）。
- 未来 lib（Kafka 等）仅作为"同工程加包"的扩展点说明，不在本 spec 的实现范围内。
- 不提供 Web UI / 管理后台；例子均为命令行输出。

## Further Notes

- **与主工程关系**：这是独立于 M4/M6 的教学范例集，刻意解耦，便于单独阅读与运行；复用的 JSON 转换器坑（JavaTimeModule + trustedPackages）与 M6 一致，可当作其延伸阅读。
- **基建已就绪**：docker-compose 的 Redis（6379）/ RabbitMQ（5672，管理台 15672）当前在跑，可直接验证。
- **构建方式**：用仓库根 `.mvnlocal.sh` 调 Maven（本机 JDK/Maven 不在 PATH）。
- **扩展点**：`middleware-examples` 设计为"中间件范例仓库"，后续加 `kafka` 等包 + 对应 `@Profile` 即可，无需改动现有结构。
