package com.example.middleware.rabbitmq;

import com.example.middleware.rabbitmq.message.DemoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Work queue 演示的结果收集器。Runner 中 work queue demo 通过两条后台线程同步拉取消息，
 * 利用 RabbitMQ 内置的 round-robin 分发，将处理记录收进此实例供打印。
 *
 * <p>实际生产建议通过 {@code @RabbitListener}（可配 {@code prefetch=1} + 手动 ack）
 * 在多实例部署下实现公平分发，详见 README 双栏对照。
 */
public class WorkQueueListener {

    private static final Logger log = LoggerFactory.getLogger(WorkQueueListener.class);

    private final List<String> results = Collections.synchronizedList(new ArrayList<>());

    public void record(String consumerName, DemoMessage msg) {
        String record = "[" + consumerName + "] received " + msg;
        results.add(record);
        log.info(record);
    }

    public List<String> getResults() {
        return Collections.unmodifiableList(new ArrayList<>(results));
    }
}
