# 启动命令
1. docker-compose up -d
2. mvn install -DskipTests
3. cd java-learn-app
4. mvn spring-boot:run

# 执行测试文件

1. mvn test
2. mvn test -Dtest=InfraConnectionTest // 只执行 InfraConnectionTest 这一个测试文件

# 手工调接口

- 一键跑通全流程（注册 → 登录拿 token → 商品 CRUD → 初始化库存 → 乐观/悲观锁下单 → 错误码 → 并发抢购）：
  `bash scripts/api-smoke.sh`
- 单条 curl 速查（可直接复制）：见 [docs/api/curl.md](docs/api/curl.md)

# 01-scaffold-and-local-infra.md

这个任务跑完了解了 Spring boot 的入口文件(EcommerceApplication.java)，代码组织方式，扫描方式，以及 resources 的作用。

## 代码组织方式
1. Controller：配置 http 的路由，注解主要有：RequestMapping, GetMapping
2. Service：写业务逻辑 @Service
3. Mapper：代码有点少，还没分析出来
4. resources/application.yml：定义的参数可以直接被第三方的包(mysql, redis) 等使用
5. config 文件夹：通过 @RestControllerAdvice 注解来做了一些控制切面的方式

## 扫描方式
不跟 nestjs 一样有 moudle 来构建地图而是直接扫描 EcommerceApplication.java 下面的文件。包含 @RestController 注解的文件就会被扫描进去。看起来比 module 文件更方便一些

# 02-auth.md

1. /auth/register: 通过用户传过来 username, password, role = user 。来创建用户 password 会用 passwordEncoder.encode 来进行加密
2. /auth/login: 通过用户传过来 username, password。然后用 username 查到信息，找到了对密码做 passwordEncoder.matches，成功再进行 jwtUtil.generate(user.getId(), user.getUsername(), user.getRole()); 拿到 token
3. /user/me: 属于被 LoginInterceptor.java 拦截的接口。规则配置在 WebConfig.java 中，WebConfig.java 会被直接注入。LoginInterceptor.java 包含了 role 的判断和 token 的解析
4. /admin/panel：用来验证角色权限问题
> 注意: LoginInterceptor.java 中可以直接使用 LoginUser.java，是因为他俩都属于同一个包中，并且同级，因此可以不 import 直接使用。另外 schema.sql 

# 03-product-db.md

1. /products (GET)：商品列表，MyBatis-Plus 的 `selectList` 零 XML 查询
2. /products (POST)：新建商品；/products/{id} (GET/PUT/DELETE)：详情/改/删
3. /products/{id}/stock (POST)：为该商品初始化库存（available = total），库存行带 `version` 字段做乐观锁
4. /products/{id}/order (POST)：下单并扣库存，默认乐观锁；请求体传 `"lockType":"PESSIMISTIC"` 走 `SELECT ... FOR UPDATE` 悲观锁。下单逻辑在 `@Transactional` 内完成「查库存→扣减→插订单」
> 注意：MyBatis-Plus 的 `@Version` 乐观锁依赖 `MybatisPlusConfig` 里的 `OptimisticLockerInnerInterceptor`，
> 且 update 时框架会自动把 `version` 拼进 WHERE 并自增；命中 0 行即代表并发冲突（抛 STOCK_CONFLICT）。
> 另外 `mybatis-plus-boot-starter` 会把 `mybatis-spring` 锁成 2.1.2（Spring Boot 2 线），与 SB3 的 Spring 6.1
> 不兼容，已在 app pom 显式覆盖为 3.0.4。
