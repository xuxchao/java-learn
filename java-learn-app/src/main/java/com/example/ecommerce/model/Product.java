package com.example.ecommerce.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（M3）。对应 {@code products} 表。
 *
 * <p>MyBatis-Plus 约定：下划线列自动映射到驼峰字段（map-underscore-to-camel-case=true）。
 * created_at / updated_at 由数据库默认值维护，因此插入/更新时让 MP 跳过这两个字段
 * （FieldStrategy.NOT_NULL：字段为 null 时不拼进 SQL，从而落到 DB 的 DEFAULT / ON UPDATE）。
 */
@TableName("products")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private BigDecimal price;

    private String description;

    @TableField(value = "created_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;

    public Product() {
    }

    public Product(String name, BigDecimal price, String description) {
        this.name = name;
        this.price = price;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
