# 启动命令
1. docker-compose up -d
2. mvn install -DskipTests
3. cd java-learn-app
4. mvn spring-boot:run

# 执行测试文件

1. mvn test
2. mvn test -Dtest=InfraConnectionTest // 只执行 InfraConnectionTest 这一个测试文件

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

5. AOP 的理解：切面(Aspect), 连接点(JoinPoint), 切入点(Pointcut)，通知(Advice)。其中 Advice 覆盖五种注解也是五种执行时机：@Before、@AfterReturning、@AfterThrowing、@After、@Around。

# 03-product-db.md

1. 早期 VERSION 乐观锁（已弃用，代码现改为 CAS）
    我感觉不会发生数据冲突的情况，因此大家都可以先 select 拿到版本号 v1。再 update 的时候在校验是否还是 v1。是就正常执行，不是就失败。我添加了 bench-optimistic.mjs 并发测试文件。大概是一件商品 30 个库存，100 个人并发争抢。结果大概是会有 10-11 个人成功，89-90 个人并发失败，此时库存没有消耗干净。这个数量我分析了一下是因为连接池大概是 10 个。每次十个算一批，因此成功率是 10%。加入重试机制之后库存都消耗成功，但是他消耗了更多的请求次数和整体耗时。
2. CAS 乐观锁 
    这是典型的适合去库存的场景，不再用 version 来判断是否并发请求了，而是改用库存数量。这样只要有库存即使并发了也不要紧，只要库存充足就会成功。是测试下来最理想的情况
3. 悲观锁 
    他跟乐观锁的区别是，在 select 拿到库存行并加锁的这一步就阻止了另外一个人再继续执行这个（其余事务在行锁上排队），因此他自始至终到 update 都会成功。劣势就是不能够并行 select。因此耗时会增加不少
4. InnoDB：这种数据引擎支持事务、行级锁、聚族索引、崩溃恢复。老的索引 MyISAM 不支持事务，是表级锁。
5. MVCC：多版本并发控制(Multi-Version Concurrency Control)。实现了读写不阻塞的功能，例如我查询 select1，然后在执行 update select2。我会将 select1 保存的 undo log 中。这个时候别人在读的时候就可以从 undo log 读取到 select1 的版本。我 update select2 在新的版本中就不会阻塞
6. 聚族索引：老的索引方式是索引单独一个文件，内容单独一个文件。找到索引文件了还需要再去寻找内容，时间缓慢。聚族索引是索引即数据，找到索引就找到了数据所以快很多
7. InnoDB 索引 B + 树：这种类似的还有 B 树，二叉树，平衡二叉树。然后呢俩种二叉树由于层级太多，导致 IO 太多，而数据库的瓶颈就是 IO 查询导致的。因此这俩种都不适合做数据库引擎。而 B+ 树比 B 树牛的地方有俩点：1 B+ 树非叶子节点不存储数据，这就可以让一页数据存储更多的节点减少 IO 数量。2 呢就是叶子之间有关联关系再做 between and 这种情况可以直接查询，而不需要从头遍历。
8. 查询语句前面添加 EXPLAN，不执行具体 SQL，而是进行分析，例如 type = xx。来看有没有走索引，ref 的索引是否正确，以及多表情况分析哪个表先查等等。还可以锁定 rows 的行数
9. 间隙锁、临时锁还是没太搞明白。TODO

# 04-cache.md

1. 击穿：缓存失效，大量并发请求直接绕过 redis 访问 db。可以让他先访问老的数据，然后异步刷新 DB
2. 穿透：id 不存在，直接绕过 redis 访问 db。这里采用了布隆过滤器来判断 id 是否存在的问题。有几个细节需要注意一下，第一个是目前获取了所有数据然后把 id 放进去进行的判断，这个商品多了会导致内存爆炸，正确的做法是需要设置滚动分页慢慢加入。
3. 雪崩：大量缓存失效，直接访问 db。通过 TTL 不同的时间 + 集群来处理
4. Redis 的使用：提供了一种高效的数据读取，读 db 要过硬盘太慢，redis 直接内存非常的快。有七种数据类型分布对应不同的场景：string：验证码，token，登录会话；hash：用户信息，商品信息；list：日志，消息队列；set：签到，黑名单；zset：热榜，排名，bitmap：海量数据签到，geo：附近门店，附近的人。了解到的扩展玩法有通过 nx ex 进行分布式锁处理。以及不同的恢复方案：RDB: 快照，缺点是每次快照间隔时间会丢数据；AOF：写日志，可以设置写日志频率，最终通过日志恢复数据，有每秒、每次等多种策略

# 05-concurrency.md

1. 幂等：类比纯函数，同一个操作调用，不管是执行一次还是 N 次，得到的结果总是一致的。使用的场景有网络波动导致的接口重复请求，一个订单下单多次等等
2. 状态机：一个事务，在任意时刻只能处于某一个状态，接收到事件之后按照固定的规则，从一个状态切换到另一个状态。这里写代码需要注意不要写 if else 进行判断。要根据状态表判断。例如 MAP 结构
3. AtomicInteger: 解决多线程原子化操作问题
4. 锁：这里提到了 synchronized、AtomicInteger、reentrantlock 。跟前面数据库的悲观锁、乐观锁相似。还需要理解 volatile 的一些用法（不管原子化，只管更新。count++ 还是存在竞态问题状态的）