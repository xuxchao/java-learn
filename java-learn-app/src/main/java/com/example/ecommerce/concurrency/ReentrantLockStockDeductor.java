package com.example.ecommerce.concurrency;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 方案 B：{@code ReentrantLock} 显式锁。
 *
 * <p>与 {@code synchronized} 等价的基本互斥能力，但更灵活：可 {@code tryLock(timeout)} 避免死等、
 * 可设公平锁（按排队顺序放行进来的线程）、可绑定多个 {@code Condition} 做精细化等待/唤醒。
 * 本实现只演示最朴素的 {@code lock()/unlock()} 互斥，效果同 synchronized，但为后续扩展（超时/公平）留了口子。
 *
 * <p><b>易错点</b>：必须放在 {@code finally} 里 {@code unlock()}，否则异常时会永久死锁——
 * 这正是它比 synchronized（自动释放）更容易出错的地方。
 */
public class ReentrantLockStockDeductor implements StockDeductor {

    private final ReentrantLock lock = new ReentrantLock();
    private int available;
    private int totalDeducted;

    public ReentrantLockStockDeductor(int initial) {
        this.available = initial;
        this.totalDeducted = 0;
    }

    @Override
    public boolean deduct(int qty) {
        lock.lock();
        try {
            if (available < qty) {
                return false;
            }
            available -= qty;
            totalDeducted += qty;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getAvailable() {
        lock.lock();
        try {
            return available;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getTotalDeducted() {
        lock.lock();
        try {
            return totalDeducted;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void reset(int initial) {
        lock.lock();
        try {
            this.available = initial;
            this.totalDeducted = 0;
        } finally {
            lock.unlock();
        }
    }
}
