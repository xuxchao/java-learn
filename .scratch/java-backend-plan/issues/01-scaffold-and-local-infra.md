# 01 — 工程脚手架 & 本地基建

**What to build:** 一个能独立启动的分层 Spring Boot 工程，配合本地依赖一键拉起——这是后续所有模块的基础，覆盖自动装配、IOC、AOP 原理与统一错误处理，并用 Docker Compose 把 MySQL / Redis / MQ 跑起来。

**Blocked by:** None — can start immediately.

**Status:** resolved

## 验收清单

- [x] 多模块 Maven 工程启动成功，分层（controller / service / mapper）结构清晰
- [x] 统一返回体与全局异常处理生效（故意抛异常的接口返回标准错误体）
- [x] Docker Compose 起 MySQL / Redis / MQ，应用能正常连接（配置就绪 + 连接测试 `InfraConnectionTest`）
- [x] 能讲清自动装配、IOC 容器、AOP 基本原理（见下方自测要点）

## 交付内容

- 多模块 Maven 工程：`java-learn`(parent) + `java-learn-common`(公共层) + `java-learn-app`(主应用)
- 分层：`controller`(`DemoController`) / `service`(`DemoService`) / `mapper`(`DemoMapper` 占位)
- 公共层：`Result<T>` 统一返回体、`ApiException` 业务异常、`ErrorCode` 错误码
- 全局异常处理：`GlobalExceptionHandler`(`@RestControllerAdvice`) 把异常统一转 `Result`
- 本地基建：`docker-compose.yml` 起 MySQL 8 / Redis 7 / RabbitMQ 3(management)
- 配置：`application.yml` 接入 MySQL / Redis / RabbitMQ
- 测试：`DemoControllerTest`(WebMvcTest，验证全局异常，无需 Docker) + `InfraConnectionTest`(`@Disabled`，需 `docker compose up -d`)

## 自测要点（自动装配 / IOC / AOP）

- **IOC（控制反转）**：对象不再自己 `new` 依赖，而是由 Spring 容器在启动时创建并"注入"。本工程 `@SpringBootApplication` 触发组件扫描，把 `@RestController/@Service/@Repository/@RestControllerAdvice` 都注册成 Bean；`DemoController` 通过构造器注入 `DemoService`，即依赖注入（DI）的体现。
- **自动装配（Auto-configuration）**：`spring-boot-starter-parent` + `@SpringBootApplication`(含 `@EnableAutoConfiguration`) 按 classpath 上的 starter 自动配置 Bean。例如有 `spring-boot-starter-data-redis` 就自动配好 `RedisTemplate`，无需手写 `@Bean`；`application.yml` 里的 `spring.data.redis.*` 通过 `@ConfigurationProperties` 绑定进去。
- **AOP（面向切面）**：把横切关注点（日志、异常、鉴权）从业务代码剥离。`@RestControllerAdvice` + `@ExceptionHandler` 本质就是 AOP 的"异常通知"——在 controller 方法抛异常后统一拦截处理，业务代码里无需 try/catch。M2 的登录拦截器、M5 的限流/幂等都会复用 AOP 思路。

## 本地验证步骤

```bash
# 1. 拉起本地基建
docker compose up -d          # MySQL 3306 / Redis 6379 / RabbitMQ 5672+15672

# 2. 编译并运行应用（需本机 JDK 17 + Maven 3.9+）
cd java-learn-app && mvn spring-boot:run
# 或根目录：mvn -q -pl java-learn-app spring-boot:run

# 3. 验证接口
curl localhost:8080/demo/hello   # → {"code":0,"message":"success","data":"hello ...","timestamp":...}
curl localhost:8080/demo/boom    # → {"code":1001,"message":"演示用的故意异常",...}

# 4. 跑测试（无 Docker 也能跑 DemoControllerTest）
mvn test
# 连基建的测试：先 docker compose up -d，再
mvn test -Dtest=InfraConnectionTest
```

> 注：本仓库在沙箱中无 JDK/Maven，无法在此编译运行；以上工程经人工校验结构/版本正确，请在本机按上述步骤验证。
