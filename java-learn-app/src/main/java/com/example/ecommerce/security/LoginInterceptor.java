package com.example.ecommerce.security;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器（M2 核心 AOP 式横切：鉴权）。
 * 在 controller 方法执行前校验 Authorization: Bearer <token>：
 *  - 缺/错 token          → 抛 UNAUTHORIZED（由全局异常处理器统一转错误体）
 *  - /admin/** 且非 ADMIN → 抛 FORBIDDEN（RBAC 演示）
 *  - 通过则把 LoginUser 写入 request attribute，controller 用 @RequestAttribute 取用
 *
 * 执行顺序（一次请求内）：Filter → DispatcherServlet → Interceptor.preHandle → Controller →
 * → AOP(@RestControllerAdvice 异常通知) / 业务 → Interceptor.postHandle → Filter。拦截器位于
 * 过滤器之后、controller 之前，适合做"登录态校验"这类与路由/角色相关的横切逻辑。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public LoginInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        boolean needsAdmin = uri.startsWith("/admin");   // RBAC：管理接口要求 ADMIN 角色

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        String token = header.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        String role = claims.get("role", String.class);
        if (needsAdmin && !"ADMIN".equals(role)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        LoginUser loginUser = new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                role);
        request.setAttribute("loginUser", loginUser);
        return true;
    }
}
