# Spec: Java 后端转岗学习路线图（电商/秒杀载体）

Status: ready-for-agent

> 状态：已综合会话决策 + 优化体检（v2），已发布至本地 issue tracker（`.scratch/java-backend-plan/spec.md`），标签 `ready-for-agent`。
> 关联文档：`CONTEXT.md`（术语表）、`docs/adr/0001-learning-stack-and-approach.md`（决策记录）、`docs/LEARNING-ROADMAP.md`（执行版路线图）。

## Problem Statement

我是有 10 年开发经验的前端 + NestJS 全栈工程师，8 年前写过 Java 后端，现在目标是应聘 Java 后端工程师。痛点有三：①离开 Java 生态 8 年，Java 版本（8→17）和 Spring Boot 生态已大变；②后端专属的 Redis / MQ / 并发 / 分布式八股不熟；③要在 1–3 个月内以"面试拿 offer"为主线高效补课，且希望"按功能一个一个学"，而不是零散啃书。

## Solution

以**电商/秒杀系统**为学习载体，把高频面试考点拆成 10 个功能模块（M1–M10），每个模块按「原理(八股) → 小练习 → 自测题」三步推进；用**三档裁剪**保证 1–3 个月紧凑期不打散：T1 死磕（Java 基础含现代语法 / 并发 / MySQL / Redis / Spring 原理）、T2 覆盖（算法 / MQ / 微服务基础）、T3 后补（JVM 调优 / 源码 / 分布式事务）。技术基线为 Java 17 + Maven + IntelliJ + Spring Boot 3.x + Docker。另设三条常驻并行轨道（计算机基础 / 算法-用 Java 写 / 求职软技能），并用双周 Mock 面试 + 面试就绪门禁作为测试切入点。

## User Stories

1. As a 转岗候选人, I want 一个清晰的按功能拆分的学习路线, so that 我不会在零散资料里迷失方向。
2. As a 转岗候选人, I want 以电商/秒杀系统为骨架, so that 每个考点都能挂到真实功能上、便于面试讲故事。
3. As a 时间紧张的候选人, I want 三档优先级裁剪, so that 我把精力压在最高频拉分的 T1 考点上。
4. As a 8 年未碰 Java 的人, I want 明确补 Java 17 现代语法, so that 我会写 Stream/Optional/record/sealed 等面试常考特性。
5. As a 零后端生态经验的人, I want 从工程脚手架开始, so that 我先跑通一个分层清晰的 Spring Boot 工程。
6. As a 面试候选人, I want 掌握 Spring Boot 自动装配/IOC/AOP 原理, so that 我能答清框架底层八股。
7. As a 面试候选人, I want 实现 JWT 登录鉴权与 RBAC, so that 我能讲清 Session vs JWT、过滤器/拦截器/AOP 顺序。
8. As a 面试候选人, I want 用 MySQL 设计商品与库存表并写事务下单, so that 我能讲清索引/事务/MVCC/锁。
9. As a 面试候选人, I want 用 Redis 做商品缓存并处理三大问题, so that 我能答缓存穿透/击穿/雪崩与一致性。
10. As a 面试候选人, I want 理解 Java 并发与线程池, so that 我能设计高并发库存扣减。
11. As a 面试候选人, I want 用 MQ 做下单异步化并保证可靠, so that 我能讲清不丢/不重/有序与事务消息。
12. As a 面试候选人, I want 理解分布式事务选型, so that 我能对比 2PC/TCC/Saga 并讲清最终一致性。
13. As a 面试候选人, I want 实现秒杀链路（限流+Lua+MQ）, so that 我能画高并发架构图并讲 trade-off。
14. As a 面试候选人, I want 了解 Spring Cloud 微服务组件, so that 我能讲清服务拆分与注册发现/网关。
15. As a 算法面试者, I want 每日用 Java 刷中等题, so that 我既练算法又顺带熟 Java。
16. As a 候选人, I want 每日 20min 计算机基础（网络/OS/数据结构）, so that 我为八股与手撕打底。
17. As a 求职者, I want 简历/行为题/空窗表达/系统设计话术, so that 我能把"拿 offer"这件事闭环。
18. As a 候选人, I want 双周 Mock 面试, so that 我提前暴露表达与知识盲区。
19. As a 候选人, I want 一份"面试就绪门禁清单", so that 我知道何时可以开始投简历。
20. As a 候选人, I want 本地 Docker 一键起 MySQL/Redis/MQ, so that 我不为环境浪费时间。
21. As a 候选人, I want 一个最小可演示项目清单, so that 简历有可讲的真项目背书。

## Implementation Decisions

- **技术栈家族**：现代互联网中厂生态（Spring Boot + MySQL + Redis + MQ + 微服务）。依据：与用户 NestJS 的 DI+MVC 心智直接迁移，市场岗位最多。
- **主线目标**：面试拿 offer 优先；保留"按功能学"，每功能映射「原理→小练习→自测题」。
- **时间窗与裁剪**：1–3 个月紧凑 → T1 死磕 / T2 覆盖 / T3 后补。
- **学习载体**：电商/秒杀系统（M1 脚手架 / M2 鉴权 / M3 商品DB / M4 缓存 / M5 并发 / M6 MQ / M7 分布式事务 / M8 秒杀 / M9 微服务 / M10 系统设计&算法）。
- **技术基线**：Java 17（LTS，补 8→17 现代语法）+ Maven + IntelliJ + Spring Boot 3.x + Docker。
- **优化决策（v2 相对初版）**：
  - M2 鉴权从 W6-8 提前至 M1 之后（W1-3），因登录是最高频面试题且网络基础挂靠其上。
  - 新增"计算机基础"常驻轨道（T1，每日 20min）。
  - 算法明确"用 Java 写"，目标 100+ 中等题。
  - Mock 面试从 W4 起每两周一次（原仅 W10-12）。
  - 新增"面试就绪门禁"Exit Checklist 与"最小可演示项目清单"。
  - 本地基建明确用 Docker Compose 起 MySQL/Redis/MQ。
  - 新增"求职软技能"轨道（简历/行为/空窗表达/系统设计话术）。
- **依赖顺序**：M1 → M2 → (M3/M4/M5 可并行) → M6 → (M7/M8/M9) → M10；缓存一致性（M4）依赖 DB 写入（M3），分布式锁（M4/M8）依赖并发理解（M5）。

## Testing Decisions

- **Seams（测试切入点，越少越好，集中为三类）**：
  1. 模块自测题：每个 M 模块结束必须过"原理三连 + 小练习跑通"，不过不进下一模块。
  2. 双周 Mock 面试：W4/W8/W10/W12 四轮，限时模拟，覆盖对应阶段模块 + 算法 + 系统设计。
  3. 就绪门禁：投简历前逐条勾选 Exit Checklist（9 项能力 + 项目可演示 + 简历/行为题定稿）。
- **好测试的标准**：只测"能否对外讲清并写对"，不测实现细节；以"能否独立画架构图 + 答清 trade-off + 限时手撕"为通过判据。
- **覆盖模块**：全部 M1–M10 + 三条常驻轨道；Mock 4 做综合复盘。
- **Prior art**：无既有测试代码（空仓库）；后续若工程化可补 Spring Boot Test + Testcontainers 做集成测试（属 T3 可选）。

## Out of Scope

- Tier3 后补内容：JVM 调优与源码级框架原理、Raft/Redlock、分库分表（ShardingSphere）、K8s/CI-CD——时间允许再啃，不阻塞面试就绪。
- 具体公司定向（如某大厂专属中间件）未细化，按需临门调整。
- 不产出生产级电商系统，仅"最小可演示 + 能讲清架构"即可。

## Further Notes

- 用户最大优势是 NestJS 全栈经验，DI/分层/拦截器/AOP 概念与 Spring 一一对应，迁移成本低于从零学 Java。
- 真正的增量是：①Java 生态现代化（Java 17 语法、Spring Boot 取代旧 Spring XML）②后端专属 Redis/MQ/并发/分布式硬八股。路线图已把这两块放在 T1 最前死磕。
- 发布说明：本 spec 当前为本地文档，待配置项目 issue tracker 并运行 `/setup-matt-pocock-skills` 后，应用 `ready-for-agent` 标签发布。
