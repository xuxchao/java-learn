package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.model.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper（M3）。通用 CRUD 由 MyBatis-Plus 提供；
 * 下单时插入订单与扣减库存在同一事务内完成（见 OrderService）。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
