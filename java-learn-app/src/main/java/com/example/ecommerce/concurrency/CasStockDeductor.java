package com.example.ecommerce.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方案 C：基于 {@code AtomicInteger} 的 CAS 自旋（乐观、无锁）。
 *
 * <p>这是 JVM 层面的"乐观锁"，与 M3 下单里 {@code UPDATE stock SET available = available - ?
 * WHERE id=? AND available >= ?} 的 DB CAS 思想一模一样：不阻塞，而是"先读旧值，再原子地比较并写入新值；
 * 若期间被别人改过（旧值对不上）就重试"。
 *
 * <p>本实现用 {@code compareAndSet} 循环扣减可用量；用另一个 {@code AtomicInteger} 累加"已扣总量"
 * 以保证计数器本身也不丢更新（{@code addAndGet} 是原子操作）。
 *
 * <p>高争用下 CAS 会自旋重试（CPU 空转），但无上下文切换；争用低时通常比锁更快。
 * JVM CAS <b>只在单实例内有效</b>，跨实例要靠 Redis/DB 的 CAS（见 M3/M8）。
 */
public class CasStockDeductor implements StockDeductor {

    private final AtomicInteger available;
    private final AtomicInteger totalDeducted = new AtomicInteger(0);

    public CasStockDeductor(int initial) {
        this.available = new AtomicInteger(initial);
    }

    @Override
    public boolean deduct(int qty) {
        int cur;
        do {
            cur = available.get();
            if (cur < qty) {
                return false; // 库存不足，直接失败（不再自旋，避免无谓空转）
            }
            // 原子：若 available 仍是 cur，则改成 cur - qty；否则循环重试读新值
        } while (!available.compareAndSet(cur, cur - qty));
        totalDeducted.addAndGet(qty);
        return true;
    }

    @Override
    public int getAvailable() {
        return available.get();
    }

    @Override
    public int getTotalDeducted() {
        return totalDeducted.get();
    }

    @Override
    public void reset(int initial) {
        available.set(initial);
        totalDeducted.set(0);
    }
}
