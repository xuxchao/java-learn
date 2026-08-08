package com.example.ecommerce.mq;

import com.example.ecommerce.idempotency.IdempotencyService;
import com.example.ecommerce.idempotency.InMemoryIdempotencyStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OrderEventConsumer 单元测试（不连任何基础设施，用 M5 的 InMemoryIdempotencyStore）。
 *
 * <p>核心断言：MQ 是 at-least-once 投递，同一 orderNo 的重复消息必然发生，
 * 但下游副作用（downstream.process）只应被真正执行一次——这是"不重"的保证。
 */
class OrderEventConsumerTest {

    @Test
    void duplicate_delivery_processes_downstream_only_once() {
        AtomicInteger processCount = new AtomicInteger();
        OrderDownstreamProcessor downstream = event -> {
            processCount.incrementAndGet();
            return "ok:" + event.getOrderNo();
        };
        IdempotencyService idem = new IdempotencyService(new InMemoryIdempotencyStore());
        OrderEventConsumer consumer = new OrderEventConsumer(idem, downstream);

        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD123", 7L, 1L, 2, new BigDecimal("20.00"), LocalDateTime.now());

        consumer.onOrderCreated(event); // 首次
        consumer.onOrderCreated(event); // 重复投递
        consumer.onOrderCreated(event); // 再重复投递

        assertEquals(1, processCount.get(), "同一 orderNo 的重复投递只应处理一次");
    }

    @Test
    void different_orders_are_each_processed() {
        AtomicInteger processCount = new AtomicInteger();
        OrderDownstreamProcessor downstream = event -> {
            processCount.incrementAndGet();
            return "ok";
        };
        IdempotencyService idem = new IdempotencyService(new InMemoryIdempotencyStore());
        OrderEventConsumer consumer = new OrderEventConsumer(idem, downstream);

        consumer.onOrderCreated(new OrderCreatedEvent("ORD-A", 7L, 1L, 1, new BigDecimal("9.9"), LocalDateTime.now()));
        consumer.onOrderCreated(new OrderCreatedEvent("ORD-B", 7L, 1L, 1, new BigDecimal("9.9"), LocalDateTime.now()));
        consumer.onOrderCreated(new OrderCreatedEvent("ORD-A", 7L, 1L, 1, new BigDecimal("9.9"), LocalDateTime.now())); // 重放 A

        assertEquals(2, processCount.get(), "不同订单各自处理一次，A 的重复投递被去重");
    }
}
