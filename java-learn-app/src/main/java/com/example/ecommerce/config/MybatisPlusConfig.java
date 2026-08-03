package com.example.ecommerce.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（M3 商品&数据库持久化）。
 *
 * <p>1. {@link MapperScan} 扫描 mapper 包，省去每个 Mapper 写 @Mapper。
 * <p>2. {@link MybatisPlusInterceptor} 是 MP 的插件链容器。这里只挂
 *    {@link OptimisticLockerInnerInterceptor}：支撑 Stock 上 {@code @Version} 乐观锁——
 *    执行 update 时自动把版本号加进 WHERE 并自增，命中 0 行即说明并发期间版本已变。
 *    （分页插件 PaginationInnerInterceptor 依赖独立的 jsqlparser 模块，M3 暂不需要分页，故不引入。）
 *
 * <p>面试考点：乐观锁本质是把"先读后写"的并发冲突交给数据库行版本号判定，避免丢失更新；
 * 它与事务隔离级别（默认 REPEATABLE READ）是两套互补机制。
 */
@Configuration
@MapperScan("com.example.ecommerce.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
