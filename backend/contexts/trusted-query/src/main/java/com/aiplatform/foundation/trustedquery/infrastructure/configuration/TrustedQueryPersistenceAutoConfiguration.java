package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryRunRepository;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 注册查询事实持久化与脱敏运营事实投影。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = QueryRunMapper.class)
public class TrustedQueryPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QueryRunRepository queryRunRepository(
            QueryRunMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresQueryRunRepository(mapper, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryOperationsFactSource queryOperationsFactSource(QueryRunMapper mapper) {
        return new PostgresQueryOperationsFactSource(mapper);
    }
}
