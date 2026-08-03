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
  `node scripts/api-smoke.mjs`（Node ≥ 18，用内置 fetch，零依赖不用 npm install；全部断言通过退出码为 0）
- 单条 curl 速查（可直接复制）：见 [docs/api/curl.md](docs/api/curl.md)

# 并发压测：乐观锁 vs 悲观锁

30 件库存、100 个用户同时开抢，打印耗时分布与最终一致性校验。两个脚本共用
`scripts/lib/bench-core.mjs`，都是零依赖（Node ≥ 18 内置 fetch）：

```bash
node scripts/bench-optimistic.mjs             # 乐观锁：version 版本号
node scripts/bench-pessimistic.mjs            # 悲观锁：SELECT ... FOR UPDATE

node scripts/bench-optimistic.mjs --retry 5   # 客户端遇 3005 最多重试 5 次
node scripts/bench-optimistic.mjs --stock 50 --users 200 --qty 2
node scripts/bench-pessimistic.mjs --keep     # 压完保留商品，便于手工查库存
```

用「发令枪」模式（所有协程先挂在同一个 Promise 上再一次性放行）保证请求真正同时发出，
实测发压窗口约 10ms。压完自动校验不超卖、库存账实相符。

本机实测（30 件库存 / 100 并发 / 每人 1 件）：

| 方案 | 墙钟耗时 | 成功 | 3005 冲突 | 库存剩余 | 实际请求数 |
| --- | --- | --- | --- | --- | --- |
| 乐观锁（不重试） | ~300-400 ms | 10~11 单 | 89~90 单 | **卖不掉 19~20 件** | 100 |
| 乐观锁（--retry 5） | ~1090 ms | 30 单 | 0 | 0 | **369（放大 3.7x）** |
| 悲观锁 | ~721 ms | 30 单 | 0 | 0 | 100 |

几个值得注意的点：

- **乐观锁不重试会大量少卖**。100 个人抢同一行，两次读写之间版本必然被改，
  成功率只有 10% 左右，20 件库存砸手里。乐观锁必须配重试才是完整方案。
- **这个场景下悲观锁反而更优**：721ms 卖光 30 件，比带重试的乐观锁（1090ms、369 次请求）
  又快又省。乐观锁的优势在低冲突场景，这种「所有人抢同一行」的秒杀是它最差的战场。
- 悲观锁耗时直方图呈**双峰**：先抢到锁的 30 单在 79~600ms 渐进爬升，
  后 70 单集中堆在 606~712ms 尾部——它们得排队等到自己才发现库存已空。
- 应用没配 Hikari `maximum-pool-size`（默认 **10**），所以测到的排队是
  「连接池排队 + 行锁排队」的叠加，不是纯粹的锁竞争。

# 清库

造完测试数据想回到干净状态时用（只要 mysql 容器在跑就行，不用装 mysql 客户端）：

```bash
node scripts/db-reset.mjs              # 交互确认后清空所有表（保留表结构，自增 ID 归 1）
node scripts/db-reset.mjs --yes        # 跳过确认
node scripts/db-reset.mjs --dry-run    # 只看会执行哪些 SQL，不落库
node scripts/db-reset.mjs --tables orders,stock --yes   # 只清指定表
node scripts/db-reset.mjs --drop --yes # 连表结构一起删，之后重启应用由 schema.sql 重建
```

> 排查提示：`TRUNCATE` 之后直接查 `information_schema.tables` 的 `AUTO_INCREMENT` / `TABLE_ROWS`
> 会看到**旧值**——MySQL 8.0 默认 `information_schema_stats_expiry=86400`，统计信息缓存 24 小时。
> 要拿实时值得先 `SET SESSION information_schema_stats_expiry=0;`，或者直接 `SELECT COUNT(*)`。

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
