package com.aiplatform.foundation.bootstrap.controlplane.configuration;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.application.DefaultOperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.domain.usage.OperationsQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import java.time.Clock;
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
public class EvaluationOperationsConfiguration {

    @Bean
    OperationsQueryUseCase operationsQueryUseCase(
            QueryOperationsFactSource facts,
            @Value("${foundation.query.quota.daily-limit:100}")
                    int dailyQueryLimit,
            @Value("${foundation.query.quota.concurrent-limit:4}")
                    int concurrentQueryLimit) {
        return new DefaultOperationsQueryService(
                facts,
                new OperationsQuotaPolicy(
                        dailyQueryLimit,
                        concurrentQueryLimit),
                Clock.systemUTC());
    }
}
