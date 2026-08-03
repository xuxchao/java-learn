package com.example.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ecommerce.model.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper（M3）。继承 MyBatis-Plus {@link BaseMapper} 即获得通用 CRUD，
 * 无需手写 XML / SQL。复杂查询可在方法上用注解或 XML 扩展。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
