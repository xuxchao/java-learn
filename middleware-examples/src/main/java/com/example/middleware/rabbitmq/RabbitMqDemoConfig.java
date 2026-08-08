package com.example.middleware.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 六种路由拓扑所需的交换机、队列与绑定声明，外加共享的 JSON 消息转换器。
 *
 * <p>所有组件均设为非持久化（{@code durable=false}）、自动删除（{@code autoDelete=true}），
 * 因为中间件范例仓库每次运行都是全新的一次性演示，不需要 broker 重启后保留历史数据。
 * 若作为生产参考，请根据实际场景决定是否开启持久化。
 */
@Configuration
public class RabbitMqDemoConfig {

    /* ===================== RabbitAdmin ===================== */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /* ===================== 1. Simple Queue ===================== */
    public static final String SIMPLE_QUEUE = "demo.simple.queue";

    @Bean
    public Queue simpleQueue() {
        return QueueBuilder.nonDurable(SIMPLE_QUEUE).build();
    }

    /* ===================== 2. Work Queue ===================== */
    public static final String WORK_QUEUE = "demo.work.queue";

    @Bean
    public Queue workQueue() {
        return QueueBuilder.nonDurable(WORK_QUEUE).build();
    }

    /* ===================== 3. Fanout Exchange ===================== */
    public static final String FANOUT_EXCHANGE   = "demo.fanout.exchange";
    public static final String FANOUT_QUEUE_A    = "demo.fanout.queue.a";
    public static final String FANOUT_QUEUE_B    = "demo.fanout.queue.b";

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE, false, true);
    }

    @Bean
    public Queue fanoutQueueA() { return QueueBuilder.nonDurable(FANOUT_QUEUE_A).build(); }

    @Bean
    public Queue fanoutQueueB() { return QueueBuilder.nonDurable(FANOUT_QUEUE_B).build(); }

    @Bean
    public Binding fanoutBindingA(FanoutExchange fanoutExchange, Queue fanoutQueueA) {
        return BindingBuilder.bind(fanoutQueueA).to(fanoutExchange);
    }

    @Bean
    public Binding fanoutBindingB(FanoutExchange fanoutExchange, Queue fanoutQueueB) {
        return BindingBuilder.bind(fanoutQueueB).to(fanoutExchange);
    }

    /* ===================== 4. Direct Exchange ===================== */
    public static final String DIRECT_EXCHANGE  = "demo.direct.exchange";
    public static final String DIRECT_QUEUE_RED   = "demo.direct.queue.red";
    public static final String DIRECT_QUEUE_BLUE  = "demo.direct.queue.blue";
    public static final String RK_RED   = "red";
    public static final String RK_BLUE  = "blue";

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, false, true);
    }

    @Bean
    public Queue directQueueRed()  { return QueueBuilder.nonDurable(DIRECT_QUEUE_RED).build(); }

    @Bean
    public Queue directQueueBlue() { return QueueBuilder.nonDurable(DIRECT_QUEUE_BLUE).build(); }

    @Bean
    public Binding directBindingRed(DirectExchange directExchange, Queue directQueueRed) {
        return BindingBuilder.bind(directQueueRed).to(directExchange).with(RK_RED);
    }

    @Bean
    public Binding directBindingBlue(DirectExchange directExchange, Queue directQueueBlue) {
        return BindingBuilder.bind(directQueueBlue).to(directExchange).with(RK_BLUE);
    }

    /* ===================== 5. Topic Exchange ===================== */
    public static final String TOPIC_EXCHANGE   = "demo.topic.exchange";
    public static final String TOPIC_QUEUE_ALL   = "demo.topic.queue.all";
    public static final String TOPIC_QUEUE_ERROR = "demo.topic.queue.error";

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, false, true);
    }

    @Bean
    public Queue topicQueueAll()   { return QueueBuilder.nonDurable(TOPIC_QUEUE_ALL).build(); }

    @Bean
    public Queue topicQueueError() { return QueueBuilder.nonDurable(TOPIC_QUEUE_ERROR).build(); }

    @Bean
    public Binding topicBindingAll(TopicExchange topicExchange, Queue topicQueueAll) {
        // 匹配所有日志（# 匹配零或多段）
        return BindingBuilder.bind(topicQueueAll).to(topicExchange).with("log.#");
    }

    @Bean
    public Binding topicBindingError(TopicExchange topicExchange, Queue topicQueueError) {
        // 仅匹配 error 级别（* 匹配恰好一段）
        return BindingBuilder.bind(topicQueueError).to(topicExchange).with("*.error");
    }

    /* ===================== 6. Headers Exchange ===================== */
    public static final String HEADERS_EXCHANGE   = "demo.headers.exchange";
    public static final String HEADERS_QUEUE_JSON   = "demo.headers.queue.json";
    public static final String HEADERS_QUEUE_BINARY = "demo.headers.queue.binary";
    public static final String HEADER_FORMAT = "format";

    @Bean
    public HeadersExchange headersExchange() {
        return new HeadersExchange(HEADERS_EXCHANGE, false, true);
    }

    @Bean
    public Queue headersQueueJson()   { return QueueBuilder.nonDurable(HEADERS_QUEUE_JSON).build(); }

    @Bean
    public Queue headersQueueBinary() { return QueueBuilder.nonDurable(HEADERS_QUEUE_BINARY).build(); }

    @Bean
    public Binding headersBindingJson(HeadersExchange headersExchange, Queue headersQueueJson) {
        // x-match: any — 头中 format=json 即匹配
        return BindingBuilder.bind(headersQueueJson).to(headersExchange).whereAny(
                Map.of(HEADER_FORMAT, "json")).match();
    }

    @Bean
    public Binding headersBindingBinary(HeadersExchange headersExchange, Queue headersQueueBinary) {
        // x-match: all — 头中必须同时满足 format=binary 且 version=2
        Map<String, Object> matchHeaders = new HashMap<>();
        matchHeaders.put(HEADER_FORMAT, "binary");
        matchHeaders.put("version", "2");
        return BindingBuilder.bind(headersQueueBinary).to(headersExchange).whereAll(matchHeaders).match();
    }

    /* ===================== JSON 消息转换器 ===================== */
    /**
     * 共享的 JSON 消息转换器，替代 Spring AMQP 默认的 JDK 序列化。
     *
     * <p>沿用主工程 M6 经验（{@code JavaTimeModule} + {@code trustedPackages}），
     * 避免 {@code LocalDateTime} 序列化/反序列化报错，确保消费者能还原带消息包类型的 JSON。
     */
    @Bean
    public MessageConverter demoMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages(
                "com.example.middleware.rabbitmq.message",
                "com.example.middleware.rabbitmq.message.*",
                "com.example.middleware.rabbitmq.*");
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * 全局监听器容器工厂：复用 JSON 转换器 + 失败消息回队重试。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter demoMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(demoMessageConverter);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    /**
     * Work queue 专用监听器容器工厂：prefetch=1（公平分发）+ 手动 ack。
     * 使用专用名称避免与全局 {@code rabbitListenerContainerFactory} 冲突，
     * {@code @RabbitListener(containerFactory = "workQueueListenerContainerFactory")} 显式指向它。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory workQueueListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter demoMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(demoMessageConverter);
        factory.setPrefetchCount(1);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
