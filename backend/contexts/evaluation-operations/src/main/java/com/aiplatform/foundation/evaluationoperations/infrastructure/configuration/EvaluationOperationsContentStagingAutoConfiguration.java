package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.infrastructure.staging.LocalEvaluationDatasetContentStagingAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 在私有对象存储明确启用时提供评测条目的短生命周期暂存与严格 JSON Lines 校验。
 */
@AutoConfiguration(before = EvaluationOperationsCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.object-storage.enabled",
        havingValue = "true")
@EnableConfigurationProperties(EvaluationOperationsRegistrationProperties.class)
public class EvaluationOperationsContentStagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EvaluationDatasetContentStagingPort.class)
    EvaluationDatasetContentStagingPort evaluationDatasetContentStagingPort(
            EvaluationOperationsRegistrationProperties properties) {
        return new LocalEvaluationDatasetContentStagingAdapter(
                properties.getStagingDirectory(),
                properties.getMaximumContentBytes());
    }
}
