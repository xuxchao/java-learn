# Redis API 范例与讲解

> 建设中 —— 本文件由后续 ticket 填充具体内容（概念讲解 + 双栏静态片段 + 实跑真实输出）。

## 覆盖范围

- 5 种基础结构：`String` / `Hash` / `List` / `Set` / `ZSet`
- `Pub/Sub` 发布订阅
- `Streams`：XADD / XREAD / XREADGROUP 消费组
- 事务：`MULTI` / `EXEC` 与 `WATCH` 乐观锁
- `Lua` 脚本（`EVAL`）原子操作
- `Pipeline` 批量提交

## 运行

```bash
cd middleware-examples
sh ../.mvnlocal.sh spring-boot:run -Dspring-boot.run.profiles=redis
# 单跑： -Dexample=string
```

详细内容见后续 ticket（02-05）。
