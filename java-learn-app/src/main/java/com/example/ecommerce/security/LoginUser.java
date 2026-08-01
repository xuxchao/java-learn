package com.example.ecommerce.security;

/**
 * 登录用户上下文（从 JWT 解析后放入 request attribute，供 controller 直接取用）。
 * 用 record 表达不可变值对象最合适。
 */
public record LoginUser(Long userId, String username, String role) {
}
