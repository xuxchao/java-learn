package com.example.ecommerce.concurrency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5 并发实验室：用 100 个真实线程并发扣库存，验证三种方案都"不超卖、不丢更新"。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>发令枪</b>：所有线程先 {@code await} 同一个 start 闸门，再一次性放行，
 *       确保请求"真同时"发起，而不是先后排队（否则并发度被串行化，测不出冲突）。</li>
 *   <li><b>两组场景</b>：①库存=线程数（应全部成功，终态归零）②库存&lt;线程数（应只成功库存数，绝不为负）。</li>
 *   <li><b>断言</b>只校验"结果正确性"，耗时对比仅打印、不进断言，避免 CI 抖动。</li>
 * </ul>
 */
class StockDeductionConcurrencyTest {

    private static final int THREADS = 100;

    /** 场景①：库存充足（=线程数）——三种方案都应 100 成功、终态归零、无丢失更新。 */
    @Test
    void no_oversell_and_no_lost_update_when_stock_equals_threads() throws Exception {
        assertAllStrategies(d -> {
            int success = stress(d, THREADS, THREADS, 1);
            assertEquals(THREADS, success, "应全部成功");
            assertEquals(0, d.getAvailable(), "扣完后剩余应为 0，绝不出现负数（超卖）");
            assertEquals(THREADS, d.getTotalDeducted(), "已扣总量应等于初始库存（无丢失更新）");
        });
    }

    /** 场景②：库存远小于线程数——只应有 stock 次成功，且剩余库存永不为负（不超卖）。 */
    @Test
    void no_oversell_when_stock_less_than_threads() throws Exception {
        int stock = 10;
        assertAllStrategies(d -> {
            int success = stress(d, THREADS, stock, 1);
            assertEquals(stock, success, "应只有库存数（10）次成功");
            assertEquals(0, d.getAvailable(), "扣完后剩余应为 0，绝不出现负数（超卖）");
            assertEquals(stock, d.getTotalDeducted(), "已扣总量应等于初始库存（无丢失更新）");
        });
    }

    /**
     * 对三种方案各跑一遍同一段逻辑，给出可读的失败信息。
     */
    private void assertAllStrategies(StrategyRunner runner) throws Exception {
        runWith("synchronized", new SynchronizedStockDeductor(0), runner);
        runWith("reentrantLock", new ReentrantLockStockDeductor(0), runner);
        runWith("cas(AtomicInteger)", new CasStockDeductor(0), runner);
    }

    private void runWith(String name, StockDeductor d, StrategyRunner runner) throws Exception {
        long t0 = System.nanoTime();
        runner.run(d);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        System.out.printf("[对比] %-20s 完成, 耗时=%dms%n", name, ms);
    }

    /**
     * 发令枪式并发压测：threads 个线程同时各调 deduct(perThread) 一次，返回成功次数。
     */
    private int stress(StockDeductor deductor, int threads, int initialStock, int perThread) throws Exception {
        deductor.reset(initialStock);
        // 手写 ThreadPoolExecutor + 有界队列（避免 Executors.newFixedThreadPool 的无界队列反模式，
        // 也契合 docs/concurrency-notes.md 第 3 节的线程池教学）。
        ExecutorService pool = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads));
        CountDownLatch startGate = new CountDownLatch(1);   // 发令枪：拦住所有线程
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();                 // 全部在此等待
                    if (deductor.deduct(perThread)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();                          // 一声令下，全员同时放行
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertTrue(finished, "压测未在 10s 内完成（疑似死锁）");
        return successCount.get();
    }

    @FunctionalInterface
    private interface StrategyRunner {
        void run(StockDeductor d) throws Exception;
    }
}
