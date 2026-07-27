package com.aiplatform.foundation.bootstrap.controlplane.configuration;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.application.DefaultOperationsQueryService;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.PostgresQueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.api.operations.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import java.time.Clock;
import java.time.Duration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 控制面使用的查询用量和脱敏审计能力显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.enabled",
        havingValue = "true",
        matchIfMissing = true)
@MapperScan(basePackageClasses = QueryRunMapper.class)
public class EvaluationOperationsConfiguration {

    @Bean
    QueryOperationsFactSource queryOperationsFactSource(QueryRunMapper mapper) {
        return new PostgresQueryOperationsFactSource(mapper);
    }

    @Bean
    OperationsQueryUseCase operationsQueryUseCase(
            QueryOperationsFactSource facts,
            @Value("${foundation.query.quota.daily-limit:100}")
                    int dailyQueryLimit,
            @Value("${foundation.query.quota.concurrent-limit:4}")
                    int concurrentQueryLimit,
            @Value("${foundation.query.quota.running-lease:PT2M}")
                    Duration runningLease) {
        return new DefaultOperationsQueryService(
                facts,
                new QueryQuotaPolicy(
                        dailyQueryLimit,
                        concurrentQueryLimit,
                        runningLease),
                Clock.systemUTC());
    }
}
