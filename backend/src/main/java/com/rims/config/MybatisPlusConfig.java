package com.rims.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分页插件单独放置：WebConfig 依赖 AuthInterceptor（进而依赖 Mapper），
 * 而 Mapper 的自动配置又依赖本插件，放在同一个配置类里会形成循环依赖。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁：状态流转（updateById）自动带 WHERE version=? 并自增；并发冲突时影响 0 行，
        // 由 WorkflowService/FinanceService 检查受影响行数后抛出冲突异常，避免静默丢更新。
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
