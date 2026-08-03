package com.example.ecommerce.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（M3）。对应 {@code orders} 表。"下单即扣库存"会在一个事务里写入一条订单并扣减库存。
 *
 * <p>order_no 业务幂等键（唯一索引），便于后续 M6/M7 用唯一约束做幂等防重。
 * status 状态机：CREATED → PAID → ...（M7 支付回调会推进状态）。
 */
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private BigDecimal amount;

    private String status;

    @TableField(value = "created_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;

    public Order() {
    }

    public Order(String orderNo, Long userId, Long productId, Integer quantity, BigDecimal amount, String status) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
