package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.model.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存 Mapper（M3）。在通用 CRUD 之上补充两个按商品维度查询的方法：
 *
 * <ul>
 *   <li>{@link #selectByProductId}：普通查询，供乐观锁路径在事务内读取当前库存与版本。</li>
 *   <li>{@link #selectByProductIdForUpdate}：带 {@code FOR UPDATE} 的悲观锁查询，
 *       在事务内锁定该行直到事务提交，杜绝并发修改（与乐观锁是两种取舍不同的方案）。</li>
 * </ul>
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Select("SELECT * FROM stock WHERE product_id = #{productId}")
    Stock selectByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM stock WHERE product_id = #{productId} FOR UPDATE")
    Stock selectByProductIdForUpdate(@Param("productId") Long productId);
}
