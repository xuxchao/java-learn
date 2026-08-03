package com.example.ecommerce.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（M3 商品&数据库持久化）。
 *
 * <p>1. {@link MapperScan} 扫描 mapper 包，省去每个 Mapper 写 @Mapper。
 * <p>2. {@link MybatisPlusInterceptor} 是 MP 的插件链容器，作为扩展点保留。
 *    本项目的扣库存并发控制已改为 CAS 乐观锁（单条原子 SQL 比较并扣减 available），
 *    不再依赖 {@code @Version} 版本号字段，因此不再挂 {@code OptimisticLockerInnerInterceptor}。
 *    （若后续需要分页，可在此追加 {@code PaginationInnerInterceptor}，它依赖独立的 jsqlparser 模块。）
 *
 * <p>面试考点：CAS 乐观锁本质是让数据库在单条语句里完成 compare-and-set，
 * 比"先读后写 + 版本号"更彻底地避免丢失更新；它与事务隔离级别（默认 REPEATABLE READ）互补。
 */
@Configuration
@MapperScan("com.example.ecommerce.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }
}
