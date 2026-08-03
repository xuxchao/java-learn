package com.example.ecommerce.service;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StockMapper;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductService 单元测试：用 Mockito 替身 ProductMapper / StockMapper，不连库。
 * 聚焦两个关注点：
 *  1. CRUD 正确委派给 MyBatis-Plus 通用方法；
 *  2. 库存初始化（initStock）的前置校验（商品必须存在、不能重复初始化、数量非负）。
 */
class ProductServiceTest {

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final StockMapper stockMapper = mock(StockMapper.class);
    private final ProductService productService = new ProductService(productMapper, stockMapper);

    @Test
    void createProduct_inserts_and_returns_entity() {
        Product created = productService.createProduct("手机", new BigDecimal("1999.00"), "desc");
        verify(productMapper, times(1)).insert(any(Product.class));
        assertNotNull(created);
    }

    @Test
    void getProduct_not_found_throws() {
        when(productMapper.selectById(1L)).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class, () -> productService.getProduct(1L));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void initStock_requires_existing_product() {
        when(productMapper.selectById(1L)).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class, () -> productService.initStock(1L, 10));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void initStock_duplicate_throws() {
        when(productMapper.selectById(1L)).thenReturn(new Product("p", null, null));
        when(stockMapper.selectByProductId(1L)).thenReturn(new Stock(1L, 5, 5));
        ApiException ex = assertThrows(ApiException.class, () -> productService.initStock(1L, 10));
        assertEquals(ErrorCode.STOCK_ALREADY_INITIALIZED.getCode(), ex.getCode());
    }

    @Test
    void initStock_negative_throws() {
        when(productMapper.selectById(1L)).thenReturn(new Product("p", null, null));
        when(stockMapper.selectByProductId(1L)).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class, () -> productService.initStock(1L, -1));
        assertEquals(ErrorCode.ORDER_CREATE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void initStock_success_sets_available_equal_total() {
        when(productMapper.selectById(1L)).thenReturn(new Product("p", null, null));
        when(stockMapper.selectByProductId(1L)).thenReturn(null);
        Stock stock = productService.initStock(1L, 10);
        assertEquals(10, stock.getTotal());
        assertEquals(10, stock.getAvailable());
    }

    @Test
    void listProducts_returns_mapper_result() {
        Product p = new Product("p", BigDecimal.ZERO, null);
        when(productMapper.selectList(any())).thenReturn(Collections.singletonList(p));
        List<Product> list = productService.listProducts();
        assertEquals(1, list.size());
    }
}
