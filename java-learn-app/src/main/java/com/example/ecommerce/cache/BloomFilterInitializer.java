package com.example.ecommerce.cache;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.ProductService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把已存在的商品主键灌进布隆过滤器。
 *
 * <p>布隆过滤器是内存结构，进程重启会清空。若不在启动时回填，重启后对已存在商品的
 * {@code mightContain} 会返回 false，被误判为"不存在"而直接拒绝（虽不会返回错误数据，
 * 但丢失了缓存加速）。启动时从 DB 全量加载一次主键即可恢复过滤器状态。
 */
@Component
public class BloomFilterInitializer implements CommandLineRunner {

    private final ProductService productService;
    private final ProductIdBloomFilter bloomFilter;

    public BloomFilterInitializer(ProductService productService, ProductIdBloomFilter bloomFilter) {
        this.productService = productService;
        this.bloomFilter = bloomFilter;
    }

    @Override
    public void run(String... args) {
        List<Product> all = productService.listProducts();
        bloomFilter.addAll(all.stream().map(Product::getId).toList());
    }
}
