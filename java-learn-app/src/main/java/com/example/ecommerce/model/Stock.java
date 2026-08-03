package com.example.ecommerce.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 库存实体（M3）。对应 {@code stock} 表，与商品一对一（product_id 唯一）。
 *
 * <p>扣库存采用 CAS（Compare-And-Swap）乐观锁：下单时在单条 SQL 中
 * "比较 available &gt;= 数量 并原子扣减"（{@code UPDATE stock SET available = available - ? WHERE id = ? AND available &gt;= ?}），
 * 不依赖任何版本号字段，从根本上消除了"先读后写"的丢失更新窗口。
 *
 * <p>这与早期基于 {@code @Version} 版本号的乐观锁不同——CAS 直接对业务值做原子 compare-and-set，
 * 并发冲突率显著更低（两条并发请求各自独立原子扣减，互不覆盖），且无需重试放大请求量。
 */
@TableName("stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Integer total;

    private Integer available;

    @TableField(value = "created_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;

    public Stock() {
    }

    public Stock(Long productId, Integer total, Integer available) {
        this.productId = productId;
        this.total = total;
        this.available = available;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getAvailable() {
        return available;
    }

    public void setAvailable(Integer available) {
        this.available = available;
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
