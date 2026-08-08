package com.example.ecommerce.mq;

import com.example.ecommerce.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单成功事件（M6 消息体）。订单在 DB 落库后由生产者发到 MQ，下游消费者异步处理。
 *
 * <p>字段与 {@code orders} 表的关键列对应，便于消费者在本地对账 / 推进状态机。
 * 保留无参构造器是 Jackson 反序列化（{@code Jackson2JsonMessageConverter}）所必需的。
 */
public class OrderCreatedEvent {

    private String orderNo;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(String orderNo, Long userId, Long productId,
                             Integer quantity, BigDecimal amount, LocalDateTime createdAt) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 从订单实体构造事件（避免 controller 里逐个取字段拼事件，收口映射逻辑）。 */
    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(
                order.getOrderNo(), order.getUserId(), order.getProductId(),
                order.getQuantity(), order.getAmount(), order.getCreatedAt());
    }
}
