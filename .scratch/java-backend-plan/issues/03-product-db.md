# 03 — 商品 & 数据库持久化

**What to build:** 商品与库存的持久化能力：合理的表设计、MyBatis-Plus 的增删改查，以及"下单即扣库存"在数据库事务中正确完成，并用 explain 验证索引是否命中。

**Blocked by:** 01 — 工程脚手架 & 本地基建.

**Status:** ready-for-agent

- [ ] 商品表 + 库存表设计合理，MyBatis-Plus CRUD 跑通
- [ ] 下单扣库存在事务中完成，隔离级别与锁使用正确（乐观锁版本号 / 悲观锁 for update）
- [ ] 故意制造索引失效并用 explain 验证、再修复
- [ ] 能讲清 InnoDB 索引 B+ 树、MVCC、间隙锁、乐观锁 vs 悲观锁
