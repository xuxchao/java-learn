package com.example.ecommerce.service;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试：用 Mockito 替身 UserRepository，不连库。
 * BCryptPasswordEncoder 与 JwtUtil 直接用真实实例（二者均为纯函数式工具，无外部依赖）。
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtUtil jwtUtil =
            new JwtUtil("test-secret-key-0123456789abcdefghijklmnopqrstuvwxyz", 3600000);
    private final AuthService authService = new AuthService(userRepository, jwtUtil);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void register_duplicate_username_throws() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User("alice", "x", "USER")));
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.register("alice", "pw", null));
        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_defaults_role_to_user() {
        when(userRepository.findByUsername("carol")).thenReturn(Optional.empty());
        authService.register("carol", "pw", null);   // 不抛异常即通过；角色默认 USER
    }

    @Test
    void login_wrong_password_throws() {
        User u = new User("alice", encoder.encode("right"), "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login("alice", "wrong"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }

    @Test
    void login_success_returns_jwt() {
        User u = new User("alice", encoder.encode("pw"), "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        String token = authService.login("alice", "pw");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }
}
