package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.security.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理接口：路径以 /admin 开头，LoginInterceptor 会要求 ADMIN 角色，否则返回 FORBIDDEN（RBAC 演示）。
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/panel")
    public Result<String> panel(@RequestAttribute("loginUser") LoginUser loginUser) {
        return Result.success("欢迎管理员 " + loginUser.username() + "，这是管理面板");
    }
}
