package com.example.ecommerce.config;

import com.example.ecommerce.cache.CacheEnvelope;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置（M4 缓存）。
 *
 * <p>提供一个 {@code RedisTemplate<String, Object>}，value 用 JSON 序列化；
 * 由于缓存 value 固定是 {@code CacheEnvelope} 类型，序列化器显式指定目标类型 {@code CacheEnvelope.class}，
 * <b>不写 {@code @class} 类型信息</b>（避免 {@code GenericJackson2JsonRedisSerializer} 在未启用默认类型时不带类型导致回读失败）。注册 {@link JavaTimeModule}
 * 以支持 {@code Product} 里的 {@code LocalDateTime} 字段。
 *
 * <p>注意 bean 名为 {@code productCacheTemplate}（类型 {@code RedisTemplate<String, Object>}），
 * 不会覆盖 Spring Boot 自动配置的 {@code redisTemplate} / {@code stringRedisTemplate}，
 * 因此 {@code InfraConnectionTest} 注入的 {@code RedisTemplate<String, String>} 不受影响。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> productCacheTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 缓存 value 固定是 CacheEnvelope 类型，用 Jackson2JsonRedisSerializer(CacheEnvelope.class)
        // 显式指定目标类型反序列化（无需 @class 类型信息，避免 GenericJackson 在不启用默认类型时不写 @class 导致回读失败）。
        Jackson2JsonRedisSerializer<CacheEnvelope> jsonSerializer = new Jackson2JsonRedisSerializer<>(CacheEnvelope.class);
        jsonSerializer.setObjectMapper(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
