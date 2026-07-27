package com.aiplatform.foundation.persistence.configuration;

import com.aiplatform.foundation.persistence.identifier.MybatisPlusSnowflakeIdGenerator;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 统一提供 MyBatis-Plus 标识生成和基础安全拦截器。
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformPersistenceProperties.class)
public class PlatformPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MybatisPlusSnowflakeIdGenerator platformSnowflakeIdGenerator(
            PlatformPersistenceProperties properties) {
        return new MybatisPlusSnowflakeIdGenerator(
                properties.workerId(),
                properties.datacenterId());
    }

    @Bean
    @ConditionalOnMissingBean
    MybatisPlusInterceptor platformMybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
