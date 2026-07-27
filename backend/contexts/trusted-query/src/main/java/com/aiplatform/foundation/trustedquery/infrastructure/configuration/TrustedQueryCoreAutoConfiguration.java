package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.trustedquery.application.candidate.service.CandidateSearchService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunService;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
            IndexCandidateRetriever candidates) {
        return new CandidateSearchService(knowledgeSpaces, indexes, models, candidates);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryRunService queryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            EntityIdGenerator identifiers,
            TrustedQueryProperties properties) {
        return new QueryRunService(
                assistants,
                models,
                candidates,
                repository,
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
}
