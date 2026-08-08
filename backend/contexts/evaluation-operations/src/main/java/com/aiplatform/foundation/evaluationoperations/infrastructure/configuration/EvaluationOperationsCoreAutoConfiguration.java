package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetEntryReader;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationDatasetRegistrationService;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationRunExecutionService;
import com.aiplatform.foundation.evaluationoperations.application.release.service.EvaluationReleasePublicationAuthorization;
import com.aiplatform.foundation.evaluationoperations.application.release.service.ReleaseDecisionService;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy.QualityGatePolicy;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.assistantdelivery.application.release.facade.ReleasePublicationAuthorization;
import com.aiplatform.foundation.assistantdelivery.infrastructure.configuration.AssistantDeliveryCoreAutoConfiguration;
import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.domain.operations.model.OperationsQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册评测运维应用服务，事实源缺失时拒绝创建不完整能力。
 */
@AutoConfiguration(after = {
    AssistantDeliveryCoreAutoConfiguration.class,
    EvaluationOperationsPersistenceAutoConfiguration.class,
    EvaluationOperationsObjectStorageAutoConfiguration.class,
    EvaluationOperationsContentStagingAutoConfiguration.class
})
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
    @ConditionalOnBean({
        EvaluationDatasetRegistrationStore.class,
        EvaluationDatasetPrivateContentStore.class,
        EvaluationDatasetContentStagingPort.class,
        EntityIdGenerator.class
    })
    EvaluationDatasetRegistrationService evaluationDatasetRegistrationService(
            EvaluationDatasetRegistrationStore datasets,
            EvaluationDatasetPrivateContentStore contents,
            EvaluationDatasetContentStagingPort staging,
            EntityIdGenerator identifiers) {
        return new EvaluationDatasetRegistrationService(
                datasets,
                contents,
                staging,
                identifiers,
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        EvaluationDatasetRepository.class,
        EvaluationRunRepository.class,
        EvaluationDatasetPrivateContentStore.class,
        EvaluationDatasetEntryReader.class,
        EvaluationAssistantExecution.class,
        EntityIdGenerator.class
    })
    EvaluationRunExecutionService evaluationRunExecutionService(
            EvaluationDatasetRepository datasets,
            EvaluationRunRepository runs,
            EvaluationDatasetPrivateContentStore contents,
            EvaluationAssistantExecution assistant,
            EvaluationDatasetEntryReader entries,
            EntityIdGenerator identifiers) {
        return new EvaluationRunExecutionService(
                datasets,
                runs,
                contents,
                assistant,
                entries,
                QualityGatePolicy.qualityGateV1(),
                identifiers,
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        EvaluationRunReleaseFactLookup.class,
        ReleaseDecisionRepository.class,
        EntityIdGenerator.class
    })
    ReleaseDecisionService releaseDecisionService(
            EvaluationRunReleaseFactLookup runs,
            ReleaseDecisionRepository decisions,
            EntityIdGenerator identifiers) {
        return new ReleaseDecisionService(runs, decisions, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(ReleasePublicationAuthorization.class)
    @ConditionalOnBean({EvaluationRunReleaseFactLookup.class, ReleaseDecisionRepository.class})
    ReleasePublicationAuthorization releasePublicationAuthorization(
            EvaluationRunReleaseFactLookup runs, ReleaseDecisionRepository decisions) {
        return new EvaluationReleasePublicationAuthorization(runs, decisions);
    }

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
