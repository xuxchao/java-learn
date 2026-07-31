# 学习技术栈与路径决策

为转岗 Java 后端工程师制定面试导向的学习方案：技术栈锁定现代互联网生态（Spring Boot + MySQL + Redis + MQ + 微服务/云原生），以"电商/秒杀系统"功能模块为骨架串知识点，基线采用 Java 17 + Maven + IntelliJ IDEA。因时间窗为 1–3 个月（紧凑），对模块做三档裁剪：Tier1 死磕（Java 基础含现代语法、并发、MySQL、Redis、Spring 原理），Tier2 覆盖（算法中等题、MQ、微服务基础），Tier3 后补（JVM 调优/源码、分布式事务深度）。

## 背景（Context）

当事人：10 年开发经验，前端 + NestJS 全栈，8 年前写过 Java 后端。目标：应聘 Java 后端工程师，主目标为通过面试拿 offer。约束：1–3 个月内要能面试。NestJS 的 DI + MVC 心智可直接迁移到 Spring Boot，主要缺口在 8 年来 Java 生态的变化（Java 8→17 现代语法、Spring Boot 取代旧 Spring、Redis/MQ/微服务成为标配）。

## 决策（Decision）

- **技术栈家族**：现代互联网中厂生态（Spring Boot 为主），而非传统企业（Oracle/WebLogic）或纯创业轻栈。
- **主线目标**：面试拿 offer 优先；组织结构保留"按功能学"，但每个功能模块都映射到对应八股/原理 + 小练习。
- **时间裁剪**：1–3 个月紧凑期 → 三档优先级，Tier3 明确后补，不平均用力。
- **学习载体**：电商/秒杀系统，因其能一次性覆盖 Redis 缓存/分布式锁、MQ 异步、MySQL 事务/锁/索引、Spring 原理等高频考点。
- **技术基线**：Java 17（LTS，覆盖面试常考现代语法）+ Maven + IntelliJ IDEA。

## 被否决的备选（Considered Options）

- **传统企业栈（Java 8 + Oracle）**：否决——与当事人 NestJS 现代背景错配，且市场岗位与成长空间更窄。
- **算法优先 / 原理源码优先 / 均匀不裁剪**：否决——紧凑期必须裁剪，三档法在"覆盖面"与"深度"间取得平衡。
- **不绑定系统、纯按模块排**：否决——失去"按功能学"的抓手，记忆点分散，不适合"一个功能一个功能学"的诉求。
- **Java 8 基线**：否决——会漏掉 Stream/Optional/var/record/sealed 等 8→17 面试常考语法。

## 后续影响（Consequences）

- 所有实操练习统一在 Java 17 + Maven 工程下进行，避免版本混用。
- 学习顺序固定为 Tier1 → Tier2 → Tier3，任何新知识点先判断归属档位。
- 秒杀系统的功能清单（见 LEARNING-ROADMAP.md）作为唯一骨架，新增考点需挂到对应模块。
