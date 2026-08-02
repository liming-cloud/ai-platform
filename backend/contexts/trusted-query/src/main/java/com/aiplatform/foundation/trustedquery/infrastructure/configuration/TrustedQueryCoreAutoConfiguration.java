package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.trustedquery.application.candidate.service.CandidateSearchService;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryRunTerminalCommitter;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.application.diagnostic.service.QueryDiagnosticService;
import com.aiplatform.foundation.trustedquery.application.feedback.service.QueryFeedbackService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunService;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 注册在线查询应用服务；所有依赖缺失均直接阻止查询进程启动。
 */
@AutoConfiguration(after = TrustedQueryPersistenceAutoConfiguration.class)
@ConditionalOnProperty(name = "foundation.query.enabled", havingValue = "true")
@EnableConfigurationProperties(TrustedQueryProperties.class)
public class TrustedQueryCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CandidateSearchService candidateSearchService(
            KnowledgeSpaceLookup knowledgeSpaces,
            ActiveIndexLookup indexes,
            ModelRuntimeResolver models,
            ObjectProvider<UsableModelConfigurationLookup> modelConfigurations,
            ObjectProvider<GovernedEmbeddingRuntime> governedEmbeddings,
            IndexCandidateRetriever candidates) {
        return new CandidateSearchService(
                knowledgeSpaces, indexes, models,
                modelConfigurations.getIfAvailable(),
                governedEmbeddings.getIfAvailable(), candidates);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryRunService queryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            ObjectProvider<UsableModelConfigurationLookup> modelConfigurations,
            ObjectProvider<GovernedEmbeddingRuntime> governedEmbeddings,
            ObjectProvider<GovernedChatRuntime> governedChats,
            ObjectProvider<ChatRouteExecutor> chatRoutes,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            QueryRunTerminalCommitter terminalCommitter,
            EntityIdGenerator identifiers,
            TrustedQueryProperties properties) {
        return new QueryRunService(
                assistants,
                models,
                modelConfigurations.getIfAvailable(),
                governedEmbeddings.getIfAvailable(),
                governedChats.getIfAvailable(),
                chatRoutes.getIfAvailable(),
                candidates,
                repository,
                terminalCommitter,
                new QueryQuotaPolicy(
                        properties.dailyLimit(),
                        properties.concurrentLimit(),
                        properties.runningLease()),
                Clock.systemUTC(),
                identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryRunEventService queryRunEventService(QueryRunRepository repository) {
        return new QueryRunEventService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(QueryDiagnosticFactLookup.class)
    QueryDiagnosticService queryDiagnosticService(
            QueryRunRepository repository,
            QueryDiagnosticFactLookup facts) {
        return new QueryDiagnosticService(repository, facts);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryFeedbackService queryFeedbackService(
            QueryRunRepository runs,
            QueryFeedbackRepository feedback,
            EntityIdGenerator identifiers) {
        return new QueryFeedbackService(
                runs,
                feedback,
                Clock.systemUTC(),
                identifiers);
    }
}
