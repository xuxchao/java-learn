package com.example.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 开启 Spring 异步（{@code @Async}），并为缓存的延迟双删 / 逻辑过期刷新提供独立线程池，
 * 避免这些后台任务占用 Tomcat 业务线程。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cache-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 缓存专用异步线程池：延迟双删 / 逻辑过期刷新走这里。
     * 用 {@code CallerRunsPolicy} 而非 {@code DiscardPolicy}——若池子打满，由发布事件的线程
     * 自己同步执行删除/刷新，<b>绝不会静默丢弃</b>延迟双删（否则一致性防护在高并发下反而失效）。
     * 延迟双删内含 {@code Thread.sleep}，专用池避免阻塞通用异步任务。
     */
    @Bean("cacheAsyncExecutor")
    public Executor cacheAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cache-evict-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
