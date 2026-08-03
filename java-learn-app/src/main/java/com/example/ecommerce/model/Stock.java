package com.example.ecommerce.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * 库存实体（M3）。对应 {@code stock} 表，与商品一对一（product_id 唯一）。
 *
 * <p>{@link Version} 标注的 {@code version} 字段是乐观锁核心：MyBatis-Plus 在执行 update 时，
 * 会自动把 {@code version} 拼进 WHERE 条件并自增。并发场景下若两事务同时改同一行，
 * 后提交的事务因版本号已变导致 WHERE 命中 0 行，从而避免"丢失更新"。
 *
 * <p>测试要点：updateById 返回 affectedRows==0 即代表乐观锁冲突，应重试或抛错。
 */
@TableName("stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Integer total;

    private Integer available;

    @Version
    private Integer version;

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
        this.version = 0;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
