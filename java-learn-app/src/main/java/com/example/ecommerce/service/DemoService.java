package com.example.ecommerce.service;

import com.example.ecommerce.mapper.DemoMapper;
import org.springframework.stereotype.Service;

/**
 * 业务层。演示 controller → service → mapper 的分层调用。
 * 真实数据库访问在 M3（商品&数据库持久化）接入 MyBatis-Plus 后落地。
 */
@Service
public class DemoService {

    private final DemoMapper demoMapper;

    public DemoService(DemoMapper demoMapper) {
        this.demoMapper = demoMapper;
    }

    public String sayHello() {
        return "hello from service layer, count=" + demoMapper.count();
    }
}
