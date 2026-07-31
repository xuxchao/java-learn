package com.example.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证应用能连上本地基建（MySQL / Redis / RabbitMQ）。
 * 默认禁用：运行前需先 `docker compose up -d` 拉起三个服务。
 * 启用方式：去掉 @Disabled，或 `mvn test -Dtest=InfraConnectionTest`。
 */
@org.junit.jupiter.api.Disabled("运行前需先 `docker compose up -d` 拉起 MySQL/Redis/RabbitMQ")
@SpringBootTest
class InfraConnectionTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void mysql_connects() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTrue(c.isValid(2));
        }
    }

    @Test
    void redis_connects() {
        String pong = redisTemplate.getConnectionFactory().getConnection().ping();
        assertTrue("PONG".equalsIgnoreCase(pong));
    }

    @Test
    void rabbit_connects() {
        rabbitTemplate.execute(channel -> {
            assertTrue(channel.isOpen());
            return null;
        });
    }
}
