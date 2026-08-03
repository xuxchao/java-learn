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
 *   3. 扣减库存（乐观锁 version / 悲观锁 for update 二选一）
 *   4. 生成订单（order_no 唯一，作为后续幂等键）
 * </pre>
 *
 * 隔离级别：Spring 默认沿用 MySQL 的 REPEATABLE READ，配合行锁/版本号解决并发扣减。
 * 乐观锁路径：updateById 由 MyBatis-Plus 自动带上 {@code version} 条件，
 * 命中 0 行即说明并发期间该行已被他人改动——调用方应重试或向用户提示冲突。
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
     * 乐观锁路径：先读后写，靠 version 字段兜底。
     * 若两次读取之间该行被别的事务改过，updateById 命中 0 行 → 抛 STOCK_CONFLICT。
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
        stock.setAvailable(stock.getAvailable() - quantity);
        int affected = stockMapper.updateById(stock);   // WHERE id=? AND version=?
        if (affected == 0) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "库存并发冲突，请重试");
        }
        return insertOrder(userId, product, quantity, amount);
    }

    /**
     * 悲观锁路径：以 {@code SELECT ... FOR UPDATE} 在事务内锁定库存行，
     * 直到本事务提交才释放，期间其它事务无法修改该行——绝对不会冲突，但并发度低。
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
        stock.setAvailable(stock.getAvailable() - quantity);
        stockMapper.updateById(stock);   // 行已锁，无需 version
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
