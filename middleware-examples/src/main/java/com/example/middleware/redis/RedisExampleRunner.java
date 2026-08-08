package com.example.middleware.redis;

import com.example.middleware.BaseExampleRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis 库例子入口。仅在 {@code redis} profile 激活时装配，因此跑 Redis 例子不会触发 RabbitMQ 连接。
 * 后续 ticket（02-05）会向 {@link #examples()} 注册具体的 API 范例。
 */
@Component
@Profile("redis")
public class RedisExampleRunner extends BaseExampleRunner {

    @Override
    protected String moduleName() {
        return "Redis";
    }

    @Override
    protected Map<String, Runnable> examples() {
        return Map.of();
    }
}
