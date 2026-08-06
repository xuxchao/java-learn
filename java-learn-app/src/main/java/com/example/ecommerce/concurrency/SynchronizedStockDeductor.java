package com.example.ecommerce.concurrency;

/**
 * 方案 A：{@code synchronized} 方法锁（JVM 监视器锁 / 管程）。
 *
 * <p>最简单的内置锁：整个 {@link #deduct} 方法是临界区，同一时刻只有一个线程能进入，
 * 天然保证"读-改-写"原子。代价：线程阻塞（操作系统层面挂起/唤醒），高争用下上下文切换开销大。
 * 适用：单实例、争用不极端、求简单可靠的场景。
 *
 * <p>读方法也加 {@code synchronized} 是为了让压测线程能安全读到一致的中间态（虽然测试主要看终态）。
 */
public class SynchronizedStockDeductor implements StockDeductor {

    private int available;
    private int totalDeducted;

    public SynchronizedStockDeductor(int initial) {
        this.available = initial;
        this.totalDeducted = 0;
    }

    @Override
    public synchronized boolean deduct(int qty) {
        if (available < qty) {
            return false;
        }
        available -= qty;
        totalDeducted += qty;
        return true;
    }

    @Override
    public synchronized int getAvailable() {
        return available;
    }

    @Override
    public synchronized int getTotalDeducted() {
        return totalDeducted;
    }

    @Override
    public synchronized void reset(int initial) {
        this.available = initial;
        this.totalDeducted = 0;
    }
}
