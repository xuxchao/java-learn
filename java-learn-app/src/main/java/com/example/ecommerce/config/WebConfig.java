package com.example.ecommerce.config;

import com.example.ecommerce.security.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册登录拦截器，并放行公开接口。
 * - 拦截所有 /**，但排除 /auth/**（注册/登录本身无需登录态）、/demo/**（M1 演示接口）、
 *   /idempotency/**（M5 幂等演示端点，本身用 Idempotency-Key 头做幂等，不依赖登录态）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/demo/**", "/idempotency/**");
    }
}
