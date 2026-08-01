package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryFeedbackMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryDiagnosticFactMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryCitationMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunEventMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunRequestMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryRunRepository;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository.PostgresQueryFeedbackRepository;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
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
    PostgresQueryRunRepository postgresQueryRunRepository(
            QueryRunMapper mapper,
            QueryDiagnosticFactMapper diagnosticMapper,
            QueryRunRequestMapper requestMapper,
            QueryCitationMapper citationMapper,
            QueryRunEventMapper eventMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactions) {
        return new PostgresQueryRunRepository(
                mapper,
                diagnosticMapper,
                requestMapper,
                citationMapper,
                eventMapper,
                identifiers,
                transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryDiagnosticFactLookup queryDiagnosticFactLookup(QueryDiagnosticFactMapper mapper) {
        return new PostgresQueryDiagnosticFactLookup(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryOperationsFactSource queryOperationsFactSource(QueryRunMapper mapper) {
        return new PostgresQueryOperationsFactSource(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryFeedbackRepository queryFeedbackRepository(QueryFeedbackMapper mapper) {
        return new PostgresQueryFeedbackRepository(mapper);
    }
}
