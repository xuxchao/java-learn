package com.example.ecommerce.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 订单事件发布者（M6 生产者）。在下单接口里、订单落库之后调用，
 * 把 {@link OrderCreatedEvent} 发到 MQ，让下游异步处理（削峰 + 解耦）。
 *
 * <p>"不丢消息" 的两处保障：
 * <ul>
 *   <li>{@code setConfirmCallback}：broker 收到消息并落盘后回调 ack；网络/交换机异常则 nack，可感知丢失。</li>
 *   <li>{@code setReturnsCallback} + {@code setMandatory(true)}：消息无法路由到任何队列时被退回，避免静默丢失。</li>
 * </ul>
 * 注意：以上只是"生产端不丢"。端到端不丢还需要消费者手动 ack（处理成功才确认）+ 事务消息/本地消息表
 * 兜底（见 docs/mq-notes.md）。本站点的发布发生在订单事务提交之后，存在极小的"已落库但未发出"窗口，
 * 生产可用「本地消息表 / 事务消息」彻底消除。
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 无法路由到队列时退回（必须配合 mandatory=true 才生效）
        this.rabbitTemplate.setMandatory(true);
        // 不丢：broker 确认回调
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("[MQ] 消息已确认到达交换机: {}", correlationData);
            } else {
                log.warn("[MQ] 消息未到达交换机: {}, 原因: {}", correlationData, cause);
            }
        });
        // 不丢：消息被退回回调
        this.rabbitTemplate.setReturnsCallback(returned -> log.warn("[MQ] 消息无法路由被退回: {}", returned));
    }

    public void publish(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(OrderMqConfig.ORDER_EXCHANGE, OrderMqConfig.ORDER_ROUTING_KEY, event);
        log.info("[MQ] 已发布下单事件: orderNo={}", event.getOrderNo());
    }
}
