package com.example.ecommerce.idempotency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IdempotencyService 纯单元测试（无 Spring、无 Redis）。用 {@link InMemoryIdempotencyStore}
 * 提供存储，重点验证 token 方案的核心不变量：<b>相同 key 只让动作执行一次，重放不重执行</b>。
 */
class IdempotencyServiceTest {

    /** 首次执行：动作跑一次，replayed=false，返回动作结果。 */
    @Test
    void first_call_executes_action_and_returns_result() {
        IdempotencyService svc = new IdempotencyService(new InMemoryIdempotencyStore());
        AtomicInteger calls = new AtomicInteger(0);

        IdempotencyResult<String> r = svc.execute("k1", String.class, () -> {
            calls.incrementAndGet();
            return "result-1";
        });

        assertEquals("result-1", r.getValue());
        assertFalse(r.isReplayed());
        assertEquals(1, calls.get(), "动作应只执行一次");
    }

    /** 重放（相同 key 第二次）：动作不再执行，返回首次的结果，replayed=true。 */
    @Test
    void replay_does_not_execute_action_again() {
        IdempotencyService svc = new IdempotencyService(new InMemoryIdempotencyStore());
        AtomicInteger calls = new AtomicInteger(0);

        IdempotencyResult<String> first = svc.execute("k1", String.class, () -> {
            calls.incrementAndGet();
            return "result-1";
        });
        IdempotencyResult<String> second = svc.execute("k1", String.class, () -> {
            calls.incrementAndGet();
            return "result-2-WRONG";
        });

        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed(), "第二次应是重放");
        assertEquals("result-1", second.getValue(), "重放必须返回首次结果，而非重新执行的新结果");
        assertEquals(1, calls.get(), "动作只能执行一次，重放不得再次触发副作用");
    }

    /** 不同 key 视为不同请求，各自独立执行。 */
    @Test
    void different_keys_execute_independently() {
        IdempotencyService svc = new IdempotencyService(new InMemoryIdempotencyStore());
        AtomicInteger calls = new AtomicInteger(0);

        svc.execute("a", String.class, () -> { calls.incrementAndGet(); return "A"; });
        svc.execute("b", String.class, () -> { calls.incrementAndGet(); return "B"; });

        assertEquals(2, calls.get(), "不同 key 各自执行一次");
    }

    /** TTL 过期后，幂等窗口结束；同一个 key 会被当成新请求重新执行。 */
    @Test
    void expired_key_can_execute_again() throws InterruptedException {
        // TTL=1 秒，便于快速观察过期
        IdempotencyService svc = new IdempotencyService(new InMemoryIdempotencyStore(), 1);
        AtomicInteger calls = new AtomicInteger(0);

        svc.execute("k1", String.class, () -> { calls.incrementAndGet(); return "v1"; });
        Thread.sleep(1200); // 等过期
        IdempotencyResult<String> again = svc.execute("k1", String.class, () -> { calls.incrementAndGet(); return "v2"; });

        assertFalse(again.isReplayed(), "TTL 过期后，应把它当成新请求而不是重放");
        assertEquals("v2", again.getValue(), "TTL 过期后，结果应来自新一轮执行");
        assertEquals(2, calls.get(), "TTL 过期后动作应重新执行");
    }
}
