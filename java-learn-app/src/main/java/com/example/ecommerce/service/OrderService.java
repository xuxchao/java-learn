package com.example.ecommerce.service;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StockMapper;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 下单业务层（M3 核心）。一条"下单即扣库存"的链路被放进同一个数据库事务：
 *
 * <pre>
 *   1. 查商品算金额（amount = price * quantity）
 *   2. 查库存并校验可用量
 *   3. 扣减库存（乐观锁 CAS / 悲观锁 for update 二选一）
 *   4. 生成订单（order_no 唯一，作为后续幂等键）
 * </pre>
 *
 * 隔离级别：Spring 默认沿用 MySQL 的 REPEATABLE READ。
 * 乐观锁路径：用单条原子 SQL 完成 CAS（Compare-And-Swap）——"比较 available &gt;= 数量 并原子扣减"，
 * 不依赖版本号字段；命中 0 行即说明并发期间该行已被他人改动，调用方应重试。
 */
@Service
public class OrderService {

    private final ProductMapper productMapper;
    private final StockMapper stockMapper;
    private final OrderMapper orderMapper;

    public OrderService(ProductMapper productMapper, StockMapper stockMapper, OrderMapper orderMapper) {
        this.productMapper = productMapper;
        this.stockMapper = stockMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public Order placeOrder(Long userId, Long productId, int quantity, LockType lockType) {
        if (quantity <= 0) {
            throw new ApiException(ErrorCode.ORDER_CREATE_FAILED, "购买数量必须 > 0");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        BigDecimal amount = product.getPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        return (lockType == LockType.PESSIMISTIC)
                ? placeOrderPessimistic(userId, product, quantity, amount)
                : placeOrderOptimistic(userId, product, quantity, amount);
    }

    /**
     * CAS 乐观锁路径：不依赖任何版本号字段，用单条原子 SQL 完成
     * "比较可用量 + 扣减"（Compare-And-Swap）。
     * 若读取时可用量已不足 → STOCK_NOT_ENOUGH；若读取时尚足、但 UPDATE 命中 0 行
     * （并发期间被其他事务消耗）→ STOCK_CONFLICT，调用方应重试。
     */
    @Transactional
    public Order placeOrderOptimistic(Long userId, Product product, int quantity, BigDecimal amount) {
        Stock stock = stockMapper.selectByProductId(product.getId());
        if (stock == null) {
            throw new ApiException(ErrorCode.STOCK_NOT_INITIALIZED);
        }
        if (stock.getAvailable() < quantity) {
            throw new ApiException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        // CAS：原子比较 available >= quantity 并扣减，彻底消除"先读后写"的竞态窗口
        int affected = stockMapper.decreaseAvailable(stock.getId(), quantity);
        if (affected == 0) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "库存并发冲突，请重试");
        }
        return insertOrder(userId, product, quantity, amount);
    }

    /**
     * 悲观锁路径：以 {@code SELECT ... FOR UPDATE} 在事务内锁定库存行，
     * 直到本事务提交才释放，期间其它事务无法修改该行——绝对不会冲突，但并发度低。
     * 扣减同样走 CAS 原子 SQL（行已锁，必然命中 1 行）。
     */
    @Transactional
    public Order placeOrderPessimistic(Long userId, Product product, int quantity, BigDecimal amount) {
        Stock stock = stockMapper.selectByProductIdForUpdate(product.getId());
        if (stock == null) {
            throw new ApiException(ErrorCode.STOCK_NOT_INITIALIZED);
        }
        if (stock.getAvailable() < quantity) {
            throw new ApiException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        int affected = stockMapper.decreaseAvailable(stock.getId(), quantity);
        if (affected == 0) {
            throw new ApiException(ErrorCode.STOCK_NOT_ENOUGH, "悲观锁下不应发生：行已被锁定");
        }
        return insertOrder(userId, product, quantity, amount);
    }

    private Order insertOrder(Long userId, Product product, int quantity, BigDecimal amount) {
        Order order = new Order(
                generateOrderNo(),
                userId,
                product.getId(),
                quantity,
                amount,
                "CREATED");
        orderMapper.insert(order);
        return order;
    }

    private String generateOrderNo() {
        long ts = System.currentTimeMillis();
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "ORD" + ts + rnd;
    }
}
