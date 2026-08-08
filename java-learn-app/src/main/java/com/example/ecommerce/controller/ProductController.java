package com.example.ecommerce.controller;

import com.example.ecommerce.cache.ProductCacheService;
import com.example.ecommerce.common.Result;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import com.example.ecommerce.mq.OrderCreatedEvent;
import com.example.ecommerce.mq.OrderEventPublisher;
import com.example.ecommerce.service.LockType;
import com.example.ecommerce.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品与下单接口（M3 读写 + M4 缓存）。
 * 路径前缀 /products，受 LoginInterceptor 保护（已在 WebConfig 注册）。
 *
 * <p>商品详情读取走 {@link ProductCacheService}（缓存优先 + 三大问题防护）。
 * GET /products/{id} 会额外返回 {@code X-Cache: HIT/MISS} 响应头，便于直观验证缓存是否生效。
 *
 * <p>端点一览：
 * <pre>
 *   GET    /products                      商品列表（未走缓存）
 *   GET    /products/{id}                 商品详情（缓存优先，带 X-Cache 头）
 *   POST   /products                      新建商品（写入后入布隆过滤器）
 *   PUT    /products/{id}                 更新商品（写后删缓存 + 延迟双删）
 *   DELETE /products/{id}                 删除商品（删缓存）
 *   POST   /products/{id}/stock           初始化库存
 *   GET    /products/{id}/stock           查询库存
 *   POST   /products/{id}/order           下单（乐观/悲观锁）
 * </pre>
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductCacheService productCacheService;
    private final OrderService orderService;
    private final OrderEventPublisher orderEventPublisher;

    public ProductController(ProductCacheService productCacheService,
                             OrderService orderService,
                             OrderEventPublisher orderEventPublisher) {
        this.productCacheService = productCacheService;
        this.orderService = orderService;
        this.orderEventPublisher = orderEventPublisher;
    }

    public record CreateProductRequest(String name, BigDecimal price, String description) {
    }

    public record UpdateProductRequest(String name, BigDecimal price, String description) {
    }

    public record InitStockRequest(Integer total) {
    }

    public record OrderRequest(Long userId, Integer quantity, LockType lockType) {
    }

    @GetMapping
    public Result<List<Product>> list() {
        return Result.success(productCacheService.listProducts());
    }

    @GetMapping("/{id}")
    public Result<Product> get(@PathVariable Long id, HttpServletResponse response) {
        ProductCacheService.CacheResult r = productCacheService.getProductWithCacheInfo(id);
        response.setHeader("X-Cache", r.fromCache() ? "HIT" : "MISS");
        return Result.success(r.product());
    }

    @PostMapping
    public Result<Product> create(@RequestBody CreateProductRequest req) {
        return Result.success(productCacheService.createProduct(req.name(), req.price(), req.description()));
    }

    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody UpdateProductRequest req) {
        return Result.success(productCacheService.updateProduct(id, req.name(), req.price(), req.description()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productCacheService.deleteProduct(id);
        return Result.success();
    }

    @PostMapping("/{id}/stock")
    public Result<Stock> initStock(@PathVariable Long id, @RequestBody InitStockRequest req) {
        return Result.success(productCacheService.initStock(id, req.total()));
    }

    @GetMapping("/{id}/stock")
    public Result<Stock> getStock(@PathVariable Long id) {
        return Result.success(productCacheService.getStock(id));
    }

    @PostMapping("/{id}/order")
    public Result<Order> order(@PathVariable Long id,
                               @RequestBody OrderRequest req) {
        LockType lockType = (req.lockType() == null) ? LockType.OPTIMISTIC : req.lockType();
        Order order = orderService.placeOrder(req.userId(), id, req.quantity(), lockType);
        // M6：订单落库成功后发消息到 MQ，下游异步处理（削峰 / 解耦）。幂等由消费者侧保证。
        orderEventPublisher.publish(OrderCreatedEvent.from(order));
        return Result.success(order);
    }
}
