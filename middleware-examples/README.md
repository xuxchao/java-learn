# middleware-examples

Redis / RabbitMQ **各种 API 的使用范例与讲解**，是一个**独立于主工程 `java-learn-app`** 的中间件范例仓库。

- 可运行 demo（Spring Boot 封装：`RedisTemplate` / `RabbitTemplate`）+ 静态带注释片段（Spring 封装 vs 原生客户端双栏对照）+ 中文讲解。
- 按库分包 `redis` / `rabbitmq`，用 `@Profile` 隔离，跑一个库不会触发另一个库的连接。
- 是主工程 **M4 缓存（Redis）** 与 **M6 消息队列（RabbitMQ）** 的延伸阅读 / 纯 API 教学材料，刻意与主工程解耦。

> 本仓库由 issue `01` 起逐步搭建；各库详细 API 文档在对应子目录 README 中（见下方导航），由后续 ticket 填充。

## 工程结构

```
middleware-examples/
├── pom.xml                      # 独立 Maven 工程（parent=spring-boot-starter-parent，不挂主工程父 pom）
├── src/main/java/com/example/middleware/
│   ├── MiddlewareExamplesApplication.java   # 启动入口
│   ├── BaseExampleRunner.java               # 例子分发基类（整库跑 / --example 单跑）
│   ├── redis/RedisExampleRunner.java        # @Profile("redis") 入口
│   └── rabbitmq/RabbitExampleRunner.java     # @Profile("rabbitmq") 入口
├── src/main/resources/
│   ├── application.yml                       # 公共配置（非 web 应用）
│   ├── application-redis.yml                 # redis profile：连 Redis
│   └── application-rabbitmq.yml              # rabbitmq profile：连 RabbitMQ
├── redis/README.md                           # Redis API 讲解（建设中）
└── rabbitmq/README.md                        # RabbitMQ API 讲解（建设中）
```

## 本地基建（docker-compose）

例子连根目录 `docker-compose.yml` 起的服务，请先确保容器在跑：

```bash
# 在仓库根目录执行
docker-compose up -d
```

| 服务 | 地址 | 管理台 |
| --- | --- | --- |
| Redis 7.2 | `localhost:6379` | — |
| RabbitMQ 3.13 | `localhost:5672` | `http://localhost:15672`（guest/guest） |

## 如何运行

本工程用仓库根的 Maven 包装器 `../.mvnlocal.sh`（本机 JDK/Maven 不在 PATH）。所有命令在 `middleware-examples/` 目录内执行。

```bash
cd middleware-examples

# 跑 Redis 全部例子（激活 redis profile）
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=redis

# 跑 RabbitMQ 全部例子（激活 rabbitmq profile）
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=rabbitmq

# 只跑某一个例子（按名字单跑）
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=redis -Dspring-boot.run.arguments=--example=string
```

参数说明：

- `-Dspring-boot.run.profiles=<profile>`：激活 `redis` 或 `rabbitmq`，对应库的 runner 才会装配。**必须指定其一**；不带 profile 时应用会启动并直接退出（没有任何例子可执行）。
- `-Dspring-boot.run.arguments=--example=<名字>`：只跑该库里名字匹配的那个例子；不传则跑完全部。
  - 注意：`spring-boot:run` 默认会 fork 一个新 JVM，Maven 命令行的 `-Dexample=...` 只落在 Maven 自己的 JVM 上、不会传到应用进程。单跑必须用 `--example=...` 经 `run.arguments` 传入（等价写法：`-Dspring-boot.run.jvmArguments="-Dexample=<名字>"`）。
- 由于是**非 web 应用**，runner 跑完即退出（exit code 0），输出直接打印到控制台。

## 模块文档导航

| 模块 | 文档 | 覆盖范围 |
| --- | --- | --- |
| Redis | [redis/README.md](redis/README.md) | String / Hash / List / Set / ZSet / Pub-Sub / Streams / 事务(WATCH) / Lua / Pipeline |
| RabbitMQ | [rabbitmq/README.md](rabbitmq/README.md) | simple / work / fanout / direct / topic / headers / 手动 ack / 发布确认 / 死信 DLX / RPC / TTL / 优先级 |

## 规划

设计来自 spec：`.scratch/middleware-examples/spec.md`，拆票见 `.scratch/middleware-examples/issues/`。
