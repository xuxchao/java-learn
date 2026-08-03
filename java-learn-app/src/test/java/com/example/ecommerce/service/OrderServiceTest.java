package com.example.ecommerce.service;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StockMapper;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService 单元测试：mock 三个 Mapper，不连库。
 * 重点验证 M3 的并发控制两条路径：
 *   - 乐观锁：updateById 命中 0 行 → 抛 STOCK_CONFLICT；
 *   - 悲观锁：先 FOR UPDATE 锁行再扣减，无需版本号。
 * 以及库存不足、商品不存在等前置校验。
 */
class OrderServiceTest {

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final StockMapper stockMapper = mock(StockMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderService orderService = new OrderService(productMapper, stockMapper, orderMapper);

    private Product product(Long id, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setPrice(price);
        return p;
    }

    @Test
    void placeOrder_optimistic_success_deducts_and_inserts() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, new BigDecimal("10.00")));
        Stock stock = new Stock(1L, 5, 5);
        stock.setId(100L);
        when(stockMapper.selectByProductId(1L)).thenReturn(stock);
        when(stockMapper.updateById(any(Stock.class))).thenReturn(1);  // 乐观锁命中

        Order order = orderService.placeOrder(7L, 1L, 2, LockType.OPTIMISTIC);

        verify(orderMapper).insert(any(Order.class));
        assertEquals(new BigDecimal("20.00"), order.getAmount());
        assertEquals(2, order.getQuantity());
    }

    @Test
    void placeOrder_optimistic_conflict_throws() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, new BigDecimal("10.00")));
        Stock stock = new Stock(1L, 5, 5);
        stock.setId(100L);
        when(stockMapper.selectByProductId(1L)).thenReturn(stock);
        when(stockMapper.updateById(any(Stock.class))).thenReturn(0);  // 并发期间版本已变

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.placeOrder(7L, 1L, 2, LockType.OPTIMISTIC));
        assertEquals(ErrorCode.STOCK_CONFLICT.getCode(), ex.getCode());
        // 冲突时不应落单
        verify(orderMapper, org.mockito.Mockito.never()).insert(any(Order.class));
    }

    @Test
    void placeOrder_pessimistic_locks_row_then_deducts() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, new BigDecimal("10.00")));
        Stock stock = new Stock(1L, 5, 5);
        stock.setId(100L);
        when(stockMapper.selectByProductIdForUpdate(1L)).thenReturn(stock);
        when(stockMapper.updateById(any(Stock.class))).thenReturn(1);

        Order order = orderService.placeOrder(7L, 1L, 3, LockType.PESSIMISTIC);

        verify(stockMapper).selectByProductIdForUpdate(1L);
        verify(orderMapper).insert(any(Order.class));
        assertEquals(3, order.getQuantity());
    }

    @Test
    void placeOrder_insufficient_stock_throws() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, new BigDecimal("10.00")));
        Stock stock = new Stock(1L, 5, 1);
        stock.setId(100L);
        when(stockMapper.selectByProductId(1L)).thenReturn(stock);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.placeOrder(7L, 1L, 5, LockType.OPTIMISTIC));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
    }

    @Test
    void placeOrder_product_not_found_throws() {
        when(productMapper.selectById(99L)).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.placeOrder(7L, 99L, 1, LockType.OPTIMISTIC));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }
}
