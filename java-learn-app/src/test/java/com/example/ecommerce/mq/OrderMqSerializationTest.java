package com.example.ecommerce.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MQ 消息体 JSON 往返测试（不连 broker）。
 *
 * <p>专门钉住 code-review 发现的 bug：事件含 {@link LocalDateTime}，若 JSON 转换器没注册
 * {@code JavaTimeModule}，运行期（真实发/收消息）会抛 "Java 8 date/time type not supported"。
 * 这里直接拿 {@link OrderMqConfig#orderMessageConverter()} 做 toMessage / fromMessage 往返，
 * 验证 {@code createdAt} 能原样还原（精确到纳秒），把这个运行期问题变成可回归的单测。
 */
class OrderMqSerializationTest {

    @Test
    void event_round_trips_with_localdatetime() {
        MessageConverter converter = new OrderMqConfig().orderMessageConverter();
        LocalDateTime now = LocalDateTime.now();
        OrderCreatedEvent event = new OrderCreatedEvent("ORD-X", 7L, 1L, 2, new BigDecimal("20.00"), now);

        Message message = converter.toMessage(event, null);
        OrderCreatedEvent back = (OrderCreatedEvent) converter.fromMessage(message);

        assertEquals("ORD-X", back.getOrderNo());
        assertEquals(2, back.getQuantity());
        assertNotNull(back.getCreatedAt(), "createdAt 不能被丢（JavaTimeModule 必须生效）");
        assertEquals(now.getYear(), back.getCreatedAt().getYear());
        assertEquals(now.getNano(), back.getCreatedAt().getNano());
    }
}
