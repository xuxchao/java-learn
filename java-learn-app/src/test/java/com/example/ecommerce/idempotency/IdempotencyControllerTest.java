package com.example.ecommerce.idempotency;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IdempotencyController Web 切片测试。只装配 Web 层，{@link IdempotencyService} 用 mock 替身，
 * 不连 Redis。验证：①缺 Idempotency-Key 头 → 3007；②带 key → 委托 service 并透传重放标记头。
 *
 * <p>WebConfig 被加载但其依赖的 JwtUtil（普通 @Component）不在切片内，须 @MockBean；
 * /idempotency/** 已在 WebConfig 排除拦截，故本端点无需 Bearer token。
 */
@WebMvcTest(IdempotencyController.class)
class IdempotencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void missing_idempotency_key_returns_3007() throws Exception {
        when(idempotencyService.execute(any(), any(), any())).thenThrow(new RuntimeException("不应被调用"));

        mockMvc.perform(post("/idempotency/echo")
                        .contentType("application/json")
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3007));
    }

    @Test
    void with_key_delegates_to_service_and_exposes_replay_header() throws Exception {
        IdempotencyController.EchoResponse resp = new IdempotencyController.EchoResponse("hi", 1);
        when(idempotencyService.execute(eq("k1"), eq(IdempotencyController.EchoResponse.class), any()))
                .thenReturn(new IdempotencyResult<>(resp, true));

        mockMvc.perform(post("/idempotency/echo")
                        .header("Idempotency-Key", "k1")
                        .contentType("application/json")
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("hi"))
                .andExpect(jsonPath("$.data.executionSeq").value(1))
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }
}
