package com.example.ecommerce.mapper;

import org.springframework.stereotype.Repository;

/**
 * 占位 Mapper。M3（商品&数据库持久化）会用 MyBatis-Plus 接入 MySQL，
 * 这里仅展示 controller → service → mapper 的完整分层，不连库。
 */
@Repository
public class DemoMapper {

    public int count() {
        // TODO: M3 接入 MyBatis-Plus 后实现真实查询
        return 0;
    }
}
