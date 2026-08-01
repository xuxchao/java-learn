package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.security.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受保护接口示例：任意已登录用户可访问。loginUser 由 LoginInterceptor 写入 request attribute。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/me")
    public Result<LoginUser> me(@RequestAttribute("loginUser") LoginUser loginUser) {
        return Result.success(loginUser);
    }
}
