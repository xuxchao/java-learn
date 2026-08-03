# 02 — 用户注册登录 & 鉴权

**What to build:** 一条完整的"注册 → 登录 → 访问受保护接口"链路：密码加密落库、登录签发 JWT、拦截器校验登录态、RBAC 区分角色，未授权访问被拦截、授权访问被放行。

**Blocked by:** 01 — 工程脚手架 & 本地基建.

**Status:** resolved

## 验收清单

- [x] 注册时密码经 BCrypt 加密落库
- [x] 登录成功签发 JWT，拦截器校验登录态
- [x] 未带 token 访问受保护接口被拦截，带有效 token 放行
- [x] RBAC 权限模型可按角色区分
- [x] 能讲清 Session vs JWT、过滤器 / 拦截器 / AOP 的执行顺序与适用

## 交付内容

- 用户持久化：`model/User` + `repository/UserRepository`（复用 M1 的 `spring-boot-starter-jdbc` + `JdbcTemplate`，不新增 ORM）；`schema.sql` 幂等建 `users` 表（`IF NOT EXISTS`，`spring.sql.init.mode=always` 每次启动执行）。
- 密码加密：引入 `spring-security-crypto` 的 `BCryptPasswordEncoder`（仅 crypto 模块，不引入完整 Spring Security 过滤器链）。
- JWT：`security/JwtUtil`（jjwt 0.12.6，HS256，claim 存 userId/username/role）+ `application.yml` 的 `jwt.secret` / `jwt.expiration-ms`。
- 拦截器鉴权：`security/LoginInterceptor`（`@Component`，preHandle 校验 `Authorization: Bearer <token>`，写入 `loginUser` 到 request attribute）+ `config/WebConfig`（注册拦截器，排除 `/auth/**` 与 `/demo/**`）。
- 接口：
  - `AuthController` `/auth/register`、`/auth/login`（公开）
  - `UserController` `/user/me`（任意登录用户）
  - `AdminController` `/admin/panel`（需 ADMIN 角色，体现 RBAC）
- 错误码：common `ErrorCode` 新增 2xxx 段（2001 用户名已存在 / 2002 凭证错误 / 2003 未登录 / 2004 无权限）。
- 测试（纯单测，无需 Docker）：`JwtUtilTest` / `AuthServiceTest` / `LoginInterceptorTest`。

## 自测要点（Session vs JWT / 过滤器·拦截器·AOP）

- **Session vs JWT**：
  - Session：服务端存会话（内存/Redis），客户端持 `JSESSIONID` cookie；状态在服务端，易水平扩展需共享存储，跨域/移动端不友好。
  - JWT：服务端**无状态**，token 自包含用户声明并由签名防篡改；客户端自持，每次请求带 `Authorization` 头；天然适合跨域/移动端/网关转发，但**无法主动吊销**（需靠短过期 + 刷新令牌/黑名单兜底）。
  - 本项目选 JWT：学习载体是"无状态鉴权 + 网关转发"，且契合面试高频考点。
- **一次请求的执行顺序**：`Filter`（Servlet 级，最早，如 CORS/鉴权过滤器）→ `DispatcherServlet` → `Interceptor.preHandle`（Spring MVC 级，路由/角色校验，本项目的 LoginInterceptor 在此）→ `Controller` → **AOP**（`@RestControllerAdvice` 异常通知 / `@Transactional` 等，包裹业务方法）→ `Interceptor.postHandle/afterCompletion` → `Filter`。
- **三者适用**：
  - 过滤器(Filter)：最底层，与 Servlet 容器耦合，适合做全链路（如 XSS 清洗、全站 CORS、链路追踪）。
  - 拦截器(Interceptor)：Spring MVC 内，能拿到 Handler/Controller 信息，适合登录态校验、权限/RBAC、接口级日志——**本项目鉴权放这里**。
  - AOP：方法级横切，适合日志、事务、业务异常统一转换（`@RestControllerAdvice` 本质就是 AOP 的异常通知，与 M1 的全局异常处理一脉相承）。

## 本地验证步骤

```bash
# 0. 拉起基建（需 MySQL，users 表由 schema.sql 自动创建）
docker compose up -d            # MySQL 3306 / Redis 6379 / RabbitMQ 5672

# 1. 编译运行
cd java-learn-app && mvn spring-boot:run

# 2. 注册（密码 BCrypt 落库）
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"123456"}'

# 3. 登录拿 token
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"123456"}' | sed -E 's/.*"data":"([^"]+)".*/\1/')

# 4. 带 token 访问受保护接口（放行）
curl localhost:8080/user/me -H "Authorization: Bearer $TOKEN"

# 5. 不带 token（被拦截，返回 code=2003）
curl localhost:8080/user/me

# 6. 注册一个 ADMIN 再访问 /admin/panel（RBAC：非 ADMIN 返回 code=2004）
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"boss","password":"123456","role":"ADMIN"}'
ADMIN_TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"boss","password":"123456"}' | sed -E 's/.*"data":"([^"]+)".*/\1/')
curl localhost:8080/admin/panel -H "Authorization: Bearer $ADMIN_TOKEN"

# 7. 跑测试
#    M2 新增的 JwtUtilTest / AuthServiceTest / LoginInterceptorTest 是纯单测，不依赖任何中间件；
#    但 M1 遗留的 InfraConnectionTest 是 @SpringBootTest，需要 docker-compose 的
#    MySQL/Redis/RabbitMQ 处于运行状态，否则整个 mvn test 会失败。
mvn test
```

> 验证记录（2026-08-03）：已在本机 JDK 17.0.20 + Maven 3.9.16 下执行 `mvn test`，
> 结果 `BUILD SUCCESS`，`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`
> （DemoControllerTest 2 / InfraConnectionTest 3 / JwtUtilTest 3 / LoginInterceptorTest 6 / AuthServiceTest 4）。
