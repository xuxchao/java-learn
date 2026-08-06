package com.example.ecommerce.concurrency;

/**
 * 库存扣减器抽象：屏蔽"用哪种并发控制手段"的差异，让同一套压测逻辑能套在三种方案上。
 *
 * <p>三个实现对应三种并发控制思想（M5 主线）：
 * <ul>
 *   <li>{@link SynchronizedStockDeductor} —— 关键字 {@code synchronized}（JVM 监视器锁，阻塞）</li>
 *   <li>{@link ReentrantLockStockDeductor} —— {@code ReentrantLock}（API 级显式锁，可公平/超时/多条件）</li>
 *   <li>{@link CasStockDeductor} —— {@code AtomicInteger} 的 CAS 自旋（乐观、无锁，JVM 层对应 M3 的 DB CAS）</li>
 * </ul>
 *
 * <p>注意区别：本包是 <b>JVM 进程内</b> 的库存计数器，演示"单实例内多线程如何安全地扣减"；
 * 而 M3 下单走的是 <b>数据库</b> 层的 CAS（{@code UPDATE ... WHERE available >= ?}），解决"跨请求/多实例"
 * 的并发。两者思想同源（乐观 vs 悲观），落点不同（内存 vs 磁盘），M5 重点是把 JVM 这套讲透。
 */
public interface StockDeductor {

    /** 尝试扣减 qty 件库存。成功返回 true；库存不足（或并发下被抢光）返回 false。 */
    boolean deduct(int qty);

    /** 当前剩余可用库存。 */
    int getAvailable();

    /** 累计已成功扣减的总量（用于校验"无丢失更新"）。 */
    int getTotalDeducted();

    /** 重置为 initial 件库存，便于一个测试里反复跑多组场景。 */
    void reset(int initial);
}
