package com.example.ecommerce.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 基础设施配置（M6）。
 *
 * <p>声明一个持久化的直连交换机 + 队列 + 绑定，并用 JSON 消息转换器替代默认的 JDK 序列化
 * （避免 Java 序列化兼容性问题，且人肉可读、跨语言友好）。
 *
 * <p>队列与交换机都设为 {@code durable=true}：broker 重启后元数据不丢，配合生产者的
 * publisher-confirm，是 MQ "不丢消息" 的第一道保障。
 */
@Configuration
public class OrderMqConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_QUEUE = "order.created.queue";
    public static final String ORDER_ROUTING_KEY = "order.created";

    @Bean
    public Queue orderCreatedQueue() {
        // 第二个参数 durable=true：队列持久化
        return new Queue(ORDER_QUEUE, true);
    }

    @Bean
    public DirectExchange orderExchange() {
        // durable=true, autoDelete=false
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Binding orderBinding(Queue orderCreatedQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with(ORDER_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器。Spring Boot 会自动把它应用到 {@code RabbitTemplate} 与
     * {@code @RabbitListener} 的监听器容器工厂上，生产者 / 消费者共用，保证序列化、反序列化一致。
     *
     * <p>关键点：必须显式给内部 ObjectMapper 注册 {@link JavaTimeModule}，否则事件里的
     * {@code LocalDateTime createdAt} 在序列化/反序列化时会抛
     * "Java 8 date/time type ... not supported"（运行期才会暴露，单测不跑 JSON 往返测不出）。
     *
     * <p>用 {@link DefaultClassMapper#setTrustedPackages} 显式信任本包，消费者才能把消息
     * 还原成 {@link OrderCreatedEvent}（Spring AMQP 出于安全默认不反序列化任意类）。
     */
    @Bean
    public MessageConverter orderMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultClassMapper classMapper = new DefaultClassMapper();
        // 信任整个项目包树：根包 com.example.ecommerce 及其子包（如 .mq），
        // 否则消费者反序列化子包里的事件会报 "not in the trusted packages"。
        classMapper.setTrustedPackages("com.example.ecommerce", "com.example.ecommerce.mq", "com.example.ecommerce.*");
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * 监听器容器工厂（覆盖 Boot 默认）。除复用上面的 JSON 转换器外，关键是
     * {@code setDefaultRequeueRejected(true)}：消费端处理抛异常时消息重回队列重试，
     * 配合 Redis 幂等键（orderNo）避免重复副作用 —— 这是消费端"不丢"的兜底。
     *
     * <p>生产建议加重试上限 + 死信队列（DLX/DLQ），避免毒消息无限重投刷日志。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter orderMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(orderMessageConverter);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }
}
