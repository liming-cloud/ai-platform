package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.domain.operations.model.OperationsQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册评测运维应用服务，事实源缺失时拒绝创建不完整能力。
 */
@AutoConfiguration
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        name = "foundation.control-plane.enabled",
        havingValue = "true")
@EnableConfigurationProperties(QueryQuotaReportingProperties.class)
public class EvaluationOperationsCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OperationsQueryService operationsQueryService(
            QueryOperationsFactSource facts,
            QueryQuotaReportingProperties properties) {
        return new OperationsQueryService(
                facts,
                new OperationsQuotaPolicy(
                        properties.dailyLimit(),
                        properties.concurrentLimit()),
                Clock.systemUTC());
    }
}
