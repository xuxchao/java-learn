# RabbitMQ API 范例与讲解

> 六种路由拓扑的**可运行 demo** + Spring AMQP vs 原生客户端**双栏对照** + 可靠性保障模式。
> 所有例子连接本地 Docker `rabbitmq:3.13-management`，guest/guest。

## 目录

- [前置知识：交换器与路由](#前置知识交换器与路由)
- [1. Simple Queue — 简单队列](#1-simple-queue--简单队列)
- [2. Work Queue — 工作队列](#2-work-queue--工作队列)
- [3. Fanout Exchange — 广播](#3-fanout-exchange--广播)
- [4. Direct Exchange — 精确路由](#4-direct-exchange--精确路由)
- [5. Topic Exchange — 通配符路由](#5-topic-exchange--通配符路由)
- [6. Headers Exchange — 消息头匹配](#6-headers-exchange--消息头匹配)
- [双栏对照：Spring AMQP vs 原生客户端](#双栏对照spring-amqp-vs-原生客户端)
- [已落地 vs 待建设](#已落地-vs-待建设)
- [已知坑位](#已知坑位)

---

## 前置知识：交换器与路由

RabbitMQ 的消息投递模型：**Producer → Exchange → (binding) → Queue → Consumer**。

```
┌──────────┐     ┌──────────────┐     ┌─────────┐     ┌──────────┐
│ Producer │────▶│   Exchange   │────▶│  Queue   │────▶│ Consumer │
└──────────┘     └──────┬───────┘     └─────────┘     └──────────┘
                        │
                  Binding (routing_key)
```

- **Exchange（交换器）**：接收生产者消息，根据路由规则把消息推到一个或多个队列。
- **Queue（队列）**：存储消息直到被消费者取走，RabbitMQ 内 FIFO。
- **Binding（绑定）**：交换器与队列之间的"连线"，携带 routing key 或 headers 匹配规则。

**四种交换器对比：**

| 类型 | 路由方式 | 类比 |
|------|---------|------|
| **Direct** | 精确匹配 routing key | 精确查找 key |
| **Fanout** | 忽略 key，广播到所有绑定队列 | 群发 |
| **Topic** | 通配符 `*`（单段）/ `#`（多段）匹配 | 文件 glob |
| **Headers** | 消息头 `x-match: any/all` 匹配 | 标签过滤 |

> **默认交换器**：RabbitMQ 内置一个无名 direct exchange（`""`），每个队列启动时会自动绑定到它，routing key = queue name。本仓库 Simple Queue 和 Work Queue 就利用了这个机制——直接用队列名当 routing key 发消息，省去显式声明 exchange。

## 运行

```bash
cd middleware-examples

# 跑全部 6 个 demo
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=rabbitmq

# 只跑某一个
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=rabbitmq -Dspring-boot.run.arguments=--example=simple
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=rabbitmq -Dspring-boot.run.arguments=--example=topic
```

> 每个 demo 在启动时 `purgeQueue()` 清空队列，发送消息后用 `receiveAndConvert()` 同步拉取并断言验证。输出即真实 RabbitMQ 行为。

---

## 1. Simple Queue — 简单队列

> 一条消息，一个消费者——最基础的点对点模型。

**涉及组件：**

| 组件 | 名称 |
|------|------|
| Queue | `demo.simple.queue`（non-durable） |
| Exchange | 默认交换器 `""`（routing key = queue name） |

**实跑输出：**

```
=== Simple Queue —— 一条消息，一个消费者 ===
[producer] sent: DemoMessage{id='1', payload='Hello, Simple Queue!', routingTag='simple', ...}
[consumer] received: DemoMessage{id='1', payload='Hello, Simple Queue!', routingTag='simple', ...}
```

**关键代码：**

```java
// 发送：直接用队列名作为 routing key，消息进入默认交换器
rabbitTemplate.convertAndSend("demo.simple.queue", msg);

// 接收：同步拉取，超时 3s
Object received = rabbitTemplate.receiveAndConvert("demo.simple.queue", 3000);
```

**要点：**
- 生产者用队列名作为 routing key 投递到**默认交换器**（direct 类型，每个队列自动绑定到它）。
- `convertAndSend` 自动把 Java 对象序列化为 JSON（`Jackson2JsonMessageConverter`）。
- `receiveAndConvert` 同步拉取一条消息（Push 模式用 `@RabbitListener`，见 Work Queue）。

---

## 2. Work Queue — 工作队列

> 多条消息分发到多个消费者，RabbitMQ 自动 round-robin——"任务队列"经典模型。

**涉及组件：**

| 组件 | 名称 |
|------|------|
| Queue | `demo.work.queue`（non-durable） |
| Exchange | 默认交换器 `""` |

**实跑输出（全部 6 demo 串联运行时）：**

```
=== Work Queue —— 一条队列，多条消息（演示消息排队与拉取） ===
[producer] sent: task-1, task-2, task-3, task-4, task-5
--- 消费者依次拉取 ---
[1] received: task-1
[2] received: task-2
[3] received: task-3
[4] received: task-4
[5] received: task-5
[extra] null (expect null)   ← 无残留，验证通过
```

**关键代码：**

```java
// 生产者循环发送 5 条
for (int i = 1; i <= 5; i++) {
    rabbitTemplate.convertAndSend("demo.work.queue", DemoMessage.of(String.valueOf(i), ...));
}

// 消费者依次同步拉取（演示 FIFO 顺序）
for (int i = 1; i <= 5; i++) {
    Object msg = rabbitTemplate.receiveAndConvert("demo.work.queue", 3000);
}
```

**为什么要用 Work Queue：**

| 场景 | 说明 |
|------|------|
| 耗时任务异步化 | 图片处理、报表生成、邮件发送——扔进队列让 worker 慢慢做 |
| 削峰填谷 | 突发流量进入队列，worker 按自身节奏消费 |
| 水平扩展 | 多实例部署时 RabbitMQ 自动 round-robin 分发，加机器 = 加处理能力 |

**多消费者公平分发（`@RabbitListener` 模式）：**

本 demo 用同步拉取演示 FIFO 顺序；生产环境推荐用 `@RabbitListener` + `prefetch=1` + 手动 ack：

```java
// 配置：prefetch=1 防止一个消费者囤积过多消息
// 手动 ack：处理成功才确认，失败则 nack + requeue
@RabbitListener(queues = "demo.work.queue", containerFactory = "workQueueListenerContainerFactory")
public void handle(DemoMessage msg, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
    try {
        process(msg);
        channel.basicAck(tag, false);   // 确认
    } catch (Exception e) {
        channel.basicNack(tag, false, true);  // 拒绝并重新入队
    }
}
```

已预置 `WorkQueueListener` 类与 `workQueueListenerContainerFactory`（prefetch=1 + manual ack），详见 `RabbitMqDemoConfig`。

---

## 3. Fanout Exchange — 广播

> 一条消息**广播**到所有绑定队列——routing key 被忽略。典型场景：配置刷新通知、系统公告。

**涉及组件：**

| 组件 | 名称 |
|------|------|
| Exchange | `demo.fanout.exchange`（fanout，non-durable, auto-delete） |
| Queue A | `demo.fanout.queue.a` |
| Queue B | `demo.fanout.queue.b` |

**实跑输出：**

```
=== Fanout Exchange —— 一条消息广播到所有绑定队列 ===
[producer] sent to exchange 'demo.fanout.exchange': DemoMessage{...Broadcast via fanout...}
[queue.a] received: DemoMessage{...Broadcast via fanout...}
[queue.b] received: DemoMessage{...Broadcast via fanout...}
```

**关键代码：**

```java
// routing key 传 ""（fanout 忽略 routing key）
rabbitTemplate.convertAndSend("demo.fanout.exchange", "", msg);

// 两条队列各自独立收到同一条消息
Object a = rabbitTemplate.receiveAndConvert("demo.fanout.queue.a", 3000);
Object b = rabbitTemplate.receiveAndConvert("demo.fanout.queue.b", 3000);
```

**Bean 声明（`RabbitMqDemoConfig`）：**

```java
@Bean public FanoutExchange fanoutExchange() { return new FanoutExchange("demo.fanout.exchange", false, true); }
@Bean public Queue fanoutQueueA() { return QueueBuilder.nonDurable("demo.fanout.queue.a").build(); }
@Bean public Queue fanoutQueueB() { return QueueBuilder.nonDurable("demo.fanout.queue.b").build(); }
@Bean public Binding fanoutBindingA(FanoutExchange e, Queue fanoutQueueA) { return BindingBuilder.bind(fanoutQueueA).to(e); }
@Bean public Binding fanoutBindingB(FanoutExchange e, Queue fanoutQueueB) { return BindingBuilder.bind(fanoutQueueB).to(e); }
```

---

## 4. Direct Exchange — 精确路由

> 按 routing key **精确匹配**——red 消息进 red 队列，blue 消息进 blue 队列，互不干扰。

**涉及组件：**

| 组件 | 名称 | routing key |
|------|------|-------------|
| Exchange | `demo.direct.exchange` | — |
| Queue Red | `demo.direct.queue.red` | `red` |
| Queue Blue | `demo.direct.queue.blue` | `blue` |

**实跑输出：**

```
=== Direct Exchange —— 按 routing key 精确路由到目标队列 ===
[producer] sent red=red blue=blue
[queue.red]  = DemoMessage{...这是 Red 消息...routingTag='red'...}
[queue.blue] = DemoMessage{...这是 Blue 消息...routingTag='blue'...}
[queue.red]  extra (expect null): null   ← red 队列没收到 blue 消息
```

**路由验证：**
- `red` → 仅 red 队列收到 ✅
- `blue` → 仅 blue 队列收到 ✅
- red 队列无 blue 消息残留 ✅（路由隔离正确）

**关键代码：**

```java
rabbitTemplate.convertAndSend("demo.direct.exchange", "red", redMsg);
rabbitTemplate.convertAndSend("demo.direct.exchange", "blue", blueMsg);
```

**对比 fanout：**

| 维度 | Fanout | Direct |
|------|--------|--------|
| routing key | 忽略 | 精确匹配 |
| 消息去向 | 所有绑定队列 | 仅匹配 key 的队列 |
| 典型场景 | 广播通知 | 按日志级别路由（error → 告警队列，info → 归档队列） |

---

## 5. Topic Exchange — 通配符路由

> 按 routing key **模式匹配**——`*` 匹配恰好一段，`#` 匹配零或多段。最灵活的 exchange 类型。

**涉及组件：**

| 组件 | 名称 | binding pattern |
|------|------|-----------------|
| Exchange | `demo.topic.exchange` | — |
| Queue All | `demo.topic.queue.all` | `log.#` |
| Queue Error | `demo.topic.queue.error` | `*.error` |

**路由矩阵：**

| routing key | `log.#` 匹配？ | `*.error` 匹配？ | 最终去向 |
|-------------|:---:|:---:|---|
| `log.info` | ✅ | ❌ | all 队列 |
| `app.error` | ❌ | ✅ | error 队列 |
| `log.error` | ✅ | ✅ | both 双队列 |

**实跑输出：**

```
=== Topic Exchange —— 通配符路由 (* 单段, # 多段) ===
[producer] sent 3 messages: log.info, app.error, log.error

[queue.all] msg1: DemoMessage{...Info log message..., routingTag='log.info'}
[queue.all] msg2: DemoMessage{...Log error message..., routingTag='log.error'}
[queue.all] msg3 (expect null): null   ← app.error 不匹配 log.#，正确

[queue.error] msg1: DemoMessage{...App error message..., routingTag='app.error'}
[queue.error] msg2: DemoMessage{...Log error message..., routingTag='log.error'}
[queue.error] msg3 (expect null): null  ← 无多余消息
```

**通配符速查：**

| pattern | 匹配 | 不匹配 |
|---------|------|--------|
| `log.#` | `log`, `log.info`, `log.error.critical` | `app.log`, `system` |
| `*.error` | `app.error`, `system.error` | `log.error.critical`（两段）, `error`（零段） |
| `#.error` | `error`, `app.error`, `a.b.error` | `error.log` |

**Bean 声明：**

```java
@Bean public TopicExchange topicExchange() { return new TopicExchange("demo.topic.exchange", false, true); }
@Bean public Binding topicBindingAll(TopicExchange e, Queue topicQueueAll) {
    return BindingBuilder.bind(topicQueueAll).to(e).with("log.#");
}
@Bean public Binding topicBindingError(TopicExchange e, Queue topicQueueError) {
    return BindingBuilder.bind(topicQueueError).to(e).with("*.error");
}
```

---

## 6. Headers Exchange — 消息头匹配

> 按**消息头**匹配而非 routing key——支持 `x-match: any`（任一满足）或 `x-match: all`（全部满足）。

**涉及组件：**

| 组件 | 名称 | 匹配模式 |
|------|------|----------|
| Exchange | `demo.headers.exchange` | — |
| Queue JSON | `demo.headers.queue.json` | `x-match: any`, `format=json` |
| Queue Binary | `demo.headers.queue.binary` | `x-match: all`, `format=binary` + `version=2` |

**实跑输出：**

```
=== Headers Exchange —— 消息头匹配（any / all，精确匹配） ===
[producer] sent header {format=json}
[producer] sent header {format=binary, version=2}
[producer] sent header {format=binary, version=1} → 不匹配任何绑定，将被丢弃

[queue.json]  msg1: DemoMessage{id='1', routingTag='json'}   ← any 匹配成功
[queue.json]  msg2 (expect null): null

[queue.binary] msg1: DemoMessage{id='2', routingTag='binary'}  ← all 匹配成功
[queue.binary] msg2 (expect null): null
```

**验证结果：**

| 消息 | format | version | json 队列 (any) | binary 队列 (all) |
|------|--------|---------|:---:|:---:|
| id=1 | json | — | ✅ | ❌ (format≠binary) |
| id=2 | binary | 2 | ❌ (format≠json) | ✅ |
| id=3 | binary | 1 | ❌ | ❌ (version≠2) — **丢弃** |

**关键代码：**

```java
// 发送时通过 MessagePostProcessor 设置消息头
rabbitTemplate.convertAndSend("demo.headers.exchange", "", msg, m -> {
    m.getMessageProperties().setHeader("format", "json");
    return m;
});
```

**Bean 声明（`any` vs `all`）：**

```java
// any：头中 format=json 即匹配
@Bean public Binding headersBindingJson(HeadersExchange e, Queue q) {
    return BindingBuilder.bind(q).to(e).whereAny(Map.of("format", "json")).match();
}

// all：头中必须同时满足 format=binary 且 version=2
@Bean public Binding headersBindingBinary(HeadersExchange e, Queue q) {
    Map<String, Object> m = new HashMap<>();
    m.put("format", "binary");
    m.put("version", "2");
    return BindingBuilder.bind(q).to(e).whereAll(m).match();
}
```

**Headers vs Topic：**

| 维度 | Topic | Headers |
|------|-------|---------|
| 匹配依据 | routing key（字符串） | 消息头（key-value） |
| 表达能力 | 通配符层级匹配 | 精确值匹配 + any/all 逻辑 |
| 典型场景 | 日志路由、事件过滤 | 多维度条件路由（format + version + region） |

---

## 双栏对照：Spring AMQP vs 原生客户端

同一个操作在两种 API 下的写法对比。

| 操作 | Spring AMQP（`RabbitTemplate`） | 原生 RabbitMQ Client（`com.rabbitmq.client`） |
|------|-------------------------------|---------------------------------------------|
| **声明队列** | `@Bean Queue queue() { return new Queue("q"); }` | `channel.queueDeclare("q", false, false, false, null);` |
| **声明 exchange** | `@Bean FanoutExchange e() { return new FanoutExchange("e"); }` | `channel.exchangeDeclare("e", "fanout");` |
| **绑定** | `@Bean Binding b(Exchange e, Queue q) { return BindingBuilder.bind(q).to(e).with("rk"); }` | `channel.queueBind("q", "e", "rk");` |
| **发送消息** | `rabbitTemplate.convertAndSend("e", "rk", obj);` | `channel.basicPublish("e", "rk", null, body);` |
| **同步拉取** | `rabbitTemplate.receiveAndConvert("q", 3000);` | `channel.basicGet("q", true);` |
| **Push 消费** | `@RabbitListener(queues = "q")` | `channel.basicConsume("q", true, callback, cancelCallback);` |
| **手动 ack** | `channel.basicAck(tag, false);`（注入 Channel 参数） | `channel.basicAck(tag, false);` |
| **消息转换** | 自动 JSON 序列化/反序列化（`Jackson2JsonMessageConverter`） | 手动 `JSON.stringify` / `JSON.parse` + `byte[]` |
| **连接管理** | `CachingConnectionFactory` 自动管理连接/通道池 | 手动管理 `Connection` + `Channel`（try-with-resources） |

**选择建议：**
- Spring Boot 项目用 Spring AMQP（`RabbitTemplate` + `@RabbitListener`），享受自动装配、JSON 转换、连接池管理。
- 非 Spring 项目或性能敏感场景用原生客户端，更精细的控制。

---

## 已落地 vs 待建设

本仓库当前（ticket 04-05）已完成 **六种路由拓扑的可运行 demo**：

| Demo | 状态 | 说明 |
|------|:--:|------|
| Simple Queue | ✅ | 默认交换器 + 同步拉取 |
| Work Queue | ✅ | 多条消息 FIFO 顺序验证 + prefetch 工厂已预置 |
| Fanout Exchange | ✅ | 双队列广播验证 |
| Direct Exchange | ✅ | routing key 精确路由 + 路由隔离验证 |
| Topic Exchange | ✅ | `*` 和 `#` 通配符验证 |
| Headers Exchange | ✅ | `any` / `all` 匹配 + 孤儿消息丢弃验证 |

后续 ticket（06-08）计划覆盖：

| 主题 | 计划内容 |
|------|---------|
| **手动 ack / nack / requeue** | 演示手动确认、拒绝与死信处理 |
| **Publisher Confirm + Returns** | 生产端确认机制（correlated confirm + mandatory return） |
| **死信队列 DLX** | 消息过期/被拒绝/队列满时的死信路由 |
| **RPC 模式** | request/reply 模式（`SendAndReceive`） |
| **TTL + 优先级队列** | 消息级/队列级过期 + 优先级队列消费 |
| **Spring 封装 vs 原生客户端** | `application.yml` 配置 + 原生 `ConnectionFactory` 代码片段 |

---

## 已知坑位

### 1. JavaTimeModule + trustedPackages（JSON 序列化）

用 `Jackson2JsonMessageConverter` 处理 `LocalDateTime` 字段时，必须注册 `JavaTimeModule` 并指定信任包：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());     // ← 否则 LocalDateTime 序列化抛异常

Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
DefaultClassMapper classMapper = new DefaultClassMapper();
classMapper.setTrustedPackages("com.example.middleware.rabbitmq.message",
                               "com.example.middleware.rabbitmq.*");  // ← 包含子包！
converter.setClassMapper(classMapper);
```

> 来自主工程 M6 运行期踩坑经验：`DefaultClassMapper.setTrustedPackages` 不含子包，必须显式加 `.*` 后缀。

### 2. 僵尸消费者（`spring-boot:run` 进程残留）

`mvn spring-boot:run` 在某些情况下 fork 出的 JVM 可能未正常退出，导致消费者连接残留在 RabbitMQ 上，持续消费消息。表现：demo 发出的消息被"看不见的消费者"吃掉，`receiveAndConvert` 返回 `null`。

**诊断：** `docker exec java-learn-rabbitmq rabbitmqctl list_consumers | grep demo.work`

**修复：** `taskkill /PID <pid> /F` 杀掉残留 Java 进程。

**预防：** Spring AMQP `@Profile` 隔离已到位，但若多次 `Ctrl+C` 中断 Maven 运行，建议跑完后检查 `netstat -ano | grep 5672`。

### 3. non-durable 队列不跨连接恢复

本仓库所有队列均为 `non-durable`（一次性 demo 不需要 broker 重启后保留）。如果你觉得奇怪"为什么重启 RabbitMQ 后队列丢了"——这是预期行为。生产环境请用 `QueueBuilder.durable()`。
