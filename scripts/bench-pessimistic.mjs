#!/usr/bin/env node
/**
 * 悲观锁抢购压测：30 件库存，100 个用户同时开抢。
 *
 * 用法：
 *   node scripts/bench-pessimistic.mjs
 *   node scripts/bench-pessimistic.mjs --stock 30 --users 100
 *   node scripts/bench-pessimistic.mjs --base http://127.0.0.1:9090
 *   node scripts/bench-pessimistic.mjs --keep        # 压完保留商品，便于手工查库存
 *
 * 前置：docker compose up -d && cd java-learn-app && mvn spring-boot:run
 * 依赖：Node >= 18（内置 fetch），零 npm 依赖。
 *
 * 走的是 OrderService.placeOrderPessimistic：
 *   SELECT ... FOR UPDATE 在事务内锁住库存行，直到事务提交才释放。
 * 同一时刻只有一个事务能持有该行的写锁，其余请求在 InnoDB 里排队等待，
 * 所以：不会出现 3005 冲突、库存一定被抢空，但单请求耗时被排队拉长。
 *
 * 注意 innodb_lock_wait_timeout 默认 50s：并发极高或事务里有慢操作时，
 * 排在后面的事务可能等锁超时（返回 5xx 而非 3004/3005）。
 */

import { runLockBench, gray, bold } from './lib/bench-core.mjs';

await runLockBench({
  lockType: 'PESSIMISTIC',
  title: '悲观锁抢购压测（SELECT ... FOR UPDATE）',
  note: ({ okCount, wallCost, okStats, opt }) => {
    console.log(bold('结论：'));
    console.log(gray('  悲观锁先加行锁再改，同一行上的事务被 InnoDB 强制串行化，因此'));
    console.log(gray('  零冲突、库存必被抢空，代价是请求在锁上排队，P99 明显被拉高。'));
    if (okStats) {
      console.log(
        gray(
          `  成功单的 min=${okStats.min.toFixed(1)}ms 与 max=${okStats.max.toFixed(1)}ms 差距，` +
            '基本就是排队深度的体现——先抢到锁的快，后面的一路等。',
        ),
      );
    }
    console.log(
      gray(
        `  ${okCount} 单成功耗时 ${wallCost.toFixed(0)}ms，即临界区串行吞吐约 ` +
          `${((okCount / wallCost) * 1000).toFixed(1)} 单/秒。`,
      ),
    );
    console.log(
      gray('  另注：应用未配置 Hikari 连接池大小（默认 10），100 并发里同时只有 10 个请求能拿到 DB 连接，'),
    );
    console.log(gray('  所以这里测到的排队 = 连接池排队 + 行锁排队的叠加。'));
    console.log(gray('  对照组：node scripts/bench-optimistic.mjs'));
  },
});
