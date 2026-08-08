# RabbitMQ API 范例与讲解

> 建设中 —— 本文件由后续 ticket 填充具体内容（概念讲解 + 双栏静态片段 + 实跑真实输出）。

## 覆盖范围

- 路由模式：`simple` / `work`(prefetch+手动ack) / `fanout` / `direct` / `topic` / `headers`
- 可靠性：手动 `ack` / `nack` / `requeue`、`publisher confirm` + `returns`、死信队列 `DLX` + 重试
- 高级模式：`RPC`(request/reply)、`TTL`(过期)、优先级队列

## 运行

```bash
cd middleware-examples
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=rabbitmq
# 单跑： -Dspring-boot.run.arguments=--example=simple
```

> 注：JSON 消息体转换器需注册 `JavaTimeModule` 且 `trustedPackages` 包含消息体包（沿用主工程 M6 经验），否则反序列化报 "not in the trusted packages"。

详细内容见后续 ticket（06-08）。
