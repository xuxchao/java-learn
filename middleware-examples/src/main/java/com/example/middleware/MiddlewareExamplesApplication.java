package com.example.middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 中间件范例工程入口。
 *
 * 本工程是独立于主工程 java-learn-app 的"中间件范例仓库"，按库分包（redis / rabbitmq），
 * 通过 Spring {@code @Profile} 隔离：激活 redis 时只装配 Redis 相关 bean，激活 rabbitmq 时只装配 MQ 相关 bean，
 * 互不干扰。各库的例子由对应的 ExampleRunner 在应用启动后顺序执行并打印输出。
 */
@SpringBootApplication
public class MiddlewareExamplesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareExamplesApplication.class, args);
    }
}
