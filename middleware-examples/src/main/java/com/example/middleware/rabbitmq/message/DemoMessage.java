package com.example.middleware.rabbitmq.message;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 中间件范例通用消息体。承载示例内容、路由属性与时间戳，用于演示 Spring AMQP 的 JSON 序列化链路。
 *
 * <p>保留无参构造器与标准 getter/setter 是 {@code Jackson2JsonMessageConverter} 反序列化所必需的；
 * {@code createdAt} 为 {@link LocalDateTime} 类型，用以验证 {@code JavaTimeModule} 注册是否正确。
 */
public class DemoMessage {

    /** 消息 id（示例中通常为递增序号） */
    private String id;

    /** 示例场景描述 */
    private String payload;

    /** 路由标识（direct/topic 示例中用于区分 routing key 或 header 匹配） */
    private String routingTag;

    /** 自定义头部（headers exchange 示例中用于匹配条件） */
    private Map<String, Object> headers;

    /** 发送时间戳（验证 JavaTimeModule 能否正确处理 Java 8 日期类型） */
    private LocalDateTime createdAt;

    public DemoMessage() {
    }

    public DemoMessage(String id, String payload, String routingTag, LocalDateTime createdAt) {
        this.id = id;
        this.payload = payload;
        this.routingTag = routingTag;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getRoutingTag() { return routingTag; }
    public void setRoutingTag(String routingTag) { this.routingTag = routingTag; }

    public Map<String, Object> getHeaders() { return headers; }
    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "DemoMessage{id='" + id + "', payload='" + payload
                + "', routingTag='" + routingTag + "', createdAt=" + createdAt + '}';
    }

    /** 快捷工厂：创建当前时间戳的示例消息 */
    public static DemoMessage of(String id, String payload, String routingTag) {
        return new DemoMessage(id, payload, routingTag, LocalDateTime.now());
    }
}
