package com.example.ecommerce;

import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StockMapper;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import com.example.ecommerce.service.LockType;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * M3 数据库集成测试：依赖本地 MySQL（docker compose up -d mysql）。
 * 每个用例用 {@code @Transactional} 包裹，方法结束自动回滚，互不污染。
 *
 * <p>验证三项 M3 核心能力：
 *  1. 下单扣库存（CAS 乐观锁）：库存扣减正确、订单金额 = 单价 × 数量；
 *  2. 下单扣库存（悲观锁 FOR UPDATE）：同样正确扣减；
 *  3. EXPLAIN 验证索引：精确匹配走 idx_products_name，对列做函数运算导致索引失效。
 */
@SpringBootTest
class ProductDbIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private StockMapper stockMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void placeOrder_optimistic_deducts_stock_via_cas() {
        Product p = productService.createProduct("iPhone15", new BigDecimal("100.00"), "desc");
        productService.initStock(p.getId(), 10);

        Order order = orderService.placeOrder(1L, p.getId(), 3, LockType.OPTIMISTIC);

        Stock after = stockMapper.selectByProductId(p.getId());
        assertEquals(7, after.getAvailable(), "CAS 乐观锁下单后可用库存应为 10-3=7");
        assertEquals(3, order.getQuantity());
        assertEquals(new BigDecimal("300.00"), order.getAmount());
        assertNotNull(order.getOrderNo());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void placeOrder_pessimistic_deducts_stock() {
        Product p = productService.createProduct("iPhone15", new BigDecimal("100.00"), "desc");
        productService.initStock(p.getId(), 10);

        Order order = orderService.placeOrder(1L, p.getId(), 4, LockType.PESSIMISTIC);

        Stock after = stockMapper.selectByProductId(p.getId());
        assertEquals(6, after.getAvailable(), "悲观锁下单后可用库存应为 10-4=6");
        assertEquals(4, order.getQuantity());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void explain_index_used_for_exact_match_but_not_for_function() {
        productService.createProduct("iPhone15", new BigDecimal("100.00"), "desc");

        // 精确匹配：应命中 idx_products_name（key 不为空）
        Map<String, Object> good = jdbcTemplate.queryForMap(
                "EXPLAIN SELECT * FROM products WHERE name = 'iPhone15'");
        assertNotNull(good.get("key"), "精确匹配 name 应走索引");
        assertEquals("idx_products_name", good.get("key"));

        // 对列做函数运算：索引失效（type=ALL，key 为 null）—— 索引失效的典型场景
        Map<String, Object> bad = jdbcTemplate.queryForMap(
                "EXPLAIN SELECT * FROM products WHERE LEFT(name, 1) = 'i'");
        assertNull(bad.get("key"), "对列使用函数会导致索引失效");
    }
}
