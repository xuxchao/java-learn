package com.example.ecommerce.controller;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.service.DemoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success(demoService.sayHello());
    }

    // 故意抛业务异常，用于验证全局异常处理能返回标准错误体
    @GetMapping("/boom")
    public Result<Void> boom() {
        throw new ApiException(ErrorCode.DEMO_BROKEN);
    }
}
