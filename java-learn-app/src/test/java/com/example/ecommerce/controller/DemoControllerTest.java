package com.example.ecommerce.controller;

import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.DemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoService demoService;

    /**
     * @WebMvcTest 只装配 Web 层 Bean（Controller / ControllerAdvice / WebMvcConfigurer /
     * HandlerInterceptor 等），因此 WebConfig 与 LoginInterceptor 会被加载，
     * 但它们依赖的普通 @Component（JwtUtil）不在切片范围内 —— 必须在此 mock 掉，
     * 否则上下文启动会报 "No qualifying bean of type JwtUtil"。
     */
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void hello_returns_success_body() throws Exception {
        when(demoService.sayHello()).thenReturn("mocked");
        mockMvc.perform(get("/demo/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("mocked"));
    }

    // 验证全局异常处理：业务异常被统一转换为标准错误体（code=1001）
    @Test
    void boom_returns_standard_error_body() throws Exception {
        mockMvc.perform(get("/demo/boom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").exists());
    }
}
