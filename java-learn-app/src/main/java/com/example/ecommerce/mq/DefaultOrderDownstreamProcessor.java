package com.example.ecommerce.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 订单下游处理真实实现（M6 消费者业务逻辑）。
 *
 * <p>模拟"下单成功后异步要做的副作用"：发送订单通知（短信 / 站内信 / 积分等）。
 * 该副作用会被 {@link OrderEventConsumer} 用 orderNo 做幂等去重，因此即使 MQ 重复投递，
 * 这里也只会真正执行一次——不会重复发通知。
 *
 * <p>设计取舍（避免与领域模型冲突）：<b>这里不写 {@code orders.status}</b>。订单状态机是
 * {@code CREATED → PAID → …}（M7 支付回调推进），消费者若擅自改成 {@code PROCESSED} 会与 M7 撞车。
 * 真正的"不重复扣减"由 M3 下单事务保证（库存已在那时扣掉，消费者绝不重复扣），本类的副作用聚焦在
 * "通知 / 下游推进"这类可幂重入的动作上。
 */
@Service
public class DefaultOrderDownstreamProcessor implements OrderDownstreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrderDownstreamProcessor.class);

    @Override
    public String process(OrderCreatedEvent event) {
        // 真实系统里这里可能是发短信 / 推站内信 / 加积分；此处以日志代表"已发送通知"
        log.info("[MQ] 已发送订单通知 orderNo={}, userId={}, productId={}, qty={}",
                event.getOrderNo(), event.getUserId(), event.getProductId(), event.getQuantity());
        return "notified:" + event.getOrderNo();
    }
}
