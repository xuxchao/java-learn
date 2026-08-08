package com.example.middleware.rabbitmq;

import com.example.middleware.BaseExampleRunner;
import com.example.middleware.rabbitmq.message.DemoMessage;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.middleware.rabbitmq.RabbitMqDemoConfig.*;

/**
 * RabbitMQ 六种路由拓扑 demo 的入口。向 {@link #examples()} 注册：simple / work / fanout / direct / topic / headers。
 *
 * <p>每个 demo 是一个自包含的 {@link Runnable}：创建消息 → 发送 → 接收 → 打印验证结果。
 * 全部通过 {@link RabbitTemplate#receiveAndConvert(String)} 同步拉取消息，
 * work queue 使用两条后台线程拉取以演示 round-robin 分发。
 */
@Component
@Profile("rabbitmq")
public class RabbitExampleRunner extends BaseExampleRunner {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Override
    protected String moduleName() {
        return "RabbitMQ";
    }

    @Override
    @SuppressWarnings("java:S3776")
    protected Map<String, Runnable> examples() {
        Map<String, Runnable> map = new LinkedHashMap<>();
        map.put("simple",  this::demoSimple);
        map.put("work",    this::demoWork);
        map.put("fanout",  this::demoFanout);
        map.put("direct",  this::demoDirect);
        map.put("topic",   this::demoTopic);
        map.put("headers", this::demoHeaders);
        return map;
    }

    // ==================== 1. Simple Queue ====================

    private void demoSimple() {
        header("Simple Queue —— 一条消息，一个消费者");

        purge(SIMPLE_QUEUE);

        DemoMessage msg = DemoMessage.of("1", "Hello, Simple Queue!", "simple");
        rabbitTemplate.convertAndSend(SIMPLE_QUEUE, msg);
        println("[producer] sent: " + msg);

        Object received = rabbitTemplate.receiveAndConvert(SIMPLE_QUEUE, 3000);
        println("[consumer] received: " + received);

        assert received != null : "simple queue 应收到消息";
        assert received instanceof DemoMessage : "反序列化后应为 DemoMessage";
    }

    // ==================== 2. Work Queue ====================

    private void demoWork() {
        header("Work Queue —— 一条队列，多条消息（演示消息排队与拉取）");

        purge(WORK_QUEUE);

        int n = 5;
        for (int i = 1; i <= n; i++) {
            DemoMessage msg = DemoMessage.of(String.valueOf(i),
                    "task-" + i + " (work-queue demo)", "work");
            rabbitTemplate.convertAndSend(WORK_QUEUE, msg);
            println("[producer] sent: " + msg);
        }

        // 单线程同步拉取，演示消息按 FIFO 顺序出队
        // （多实例/多线程部署时 RabbitMQ 自动 round-robin 分发，见 README 详解）
        println("--- 消费者依次拉取 ---");
        for (int i = 1; i <= n; i++) {
            Object msg = rabbitTemplate.receiveAndConvert(WORK_QUEUE, 3000);
            println("[" + i + "] received: " + msg);
            if (msg == null) {
                println("  ! 警告：第 " + i + " 次拉取为空，预期应收到 " + n + " 条");
                break;
            }
        }

        // 验证无残留
        Object extra = rabbitTemplate.receiveAndConvert(WORK_QUEUE, 500);
        println("[extra] " + extra + " (expect null)");
        assert extra == null : "队列不应有残留消息";
    }

    // ==================== 3. Fanout Exchange ====================

    private void demoFanout() {
        header("Fanout Exchange —— 一条消息广播到所有绑定队列");

        purge(FANOUT_QUEUE_A);
        purge(FANOUT_QUEUE_B);

        DemoMessage msg = DemoMessage.of("1", "Broadcast via fanout", "fanout");
        rabbitTemplate.convertAndSend(FANOUT_EXCHANGE, "", msg);
        println("[producer] sent to exchange '" + FANOUT_EXCHANGE + "': " + msg);

        Object a = rabbitTemplate.receiveAndConvert(FANOUT_QUEUE_A, 3000);
        Object b = rabbitTemplate.receiveAndConvert(FANOUT_QUEUE_B, 3000);

        println("[queue.a] received: " + a);
        println("[queue.b] received: " + b);

        assert a != null : "fanout.queue.a 应收到消息";
        assert b != null : "fanout.queue.b 应收到消息";
    }

    // ==================== 4. Direct Exchange ====================

    private void demoDirect() {
        header("Direct Exchange —— 按 routing key 精确路由到目标队列");

        purge(DIRECT_QUEUE_RED);
        purge(DIRECT_QUEUE_BLUE);

        DemoMessage redMsg  = DemoMessage.of("1", "这是 Red 消息", RK_RED);
        DemoMessage blueMsg = DemoMessage.of("2", "这是 Blue 消息", RK_BLUE);
        rabbitTemplate.convertAndSend(DIRECT_EXCHANGE, RK_RED, redMsg);
        rabbitTemplate.convertAndSend(DIRECT_EXCHANGE, RK_BLUE, blueMsg);
        println("[producer] sent red=" + RK_RED + " blue=" + RK_BLUE);

        Object redReceived  = rabbitTemplate.receiveAndConvert(DIRECT_QUEUE_RED, 3000);
        Object blueReceived = rabbitTemplate.receiveAndConvert(DIRECT_QUEUE_BLUE, 3000);
        Object redExtra     = rabbitTemplate.receiveAndConvert(DIRECT_QUEUE_RED, 500);

        println("[queue.red]  = " + redReceived);
        println("[queue.blue] = " + blueReceived);
        println("[queue.red]  extra (expect null): " + redExtra);

        assert redReceived != null : "red 队列应收到 red 路由消息";
        assert blueReceived != null : "blue 队列应收到 blue 路由消息";
        assert redExtra == null : "red 队列不应收到 blue 路由消息（验证了路由隔离）";
    }

    // ==================== 5. Topic Exchange ====================

    private void demoTopic() {
        header("Topic Exchange —— 通配符路由 (* 单段, # 多段)");

        purge(TOPIC_QUEUE_ALL);
        purge(TOPIC_QUEUE_ERROR);

        // log.info → 仅匹配 log.#（all 队列）
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE, "log.info",
                DemoMessage.of("1", "Info log message", "log.info"));
        // app.error → 仅匹配 *.error（error 队列）
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE, "app.error",
                DemoMessage.of("2", "App error message", "app.error"));
        // log.error → 同时匹配 log.# 和 *.error（两个队列都收到）
        rabbitTemplate.convertAndSend(TOPIC_EXCHANGE, "log.error",
                DemoMessage.of("3", "Log error message", "log.error"));
        println("[producer] sent 3 messages: log.info, app.error, log.error");

        // 验证 all 队列：log.info + log.error = 2 条
        Object all1 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ALL, 3000);
        Object all2 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ALL, 3000);
        Object all3 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ALL, 500);
        println("[queue.all] msg1: " + all1);
        println("[queue.all] msg2: " + all2);
        println("[queue.all] msg3 (expect null): " + all3);

        // 验证 error 队列：app.error + log.error = 2 条
        Object err1 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ERROR, 3000);
        Object err2 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ERROR, 3000);
        Object err3 = rabbitTemplate.receiveAndConvert(TOPIC_QUEUE_ERROR, 500);
        println("[queue.error] msg1: " + err1);
        println("[queue.error] msg2: " + err2);
        println("[queue.error] msg3 (expect null): " + err3);

        assert all1 != null && all2 != null : "all 队列应收到 2 条（log.info + log.error）";
        assert all3 == null : "all 队列不应有第 3 条（app.error 不匹配 log.#）";
        assert err1 != null && err2 != null : "error 队列应收到 2 条（app.error + log.error）";
        assert err3 == null : "error 队列不应有第 3 条";
    }

    // ==================== 6. Headers Exchange ====================

    private void demoHeaders() {
        header("Headers Exchange —— 消息头匹配（any / all，精确匹配）");

        purge(HEADERS_QUEUE_JSON);
        purge(HEADERS_QUEUE_BINARY);

        // msg1: format=json → json 队列 (any: format=json ✓), binary 队列 (all: 要求 format=binary ✗)
        rabbitTemplate.convertAndSend(HEADERS_EXCHANGE, "",
                DemoMessage.of("1", "JSON format message", "json"), m -> {
                    m.getMessageProperties().setHeader(HEADER_FORMAT, "json");
                    return m;
                });
        println("[producer] sent header {format=json}");

        // msg2: format=binary + version=2 → json ✗, binary ✓ (all: format=binary + version=2)
        rabbitTemplate.convertAndSend(HEADERS_EXCHANGE, "",
                DemoMessage.of("2", "Binary v2 message", "binary"), m -> {
                    m.getMessageProperties().setHeader(HEADER_FORMAT, "binary");
                    m.getMessageProperties().setHeader("version", "2");
                    return m;
                });
        println("[producer] sent header {format=binary, version=2}");

        // msg3: format=binary + version=1 → json ✗ (format不等于json), binary ✗ (version≠2)
        // headers exchange 不做模糊匹配，不匹配任何绑定的消息会被**丢弃**
        rabbitTemplate.convertAndSend(HEADERS_EXCHANGE, "",
                DemoMessage.of("3", "Binary v1 (will be discarded!)", "orphan"), m -> {
                    m.getMessageProperties().setHeader(HEADER_FORMAT, "binary");
                    m.getMessageProperties().setHeader("version", "1");
                    return m;
                });
        println("[producer] sent header {format=binary, version=1} → 不匹配任何绑定，将被丢弃");

        // 等待消息路由完成
        sleep(300);

        // json 队列（any: format=json）→ 仅 msg1（id=1）
        Object j1 = rabbitTemplate.receiveAndConvert(HEADERS_QUEUE_JSON, 3000);
        Object j2 = rabbitTemplate.receiveAndConvert(HEADERS_QUEUE_JSON, 500);
        println("[queue.json]  msg1: " + j1);
        println("[queue.json]  msg2 (expect null): " + j2);

        // binary 队列（all: format=binary + version=2）→ 仅 msg2（id=2）
        Object b1 = rabbitTemplate.receiveAndConvert(HEADERS_QUEUE_BINARY, 3000);
        Object b2 = rabbitTemplate.receiveAndConvert(HEADERS_QUEUE_BINARY, 500);
        println("[queue.binary] msg1: " + b1);
        println("[queue.binary] msg2 (expect null): " + b2);

        assert j1 != null : "json 队列应收到 1 条（any: format=json 只匹配 format=json 的消息）";
        assert j2 == null : "json 队列不应有第 2 条";
        assert b1 != null : "binary 队列应收到 1 条（all: format=binary + version=2 都满足）";
        assert b2 == null : "binary 队列不应收到 version≠2 的消息";
    }

    // ==================== helpers ====================

    private void header(String title) {
        println("");
        println("=== " + title + " ===");
    }

    private void println(String s) {
        System.out.println(s);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 使用 RabbitAdmin 同步清空队列 */
    private void purge(String queueName) {
        rabbitAdmin.purgeQueue(queueName, false);
    }
}
