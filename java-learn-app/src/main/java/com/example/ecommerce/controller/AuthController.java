package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开接口：注册 / 登录。已在 WebConfig 中排除拦截器校验。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(String username, String password, String role) {
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest req) {
        authService.register(req.username(), req.password(), req.role());
        return Result.success();
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest req) {
        String token = authService.login(req.username(), req.password());
        return Result.success(token);
    }
}
