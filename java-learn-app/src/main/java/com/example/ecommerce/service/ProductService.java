package com.example.ecommerce.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.mapper.StockMapper;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Stock;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品与库存业务层（M3）。
 *
 * <p>直接复用 MyBatis-Plus 的 {@code BaseMapper} 通用 CRUD（selectList / selectById /
 * insert / updateById / deleteById），演示"零 XML"的持久化写法。
 *
 * <p>约定：创建商品与初始化库存分开——商品建好后通过 {@link #initStock} 单独建库存行，
 * 这样库存的并发语义（乐观锁版本号）更清晰，也贴近真实电商"商品上架 + 备货"两步。
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final StockMapper stockMapper;

    public ProductService(ProductMapper productMapper, StockMapper stockMapper) {
        this.productMapper = productMapper;
        this.stockMapper = stockMapper;
    }

    public List<Product> listProducts() {
        return productMapper.selectList(new QueryWrapper<Product>().orderByDesc("id"));
    }

    public Product getProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    public Product createProduct(String name, BigDecimal price, String description) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.ORDER_CREATE_FAILED, "商品名称不能为空");
        }
        Product product = new Product(name, price, description);
        productMapper.insert(product);
        return product;
    }

    public Product updateProduct(Long id, String name, BigDecimal price, String description) {
        Product existing = getProduct(id);
        if (name != null) {
            existing.setName(name);
        }
        if (price != null) {
            existing.setPrice(price);
        }
        if (description != null) {
            existing.setDescription(description);
        }
        productMapper.updateById(existing);
        return existing;
    }

    public void deleteProduct(Long id) {
        getProduct(id);   // 不存在则抛 PRODUCT_NOT_FOUND
        productMapper.deleteById(id);
    }

    public Stock getStock(Long productId) {
        Stock stock = stockMapper.selectByProductId(productId);
        if (stock == null) {
            throw new ApiException(ErrorCode.STOCK_NOT_INITIALIZED);
        }
        return stock;
    }

    public Stock initStock(Long productId, Integer total) {
        getProduct(productId);   // 商品不存在则抛 PRODUCT_NOT_FOUND
        if (total == null || total < 0) {
            throw new ApiException(ErrorCode.ORDER_CREATE_FAILED, "库存数量不能为负");
        }
        if (stockMapper.selectByProductId(productId) != null) {
            throw new ApiException(ErrorCode.STOCK_ALREADY_INITIALIZED);
        }
        Stock stock = new Stock(productId, total, total);
        stockMapper.insert(stock);
        return stock;
    }
}
