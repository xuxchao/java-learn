package com.example.middleware.rabbitmq;

import com.example.middleware.BaseExampleRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 库例子入口。仅在 {@code rabbitmq} profile 激活时装配，因此跑 MQ 例子不会触发 Redis 连接。
 * 后续 ticket（06-08）会向 {@link #examples()} 注册具体的路由模式 / 可靠性 / 高级模式范例。
 */
@Component
@Profile("rabbitmq")
public class RabbitExampleRunner extends BaseExampleRunner {

    @Override
    protected String moduleName() {
        return "RabbitMQ";
    }

    @Override
    protected Map<String, Runnable> examples() {
        return Map.of();
    }
}
