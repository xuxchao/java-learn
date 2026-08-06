package com.example.ecommerce.idempotency;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口幂等演示端点（M5）。
 *
 * <p>客户端在下单/支付这类"写且不可重复"的请求里带上 {@code Idempotency-Key} 头，
 * 相同 key 的重复请求（网络重试、用户连点、网关重发）只会被真正处理一次——第二次直接返回首次的结果，
 * 且不会再次触发 {@code executionCount} 自增（无副作用）。响应头 {@code Idempotency-Replayed}
 * 标识本次是否命中重放。
 *
 * <p>本端点演示的是 <b>token 方案</b>（幂等键由客户端生成并携带）；另两种见 docs/concurrency-notes.md：
 * DB 唯一索引（本项目 {@code orders.order_no} 唯一，重复插入直接抛错）与 状态机
 * （订单状态流转 CREATED→PAID 用 guard 拒绝重复支付）。
 */
@RestController
@RequestMapping("/idempotency")
public class IdempotencyController {

    private final IdempotencyService idempotencyService;
    /** 仅用于演示"重放不会重复执行"——每次真正处理才 +1，重放时不变。 */
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public IdempotencyController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    public record EchoRequest(String message) {
    }

    public record EchoResponse(String message, int executionSeq) {
    }

    @PostMapping("/echo")
    public Result<EchoResponse> echo(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody EchoRequest req,
            HttpServletResponse response) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        IdempotencyResult<EchoResponse> result = idempotencyService.execute(key, EchoResponse.class, () -> {
            int seq = executionCount.incrementAndGet();
            return new EchoResponse(req.message(), seq);
        });
        response.setHeader("Idempotency-Replayed", String.valueOf(result.isReplayed()));
        return Result.success(result.getValue());
    }
}
