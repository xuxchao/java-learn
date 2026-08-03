package com.example.ecommerce.security;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoginInterceptor 纯单元测试：不启动 Spring 容器，直接 new 出拦截器，
 * 用 MockHttpServletRequest/Response 模拟一次请求的 preHandle 阶段。
 *
 * 覆盖 M2 鉴权的四条主路径：
 *  1. 无 Authorization 头            → UNAUTHORIZED
 *  2. token 非法/签名不匹配          → UNAUTHORIZED
 *  3. 普通用户访问 /admin/**         → FORBIDDEN（RBAC）
 *  4. 合法 token                     → 放行并把 LoginUser 写入 request attribute
 */
class LoginInterceptorTest {

    private final JwtUtil jwtUtil =
            new JwtUtil("test-secret-key-0123456789abcdefghijklmnopqrstuvwxyz", 3600000);

    private final LoginInterceptor interceptor = new LoginInterceptor(jwtUtil);

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private MockHttpServletRequest request(String uri, String authHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (authHeader != null) {
            req.addHeader("Authorization", authHeader);
        }
        return req;
    }

    @Test
    void missing_token_throws_unauthorized() {
        MockHttpServletRequest req = request("/user/me", null);
        ApiException ex = assertThrows(ApiException.class,
                () -> interceptor.preHandle(req, response, new Object()));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void malformed_header_throws_unauthorized() {
        // 没有 "Bearer " 前缀，视为未登录
        MockHttpServletRequest req = request("/user/me", "abc.def.ghi");
        ApiException ex = assertThrows(ApiException.class,
                () -> interceptor.preHandle(req, response, new Object()));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void invalid_token_throws_unauthorized() {
        MockHttpServletRequest req = request("/user/me", "Bearer not-a-valid-token");
        ApiException ex = assertThrows(ApiException.class,
                () -> interceptor.preHandle(req, response, new Object()));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void normal_user_accessing_admin_throws_forbidden() {
        String token = jwtUtil.generate(1L, "alice", "USER");
        MockHttpServletRequest req = request("/admin/panel", "Bearer " + token);
        ApiException ex = assertThrows(ApiException.class,
                () -> interceptor.preHandle(req, response, new Object()));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void admin_user_accessing_admin_passes() {
        String token = jwtUtil.generate(9L, "root", "ADMIN");
        MockHttpServletRequest req = request("/admin/panel", "Bearer " + token);

        assertTrue(interceptor.preHandle(req, response, new Object()));

        Object attr = req.getAttribute("loginUser");
        LoginUser loginUser = assertInstanceOf(LoginUser.class, attr);
        assertEquals(9L, loginUser.userId().longValue());
        assertEquals("root", loginUser.username());
        assertEquals("ADMIN", loginUser.role());
    }

    @Test
    void valid_token_passes_and_sets_login_user() {
        String token = jwtUtil.generate(3L, "bob", "USER");
        MockHttpServletRequest req = request("/user/me", "Bearer " + token);

        assertTrue(interceptor.preHandle(req, response, new Object()));

        LoginUser loginUser = (LoginUser) req.getAttribute("loginUser");
        assertEquals(3L, loginUser.userId().longValue());
        assertEquals("bob", loginUser.username());
        assertEquals("USER", loginUser.role());
    }
}
