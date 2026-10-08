package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.service.EvaluationAssistantExecutionService;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.DraftAssistantVersionLookupService;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.PublishedAssistantVersionLookupService;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service.AssistantTestRunService;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out.AssistantTestRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.service.DefaultQueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册助手应用服务与跨上下文发布门面。
 */
@AutoConfiguration(after = AssistantDeliveryPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.assistant-delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AssistantTestRunProperties.class)
public class AssistantDeliveryCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup publishedAssistantAuthorizationLookup(AssistantRepository repository) {
        return new com.aiplatform.foundation.assistantdelivery.application.assistant.service.PublishedAssistantAuthorizationLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    PublishedAssistantVersionLookup publishedAssistantVersionLookup(
            AssistantRepository repository) {
        return new PublishedAssistantVersionLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    DraftAssistantVersionLookup draftAssistantVersionLookup(
            AssistantRepository repository) {
        return new DraftAssistantVersionLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryAssistantCatalogService queryAssistantCatalogService(
            AssistantRepository repository) {
        return new DefaultQueryAssistantCatalogService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        ModelRuntimeResolver.class,
        UsableModelConfigurationLookup.class,
        GovernedEmbeddingRuntime.class,
        GovernedChatRuntime.class,
        ChatRouteExecutor.class,
        IndexCandidateRetriever.class
    })
    AssistantTrustedAnswerExecutor assistantTrustedAnswerExecutor(
            ModelRuntimeResolver models,
        UsableModelConfigurationLookup configurations,
        GovernedEmbeddingRuntime embeddings,
        GovernedChatRuntime chats,
        ChatRouteExecutor chatRoutes,
        IndexCandidateRetriever candidates) {
        return new AssistantTrustedAnswerExecutor(
                models, configurations, embeddings, chats, chatRoutes, candidates);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AssistantTrustedAnswerExecutor.class)
    EvaluationAssistantExecution evaluationAssistantExecution(
            AssistantRepository repository,
            AssistantTrustedAnswerExecutor executor) {
        return new EvaluationAssistantExecutionService(repository, executor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        AssistantTestRunRepository.class,
        AssistantTestRunTerminalCommitter.class,
        AssistantTrustedAnswerExecutor.class
    })
    AssistantTestRunService assistantTestRunService(
            DraftAssistantVersionLookup drafts,
            AssistantTestRunRepository repository,
            AssistantTestRunTerminalCommitter terminalCommitter,
            AssistantTrustedAnswerExecutor executor,
            EntityIdGenerator identifiers,
            AssistantTestRunProperties properties) {
        return new AssistantTestRunService(
                drafts, repository, terminalCommitter,
                new AssistantTestQuotaPolicy(
                        properties.dailyLimit(), properties.concurrentLimit(), properties.runningLease()),
                executor, Clock.systemUTC(), identifiers);
    }

}
