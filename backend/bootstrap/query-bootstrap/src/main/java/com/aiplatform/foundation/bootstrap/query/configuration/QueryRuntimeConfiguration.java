package com.aiplatform.foundation.bootstrap.query.configuration;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.PostgresQueryRunRepository;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunEventUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.application.candidate.DefaultCandidateSearchService;
import com.aiplatform.foundation.trustedquery.application.run.DefaultQueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.run.DefaultQueryRunService;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import java.time.Clock;
import java.time.Duration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 在线查询进程的会话恢复、嵌入和向量检索显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = {
    QueryRunMapper.class
})
public class QueryRuntimeConfiguration {

    @Bean
    CandidateSearchUseCase candidateSearchUseCase(
            ActiveIndexLookup activeIndexes,
            ModelRuntimeResolver models,
            PgVectorIndexStore candidates) {
        return new DefaultCandidateSearchService(
                activeIndexes,
                models,
                candidates);
    }

    @Bean
    QueryRunRepository queryRunRepository(
            QueryRunMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresQueryRunRepository(mapper, transactions);
    }

    @Bean
    QueryRunUseCase queryRunUseCase(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            PgVectorIndexStore candidates,
            QueryRunRepository repository,
            EntityIdGenerator identifiers,
            @Value("${foundation.query.quota.daily-limit:100}")
                    int dailyQueryLimit,
            @Value("${foundation.query.quota.concurrent-limit:4}")
                    int concurrentQueryLimit,
            @Value("${foundation.query.quota.running-lease:PT2M}")
                    Duration runningLease) {
        return new DefaultQueryRunService(
                assistants,
                models,
                candidates,
                repository,
                new QueryQuotaPolicy(
                        dailyQueryLimit,
                        concurrentQueryLimit,
                        runningLease),
                Clock.systemUTC(),
                identifiers);
    }

    @Bean
    QueryRunEventUseCase queryRunEventUseCase(QueryRunRepository repository) {
        return new DefaultQueryRunEventService(repository);
    }
}
