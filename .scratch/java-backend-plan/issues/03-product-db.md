# 03 — 商品 & 数据库持久化

**What to build:** 商品与库存的持久化能力：合理的表设计、MyBatis-Plus 的增删改查，以及"下单即扣库存"在数据库事务中正确完成，并用 explain 验证索引是否命中。

**Blocked by:** 01 — 工程脚手架 & 本地基建.

**Status:** resolved

- [x] 商品表 + 库存表设计合理，MyBatis-Plus CRUD 跑通
- [x] 下单扣库存在事务中完成，隔离级别与锁使用正确（乐观锁版本号 / 悲观锁 for update）
- [x] 故意制造索引失效并用 explain 验证、再修复
- [x] 能讲清 InnoDB 索引 B+ 树、MVCC、间隙锁、乐观锁 vs 悲观锁

## 交付内容

### 表结构（schema.sql，IF NOT EXISTS 幂等）
- `products`：id / name（二级索引 `idx_products_name`，用于 EXPLAIN 验证）/ price / description / 时间戳
- `stock`：id / product_id（唯一）/ total / available / **version（乐观锁）/ 时间戳
- `orders`：id / order_no（唯一，后续做幂等键）/ user_id / product_id / quantity / amount / status

### 代码（java-learn-app）
- 实体：`model/Product`、`model/Stock`（`@Version` 乐观锁）、`model/Order`（均 `@TableName` 映射）
- Mapper：`mapper/ProductMapper`、`StockMapper`（额外 `selectByProductId` / `selectByProductIdForUpdate`）、
  `OrderMapper`，全部继承 `BaseMapper`，零 XML
- 配置：`config/MybatisPlusConfig`（@MapperScan + `OptimisticLockerInnerInterceptor` 乐观锁拦截器）
- 业务：`service/ProductService`（CRUD + 库存初始化）、`service/OrderService`
  （`placeOrder`，默认乐观锁，可传 `lockType=PESSIMISTIC` 走 `FOR UPDATE` 悲观锁，全程 `@Transactional`）
- 接口：`controller/ProductController`（`/products` CRUD + `/{id}/stock` + `/{id}/order`），受登录拦截器保护
- 错误码：`ErrorCode` 新增 3xxx 段（PRODUCT_NOT_FOUND / STOCK_NOT_ENOUGH / STOCK_CONFLICT / …）

### 重要踩坑（已解决）
- **mybatis-spring 兼容性**：`mybatis-plus-boot-starter` 的 BOM 把 `mybatis-spring` 锁在 2.1.2（SB2 线），
  与 Spring Boot 3 的 Spring 6.1 冲突，导致上下文起不来（`factoryBeanObjectType` 类型错误）。
  已在 app pom 显式覆盖 `mybatis-spring:3.0.4`（SB3 线）修复。

## 验收要点（自测）

1. **建表 + CRUD**：`docker compose up -d mysql` 后起应用，POST /products 建商品 → POST /products/{id}/stock 备货
   → GET /products/{id} 查回；MyBatis-Plus 通用方法已跑通。
2. **下单扣库存（两路锁）**：POST /products/{id}/order 默认乐观锁，库存 available 减少、version 自增；
   传 `{"lockType":"PESSIMISTIC"}` 走 `SELECT ... FOR UPDATE` 行锁，同样正确扣减。
3. **EXPLAIN 索引验证**（见 `ProductDbIntegrationTest.explain_index_used_for_exact_match_but_not_for_function`）：
   - 好查询 `WHERE name = 'x'` → `key = idx_products_name`（走索引）
   - 坏查询 `WHERE LEFT(name,1) = 'i'`（对列套函数）→ `key = NULL`、`type = ALL`（索引失效）
   - 修复方式：避免在索引列上做函数运算，或建函数索引 / 改写为前缀匹配。
4. **事务正确性**：下单逻辑在 `@Transactional` 内完成「查库存 → 扣减 → 插订单」，乐观锁冲突时 `updateById`
   命中 0 行抛 `STOCK_CONFLICT`，保证不超卖、不丢更新。

## 面试八股自测（要能口述）
- InnoDB 索引是 **B+ 树**（矮胖、叶子链表、聚簇索引即主键序），回表、覆盖索引、最左前缀。
- **MVCC**： undo log + 隐藏事务 id/回滚指针 + ReadView，实现快照读、可重复读不阻塞读写。
- **间隙锁 / Next-Key Lock**：RR 下防幻读，锁住记录+间隙；这也是为什么 `FOR UPDATE` 能挡并发插入。
- **乐观锁 vs 悲观锁**：乐观锁（version/ CAS）适合冲突少、高并发读，冲突时重试；悲观锁（`FOR UPDATE`）
  适合冲突多、强一致，但锁等待降低吞吐。两者都建立在事务隔离级别之上。

