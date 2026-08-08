package com.example.ecommerce.mq;

import com.example.ecommerce.idempotency.IdempotencyResult;
import com.example.ecommerce.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * 订单事件消费者（M6 下游）。异步处理下单成功事件。
 *
 * <p><b>幂等（不重）</b>：以 {@code orderNo} 作为幂等键交给 M5 的 {@link IdempotencyService}
 * （底层是 Redis 的 {@code SET NX}）。MQ 是 at-least-once 语义——网络重投、消费者重启前的
 * 未 ack 消息都会重复到达，但同一 orderNo 只会被真正处理一次；重放直接返回首次缓存的结果，
 * 从而在不依赖"恰好一次"投递的前提下实现 effectively-once（不重复扣减 / 不重复通知）。
 *
 * <p>配合生产端 publisher-confirm + 消费者 ack，整条链路满足面试常考的"不丢 / 不重"两问。
 */
@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final IdempotencyService idempotencyService;
    private final OrderDownstreamProcessor downstream;

    public OrderEventConsumer(IdempotencyService idempotencyService, OrderDownstreamProcessor downstream) {
        this.idempotencyService = idempotencyService;
        this.downstream = downstream;
    }

    @RabbitListener(queues = OrderMqConfig.ORDER_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[MQ] 收到下单事件: orderNo={}", event.getOrderNo());
        IdempotencyResult<String> result = idempotencyService.execute(
                event.getOrderNo(), String.class, () -> downstream.process(event));
        if (result.isReplayed()) {
            log.info("[MQ] 重复投递已去重（不重复处理）: orderNo={}", event.getOrderNo());
        } else {
            log.info("[MQ] 首次处理完成: orderNo={}, result={}", event.getOrderNo(), result.getValue());
        }
    }
}
