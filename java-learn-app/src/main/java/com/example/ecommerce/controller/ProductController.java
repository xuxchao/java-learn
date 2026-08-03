package com.example.ecommerce.controller;

import com.example.ecommerce.common.Result;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import com.example.ecommerce.service.LockType;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品与下单接口（M3）。路径前缀 /products，受 LoginInterceptor 保护（已在 WebConfig 注册）。
 *
 * <p>端点一览：
 * <pre>
 *   GET    /products                      商品列表
 *   GET    /products/{id}                 商品详情
 *   POST   /products                      新建商品
 *   PUT    /products/{id}                 更新商品
 *   DELETE /products/{id}                 删除商品
 *   POST   /products/{id}/stock           初始化库存（total 件，available=total）
 *   GET    /products/{id}/stock           查询库存
 *   POST   /products/{id}/order           下单（默认乐观锁，可传 lockType=PESSIMISTIC 走悲观锁）
 * </pre>
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final OrderService orderService;

    public ProductController(ProductService productService, OrderService orderService) {
        this.productService = productService;
        this.orderService = orderService;
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
        return Result.success(productService.listProducts());
    }

    @GetMapping("/{id}")
    public Result<Product> get(@PathVariable Long id) {
        return Result.success(productService.getProduct(id));
    }

    @PostMapping
    public Result<Product> create(@RequestBody CreateProductRequest req) {
        return Result.success(productService.createProduct(req.name(), req.price(), req.description()));
    }

    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody UpdateProductRequest req) {
        return Result.success(productService.updateProduct(id, req.name(), req.price(), req.description()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @PostMapping("/{id}/stock")
    public Result<Stock> initStock(@PathVariable Long id, @RequestBody InitStockRequest req) {
        return Result.success(productService.initStock(id, req.total()));
    }

    @GetMapping("/{id}/stock")
    public Result<Stock> getStock(@PathVariable Long id) {
        return Result.success(productService.getStock(id));
    }

    @PostMapping("/{id}/order")
    public Result<Order> order(@PathVariable Long id,
                              @RequestBody OrderRequest req) {
        LockType lockType = (req.lockType() == null) ? LockType.OPTIMISTIC : req.lockType();
        Order order = orderService.placeOrder(req.userId(), id, req.quantity(), lockType);
        return Result.success(order);
    }
}
