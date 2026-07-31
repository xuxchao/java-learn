# Java 后端求职学习语境

为一位有 10 年开发经验（前端 + NestJS 全栈、8 年前写过 Java 后端）的工程师，规划"转岗 Java 后端工程师"的面试导向学习路径。以"电商/秒杀系统"的功能模块为骨架，按功能串起技术与八股知识点。

## 技术栈基线

**Spring Boot**：Java 生态主流的应用框架，基于约定优于配置，内嵌容器、自动装配。
_Avoid_: Spring（仅指传统 XML 配置的旧框架，本计划以 Boot 为准）

**自动装配（Auto-configuration）**：Spring Boot 按 classpath 与配置自动注册 Bean 的机制（@SpringBootApplication → @EnableAutoConfiguration）。
_Avoid_: 手动 XML 配置

**IOC / DI**：控制反转 / 依赖注入。容器管理对象生命周期与依赖关系，是 Spring 一切的基础。
_Avoid_: 自己 new 对象

**AOP**：面向切面编程。用在日志、事务、权限等横切关注点，基于动态代理（JDK / CGLIB）。
_Avoid_: 把横切逻辑写进业务方法

**Maven**：Java 主流构建与依赖管理工具（pom.xml）。本计划基线。
_Avoid_: Gradle（仅在你目标公司明确要求时再学）

## Java 语言（8→17 现代语法）

**Stream API**：Java 8 引入的函数式集合操作（map/filter/reduce），面试常考。
_Avoid_: 手写 for 循环替代

**Optional**：显式表达"可能为空"的容器，抑制 NPE。
_Avoid_: 链式 get() 后又判空（失去意义）

**var（局部变量类型推断）**：Java 10+，仅局部变量，编译期仍强类型。
_Avoid_: 用于字段/方法返回/lambda 参数

**record**：Java 16+ 不可变数据载体，自动生成构造器/getter/equals/hashCode。
_Avoid_: 为需要可变的实体用 record

**sealed**：Java 17+ 限制哪些类可继承，配合 pattern matching。

## 并发（Tier1 死磕）

**JMM（Java 内存模型）**：规定线程如何与主内存交互，是理解可见性/有序性的基础。
_Avoid_: 把它和 JVM 内存结构（堆/栈）混为一谈

**volatile**：保证可见性 + 禁止指令重排，但不保证原子性。
_Avoid_: 用它代替锁做复合操作（如 i++）

**CAS**：无锁原子更新（Compare-And-Swap），java.util.concurrent 原子类的底层。
_Avoid_: 高争用下仍认为它一定比锁快（有 ABA / 自旋开销）

**AQS**：抽象队列同步器，ReentrantLock / 各种同步器（Semaphore/CountDownLatch）的骨架。
_Avoid_: 把 AQS 当成可直接使用的业务锁

**线程池**：通过ThreadPoolExecutor 复用线程；核心参数（core/size/queue/handler）必考。
_Avoid_: 用 Executors 无界队列（易 OOM），面试也常被问

## 存储（MySQL）

**索引（B+ 树）**：InnoDB 聚簇索引，回表、最左前缀、覆盖索引是高频考点。
_Avoid_: 在区分度低的列建索引

**事务隔离级别**：READ UNCOMMITTED → RR（MySQL 默认）→ SERIALIZABLE；RR 靠 MVCC + 间隙锁防幻读。
_Avoid_: 以为 RR 完全不会幻读（要靠当前读 + 间隙锁）

**乐观锁 / 悲观锁**：乐观用版本号/CAS，悲观用 select ... for update。秒杀库存扣减的核心。
_Avoid_: 高并发库存用悲观锁（锁竞争灾难）

**MyBatis**：半自动 ORM，SQL 手写；#{} 预编译防注入，${} 拼接（危险）。
_Avoid_: 用 ${} 拼用户输入

## 缓存（Redis）

**缓存三大问题**：穿透（查不存在的 key，用布隆过滤器/空值缓存）、击穿（热点 key 失效，用互斥/逻辑过期）、雪崩（大量 key 同时失效，用随机 TTL/集群）。
_Avoid_: 只背名词不背各自解法

**缓存一致性**：双写一致性难保证；常用"先更新 DB 再删缓存" + 延迟双删。
_Avoid_: 认为"先删缓存"万无一失

**分布式锁**：基于 Redis（SET NX + 过期）+ Redisson 看门狗；用于跨进程互斥。
_Avoid_: 锁不设过期（死锁）/ 只 setnx 不校验持有者

## 消息（MQ，Tier2）

**消息可靠性**：不丢（生产者确认 + 持久化 + 消费者手动 ack）、不重（幂等消费）、有序（分区/队列顺序）。
_Avoid_: 把 MQ 当同步调用用

**事务消息**：用 MQ 实现跨服务最终一致性的手段（如 RocketMQ 半消息）。
_Avoid_: 用 MQ 做强一致事务

## 分布式 / 微服务（Tier2→3）

**CAP / BASE**：分布式下一致性/可用性/分区容错不可兼得；BASE 接受最终一致。
_Avoid_: 指望分布式系统做到强一致且高可用

**分布式事务**：2PC（强一致但阻塞）、TCC（业务补偿）、Saga（长事务）、事务消息（最终一致）。
_Avoid_: 微服务内也上一套重分布式事务

**限流 / 熔断 / 降级**：限流（令牌桶/漏桶/滑动窗口）保护系统；熔断（错误率超阈值开闸）；降级（兜底返回）。
_Avoid_: 三者混为一谈

**Spring Cloud**：微服务组件族（注册发现 Nacos/Eureka、网关 Gateway、熔断 Sentinel、配置中心）。
_Avoid_: 小项目也上全套微服务（过度设计）

## 面试语境

**八股**：对底层原理 + 源码的口头考查（非贬义），本计划 Tier1 的重点。
_Avoid_: 只背结论不背"为什么"

**系统设计**：给定场景（如设计短链/秒杀/排行榜）的高并发高可用方案设计，Tier2 冲刺内容。
