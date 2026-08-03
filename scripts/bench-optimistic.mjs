#!/usr/bin/env node
/**
 * 乐观锁抢购压测：30 件库存，100 个用户同时开抢。
 *
 * 用法：
 *   node scripts/bench-optimistic.mjs
 *   node scripts/bench-optimistic.mjs --stock 30 --users 100
 *   node scripts/bench-optimistic.mjs --retry 5      # 客户端遇 3005 最多重试 5 次
 *   node scripts/bench-optimistic.mjs --base http://127.0.0.1:9090
 *   node scripts/bench-optimistic.mjs --keep         # 压完保留商品，便于手工查库存
 *
 * 前置：docker compose up -d && cd java-learn-app && mvn spring-boot:run
 * 依赖：Node >= 18（内置 fetch），零 npm 依赖。
 *
 * 走的是 OrderService.placeOrderOptimistic：
 *   SELECT ... → 内存里减库存 → UPDATE ... WHERE id=? AND version=?
 * 两次读写之间该行被别人改过，UPDATE 命中 0 行 → 抛 3005 STOCK_CONFLICT。
 * 服务端不重试，所以默认跑法下大量请求会以 3005 失败——这不是 bug，
 * 而是乐观锁的固有特征：冲突检测交给数据库，冲突处理交给调用方。
 */

import { runLockBench, gray, yellow, bold } from './lib/bench-core.mjs';

await runLockBench({
  lockType: 'OPTIMISTIC',
  title: '乐观锁抢购压测（version 版本号）',
  note: ({ okCount, conflict, opt }) => {
    console.log(bold('结论：'));
    console.log(
      gray('  乐观锁不加锁，冲突时靠 UPDATE ... WHERE version=? 命中 0 行来发现，因此'),
    );
    console.log(gray('  单请求很快（没有等锁），但高并发下冲突率高、成功率低。'));

    if (conflict > 0 && opt.retry === 0) {
      const lost = opt.stock - okCount * opt.qty;
      if (lost > 0) {
        console.log(
          yellow(
            `  注意：库存还剩 ${lost} 件没卖出去，${conflict} 个用户被 3005 直接打回。`,
          ),
        );
        console.log(
          yellow('  这就是不重试的代价。加 --retry 5 再跑一次，观察能否把库存抢空、以及耗时涨多少。'),
        );
      }
    }
    if (opt.retry > 0) {
      console.log(
        gray('  带重试后成功率会明显上升，但总请求数被放大，服务端实际压力大于并发数。'),
      );
    }
    console.log(gray('  对照组：node scripts/bench-pessimistic.mjs'));
  },
});
