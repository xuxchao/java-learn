# 01 — 工程脚手架 & 本地基建

**What to build:** 一个能独立启动的分层 Spring Boot 工程，配合本地依赖一键拉起——这是后续所有模块的基础，覆盖自动装配、IOC、AOP 原理与统一错误处理，并用 Docker Compose 把 MySQL / Redis / MQ 跑起来。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] 多模块 Maven 工程启动成功，分层（controller / service / mapper）结构清晰
- [ ] 统一返回体与全局异常处理生效（故意抛异常的接口返回标准错误体）
- [ ] Docker Compose 起 MySQL / Redis / MQ，应用能正常连接
- [ ] 能讲清自动装配、IOC 容器、AOP 基本原理（自测题通过）
