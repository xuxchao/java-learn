package com.example.ecommerce.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 纯单元测试：不依赖 Spring 容器 / 数据库。
 * 直接构造实例（secret 长度需 >= 32 字节以满足 HS256）。
 */
class JwtUtilTest {

    private final JwtUtil jwtUtil =
            new JwtUtil("test-secret-key-0123456789abcdefghijklmnopqrstuvwxyz", 3600000);

    @Test
    void generate_and_parse_roundtrip() {
        String token = jwtUtil.generate(7L, "bob", "ADMIN");
        Claims claims = jwtUtil.parse(token);
        assertEquals("7", claims.getSubject());
        assertEquals("bob", claims.get("username", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void parse_invalid_token_throws() {
        assertThrows(Exception.class, () -> jwtUtil.parse("not-a-valid-token"));
    }

    @Test
    void token_has_three_parts() {
        String token = jwtUtil.generate(1L, "alice", "USER");
        assertTrue(token.split("\\.").length == 3, "JWT 应由 header.payload.signature 三段组成");
    }
}
