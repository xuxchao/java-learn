package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.LockType;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductController Web 切片测试。@WebMvcTest 只装配 Web 层，
 * 因此 WebConfig + LoginInterceptor 会被加载但其依赖的 JwtUtil（普通 @Component）不在切片内，
 * 必须 @MockBean 掉，否则上下文启动报 "No qualifying bean of type JwtUtil"。
 *
 * <p>/products/** 受登录拦截器保护，故每个请求都带 {@code Authorization: Bearer fake} 头，
 * 并把 JwtUtil.parse 打桩返回合法 Claims（sub=7 / role=USER），让拦截器放行。
 * Service 层用 mock 替身，不连库。
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtUtil jwtUtil;

    private void stubAuth() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("username", String.class)).thenReturn("tester");
        when(claims.get("role", String.class)).thenReturn("USER");
        when(jwtUtil.parse(anyString())).thenReturn(claims);
    }

    @Test
    void list_returns_success_body() throws Exception {
        stubAuth();
        when(productService.listProducts()).thenReturn(List.of(new Product("手机", new BigDecimal("1999"), "desc")));
        mockMvc.perform(get("/products").header("Authorization", "Bearer fake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("手机"));
    }

    @Test
    void create_product_returns_created() throws Exception {
        stubAuth();
        Product created = new Product("手机", new BigDecimal("1999"), "desc");
        when(productService.createProduct("手机", new BigDecimal("1999"), "desc")).thenReturn(created);
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer fake")
                        .contentType("application/json")
                        .content("{\"name\":\"手机\",\"price\":1999.00,\"description\":\"desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void init_stock_returns_success() throws Exception {
        stubAuth();
        mockMvc.perform(post("/products/1/stock")
                        .header("Authorization", "Bearer fake")
                        .contentType("application/json")
                        .content("{\"total\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void order_defaults_to_optimistic_lock() throws Exception {
        stubAuth();
        Order order = new Order();
        order.setQuantity(2);
        when(orderService.placeOrder(7L, 1L, 2, LockType.OPTIMISTIC)).thenReturn(order);
        mockMvc.perform(post("/products/1/order")
                        .header("Authorization", "Bearer fake")
                        .contentType("application/json")
                        .content("{\"userId\":7,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
