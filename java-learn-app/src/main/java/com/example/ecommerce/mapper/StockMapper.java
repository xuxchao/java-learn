package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.model.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper（M3）。在通用 CRUD 之上补充按商品维度查询与 CAS 扣减的方法：
 *
 * <ul>
 *   <li>{@link #selectByProductId}：普通查询，供乐观锁路径在事务内读取当前可用库存。</li>
 *   <li>{@link #selectByProductIdForUpdate}：带 {@code FOR UPDATE} 的悲观锁查询，
 *       在事务内锁定该行直到事务提交，杜绝并发修改（与乐观锁是两种取舍不同的方案）。</li>
 *   <li>{@link #decreaseAvailable}：CAS 乐观锁核心。单条 SQL 内完成
 *       "比较 available &gt;= quantity" 与 "原子扣减 available = available - quantity"，
 *       彻底避免"先读后写"的丢失更新；返回受影响行数，0 表示并发期间可用量已被他人消耗。</li>
 * </ul>
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Select("SELECT * FROM stock WHERE product_id = #{productId}")
    Stock selectByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM stock WHERE product_id = #{productId} FOR UPDATE")
    Stock selectByProductIdForUpdate(@Param("productId") Long productId);

    @Update("UPDATE stock SET available = available - #{quantity} WHERE id = #{id} AND available >= #{quantity}")
    int decreaseAvailable(@Param("id") Long id, @Param("quantity") int quantity);
}
